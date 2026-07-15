package org.chenile.filewatch.handler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.chenile.core.event.EventProcessor;
import org.chenile.filewatch.errorcodes.ErrorCodes;
import org.chenile.filewatch.model.FileWatchDefinition;
import org.chenile.utils.stream.Looper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Processes a completed header file and its corresponding records file.
 */
public class FileProcessor {
	private static final Logger logger = LoggerFactory.getLogger(FileProcessor.class);

	public static final String ACTUAL_FILE_NAME = "actual.file";
	public static final String ACTUAL_FILE_ENCODING = "actual.file.encoding";
	public static final String HEADER_EXTENSION = ".header";
	public static final String LAST_PROPERTY = "last.property";

	@Autowired
	private EventProcessor eventProcessor;
	@Autowired
	private FileWatchEventLogger eventLogger;
	@Autowired
	private Looper looper;

	public boolean shouldProcess(Path fileDiscovered) {
		return fileDiscovered != null && fileDiscovered.getFileName() != null
				&& fileDiscovered.getFileName().toString().endsWith(HEADER_EXTENSION);
	}

	public void processFile(FileWatchDefinition fileWatchDefinition, Path fileDiscovered, Path processedDir) {
		processFile(fileWatchDefinition, fileDiscovered, processedDir, null);
	}

	@SuppressWarnings("unchecked")
	public void processFile(FileWatchDefinition fileWatchDefinition, Path fileDiscovered, Path processedDir,
			Path errorDir) {
		if (!shouldProcess(fileDiscovered)) {
			return;
		}
		if (!Files.isRegularFile(fileDiscovered)) {
			return;
		}

		Properties headers = extractHeaders(fileDiscovered);
		if (headers == null) {
			return;
		}
		if (!headers.containsKey(ACTUAL_FILE_NAME) || !headers.containsKey(ACTUAL_FILE_ENCODING)) {
			eventLogger.logError(null, ErrorCodes.MISSING_HEADER_PROPERTIES.getSubError(),
					"Header file " + fileDiscovered + " does not contain required headers "
							+ ACTUAL_FILE_NAME + " or " + ACTUAL_FILE_ENCODING);
			moveFilesToError(fileDiscovered, null, errorDir);
			return;
		}

		Path recordsFile;
		try {
			recordsFile = resolveActualFile(fileDiscovered, headers.getProperty(ACTUAL_FILE_NAME));
		} catch (IllegalArgumentException e) {
			eventLogger.logError(null, ErrorCodes.CANNOT_PROCESS_FILE.getSubError(), e.getMessage(), e);
			moveFilesToError(fileDiscovered, null, errorDir);
			return;
		}

		try (InputStream inputStream = Files.newInputStream(recordsFile)) {
			Map<String, String> eventHeaders = eventHeaders(headers);
			looper.loop(inputStream, headers.getProperty(ACTUAL_FILE_ENCODING), headers,
					fileWatchDefinition.getRecordClass(),
					record -> eventProcessor.handleEvent(fileWatchDefinition.getFileWatchId(), record, eventHeaders));
			moveFilesToProcessed(fileDiscovered, recordsFile, processedDir);
		} catch (Exception e) {
			eventLogger.logError(null, ErrorCodes.CANNOT_PROCESS_FILE.getSubError(),
					"Unable to process file " + recordsFile, e);
			moveFilesToError(fileDiscovered, recordsFile, errorDir);
		}
	}

	private Path resolveActualFile(Path headerFile, String actualFileName) {
		Path watchDir = headerFile.getParent();
		Path recordsFile = watchDir.resolve(actualFileName).normalize();
		if (!recordsFile.startsWith(watchDir.normalize())) {
			throw new IllegalArgumentException("Header file " + headerFile + " refers to file outside watch directory: "
					+ actualFileName);
		}
		if (!Files.isRegularFile(recordsFile)) {
			throw new IllegalArgumentException("Header file " + headerFile + " refers to missing or non-regular file: "
					+ recordsFile);
		}
		return recordsFile;
	}

	private Map<String, String> eventHeaders(Properties headers) {
		Map<String, String> eventHeaders = new HashMap<>();
		headers.forEach((key, value) -> eventHeaders.put(String.valueOf(key), String.valueOf(value)));
		return eventHeaders;
	}

	private void moveFilesToProcessed(Path headerFilename, Path recordsFile, Path processedDir) {
		move(headerFilename, processedDir, ErrorCodes.CANNOT_MOVE_TO_PROCESSED.getSubError());
		move(recordsFile, processedDir, ErrorCodes.CANNOT_MOVE_TO_PROCESSED.getSubError());
	}

	private void moveFilesToError(Path headerFilename, Path recordsFile, Path errorDir) {
		if (errorDir == null) {
			return;
		}
		move(headerFilename, errorDir, ErrorCodes.CANNOT_MOVE_TO_ERROR.getSubError());
		if (recordsFile != null) {
			move(recordsFile, errorDir, ErrorCodes.CANNOT_MOVE_TO_ERROR.getSubError());
		}
	}

	private void move(Path file, Path targetDir, String errorCode) {
		try {
			if (file == null || !Files.exists(file)) {
				return;
			}
			Files.createDirectories(targetDir);
			Path target = targetDir.resolve(file.getFileName());
			Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			eventLogger.logError(null, errorCode,
					"Cannot move the file " + file + " to directory " + targetDir, e);
			logger.error("Cannot move file {} to {}", file, targetDir, e);
		}
	}

	private Properties extractHeaders(Path file) {
		try (InputStream input = Files.newInputStream(file)) {
			Properties props = new Properties();
			props.load(input);
			if (props.containsKey(LAST_PROPERTY)) {
				props.remove(LAST_PROPERTY);
			} else {
				eventLogger.logWarning(ErrorCodes.MISSING_HEADER_PROPERTIES.getSubError(),
						"Header file " + file + " does not contain the expected property "
								+ LAST_PROPERTY + " Ignoring it");
				return null;
			}
			return props;
		} catch (IOException ex) {
			eventLogger.logError(null, ErrorCodes.CANNOT_PROCESS_FILE.getSubError(),
					"Cannot read Header file " + file, ex);
			return null;
		}
	}
}
