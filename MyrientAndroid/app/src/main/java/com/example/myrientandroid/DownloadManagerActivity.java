package com.example.myrientandroid;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.core.content.ContextCompat; // Adicionado

// import android.app.DownloadManager; // Removido
import android.content.Context; // Mantido para getSystemService em DownloadService (embora não usado aqui agora)
import android.content.Intent; // Adicionado/Confirmado
// import android.database.Cursor; // Removido
import android.os.Bundle;
// import android.os.Handler; // Removido
// import android.os.Looper; // Removido
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
// import java.util.Comparator; // Comparator está implícito na lambda de sort
import java.util.List;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;


public class DownloadManagerActivity extends AppCompatActivity {

    private static final String TAG = "DownloadManagerAct";
    private RecyclerView recyclerViewDownloads;
    private DownloadsAdapter downloadsAdapter;
    private List<DownloadProgressInfo> downloadItemsList = new ArrayList<>();
    private TextView textViewNoDownloads;
    private DownloadDbHelper dbHelper;
    private DownloadUpdateReceiver downloadUpdateReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_download_manager);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Meus Downloads"); // Título atualizado
        }

        dbHelper = new DownloadDbHelper(this);

        recyclerViewDownloads = findViewById(R.id.recyclerViewDownloads);
        textViewNoDownloads = findViewById(R.id.textViewNoDownloads);
        recyclerViewDownloads.setLayoutManager(new LinearLayoutManager(this));

        downloadsAdapter = new DownloadsAdapter(downloadItemsList, new DownloadsAdapter.OnDownloadInteractionListener() {
            @Override
            public void onCancelClick(String downloadId) {
                cancelDownloadAction(downloadId);
            }

            @Override
            public void onPauseClick(String downloadId) {
                Toast.makeText(DownloadManagerActivity.this, "Pausar ID: " + downloadId + " (não implementado)", Toast.LENGTH_SHORT).show();
                // Futuramente: Enviar Intent para DownloadService.ACTION_PAUSE_DOWNLOAD
            }

            @Override
            public void onResumeClick(String downloadId) {
                Toast.makeText(DownloadManagerActivity.this, "Continuar ID: " + downloadId + " (não implementado)", Toast.LENGTH_SHORT).show();
                // Futuramente: Enviar Intent para DownloadService.ACTION_RESUME_DOWNLOAD
            }
        });
        recyclerViewDownloads.setAdapter(downloadsAdapter);

        downloadUpdateReceiver = new DownloadUpdateReceiver();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadInitialDownloads(); // Carrega a lista inicial do DB

        IntentFilter filter = new IntentFilter();
        filter.addAction(DownloadService.BROADCAST_DOWNLOAD_PROGRESS);
        filter.addAction(DownloadService.BROADCAST_DOWNLOAD_STATE_CHANGED);
        LocalBroadcastManager.getInstance(this).registerReceiver(downloadUpdateReceiver, filter);
        Log.d(TAG, "BroadcastReceiver registrado.");
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(downloadUpdateReceiver);
        Log.d(TAG, "BroadcastReceiver desregistrado.");
    }

    private void loadInitialDownloads() {
        Log.d(TAG, "Carregando downloads iniciais do DB...");
        // Executar em uma thread para não bloquear a UI, embora a leitura do DB possa ser rápida para poucos itens
        new Thread(() -> {
            List<DownloadProgressInfo> itemsFromDb = dbHelper.getAllDownloadsSortedByDate();
            runOnUiThread(() -> {
                downloadItemsList.clear();
                downloadItemsList.addAll(itemsFromDb);
                downloadsAdapter.notifyDataSetChanged(); // Ou usar adapter.updateList(itemsFromDb);

                if (downloadItemsList.isEmpty()) {
                    textViewNoDownloads.setVisibility(View.VISIBLE);
                    recyclerViewDownloads.setVisibility(View.GONE);
                } else {
                    textViewNoDownloads.setVisibility(View.GONE);
                    recyclerViewDownloads.setVisibility(View.VISIBLE);
                }
                Log.d(TAG, "Downloads iniciais carregados: " + downloadItemsList.size());
            });
        }).start();
    }

    private void cancelDownloadAction(String downloadId) {
        Log.d(TAG, "Solicitando cancelamento para o download ID: " + downloadId);
        Intent intent = new Intent(this, DownloadService.class);
        intent.setAction(DownloadService.ACTION_CANCEL_DOWNLOAD);
        intent.putExtra(DownloadService.EXTRA_DOWNLOAD_ID, downloadId);
        ContextCompat.startForegroundService(this, intent);
        Toast.makeText(this, "Solicitação de cancelamento enviada...", Toast.LENGTH_SHORT).show();
        // A UI será atualizada via BroadcastReceiver quando o serviço confirmar o cancelamento.
        // Para feedback imediato, poderíamos mudar o estado localmente para "Cancelando..."
        // e então o broadcast confirmaria ou reverteria.
    }

    // O BroadcastReceiver será adicionado no próximo passo do plano.

    private class DownloadUpdateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) {
                return;
            }

            DownloadProgressInfo receivedInfo = intent.getParcelableExtra(DownloadService.EXTRA_DOWNLOAD_INFO);
            if (receivedInfo == null) {
                Log.w(TAG, "Recebido broadcast sem DownloadProgressInfo.");
                return;
            }

            Log.d(TAG, "Broadcast recebido: Ação=" + intent.getAction() + ", ID=" + receivedInfo.getId() + ", Status=" + receivedInfo.getStatus());

            boolean listChanged = false;
            int foundIndex = -1;
            for (int i = 0; i < downloadItemsList.size(); i++) {
                if (downloadItemsList.get(i).getId().equals(receivedInfo.getId())) {
                    foundIndex = i;
                    break;
                }
            }

            if (foundIndex != -1) {
                // Atualiza item existente
                downloadItemsList.set(foundIndex, receivedInfo);
                listChanged = true;
            } else {
                // Novo item (ex: PENDING de um novo download)
                // Adiciona e reordena para garantir que PENDING apareça (a ordenação é por timestamp de criação)
                downloadItemsList.add(receivedInfo);
                listChanged = true; // Indica que a lista mudou e precisa ser reordenada/notificada
            }

            if (listChanged) {
                if (foundIndex != -1) {
                    // Item existente foi atualizado, notificar mudança específica
                    downloadsAdapter.notifyItemChanged(foundIndex);
                } else {
                    // Novo item foi adicionado, reordenar e notificar dataset changed
                    // A ordenação deve ser consistente com loadInitialDownloads
                    Collections.sort(downloadItemsList, (o1, o2) -> Long.compare(o2.getCreatedAt(), o1.getCreatedAt()));
                    downloadsAdapter.notifyDataSetChanged();
                }
            }

            // Atualizar visibilidade do texto "Nenhum download"
            if (downloadItemsList.isEmpty()) {
                textViewNoDownloads.setVisibility(View.VISIBLE);
                recyclerViewDownloads.setVisibility(View.GONE);
            } else {
                textViewNoDownloads.setVisibility(View.GONE);
                recyclerViewDownloads.setVisibility(View.VISIBLE);
            }
        }
    }
}
