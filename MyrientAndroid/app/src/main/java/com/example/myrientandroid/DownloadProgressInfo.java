package com.example.myrientandroid;

import android.os.Parcel;
import android.os.Parcelable;

public class DownloadProgressInfo implements Parcelable {

    public enum DownloadStatus {
        PENDING,
        DOWNLOADING,
        PAUSED,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    private String id; // Unique ID for the download, e.g., URL or UUID
    private String fileName;
    private DownloadStatus status;
    private int progress; // 0-100
    private long bytesDownloaded;
    private long totalBytes;
    private String downloadSpeed; // e.g., "500 KB/s"
    private String originalUrl;
    private String localFilePath; // Path to the downloaded file
    private String failureReason; // Reason for failure
    private long createdAt; // Timestamp for sorting

    // Constructor
    public DownloadProgressInfo(String id, String originalUrl, String fileName) {
        this.id = id;
        this.originalUrl = originalUrl;
        this.fileName = fileName;
        this.status = DownloadStatus.PENDING;
        this.progress = 0;
        this.bytesDownloaded = 0;
        this.totalBytes = -1; // Unknown at first
        this.downloadSpeed = "0 KB/s";
        this.localFilePath = null;
        this.failureReason = null;
    }

    // Getters
    public String getId() { return id; }
    public String getFileName() { return fileName; }
    public DownloadStatus getStatus() { return status; }
    public int getProgress() { return progress; }
    public long getBytesDownloaded() { return bytesDownloaded; }
    public long getTotalBytes() { return totalBytes; }
    public String getDownloadSpeed() { return downloadSpeed; }
    public String getOriginalUrl() { return originalUrl; }
    public String getLocalFilePath() { return localFilePath; }
    public String getFailureReason() { return failureReason; }
    public long getCreatedAt() { return createdAt; }

    // Helper methods similar to old DownloadItem
    public String getStatusText() {
        if (status == null) return "Desconhecido";
        switch (status) {
            case PENDING: return "Pendente";
            case DOWNLOADING: return "Baixando";
            case PAUSED: return "Pausado";
            case COMPLETED: return "Concluído";
            case FAILED: return "Falhou" + (failureReason != null ? " (" + failureReason + ")" : "");
            case CANCELLED: return "Cancelado";
            default: return status.name();
        }
    }

    public int getProgressPercentage() { // Already exists as getProgress(), but this name is more specific if we want simple 0-100
        return progress;
    }

    public String getProgressSizeText() {
        if (totalBytes < 0) { // if size is not yet known
             if (bytesDownloaded > 0) {
                 return formatFileSize(bytesDownloaded) + " / ?";
             }
             return "Aguardando tamanho...";
        }
        if (totalBytes == 0 && bytesDownloaded == 0 && status == DownloadStatus.PENDING) {
             return "0 B / 0 B";
        }
        return formatFileSize(bytesDownloaded) + " / " + formatFileSize(totalBytes);
    }

    public static String formatFileSize(long size) { // Static helper
        if (size < 0) return "?";
        if (size == 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        // Ensure digitGroups is within the bounds of the units array
        digitGroups = Math.max(0, Math.min(digitGroups, units.length - 1));
        return new java.text.DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    public boolean isCancelable() {
        return status == DownloadStatus.PENDING ||
               status == DownloadStatus.DOWNLOADING ||
               status == DownloadStatus.PAUSED;
    }
     public boolean isPausable() { // Placeholder for future
        return status == DownloadStatus.DOWNLOADING;
    }

    public boolean isResumable() { // Placeholder for future
        return status == DownloadStatus.PAUSED;
    }


    // Setters
    public void setId(String id) { this.id = id; } // Should generally be immutable after creation
    public void setFileName(String fileName) { this.fileName = fileName; }
    public void setStatus(DownloadStatus status) { this.status = status; }
    public void setProgress(int progress) { this.progress = progress; }
    public void setBytesDownloaded(long bytesDownloaded) { this.bytesDownloaded = bytesDownloaded; }
    public void setTotalBytes(long totalBytes) { this.totalBytes = totalBytes; }
    public void setDownloadSpeed(String downloadSpeed) { this.downloadSpeed = downloadSpeed; }
    public void setOriginalUrl(String originalUrl) { this.originalUrl = originalUrl; } // Should be immutable
    public void setLocalFilePath(String localFilePath) { this.localFilePath = localFilePath; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    // Parcelable implementation (for sending through Intents, e.g., LocalBroadcastManager)
    protected DownloadProgressInfo(Parcel in) {
        id = in.readString();
        fileName = in.readString();
        status = DownloadStatus.valueOf(in.readString());
        progress = in.readInt();
        bytesDownloaded = in.readLong();
        totalBytes = in.readLong();
        downloadSpeed = in.readString();
        originalUrl = in.readString();
        localFilePath = in.readString();
        failureReason = in.readString();
        createdAt = in.readLong();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(fileName);
        dest.writeString(status.name());
        dest.writeInt(progress);
        dest.writeLong(bytesDownloaded);
        dest.writeLong(totalBytes);
        dest.writeString(downloadSpeed);
        dest.writeString(originalUrl);
        dest.writeString(localFilePath);
        dest.writeString(failureReason);
        dest.writeLong(createdAt);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<DownloadProgressInfo> CREATOR = new Creator<DownloadProgressInfo>() {
        @Override
        public DownloadProgressInfo createFromParcel(Parcel in) {
            return new DownloadProgressInfo(in);
        }

        @Override
        public DownloadProgressInfo[] newArray(int size) {
            return new DownloadProgressInfo[size];
        }
    };
}
