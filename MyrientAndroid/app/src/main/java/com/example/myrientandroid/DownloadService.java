package com.example.myrientandroid;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context; // Adicionado
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import java.util.List; // Adicionado
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class DownloadService extends Service {

    private static final String TAG = "DownloadService";
    private static final String CHANNEL_ID = "DownloadServiceChannel";
    private static final int FOREGROUND_SERVICE_NOTIFICATION_ID = 1;
    private static final int MAX_CONCURRENT_DOWNLOADS = 3;

    public static final String ACTION_START_DOWNLOAD = "com.example.myrientandroid.ACTION_START_DOWNLOAD";
    public static final String ACTION_CANCEL_DOWNLOAD = "com.example.myrientandroid.ACTION_CANCEL_DOWNLOAD";
    // Future actions: ACTION_PAUSE_DOWNLOAD, ACTION_RESUME_DOWNLOAD

    public static final String EXTRA_URL = "com.example.myrientandroid.EXTRA_URL";
    public static final String EXTRA_FILE_NAME = "com.example.myrientandroid.EXTRA_FILE_NAME";
    public static final String EXTRA_DOWNLOAD_ID = "com.example.myrientandroid.EXTRA_DOWNLOAD_ID";

    // Intent actions for LocalBroadcastManager
    public static final String BROADCAST_DOWNLOAD_PROGRESS = "com.example.myrientandroid.BROADCAST_DOWNLOAD_PROGRESS";
    public static final String BROADCAST_DOWNLOAD_STATE_CHANGED = "com.example.myrientandroid.BROADCAST_DOWNLOAD_STATE_CHANGED"; // For complete, failed, cancelled
    public static final String EXTRA_DOWNLOAD_INFO = "com.example.myrientandroid.EXTRA_DOWNLOAD_INFO";


    private OkHttpClient okHttpClient;
    private ExecutorService downloadExecutorService;
    private DownloadDbHelper dbHelper;
    private ConcurrentHashMap<String, Call> activeCalls = new ConcurrentHashMap<>();
    private int activeDownloadTasks = 0;
    private Handler serviceHandler; // For processing queue and other service logic off the main thread


    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "DownloadService onCreate");
        dbHelper = new DownloadDbHelper(this);
        okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
        downloadExecutorService = Executors.newFixedThreadPool(MAX_CONCURRENT_DOWNLOADS);

        HandlerThread handlerThread = new HandlerThread("DownloadServiceHandlerThread");
        handlerThread.start();
        serviceHandler = new Handler(handlerThread.getLooper());

        createNotificationChannel();
        Notification notification = createForegroundServiceNotification("Serviço de download está ativo.", 0);
        startForeground(FOREGROUND_SERVICE_NOTIFICATION_ID, notification);
    }

    private Notification createForegroundServiceNotification(String text, int activeDownloads) {
        Intent notificationIntent = new Intent(this, DownloadManagerActivity.class); // Open Download Manager
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        String contentText = activeDownloads > 0 ? activeDownloads + " download(s) em progresso." : text;

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Myrient Downloader")
                .setContentText(contentText)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void updateForegroundServiceNotification() {
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        int currentActiveDownloads = 0;
        // Query DB or count active tasks to get the number of truly active downloads
        List<DownloadProgressInfo> allItems = dbHelper.getAllDownloadsSortedByDate();
        for (DownloadProgressInfo item : allItems) {
            if (item.getStatus() == DownloadProgressInfo.DownloadStatus.DOWNLOADING) {
                currentActiveDownloads++;
            }
        }
        Notification notification = createForegroundServiceNotification("Serviço de download está ativo.", currentActiveDownloads);
        notificationManager.notify(FOREGROUND_SERVICE_NOTIFICATION_ID, notification);
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "DownloadService onStartCommand: " + (intent != null ? intent.getAction() : "null intent"));
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_START_DOWNLOAD.equals(action)) {
                final String url = intent.getStringExtra(EXTRA_URL);
                final String fileName = intent.getStringExtra(EXTRA_FILE_NAME);
                if (url != null && fileName != null) {
                    // Use serviceHandler to run DB operations and queue processing off the main thread
                    serviceHandler.post(() -> {
                        String downloadId = UUID.randomUUID().toString(); // Generate a unique ID
                        DownloadProgressInfo newItem = new DownloadProgressInfo(downloadId, url, fileName);

                        // Check if a download for this URL already exists (optional, based on requirements)
                        // For now, assume new download request means new entry.

                        dbHelper.addDownload(newItem);
                        Log.i(TAG, "Novo download adicionado ao DB: " + fileName + " (ID: " + downloadId + ")");
                        sendBroadcastStateChanged(newItem); // Notify UI about PENDING state
                        processQueue();
                    });
                }
            } else if (ACTION_CANCEL_DOWNLOAD.equals(action)) {
                final String downloadIdToCancel = intent.getStringExtra(EXTRA_DOWNLOAD_ID);
                if (downloadIdToCancel != null) {
                     serviceHandler.post(() -> cancelDownloadInternal(downloadIdToCancel, true));
                }
            }
        }
        return START_STICKY; // If the service is killed, try to restart it
    }

    private void processQueue() {
        // This method should be called on serviceHandler's thread
        Log.d(TAG, "Processando fila... Downloads ativos: " + activeDownloadTasks);
        if (activeDownloadTasks >= MAX_CONCURRENT_DOWNLOADS) {
            Log.d(TAG, "Máximo de downloads concorrentes atingido.");
            return;
        }

        DownloadProgressInfo nextDownload = findNextPendingDownload();
        if (nextDownload != null) {
            Log.i(TAG, "Iniciando download para: " + nextDownload.getFileName());
            activeDownloadTasks++;
            updateForegroundServiceNotification();
            nextDownload.setStatus(DownloadProgressInfo.DownloadStatus.DOWNLOADING);
            dbHelper.updateDownloadStatus(nextDownload.getId(), DownloadProgressInfo.DownloadStatus.DOWNLOADING, null);
            sendBroadcastStateChanged(nextDownload); // Notify UI about DOWNLOADING state

            DownloadTaskRunnable task = new DownloadTaskRunnable(nextDownload);
            downloadExecutorService.submit(task);
        } else {
            Log.d(TAG, "Nenhum download pendente na fila.");
             if(activeDownloadTasks == 0) { // No active tasks and no pending tasks
                // Consider stopping the service if queue is empty and no active downloads
                // stopSelf(); // Or use stopSelf(startId)
                Log.d(TAG, "Fila vazia e sem downloads ativos. Serviço pode ser parado se necessário.");
             }
        }
    }

    private DownloadProgressInfo findNextPendingDownload() {
        // Query DB for the oldest PENDING download
        List<DownloadProgressInfo> allItems = dbHelper.getAllDownloadsSortedByDate(); // Sorted newest first
        for (int i = allItems.size() - 1; i >= 0; i--) { // Iterate backwards to get oldest
            DownloadProgressInfo item = allItems.get(i);
            if (item.getStatus() == DownloadProgressInfo.DownloadStatus.PENDING) {
                // Check if this download is already being processed by another call to processQueue
                // (simple check, could be more robust with a state in DB like "PROCESSING_QUEUE")
                if (!activeCalls.containsKey(item.getId())) {
                    return item;
                }
            }
        }
        return null;
    }


    private void cancelDownloadInternal(String downloadId, boolean userRequested) {
        Log.d(TAG, "Tentando cancelar download ID: " + downloadId);
        Call call = activeCalls.remove(downloadId);
        if (call != null && !call.isCanceled()) {
            call.cancel(); // This will trigger onFailure in OkHttp callback with IOException "Canceled"
            Log.i(TAG, "Chamada OkHttp cancelada para download ID: " + downloadId);
        }
        // The DownloadTaskRunnable's onFailure or successful completion (if it was very fast)
        // will handle DB update to CANCELLED or FAILED.
        // If user requested, we ensure the status is set to CANCELLED.
        if (userRequested) {
            DownloadProgressInfo item = dbHelper.getDownload(downloadId);
            if (item != null && item.getStatus() != DownloadProgressInfo.DownloadStatus.COMPLETED &&
                                 item.getStatus() != DownloadProgressInfo.DownloadStatus.FAILED &&
                                 item.getStatus() != DownloadProgressInfo.DownloadStatus.CANCELLED) {
                item.setStatus(DownloadProgressInfo.DownloadStatus.CANCELLED);
                item.setFailureReason("Cancelado pelo usuário");
                dbHelper.updateDownloadStatus(downloadId, DownloadProgressInfo.DownloadStatus.CANCELLED, "Cancelado pelo usuário");
                sendBroadcastStateChanged(item);
                removeIndividualNotification(item.getId().hashCode()); // Use a consistent ID for notification
            }
        }
        // Decrement activeDownloadTasks if it was active, and process queue
        // This part is tricky because the task itself also decrements.
        // For now, let processQueue handle checking active tasks again.
        serviceHandler.post(this::processQueue);
        updateForegroundServiceNotification();
    }


    private class DownloadTaskRunnable implements Runnable {
        private DownloadProgressInfo downloadInfo;

        DownloadTaskRunnable(DownloadProgressInfo downloadInfo) {
            this.downloadInfo = downloadInfo;
        }

        @Override
        public void run() {
            Log.d(TAG, "DownloadTaskRunnable iniciado para: " + downloadInfo.getFileName());
            InputStream inputStream = null;
            OutputStream outputStream = null;
            Call call = null;

            try {
                // --- Destination File Setup (Simplified for now, internal storage) ---
                File targetDir = new File(getFilesDir(), "MyrientDownloads");
                if (!targetDir.exists()) {
                    targetDir.mkdirs();
                }
                File targetFile = new File(targetDir, downloadInfo.getFileName());
                downloadInfo.setLocalFilePath(targetFile.getAbsolutePath());
                dbHelper.updateDownload(downloadInfo); // Save local file path

                // --- OkHttp Request ---
                Request request = new Request.Builder().url(downloadInfo.getOriginalUrl()).build();
                call = okHttpClient.newCall(request);
                activeCalls.put(downloadInfo.getId(), call); // Track the call

                Response response = call.execute();
                ResponseBody body = response.body();

                if (!response.isSuccessful() || body == null) {
                    throw new IOException("Falha no download: " + response.code() + " - " + response.message());
                }

                long totalBytes = body.contentLength();
                downloadInfo.setTotalBytes(totalBytes);
                dbHelper.updateDownloadProgress(downloadInfo.getId(), 0, totalBytes, 0, "0 KB/s"); // Initial update with total size
                sendBroadcastProgress(downloadInfo);

                inputStream = body.byteStream();
                outputStream = new FileOutputStream(targetFile); // Overwrites if exists

                byte[] data = new byte[8192]; // 8KB buffer
                long bytesDownloaded = 0;
                int bytesRead;
                long lastUpdateTime = System.currentTimeMillis();
                long lastUpdateBytes = 0;

                // --- Download Loop ---
                while ((bytesRead = inputStream.read(data)) != -1) {
                    if (call.isCanceled()) { // Check for cancellation
                        Log.d(TAG, "Download cancelado durante o loop: " + downloadInfo.getFileName());
                        throw new IOException("Download cancelado");
                    }
                    outputStream.write(data, 0, bytesRead);
                    bytesDownloaded += bytesRead;
                    downloadInfo.setBytesDownloaded(bytesDownloaded);

                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastUpdateTime >= 1000 || bytesDownloaded == totalBytes) { // Update every second or on completion
                        int progress = (totalBytes > 0) ? (int) ((bytesDownloaded * 100) / totalBytes) : 0;
                        downloadInfo.setProgress(progress);

                        long timeDiff = currentTime - lastUpdateTime;
                        long bytesDiff = bytesDownloaded - lastUpdateBytes;
                        double speed = (timeDiff > 0) ? (double) bytesDiff / timeDiff * 1000 : 0; // Bytes per second
                        downloadInfo.setDownloadSpeed(formatSpeed(speed));

                        dbHelper.updateDownloadProgress(downloadInfo.getId(), bytesDownloaded, totalBytes, progress, downloadInfo.getDownloadSpeed());
                        sendBroadcastProgress(downloadInfo);
                        updateIndividualNotification(downloadInfo);

                        lastUpdateTime = currentTime;
                        lastUpdateBytes = bytesDownloaded;
                    }
                }
                outputStream.flush();
                // --- Download Successful ---
                downloadInfo.setStatus(DownloadProgressInfo.DownloadStatus.COMPLETED);
                downloadInfo.setProgress(100);
                dbHelper.updateDownloadStatus(downloadInfo.getId(), DownloadProgressInfo.DownloadStatus.COMPLETED, null);
                sendBroadcastStateChanged(downloadInfo);
                removeIndividualNotification(downloadInfo.getId().hashCode());
                Log.i(TAG, "Download concluído: " + downloadInfo.getFileName());

            } catch (IOException e) {
                Log.e(TAG, "Erro no download de " + downloadInfo.getFileName(), e);
                if (call != null && call.isCanceled()) {
                    downloadInfo.setStatus(DownloadProgressInfo.DownloadStatus.CANCELLED);
                    downloadInfo.setFailureReason("Cancelado");
                } else {
                    downloadInfo.setStatus(DownloadProgressInfo.DownloadStatus.FAILED);
                    downloadInfo.setFailureReason(e.getMessage() != null ? e.getMessage() : "Erro desconhecido");
                }
                dbHelper.updateDownloadStatus(downloadInfo.getId(), downloadInfo.getStatus(), downloadInfo.getFailureReason());
                sendBroadcastStateChanged(downloadInfo);
                updateIndividualNotificationAsFailedOrCancelled(downloadInfo); // Keep notification to show error/cancelled state
            } finally {
                try {
                    if (outputStream != null) outputStream.close();
                    if (inputStream != null) inputStream.close();
                } catch (IOException e) {
                    Log.e(TAG, "Erro ao fechar streams", e);
                }
                activeCalls.remove(downloadInfo.getId());
                activeDownloadTasks--;
                serviceHandler.post(() -> {
                    processQueue(); // Try to process next in queue
                    updateForegroundServiceNotification();
                });
            }
        }
    }

    private String formatSpeed(double bytesPerSecond) {
        if (bytesPerSecond < 1024) {
            return String.format(Locale.getDefault(), "%.1f B/s", bytesPerSecond);
        } else if (bytesPerSecond < 1024 * 1024) {
            return String.format(Locale.getDefault(), "%.1f KB/s", bytesPerSecond / 1024.0);
        } else {
            return String.format(Locale.getDefault(), "%.1f MB/s", bytesPerSecond / (1024.0 * 1024.0));
        }
    }

    private void sendBroadcastProgress(DownloadProgressInfo info) {
        Intent intent = new Intent(BROADCAST_DOWNLOAD_PROGRESS);
        intent.putExtra(EXTRA_DOWNLOAD_INFO, info);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void sendBroadcastStateChanged(DownloadProgressInfo info) {
        Intent intent = new Intent(BROADCAST_DOWNLOAD_STATE_CHANGED);
        intent.putExtra(EXTRA_DOWNLOAD_INFO, info);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Download Service Channel",
                    NotificationManager.IMPORTANCE_LOW // Use LOW to avoid sound for ongoing, can be DEFAULT for individual progress
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    private void updateIndividualNotification(DownloadProgressInfo info) {
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        int notificationId = info.getId().hashCode(); // Unique ID for this download's notification

        Intent cancelIntent = new Intent(this, DownloadService.class);
        cancelIntent.setAction(ACTION_CANCEL_DOWNLOAD);
        cancelIntent.putExtra(EXTRA_DOWNLOAD_ID, info.getId());
        PendingIntent cancelPendingIntent = PendingIntent.getService(this, notificationId, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(info.getFileName())
                .setContentText(String.format(Locale.getDefault(), "%s - %d%% (%s)",
                        info.getStatus().name(), info.getProgress(), info.getDownloadSpeed()))
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(100, info.getProgress(), false)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancelar", cancelPendingIntent);

        notificationManager.notify(notificationId, builder.build());
    }

    private void updateIndividualNotificationAsFailedOrCancelled(DownloadProgressInfo info) {
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        int notificationId = info.getId().hashCode();

        String statusText = info.getStatus() == DownloadProgressInfo.DownloadStatus.CANCELLED ? "Cancelado" : "Falhou";
         if (info.getFailureReason() != null && !info.getFailureReason().isEmpty()) {
            statusText += ": " + info.getFailureReason();
        }


        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(info.getFileName())
                .setContentText(statusText)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(false) // No longer ongoing
                .setAutoCancel(true); // Allow user to dismiss

        notificationManager.notify(notificationId, builder.build());
    }


    private void removeIndividualNotification(int notificationId) {
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.cancel(notificationId);
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "DownloadService onDestroy");
        if (downloadExecutorService != null) {
            downloadExecutorService.shutdownNow(); // Attempt to stop all actively executing tasks
        }
        if (serviceHandler != null && serviceHandler.getLooper() != null) {
            serviceHandler.getLooper().quitSafely();
        }
        // Clean up active calls if any are left (though shutdownNow should trigger their cancellation)
        for (Call call : activeCalls.values()) {
            if (!call.isCanceled()) {
                call.cancel();
            }
        }
        activeCalls.clear();
        Log.i(TAG, "DownloadService destruído e recursos liberados.");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // Not a bound service
    }
}
