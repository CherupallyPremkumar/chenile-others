package org.chenile.configuration.filewatch;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

@ConfigurationProperties(prefix = "chenile.file.watch")
public class FileWatchProperties {
	private Resource[] jsonPackage = new Resource[0];
	private JsonProperties json = new JsonProperties();
	private String sourceFolder;
	private FolderProperties source = new FolderProperties();
	private String destFolder;
	private FolderProperties dest = new FolderProperties();
	private String errorFolder;
	private FolderProperties error = new FolderProperties();
	private Integer polltimeSeconds;
	private PolltimeProperties polltime = new PolltimeProperties();
	private long stabilityCheckDelayMs = 250;
	private boolean scanExistingOnStartup = true;
	private int reconciliationScanSeconds = 60;
	private int maxConcurrentFiles = 3;

	public Resource[] getJsonPackage() {
		return jsonPackage.length > 0 ? jsonPackage : json.getPackage();
	}

	public void setJsonPackage(Resource[] jsonPackage) {
		this.jsonPackage = jsonPackage == null ? new Resource[0] : jsonPackage;
	}

	public JsonProperties getJson() {
		return json;
	}

	public void setJson(JsonProperties json) {
		this.json = json == null ? new JsonProperties() : json;
	}

	public String getSourceFolder() {
		return sourceFolder == null ? source.getFolder() : sourceFolder;
	}

	public void setSourceFolder(String sourceFolder) {
		this.sourceFolder = sourceFolder;
	}

	public FolderProperties getSource() {
		return source;
	}

	public void setSource(FolderProperties source) {
		this.source = source == null ? new FolderProperties() : source;
	}

	public String getDestFolder() {
		return destFolder == null ? dest.getFolder() : destFolder;
	}

	public void setDestFolder(String destFolder) {
		this.destFolder = destFolder;
	}

	public FolderProperties getDest() {
		return dest;
	}

	public void setDest(FolderProperties dest) {
		this.dest = dest == null ? new FolderProperties() : dest;
	}

	public String getErrorFolder() {
		return errorFolder == null ? error.getFolder() : errorFolder;
	}

	public void setErrorFolder(String errorFolder) {
		this.errorFolder = errorFolder;
	}

	public FolderProperties getError() {
		return error;
	}

	public void setError(FolderProperties error) {
		this.error = error == null ? new FolderProperties() : error;
	}

	public int getPolltimeSeconds() {
		return polltimeSeconds == null ? polltime.getSeconds() : polltimeSeconds;
	}

	public void setPolltimeSeconds(int polltimeSeconds) {
		this.polltimeSeconds = polltimeSeconds;
	}

	public PolltimeProperties getPolltime() {
		return polltime;
	}

	public void setPolltime(PolltimeProperties polltime) {
		this.polltime = polltime == null ? new PolltimeProperties() : polltime;
	}

	public long getStabilityCheckDelayMs() {
		return stabilityCheckDelayMs;
	}

	public void setStabilityCheckDelayMs(long stabilityCheckDelayMs) {
		this.stabilityCheckDelayMs = stabilityCheckDelayMs;
	}

	public boolean isScanExistingOnStartup() {
		return scanExistingOnStartup;
	}

	public void setScanExistingOnStartup(boolean scanExistingOnStartup) {
		this.scanExistingOnStartup = scanExistingOnStartup;
	}

	public int getReconciliationScanSeconds() {
		return reconciliationScanSeconds;
	}

	public void setReconciliationScanSeconds(int reconciliationScanSeconds) {
		this.reconciliationScanSeconds = reconciliationScanSeconds;
	}

	public int getMaxConcurrentFiles() {
		return maxConcurrentFiles;
	}

	public void setMaxConcurrentFiles(int maxConcurrentFiles) {
		this.maxConcurrentFiles = maxConcurrentFiles;
	}

	public static class JsonProperties {
		private Resource[] packageResources = new Resource[0];

		public Resource[] getPackage() {
			return packageResources;
		}

		public void setPackage(Resource[] packageResources) {
			this.packageResources = packageResources == null ? new Resource[0] : packageResources;
		}
	}

	public static class FolderProperties {
		private String folder;

		public String getFolder() {
			return folder;
		}

		public void setFolder(String folder) {
			this.folder = folder;
		}
	}

	public static class PolltimeProperties {
		private int seconds = 30;

		public int getSeconds() {
			return seconds;
		}

		public void setSeconds(int seconds) {
			this.seconds = seconds;
		}
	}
}
