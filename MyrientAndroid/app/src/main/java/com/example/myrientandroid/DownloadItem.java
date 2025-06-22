package com.example.myrientandroid;

import android.app.DownloadManager;
import java.text.DecimalFormat;

public class DownloadItem {
    private long id;
    private String fileName;
    private int status;
    private int reason;
    private long totalBytes;
    private long bytesDownloaded;
    private String uri;
    private String localUri;
    private long lastModifiedTimestamp; // For sorting

    public DownloadItem(long id, String fileName, int status, int reason, long totalBytes, long bytesDownloaded, String uri, String localUri, long lastModifiedTimestamp) {
        this.id = id;
        this.fileName = fileName;
        this.status = status;
        this.reason = reason;
        this.totalBytes = totalBytes;
        this.bytesDownloaded = bytesDownloaded;
        this.uri = uri;
        this.localUri = localUri;
        this.lastModifiedTimestamp = lastModifiedTimestamp;
    }

    // Getters
    public long getId() { return id; }
    public String getFileName() { return fileName != null ? fileName : "Unknown File"; }
    public int getStatus() { return status; }
    public int getReason() { return reason; }
    public long getTotalBytes() { return totalBytes; }
    public long getBytesDownloaded() { return bytesDownloaded; }
    public String getUri() { return uri; }
    public String getLocalUri() { return localUri; }
    public long getLastModifiedTimestamp() { return lastModifiedTimestamp; }

    // Setters - might be needed if we update items in place
    public void setStatus(int status) { this.status = status; }
    public void setReason(int reason) { this.reason = reason; }
    public void setTotalBytes(long totalBytes) { this.totalBytes = totalBytes; }
    public void setBytesDownloaded(long bytesDownloaded) { this.bytesDownloaded = bytesDownloaded; }


    public String getStatusText() {
        switch (status) {
            case DownloadManager.STATUS_PENDING:
                return "Pendente";
            case DownloadManager.STATUS_RUNNING:
                return "Em andamento";
            case DownloadManager.STATUS_PAUSED:
                // Note: STATUS_PAUSED can mean waiting for network, or waiting for retry.
                return "Pausado (Motivo: " + getReasonText() + ")";
            case DownloadManager.STATUS_SUCCESSFUL:
                return "Concluído";
            case DownloadManager.STATUS_FAILED:
                return "Falhou (Motivo: " + getReasonText() + ")";
            default:
                return "Desconhecido (" + status + ")";
        }
    }

    public String getReasonText() {
        // These are just some common reasons. Refer to DownloadManager documentation for all.
        switch (reason) {
            case DownloadManager.ERROR_CANNOT_RESUME: return "Não pode ser retomado";
            case DownloadManager.ERROR_DEVICE_NOT_FOUND: return "Dispositivo não encontrado";
            case DownloadManager.ERROR_FILE_ALREADY_EXISTS: return "Arquivo já existe";
            case DownloadManager.ERROR_FILE_ERROR: return "Erro de arquivo";
            case DownloadManager.ERROR_HTTP_DATA_ERROR: return "Erro de dados HTTP";
            case DownloadManager.ERROR_INSUFFICIENT_SPACE: return "Espaço insuficiente";
            case DownloadManager.ERROR_TOO_MANY_REDIRECTS: return "Muitos redirecionamentos";
            case DownloadManager.ERROR_UNHANDLED_HTTP_CODE: return "Código HTTP não tratado";
            case DownloadManager.ERROR_UNKNOWN: return "Erro desconhecido";
            // For STATUS_PAUSED reasons
            case DownloadManager.PAUSED_QUEUED_FOR_WIFI: return "Aguardando Wi-Fi";
            case DownloadManager.PAUSED_UNKNOWN: return "Pausado (desconhecido)";
            case DownloadManager.PAUSED_WAITING_FOR_NETWORK: return "Aguardando rede";
            case DownloadManager.PAUSED_WAITING_TO_RETRY: return "Aguardando nova tentativa";
            default:
                return "" + reason; // Just show the code if not specifically handled
        }
    }

    public int getProgressPercentage() {
        if (totalBytes <= 0) return 0;
        return (int) ((bytesDownloaded * 100L) / totalBytes);
    }

    public String getProgressSizeText() {
        if (totalBytes < 0) { // if size is not yet known (e.g. pending or just started)
             if (bytesDownloaded > 0) {
                 return formatFileSize(bytesDownloaded) + " / ?";
             }
             return "Aguardando tamanho...";
        }
        return formatFileSize(bytesDownloaded) + " / " + formatFileSize(totalBytes);
    }

    public static String formatFileSize(long size) {
        if (size <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    public boolean isCancelable() {
        return status == DownloadManager.STATUS_PENDING ||
               status == DownloadManager.STATUS_RUNNING ||
               status == DownloadManager.STATUS_PAUSED;
    }
}
