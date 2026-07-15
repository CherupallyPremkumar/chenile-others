package org.chenile.filewatch.handler;

/**
 * @deprecated File watching is coordinated by {@link FileWatcherExecutorService}.
 */
@Deprecated
public class FileWatcher implements Runnable {
	@Override
	public void run() {
		// Kept as a compatibility shim for code that still references this type.
	}
}
