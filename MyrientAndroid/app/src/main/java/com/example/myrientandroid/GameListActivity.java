package com.example.myrientandroid;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.EditText;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

public class GameListActivity extends AppCompatActivity implements GameAdapter.OnGameInteractionListener {

    private static final String TAG = "GameListActivity";
    private RecyclerView recyclerViewGames;
    private GameAdapter gameAdapter;
    private MyrientScraper myrientScraper;
    private List<MyrientScraper.GameItem> gameItems = new ArrayList<>();
    private EditText editTextSearch;
    private String consoleName;
    private String consoleUrl;
    private ProgressBar progressBarGameList;
    private TextView textViewGameListInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_list);

        consoleName = getIntent().getStringExtra("CONSOLE_NAME");
        consoleUrl = getIntent().getStringExtra("CONSOLE_URL");

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(consoleName != null ? consoleName : "Games");
        }

        recyclerViewGames = findViewById(R.id.recyclerViewGames);
        progressBarGameList = findViewById(R.id.progressBarGameList);
        textViewGameListInfo = findViewById(R.id.textViewGameListInfo);
        editTextSearch = findViewById(R.id.editTextSearch);

        recyclerViewGames.setLayoutManager(new LinearLayoutManager(this));
        gameAdapter = new GameAdapter(gameItems, this);
        recyclerViewGames.setAdapter(gameAdapter);

        editTextSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                gameAdapter.filter(s.toString());
                if (gameAdapter.getItemCount() == 0) {
                    if (gameItems.isEmpty()){ // Original list was empty
                        textViewGameListInfo.setText("No games found for this console.");
                    } else { // Search yielded no results from a non-empty list
                        textViewGameListInfo.setText("No games match your search.");
                    }
                    textViewGameListInfo.setVisibility(View.VISIBLE);
                    recyclerViewGames.setVisibility(View.GONE);
                } else {
                    textViewGameListInfo.setVisibility(View.GONE);
                    recyclerViewGames.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        myrientScraper = new MyrientScraper();
        if (consoleUrl != null) {
            fetchGamesData(consoleUrl);
        } else {
            Toast.makeText(this, "Console URL not found.", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Console URL is null.");
        }
    }

    private void fetchGamesData(String url) {
        progressBarGameList.setVisibility(View.VISIBLE);
        recyclerViewGames.setVisibility(View.GONE);
        textViewGameListInfo.setVisibility(View.GONE);

        myrientScraper.fetchGamesForConsole(url, new MyrientScraper.ScraperCallback<List<MyrientScraper.GameItem>>() {
            @Override
            public void onCompleted(List<MyrientScraper.GameItem> result) {
                progressBarGameList.setVisibility(View.GONE);
                gameItems.clear();
                gameItems.addAll(result);
                // gameAdapter.updateData will call filter, which handles visibility of recycler vs text info for empty filtered list
                gameAdapter.updateData(result);

                if (result.isEmpty()) {
                    textViewGameListInfo.setText("No games found for this console.");
                    textViewGameListInfo.setVisibility(View.VISIBLE);
                    recyclerViewGames.setVisibility(View.GONE);
                    // Toast.makeText(GameListActivity.this, "No games found or failed to parse.", Toast.LENGTH_LONG).show();
                } else {
                    // Visibility is handled by adapter's filter method based on search results
                    // If search is empty, and results are present, RecyclerView will be visible.
                    // If search is active and filters everything, adapter will show no items,
                    // we might need an additional "No results for your search" text in GameAdapter or here.
                    // For now, let's assume GameAdapter handles showing/hiding itself if its list is empty.
                    // The initial state is correct with this.
                    recyclerViewGames.setVisibility(View.VISIBLE);
                    textViewGameListInfo.setVisibility(View.GONE);
                }
            }

            @Override
            public void onError(Exception e) {
                progressBarGameList.setVisibility(View.GONE);
                recyclerViewGames.setVisibility(View.GONE);
                textViewGameListInfo.setText("Error fetching games: " + e.getMessage());
                textViewGameListInfo.setVisibility(View.VISIBLE);
                Log.e(TAG, "Error fetching games", e);
                // Toast.makeText(GameListActivity.this, "Error fetching games: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onDownloadClick(MyrientScraper.GameItem gameItem) {
        if (gameItem == null || gameItem.downloadUrl == null || gameItem.downloadUrl.isEmpty()) {
            Toast.makeText(this, "Invalid download URL.", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Download attempt with invalid gameItem or URL.");
            return;
        }

        String fileName = gameItem.name; // Or extract from URL if more reliable
        // Try to sanitize filename a bit, though DownloadManager might handle this.
        // fileName = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");


        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(gameItem.downloadUrl));
        request.setTitle(fileName);
        request.setDescription("Downloading " + fileName);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

        // Save to public Downloads directory
        // This location is generally accessible and standard for user-initiated downloads.
        // For API 29+, direct WRITE_EXTERNAL_STORAGE is not needed for this specific directory.
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

        // Optionally: Set MIME type if known, though often inferred
        // request.setMimeType("application/octet-stream"); // Generic binary file

        DownloadManager downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        if (downloadManager != null) {
            try {
                downloadManager.enqueue(request);
                Toast.makeText(this, "Download started for " + fileName, Toast.LENGTH_LONG).show();
                Log.d(TAG, "Download enqueued for: " + fileName + " URL: " + gameItem.downloadUrl);
            } catch (Exception e) {
                Log.e(TAG, "Error starting download for " + fileName, e);
                Toast.makeText(this, "Error starting download: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        } else {
            Log.e(TAG, "DownloadManager service not available.");
            Toast.makeText(this, "Download service not available.", Toast.LENGTH_LONG).show();
        }
    }
}
