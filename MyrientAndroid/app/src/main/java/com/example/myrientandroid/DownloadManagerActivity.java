package com.example.myrientandroid;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class DownloadManagerActivity extends AppCompatActivity {

    private static final String TAG = "DownloadManagerAct";
    private RecyclerView recyclerViewDownloads;
    private DownloadsAdapter downloadsAdapter;
    private List<DownloadItem> downloadItems = new ArrayList<>();
    private TextView textViewNoDownloads;
    private DownloadManager systemDownloadManager;
    private Handler handler;
    private Runnable updateRunnable;
    private static final long UPDATE_INTERVAL = 2000; // 2 segundos

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_download_manager);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Gerenciador de Downloads");
        }

        recyclerViewDownloads = findViewById(R.id.recyclerViewDownloads);
        textViewNoDownloads = findViewById(R.id.textViewNoDownloads);
        recyclerViewDownloads.setLayoutManager(new LinearLayoutManager(this));

        downloadsAdapter = new DownloadsAdapter(downloadItems, downloadId -> {
            // Lógica de cancelamento
            cancelDownload(downloadId);
        });
        recyclerViewDownloads.setAdapter(downloadsAdapter);

        systemDownloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        handler = new Handler(Looper.getMainLooper());

        setupUpdateRunnable();
    }

    private void setupUpdateRunnable() {
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                loadDownloads();
                handler.postDelayed(this, UPDATE_INTERVAL);
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(updateRunnable); // Inicia a atualização
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(updateRunnable); // Para a atualização
    }

    private void loadDownloads() {
        Log.d(TAG, "Carregando downloads...");
        new Thread(() -> {
            List<DownloadItem> currentDownloads = new ArrayList<>();
            DownloadManager.Query query = new DownloadManager.Query();
            //  query.setFilterByStatus(~(DownloadManager.STATUS_FAILED | DownloadManager.STATUS_SUCCESSFUL)); // Exemplo: apenas ativos/pendentes
            // Para mostrar todos os downloads feitos pelo app:
            // Não há um filtro direto para "apenas deste app" no query,
            // então teremos que buscar todos e potencialmente filtrar ou apenas exibir.
            // O DownloadManager geralmente só retorna downloads iniciados pelo UID do app.

            Cursor cursor = null;
            try {
                cursor = systemDownloadManager.query(query);
                if (cursor != null && cursor.moveToFirst()) {
                    int idColumn = cursor.getColumnIndex(DownloadManager.COLUMN_ID);
                    int titleColumn = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE);
                    int statusColumn = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                    int reasonColumn = cursor.getColumnIndex(DownloadManager.COLUMN_REASON);
                    int totalBytesColumn = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
                    int downloadedBytesColumn = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                    int uriColumn = cursor.getColumnIndex(DownloadManager.COLUMN_URI);
                    int localUriColumn = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
                    int lastModifiedColumn = cursor.getColumnIndex(DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP);

                    do {
                        long id = cursor.getLong(idColumn);
                        String title = cursor.getString(titleColumn);
                        int status = cursor.getInt(statusColumn);
                        int reason = cursor.getInt(reasonColumn);
                        long totalBytes = cursor.getLong(totalBytesColumn);
                        long downloadedBytes = cursor.getLong(downloadedBytesColumn);
                        String uri = cursor.getString(uriColumn);
                        String localUri = cursor.getString(localUriColumn);
                        long lastModified = cursor.getLong(lastModifiedColumn);

                        currentDownloads.add(new DownloadItem(id, title, status, reason, totalBytes, downloadedBytes, uri, localUri, lastModified));
                    } while (cursor.moveToNext());
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao consultar downloads", e);
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }

            // Ordenar por mais recente
            Collections.sort(currentDownloads, (o1, o2) -> Long.compare(o2.getLastModifiedTimestamp(), o1.getLastModifiedTimestamp()));

            runOnUiThread(() -> {
                downloadItems.clear();
                downloadItems.addAll(currentDownloads);
                downloadsAdapter.notifyDataSetChanged();

                if (downloadItems.isEmpty()) {
                    textViewNoDownloads.setVisibility(View.VISIBLE);
                    recyclerViewDownloads.setVisibility(View.GONE);
                } else {
                    textViewNoDownloads.setVisibility(View.GONE);
                    recyclerViewDownloads.setVisibility(View.VISIBLE);
                }
                Log.d(TAG, "Downloads carregados: " + downloadItems.size());
            });
        }).start();
    }

    private void cancelDownload(long downloadId) {
        if (systemDownloadManager != null) {
            int count = systemDownloadManager.remove(downloadId);
            if (count > 0) {
                Toast.makeText(this, "Download cancelado.", Toast.LENGTH_SHORT).show();
                // A lista será atualizada na próxima execução do updateRunnable
                // Para feedback imediato, poderíamos remover o item da lista localmente e notificar o adapter,
                // ou forçar uma atualização.
                loadDownloads(); // Força atualização imediata
            } else {
                Toast.makeText(this, "Não foi possível cancelar o download.", Toast.LENGTH_SHORT).show();
                Log.w(TAG, "Falha ao cancelar download ID: " + downloadId + ", contagem de remoção: " + count);
            }
        }
    }
}
