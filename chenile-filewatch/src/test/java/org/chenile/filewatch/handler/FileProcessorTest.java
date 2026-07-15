package org.chenile.filewatch.handler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.chenile.core.event.EventProcessor;
import org.chenile.filewatch.model.FileWatchDefinition;
import org.chenile.filewatch.test.service.FooModel;
import org.chenile.utils.stream.Looper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

class FileProcessorTest {
	private FileSystem fileSystem;
	private Path watchDir;
	private Path processedDir;
	private Path errorDir;
	private EventProcessor eventProcessor;
	private FileProcessor fileProcessor;
	private FileWatchDefinition definition;

	@BeforeEach
	void setUp() throws Exception {
		fileSystem = Jimfs.newFileSystem(Configuration.unix());
		watchDir = fileSystem.getPath("/watch/foo");
		processedDir = fileSystem.getPath("/processed/foo");
		errorDir = fileSystem.getPath("/error/foo");
		Files.createDirectories(watchDir);
		Files.createDirectories(processedDir);
		Files.createDirectories(errorDir);
		eventProcessor = mock(EventProcessor.class);
		fileProcessor = new FileProcessor();
		ReflectionTestUtils.setField(fileProcessor, "eventProcessor", eventProcessor);
		ReflectionTestUtils.setField(fileProcessor, "eventLogger", mock(FileWatchEventLogger.class));
		ReflectionTestUtils.setField(fileProcessor, "looper", new Looper<>());
		definition = new FileWatchDefinition();
		definition.setFileWatchId("foo");
		definition.setRecordClass(FooModel.class);
	}

	@Test
	void ignoresNonHeaderFiles() {
		assertFalse(fileProcessor.shouldProcess(watchDir.resolve("data.csv")));
		assertTrue(fileProcessor.shouldProcess(watchDir.resolve("data.header")));
	}

	@Test
	void successfulCsvFileMovesFilesAndPropagatesHeaders() throws Exception {
		Path actual = watchDir.resolve("data.csv");
		Path header = watchDir.resolve("data.header");
		Files.write(actual, List.of("bar,baz", "bar1,baz1"), StandardCharsets.UTF_8);
		Files.write(header, List.of(
				"x-tenant=tenant1",
				FileProcessor.ACTUAL_FILE_NAME + "=data.csv",
				FileProcessor.ACTUAL_FILE_ENCODING + "=csv",
				FileProcessor.LAST_PROPERTY + "=done"), StandardCharsets.UTF_8);

		fileProcessor.processFile(definition, header, processedDir, errorDir);

		assertFalse(Files.exists(header));
		assertFalse(Files.exists(actual));
		assertTrue(Files.exists(processedDir.resolve("data.header")));
		assertTrue(Files.exists(processedDir.resolve("data.csv")));
		verify(eventProcessor).handleEvent(eq("foo"), any(FooModel.class), eq(Map.of(
				"x-tenant", "tenant1",
				FileProcessor.ACTUAL_FILE_NAME, "data.csv",
				FileProcessor.ACTUAL_FILE_ENCODING, "csv")));
	}

	@Test
	void missingLastPropertyLeavesFilesInPlace() throws Exception {
		Path actual = watchDir.resolve("data.csv");
		Path header = watchDir.resolve("data.header");
		Files.write(actual, List.of("bar,baz", "bar1,baz1"), StandardCharsets.UTF_8);
		Files.write(header, List.of(
				FileProcessor.ACTUAL_FILE_NAME + "=data.csv",
				FileProcessor.ACTUAL_FILE_ENCODING + "=csv"), StandardCharsets.UTF_8);

		fileProcessor.processFile(definition, header, processedDir, errorDir);

		assertTrue(Files.exists(header));
		assertTrue(Files.exists(actual));
		verify(eventProcessor, never()).handleEvent(any(), any(), any());
	}

	@Test
	void pathTraversalActualFileMovesHeaderToErrorFolder() throws Exception {
		Path header = watchDir.resolve("data.header");
		Files.write(header, List.of(
				FileProcessor.ACTUAL_FILE_NAME + "=../outside.csv",
				FileProcessor.ACTUAL_FILE_ENCODING + "=csv",
				FileProcessor.LAST_PROPERTY + "=done"), StandardCharsets.UTF_8);

		fileProcessor.processFile(definition, header, processedDir, errorDir);

		assertFalse(Files.exists(header));
		assertTrue(Files.exists(errorDir.resolve("data.header")));
		verify(eventProcessor, never()).handleEvent(any(), any(), any());
	}
}
