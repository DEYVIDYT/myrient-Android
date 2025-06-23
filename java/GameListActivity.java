package com.example.myrientandroid;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.DiffUtil;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import androidx.core.content.ContextCompat;
import android.os.Bundle;
import android.os.Environment;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.view.View;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.EditText;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

public class GameListActivity extends AppCompatActivity implements GameAdapter.OnGameInteractionListener {

    private static final String TAG = "GameListActivity";
    private static final int ITEMS_PER_PAGE = 50; // Carrega 50 jogos por vez
    
    private RecyclerView recyclerViewGames;
    private GameAdapter gameAdapter;
    private MyrientScraper myrientScraper;
    private List<MyrientScraper.GameItem> allGameItems = new ArrayList<>();
    private List<MyrientScraper.GameItem> displayedItems = new ArrayList<>();
    private EditText editTextSearch;
    private String consoleName;
    private String consoleUrl;
    private ProgressBar progressBarGameList;
    private TextView textViewGameListInfo;
    private View loadingLayout;
    private View emptyLayout;
    
    private boolean isLoading = false;
    private boolean hasMoreItems = true;
    private int currentPage = 0;
    private String currentSearchQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_list);

        consoleName = getIntent().getStringExtra("CONSOLE_NAME");
        consoleUrl = getIntent().getStringExtra("CONSOLE_URL");

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(consoleName != null ? consoleName : "Games");
        }

        initializeViews();
        setupRecyclerView();
        setupSearch();

        myrientScraper = new MyrientScraper();
        if (consoleUrl != null) {
            fetchGamesData(consoleUrl);
        } else {
            Toast.makeText(this, "Console URL not found.", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Console URL is null.");
        }
    }

    private void initializeViews() {
        recyclerViewGames = findViewById(R.id.recyclerViewGames);
        progressBarGameList = findViewById(R.id.progressBarGameList);
        textViewGameListInfo = findViewById(R.id.textViewGameListInfo);
        editTextSearch = findViewById(R.id.editTextSearch);
        loadingLayout = findViewById(R.id.loadingLayout);
        emptyLayout = findViewById(R.id.emptyLayout);
    }

    private void setupRecyclerView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerViewGames.setLayoutManager(layoutManager);
        
        // Otimizações do RecyclerView
        recyclerViewGames.setHasFixedSize(true);
        recyclerViewGames.setItemViewCacheSize(20);
        recyclerViewGames.setDrawingCacheEnabled(true);
        recyclerViewGames.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);
        
        gameAdapter = new GameAdapter(displayedItems, this);
        recyclerViewGames.setAdapter(gameAdapter);

        // Scroll listener para paginação
        recyclerViewGames.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                
                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager != null && !isLoading && hasMoreItems) {
                    int visibleItemCount = layoutManager.getChildCount();
                    int totalItemCount = layoutManager.getItemCount();
                    int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();

                    if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 5) {
                        loadMoreItems();
                    }
                }
            }
        });
    }

    private void setupSearch() {
        editTextSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = s.toString();
                currentPage = 0;
                hasMoreItems = true;
                displayedItems.clear();
                gameAdapter.notifyDataSetChanged();
                loadMoreItems();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void fetchGamesData(String url) {
        showLoading();

        myrientScraper.fetchGamesForConsole(url, new MyrientScraper.ScraperCallback<List<MyrientScraper.GameItem>>() {
            @Override
            public void onCompleted(List<MyrientScraper.GameItem> result) {
                hideLoading();
                allGameItems.clear();
                allGameItems.addAll(result);
                
                currentPage = 0;
                hasMoreItems = true;
                displayedItems.clear();
                
                if (allGameItems.isEmpty()) {
                    showEmpty("Nenhum jogo encontrado para este console.");
                } else {
                    loadMoreItems();
                }
            }

            @Override
            public void onError(Exception e) {
                hideLoading();
                showEmpty("Erro ao carregar jogos: " + e.getMessage());
                Log.e(TAG, "Error fetching games", e);
            }
        });
    }

    private void loadMoreItems() {
        if (isLoading || !hasMoreItems) return;
        
        isLoading = true;
        
        // Simula um pequeno delay para evitar carregamento muito rápido
        recyclerViewGames.postDelayed(() -> {
            List<MyrientScraper.GameItem> filteredItems = getFilteredItems();
            int startIndex = currentPage * ITEMS_PER_PAGE;
            int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, filteredItems.size());
            
            if (startIndex < filteredItems.size()) {
                List<MyrientScraper.GameItem> newItems = filteredItems.subList(startIndex, endIndex);
                int oldSize = displayedItems.size();
                displayedItems.addAll(newItems);
                gameAdapter.notifyItemRangeInserted(oldSize, newItems.size());
                currentPage++;
                
                if (endIndex >= filteredItems.size()) {
                    hasMoreItems = false;
                }
                
                showRecyclerView();
            } else {
                hasMoreItems = false;
                if (displayedItems.isEmpty()) {
                    showEmpty("Nenhum jogo corresponde à sua busca.");
                }
            }
            
            isLoading = false;
        }, 100);
    }

    private List<MyrientScraper.GameItem> getFilteredItems() {
        if (currentSearchQuery.isEmpty()) {
            return allGameItems;
        }
        
        List<MyrientScraper.GameItem> filtered = new ArrayList<>();
        String lowerCaseQuery = currentSearchQuery.toLowerCase();
        for (MyrientScraper.GameItem item : allGameItems) {
            if (item.name.toLowerCase().contains(lowerCaseQuery)) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    private void showLoading() {
        loadingLayout.setVisibility(View.VISIBLE);
        recyclerViewGames.setVisibility(View.GONE);
        emptyLayout.setVisibility(View.GONE);
    }

    private void hideLoading() {
        loadingLayout.setVisibility(View.GONE);
    }

    private void showRecyclerView() {
        recyclerViewGames.setVisibility(View.VISIBLE);
        emptyLayout.setVisibility(View.GONE);
    }

    private void showEmpty(String message) {
        textViewGameListInfo.setText(message);
        emptyLayout.setVisibility(View.VISIBLE);
        recyclerViewGames.setVisibility(View.GONE);
    }

    @Override
    public void onDownloadClick(MyrientScraper.GameItem gameItem) {
        if (gameItem == null || gameItem.downloadUrl == null || gameItem.downloadUrl.isEmpty()) {
            Toast.makeText(this, "URL de download inválida.", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Download attempt with invalid gameItem or URL.");
            return;
        }

        String fileName = gameItem.name;

        Intent intent = new Intent(this, DownloadService.class);
        intent.setAction(DownloadService.ACTION_START_DOWNLOAD);
        intent.putExtra(DownloadService.EXTRA_URL, gameItem.downloadUrl);
        intent.putExtra(DownloadService.EXTRA_FILE_NAME, fileName);

        ContextCompat.startForegroundService(this, intent);
        Toast.makeText(this, "Download de " + fileName + " adicionado à fila.", Toast.LENGTH_LONG).show();
        Log.d(TAG, "Solicitação de download enviada ao DownloadService para: " + fileName + " URL: " + gameItem.downloadUrl);
    }
}

