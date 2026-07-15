package org.chenile.filewatch.init;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import org.chenile.configuration.filewatch.FileWatchProperties;
import org.chenile.core.init.BaseInitializer;
import org.chenile.core.model.ChenileConfiguration;
import org.chenile.filewatch.model.FileWatchDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Picks up all the file watch resources and registers all of them in
 * {@link ChenileConfiguration} ChenileConfiguration provides for extension
 * points for registering new type of resources. This class uses the extension
 * point to register the file watches
 * 
 * @author Raja Shankar Kolluru
 *
 */

public class ChenileFileWatchInitializer
		extends
			BaseInitializer<FileWatchDefinition> {

	private static final Logger LOG = LoggerFactory
			.getLogger(ChenileFileWatchInitializer.class);

	private final FileWatchProperties properties;

	public ChenileFileWatchInitializer(FileWatchProperties properties) {
		super(properties.getJsonPackage());
		this.properties = properties;
	}

	protected void registerModelInChenile(FileWatchDefinition fwd) {
		Map<String, FileWatchDefinition> map = getExtensionMap(
				FileWatchDefinition.EXTENSION);
		map.put(fwd.getFileWatchId(), fwd);

		createDirectory(Paths.get(properties.getSourceFolder()).resolve(fwd.getDirToWatch()));
		createDirectory(Paths.get(properties.getDestFolder()).resolve(fwd.getDirToWatch()));
		if (properties.getErrorFolder() != null && !properties.getErrorFolder().isBlank()) {
			createDirectory(Paths.get(properties.getErrorFolder()).resolve(fwd.getDirToWatch()));
		}
	}

	private void createDirectory(Path path) {
		try {
			Files.createDirectories(path);
		} catch (IOException e) {
			LOG.error("Failed to create file-watch directory {}", path, e);
		}
	}

	@Override
	protected Class<FileWatchDefinition> getModelType() {
		return FileWatchDefinition.class;
	}
}
