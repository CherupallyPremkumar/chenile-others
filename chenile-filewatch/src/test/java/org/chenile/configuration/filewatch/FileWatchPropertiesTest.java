package org.chenile.configuration.filewatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class FileWatchPropertiesTest {

	@Test
	void bindsExistingAndProductionProperties() {
		FileWatchProperties properties = new Binder(new MapConfigurationPropertySource(Map.of(
				"chenile.file.watch.source.folder", "/in",
				"chenile.file.watch.dest.folder", "/processed",
				"chenile.file.watch.error.folder", "/error",
				"chenile.file.watch.polltime.seconds", "2",
				"chenile.file.watch.stability-check-delay-ms", "50",
				"chenile.file.watch.scan-existing-on-startup", "false",
				"chenile.file.watch.reconciliation-scan-seconds", "10",
				"chenile.file.watch.max-concurrent-files", "4")))
				.bind("chenile.file.watch", FileWatchProperties.class)
				.get();

		assertEquals("/in", properties.getSourceFolder());
		assertEquals("/processed", properties.getDestFolder());
		assertEquals("/error", properties.getErrorFolder());
		assertEquals(2, properties.getPolltimeSeconds());
		assertEquals(50, properties.getStabilityCheckDelayMs());
		assertFalse(properties.isScanExistingOnStartup());
		assertEquals(10, properties.getReconciliationScanSeconds());
		assertEquals(4, properties.getMaxConcurrentFiles());
	}

	@Test
	void hasProductionSafeDefaults() {
		FileWatchProperties properties = new FileWatchProperties();

		assertEquals(30, properties.getPolltimeSeconds());
		assertEquals(250, properties.getStabilityCheckDelayMs());
		assertTrue(properties.isScanExistingOnStartup());
		assertEquals(60, properties.getReconciliationScanSeconds());
		assertEquals(3, properties.getMaxConcurrentFiles());
	}
}
