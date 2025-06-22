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
    public static final String ACTION_PAUSE_DOWNLOAD = "com.example.myrientandroid.ACTION_PAUSE_DOWNLOAD";
    public static final String ACTION_RESUME_DOWNLOAD = "com.example.myrientandroid.ACTION_RESUME_DOWNLOAD";

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
            } else if (ACTION_PAUSE_DOWNLOAD.equals(action)) {
                final String downloadIdToPause = intent.getStringExtra(EXTRA_DOWNLOAD_ID);
                if (downloadIdToPause != null) {
                    serviceHandler.post(() -> pauseDownloadInternal(downloadIdToPause));
                }
            } else if (ACTION_RESUME_DOWNLOAD.equals(action)) {
                final String downloadIdToResume = intent.getStringExtra(EXTRA_DOWNLOAD_ID);
                if (downloadIdToResume != null) {
                    serviceHandler.post(() -> resumeDownloadInternal(downloadIdToResume));
                }
            }
        }
        return START_STICKY; // If the service is killed, try to restart it
    }

    private void pauseDownloadInternal(String downloadId) {
        Log.d(TAG, "Solicitando pausa para download ID: " + downloadId);
        DownloadProgressInfo item = dbHelper.getDownload(downloadId);

        if (item != null && item.getStatus() == DownloadProgressInfo.DownloadStatus.DOWNLOADING) {
            Log.i(TAG, "Pausando download: " + item.getFileName());
            // 1. Atualizar status no DB para PAUSED PRIMEIRO
            // A DownloadTaskRunnable verificará este status ao capturar a IOException do cancelamento da Call
            dbHelper.updateDownloadStatus(downloadId, DownloadProgressInfo.DownloadStatus.PAUSED, "Pausado pelo usuário");
            item.setStatus(DownloadProgressInfo.DownloadStatus.PAUSED); // Atualiza o objeto local também
            item.setFailureReason("Pausado pelo usuário"); // Reutilizando failureReason para mensagem de status

            // 2. Enviar broadcast para UI
            sendBroadcastStateChanged(item);

            // 3. Cancelar a Call OkHttp (a task tratará isso como pausa devido ao status no DB)
            Call call = activeCalls.get(downloadId); // Não remover ainda, a task pode precisar dele para verificar isCanceled
            if (call != null) {
                call.cancel(); // Isso fará com que a DownloadTaskRunnable entre no bloco catch
                Log.d(TAG, "Call OkHttp cancelada para intenção de pausa, ID: " + downloadId);
            } else {
                Log.w(TAG, "Nenhuma Call ativa encontrada para pausar, ID: " + downloadId + ". O download pode já ter terminado ou falhado.");
                // Se não há call ativa, mas o status era DOWNLOADING, pode ser um estado inconsistente.
                // A task, ao terminar, deve ter atualizado o status.
                // Forçar uma atualização da fila e notificação do serviço.
                activeDownloadTasks = Math.max(0, activeDownloadTasks -1); // Decrementa se estava contando como ativo
                 processQueue();
            }

            // 4. Atualizar notificação individual para "Pausado"
            // A DownloadTaskRunnable também tentará fazer isso ao finalizar, mas podemos fazer aqui para feedback mais rápido.
            updateIndividualNotificationAsPaused(item);


            // 5. Atualizar notificação do serviço (contagem de ativos pode mudar)
            updateForegroundServiceNotification();

        } else {
            Log.w(TAG, "Não foi possível pausar download ID: " + downloadId + ". Status atual: " + (item != null ? item.getStatus() : "não encontrado"));
        }
    }

    private void updateIndividualNotificationAsPaused(DownloadProgressInfo info) {
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        int notificationId = info.getId().hashCode();

        // Intent para retomar (será implementado)
        Intent resumeIntent = new Intent(this, DownloadService.class);
        resumeIntent.setAction(ACTION_RESUME_DOWNLOAD);
        resumeIntent.putExtra(EXTRA_DOWNLOAD_ID, info.getId());
        PendingIntent resumePendingIntent = PendingIntent.getService(this, notificationId + 1, resumeIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Intent para cancelar
        Intent cancelIntent = new Intent(this, DownloadService.class);
        cancelIntent.setAction(ACTION_CANCEL_DOWNLOAD);
        cancelIntent.putExtra(EXTRA_DOWNLOAD_ID, info.getId());
        PendingIntent cancelPendingIntent = PendingIntent.getService(this, notificationId + 2, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);


        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(info.getFileName())
                .setContentText("Pausado - " + info.getProgressSizeText())
                .setSmallIcon(android.R.drawable.stat_sys_download_done) // Ícone diferente para pausado
                .setOngoing(false) // Permite dispensar se desejado, ou manter ongoing
                .setAutoCancel(true) // Se não for ongoing
                .setProgress(100, info.getProgress(), false)
                .addAction(android.R.drawable.ic_media_play, "Continuar", resumePendingIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancelar", cancelPendingIntent);

        notificationManager.notify(notificationId, builder.build());
    }


    private void resumeDownloadInternal(String downloadId) {
        Log.d(TAG, "Solicitando continuação para download ID: " + downloadId);
        DownloadProgressInfo item = dbHelper.getDownload(downloadId);

        if (item != null && item.getStatus() == DownloadProgressInfo.DownloadStatus.PAUSED) {
            Log.i(TAG, "Continuando download: " + item.getFileName());
            // 1. Atualizar status no DB para PENDING
            // A DownloadTaskRunnable, ao ser iniciada para um item PENDING com bytesDownloaded > 0,
            // saberá que deve tentar uma retomada com Range header.
            dbHelper.updateDownloadStatus(downloadId, DownloadProgressInfo.DownloadStatus.PENDING, null); // Limpa a razão da pausa
            item.setStatus(DownloadProgressInfo.DownloadStatus.PENDING);
            item.setFailureReason(null);


            // 2. Enviar broadcast para UI (opcional, ou deixar processQueue fazer ao mudar para DOWNLOADING)
            // sendBroadcastStateChanged(item); // Ou a UI pode apenas esperar o status DOWNLOADING

            // 3. Chamar processQueue para que o item seja pego pela lógica de enfileiramento
            processQueue(); // Isso tentará iniciar a task se houver slots disponíveis

            // 4. Atualizar notificação do serviço
            updateForegroundServiceNotification();
            // A notificação individual será atualizada para progresso quando a task iniciar.
            // Poderia remover a notificação de "Pausado" aqui ou deixar a task lidar com isso.
            // Para evitar que a notificação "Pausado" com botão "Continuar" persista, vamos removê-la.
            removeIndividualNotification(item.getId().hashCode());


        } else {
            Log.w(TAG, "Não foi possível continuar download ID: " + downloadId + ". Status atual: " + (item != null ? item.getStatus() : "não encontrado"));
        }
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
                    if (!targetDir.mkdirs()) {
                        Log.w(TAG, "Não foi possível criar o diretório de downloads: " + targetDir.getAbsolutePath());
                        // Considerar falhar o download aqui se o diretório não puder ser criado
                    }
                }
                File targetFile = new File(targetDir, downloadInfo.getFileName());
                // Se o arquivo já existe e não estamos retomando, alguns podem querer renomear ou falhar.
                // Por agora, vamos sobrescrever se não for uma retomada.

                downloadInfo.setLocalFilePath(targetFile.getAbsolutePath());
                // Atualizar o DB com o caminho local, se ainda não estiver lá ou mudou.
                // dbHelper.updateDownload(downloadInfo); // Já feito ao adicionar, ou pode ser feito aqui se o nome do arquivo for sanitizado.


                // --- OkHttp Request Setup ---
                Request.Builder requestBuilder = new Request.Builder().url(downloadInfo.getOriginalUrl());

                boolean isResuming = downloadInfo.getStatus() == DownloadProgressInfo.DownloadStatus.PENDING && downloadInfo.getBytesDownloaded() > 0;
                if (isResuming) {
                    Log.i(TAG, "Tentando retomar download para " + downloadInfo.getFileName() + " a partir de " + downloadInfo.getBytesDownloaded() + " bytes.");
                    requestBuilder.addHeader("Range", "bytes=" + downloadInfo.getBytesDownloaded() + "-");
                }

                call = okHttpClient.newCall(requestBuilder.build());
                activeCalls.put(downloadInfo.getId(), call);

                Response response = call.execute();
                ResponseBody body = response.body();

                if (body == null) { // Checagem de corpo nulo
                    throw new IOException("Corpo da resposta nulo para " + downloadInfo.getFileName());
                }

                long totalBytesReportedByServer = body.contentLength(); // Tamanho do conteúdo restante (se Range) ou total.
                long initialBytesDownloaded = downloadInfo.getBytesDownloaded(); // Bytes que já tínhamos.
                long totalBytesForProgress;

                if (isResuming) {
                    if (response.code() == 206) { // HTTP_PARTIAL_CONTENT
                        Log.i(TAG, "Servidor suportou retomada (206) para " + downloadInfo.getFileName());
                        // totalBytes já deve estar correto no DB de antes. Se não, content-length aqui é o restante.
                        // O Content-Range header diria o total: "bytes offset-last/total"
                        String contentRange = response.header("Content-Range");
                        if (contentRange != null) {
                            try {
                                long serverTotal = Long.parseLong(contentRange.substring(contentRange.indexOf('/') + 1));
                                downloadInfo.setTotalBytes(serverTotal);
                            } catch (Exception e) { Log.e(TAG, "Erro ao parsear Content-Range: " + contentRange); }
                        }
                        // Se totalBytes ainda for -1, usar o totalBytesReportedByServer + initialBytesDownloaded
                        if(downloadInfo.getTotalBytes() <=0 && totalBytesReportedByServer > 0) {
                            downloadInfo.setTotalBytes(initialBytesDownloaded + totalBytesReportedByServer);
                        }
                         totalBytesForProgress = downloadInfo.getTotalBytes();
                         outputStream = new FileOutputStream(targetFile, true); // Append
                    } else {
                        Log.w(TAG, "Servidor não suportou retomada (código " + response.code() + ") para " + downloadInfo.getFileName() + ". Reiniciando download.");
                        isResuming = false; // Tratar como novo download
                        initialBytesDownloaded = 0;
                        downloadInfo.setBytesDownloaded(0);
                        downloadInfo.setTotalBytes(totalBytesReportedByServer > 0 ? totalBytesReportedByServer : -1); // Atualiza o total se conhecido
                        totalBytesForProgress = downloadInfo.getTotalBytes();
                        outputStream = new FileOutputStream(targetFile, false); // Overwrite
                    }
                } else { // Novo download
                    if (!response.isSuccessful()) {
                        throw new IOException("Falha no download (novo): " + response.code() + " - " + response.message());
                    }
                    initialBytesDownloaded = 0;
                    downloadInfo.setBytesDownloaded(0);
                    downloadInfo.setTotalBytes(totalBytesReportedByServer > 0 ? totalBytesReportedByServer : -1);
                    totalBytesForProgress = downloadInfo.getTotalBytes();
                    outputStream = new FileOutputStream(targetFile, false); // Overwrite
                }

                // Atualiza DB com o tamanho total se foi descoberto/confirmado
                if (downloadInfo.getTotalBytes() > 0) {
                     dbHelper.updateDownloadProgress(downloadInfo.getId(), downloadInfo.getBytesDownloaded(), downloadInfo.getTotalBytes(), downloadInfo.getProgress(), downloadInfo.getDownloadSpeed());
                }
                sendBroadcastProgress(downloadInfo); // Envia progresso inicial (pode ser 0%)

                inputStream = body.byteStream();
                byte[] data = new byte[8192];
                long currentBytesDownloadedThisSession = 0;
                int bytesRead;
                long lastUpdateTime = System.currentTimeMillis();
                long lastUpdateBytes = 0;

                // --- Download Loop ---
                while ((bytesRead = inputStream.read(data)) != -1) {
                    // Re-check for cancellation inside the loop
                    if (activeCalls.get(downloadInfo.getId()) == null || activeCalls.get(downloadInfo.getId()).isCanceled()) {
                         // Check current status from DB to see if this was a pause
                        DownloadProgressInfo currentDbState = dbHelper.getDownload(downloadInfo.getId());
                        if (currentDbState != null && currentDbState.getStatus() == DownloadProgressInfo.DownloadStatus.PAUSED) {
                            Log.i(TAG, "Download pausado (detectado no loop): " + downloadInfo.getFileName());
                            // Bytes já foram salvos, estado já foi setado para PAUSED no DB pelo pauseDownloadInternal
                            // A notificação também já foi atualizada por lá.
                            // Apenas sair da task.
                            return; // Exit runnable
                        } else {
                            // Se não foi uma pausa, então é um cancelamento.
                            Log.d(TAG, "Download cancelado durante o loop (detectado): " + downloadInfo.getFileName());
                            throw new IOException("Download cancelado");
                        }
                    }

                    outputStream.write(data, 0, bytesRead);
                    currentBytesDownloadedThisSession += bytesRead;
                    long totalOverallBytesDownloaded = initialBytesDownloaded + currentBytesDownloadedThisSession;
                    downloadInfo.setBytesDownloaded(totalOverallBytesDownloaded);

                    long currentTime = System.currentTimeMillis();
                    // totalBytesForProgress pode ser -1 se o tamanho total não for conhecido.
                    if (currentTime - lastUpdateTime >= 1000 || (totalBytesForProgress > 0 && totalOverallBytesDownloaded == totalBytesForProgress) ) {
                        int progress = (totalBytesForProgress > 0) ? (int) ((totalOverallBytesDownloaded * 100) / totalBytesForProgress) : 0;
                        downloadInfo.setProgress(progress);

                        long timeDiff = currentTime - lastUpdateTime;
                        long bytesDiff = totalOverallBytesDownloaded - lastUpdateBytes; // Comparar com o totalOverallBytesDownloaded anterior
                        double speed = (timeDiff > 0) ? (double) bytesDiff / timeDiff * 1000 : 0;
                        downloadInfo.setDownloadSpeed(formatSpeed(speed));

                        dbHelper.updateDownloadProgress(downloadInfo.getId(), totalOverallBytesDownloaded, totalBytesForProgress, progress, downloadInfo.getDownloadSpeed());
                        sendBroadcastProgress(downloadInfo);
                        updateIndividualNotification(downloadInfo);

                        lastUpdateTime = currentTime;
                        lastUpdateBytes = totalOverallBytesDownloaded; // Atualiza para o total geral
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
                Log.e(TAG, "Exceção no download de " + downloadInfo.getFileName() + ": " + e.getMessage());
                // Verificar o estado no DB para distinguir Pausa de Cancelamento/Falha
                DownloadProgressInfo currentDbState = dbHelper.getDownload(downloadInfo.getId());
                DownloadProgressInfo.DownloadStatus finalStatus = DownloadProgressInfo.DownloadStatus.FAILED; // Default
                String reason = e.getMessage() != null ? e.getMessage() : "Erro desconhecido";

                if (currentDbState != null && currentDbState.getStatus() == DownloadProgressInfo.DownloadStatus.PAUSED) {
                    finalStatus = DownloadProgressInfo.DownloadStatus.PAUSED;
                    reason = "Pausado pelo usuário"; // Já definido por pauseDownloadInternal
                    Log.i(TAG, "Download " + downloadInfo.getFileName() + " confirmado como PAUSADO após exceção (provavelmente call.cancel()).");
                } else if (call != null && call.isCanceled()) { // Se a call foi cancelada e não era uma pausa
                    finalStatus = DownloadProgressInfo.DownloadStatus.CANCELLED;
                    reason = "Cancelado";
                     Log.i(TAG, "Download " + downloadInfo.getFileName() + " confirmado como CANCELADO após exceção.");
                    deletePartialFile(downloadInfo.getLocalFilePath());
                } else {
                     Log.w(TAG, "Download " + downloadInfo.getFileName() + " FALHOU. Razão: " + reason);
                    // Considerar deletar arquivo parcial em caso de falha também, dependendo da política.
                    // deletePartialFile(downloadInfo.getLocalFilePath());
                }

                downloadInfo.setStatus(finalStatus);
                downloadInfo.setFailureReason(reason);
                dbHelper.updateDownloadStatus(downloadInfo.getId(), finalStatus, reason);
                sendBroadcastStateChanged(downloadInfo);

                if (finalStatus == DownloadProgressInfo.DownloadStatus.PAUSED) {
                    // A notificação de pausa já foi (ou será) tratada por pauseDownloadInternal
                    // updateIndividualNotificationAsPaused(downloadInfo); // Redundante se pauseDownloadInternal já fez
                } else {
                    updateIndividualNotificationAsFailedOrCancelled(downloadInfo);
                }

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

    private void deletePartialFile(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return;
        }
        File file = new File(filePath);
        if (file.exists()) {
            if (file.delete()) {
                Log.i(TAG, "Arquivo parcial deletado: " + filePath);
            } else {
                Log.w(TAG, "Falha ao deletar arquivo parcial: " + filePath);
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // Not a bound service
    }
}
