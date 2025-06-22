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
