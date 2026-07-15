package org.chenile.filewatch.handler;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import org.chenile.base.exception.ServerException;
import org.chenile.configuration.filewatch.FileWatchProperties;
import org.chenile.core.errorcodes.ErrorCodes;
import org.chenile.filewatch.model.FileWatchDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.SmartLifecycle;

/**
 * Coordinates file-watch registration, event-loop lifecycle and reconciliation scans.
 */
public class FileWatcherExecutorService implements SmartLifecycle, DisposableBean {
	private static final Logger logger = LoggerFactory.getLogger(FileWatcherExecutorService.class);

	private final FileWatchProperties properties;
	private final FileSystem fileSystem;
	private final WatchService watcher;
	private final ExecutorService watcherExecutor = Executors.newSingleThreadExecutor(r -> {
		Thread thread = new Thread(r, "chenile-filewatch-loop");
		thread.setDaemon(true);
		return thread;
	});
	private final Map<WatchKey, WatchInfo> watchKeyToInfoMap = new ConcurrentHashMap<>();
	private final Map<Path, Boolean> inFlightHeaders = new ConcurrentHashMap<>();
	private final AtomicBoolean running = new AtomicBoolean(false);
	private Instant lastReconciliationScan = Instant.EPOCH;

	@Autowired
	protected ExecutorService executorService;
	@Autowired
	private FileProcessor fileProcessor;
	private Future<?> watcherTask;

	public FileWatcherExecutorService(FileWatchProperties properties, FileSystem fileSystem) {
		this.properties = properties;
		this.fileSystem = fileSystem;
		try {
			this.watcher = fileSystem.newWatchService();
		} catch (IOException e) {
			throw new ServerException(ErrorCodes.SERVICE_EXCEPTION.getSubError(),
					"Cannot instantiate File watcher service for file system " + fileSystem, e);
		}
	}

	private static class WatchInfo {
		public final Path watchDir;
		public final Path processedDir;
		public final Path errorDir;
		public final FileWatchDefinition fileWatchDefinition;

		public WatchInfo(Path watchDir, Path processedDir, Path errorDir, FileWatchDefinition fileWatchDefinition) {
			this.watchDir = watchDir;
			this.processedDir = processedDir;
			this.errorDir = errorDir;
			this.fileWatchDefinition = fileWatchDefinition;
		}
	}

	public void registerWatch(FileWatchDefinition fileWatchDefinition) {
		Path watchDir = fileSystem.getPath(properties.getSourceFolder()).resolve(fileWatchDefinition.getDirToWatch());
		Path processedDir = fileSystem.getPath(properties.getDestFolder()).resolve(fileWatchDefinition.getDirToWatch());
		Path errorDir = properties.getErrorFolder() == null || properties.getErrorFolder().isBlank()
				? null
				: fileSystem.getPath(properties.getErrorFolder()).resolve(fileWatchDefinition.getDirToWatch());
		try {
			Files.createDirectories(watchDir);
			Files.createDirectories(processedDir);
			if (errorDir != null) {
				Files.createDirectories(errorDir);
			}
			WatchKey key = watchDir.register(this.watcher, StandardWatchEventKinds.ENTRY_MODIFY,
					StandardWatchEventKinds.ENTRY_CREATE);
			WatchInfo watchInfo = new WatchInfo(watchDir, processedDir, errorDir, fileWatchDefinition);
			watchKeyToInfoMap.put(key, watchInfo);
			if (properties.isScanExistingOnStartup()) {
				processExisting(watchInfo);
			}
		} catch (IOException e) {
			throw new ServerException(ErrorCodes.SERVICE_EXCEPTION.getSubError(),
					"Cannot register file watch definition with watch ID " + fileWatchDefinition.getFileWatchId()
							+ " and watch directory = " + watchDir,
					e);
		}
	}

	private void processExisting(WatchInfo watchInfo) {
		try (Stream<Path> paths = Files.list(watchInfo.watchDir)) {
			paths.forEach(path -> submitHeader(watchInfo, path));
		} catch (IOException e) {
			throw new ServerException(ErrorCodes.SERVICE_EXCEPTION.getSubError(),
					"Cannot process existing files in " + watchInfo.watchDir + " configured in file watch definition with watch ID "
							+ watchInfo.fileWatchDefinition.getFileWatchId(),
					e);
		}
	}

	public void startWatch() {
		start();
	}

	@Override
	public void start() {
		if (!running.compareAndSet(false, true)) {
			return;
		}
		watcherTask = watcherExecutor.submit(this::watchLoop);
	}

	private void watchLoop() {
		while (running.get()) {
			try {
				reconcileIfDue();
				WatchKey key = watcher.poll(Math.max(1, properties.getPolltimeSeconds()), TimeUnit.SECONDS);
				if (key == null) {
					continue;
				}
				WatchInfo watchInfo = watchKeyToInfoMap.get(key);
				if (watchInfo == null) {
					key.reset();
					continue;
				}
				for (WatchEvent<?> event : key.pollEvents()) {
					if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
						processExisting(watchInfo);
						continue;
					}
					@SuppressWarnings("unchecked")
					WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
					submitHeader(watchInfo, watchInfo.watchDir.resolve(pathEvent.context()));
				}
				if (!key.reset()) {
					watchKeyToInfoMap.remove(key);
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				stop();
			} catch (ClosedWatchServiceException e) {
				if (running.get()) {
					logger.error("File watcher service closed unexpectedly", e);
				}
				stop();
			} catch (Exception e) {
				logger.error("File watcher loop failed but will continue", e);
			}
		}
	}

	private void reconcileIfDue() {
		int reconciliationSeconds = properties.getReconciliationScanSeconds();
		if (reconciliationSeconds <= 0) {
			return;
		}
		Instant now = Instant.now();
		if (Duration.between(lastReconciliationScan, now).getSeconds() < reconciliationSeconds) {
			return;
		}
		lastReconciliationScan = now;
		for (WatchInfo watchInfo : watchKeyToInfoMap.values()) {
			processExisting(watchInfo);
		}
	}

	private void submitHeader(WatchInfo watchInfo, Path path) {
		if (!fileProcessor.shouldProcess(path)) {
			return;
		}
		Path normalizedPath = path.normalize();
		if (inFlightHeaders.putIfAbsent(normalizedPath, Boolean.TRUE) != null) {
			return;
		}
		executorService.submit(() -> {
			try {
				waitForStability();
				fileProcessor.processFile(watchInfo.fileWatchDefinition, normalizedPath, watchInfo.processedDir,
						watchInfo.errorDir);
			} finally {
				inFlightHeaders.remove(normalizedPath);
			}
		});
	}

	private void waitForStability() {
		long delay = properties.getStabilityCheckDelayMs();
		if (delay <= 0) {
			return;
		}
		try {
			Thread.sleep(delay);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	@Override
	public void stop() {
		if (!running.compareAndSet(true, false)) {
			return;
		}
		closeQuietly(watcher);
		if (watcherTask != null) {
			watcherTask.cancel(true);
		}
	}

	@Override
	public boolean isRunning() {
		return running.get();
	}

	@Override
	public boolean isAutoStartup() {
		return false;
	}

	@Override
	public void destroy() {
		stop();
		watcherExecutor.shutdownNow();
		executorService.shutdownNow();
	}

	private void closeQuietly(Closeable closeable) {
		try {
			closeable.close();
		} catch (IOException e) {
			logger.debug("Cannot close file watcher resource", e);
		}
	}
}
