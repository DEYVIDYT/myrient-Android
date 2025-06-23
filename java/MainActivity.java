package com.example.myrientandroid;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;


import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements ConsoleAdapter.OnConsoleClickListener {

    private static final String TAG = "MainActivity";
    private RecyclerView recyclerViewConsoles;
    private ConsoleAdapter consoleAdapter;
    private MyrientScraper myrientScraper;
    private List<MyrientScraper.ConsoleItem> consoleItems = new ArrayList<>();
    private ProgressBar progressBarMain;
    private TextView textViewMainInfo;

    // ActivityResultLauncher for permission request
    private ActivityResultLauncher<String> requestPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize the ActivityResultLauncher
        requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                // Permission is granted. Continue the action or workflow in your app.
                Log.d(TAG, "POST_NOTIFICATIONS permission granted.");
                Toast.makeText(this, "Permissão de notificação concedida.", Toast.LENGTH_SHORT).show();
            } else {
                // Explain to the user that the feature is unavailable because the
                // features requires a permission that the user has denied. At the
                // same time, respect the user's decision. Don't link to system
                // settings in an effort to convince the user to change their
                // decision.
                Log.d(TAG, "POST_NOTIFICATIONS permission denied.");
                Toast.makeText(this, "Permissão de notificação negada. Algumas funcionalidades podem ser limitadas.", Toast.LENGTH_LONG).show();
            }
        });

        askNotificationPermission();

        recyclerViewConsoles = findViewById(R.id.recyclerViewConsoles);
        progressBarMain = findViewById(R.id.progressBarMain);
        textViewMainInfo = findViewById(R.id.textViewMainInfo);

        recyclerViewConsoles.setLayoutManager(new LinearLayoutManager(this));
        consoleAdapter = new ConsoleAdapter(consoleItems, this);
        recyclerViewConsoles.setAdapter(consoleAdapter);

        myrientScraper = new MyrientScraper();
        fetchConsolesData();
    }

    private void askNotificationPermission() {
        // This is only necessary for API level 33 and higher (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED) {
                // FCM SDK (and your app) can post notifications.
                Log.d(TAG, "POST_NOTIFICATIONS permission already granted.");
            } else if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                // TODO: Display an educational UI explaining to the user the importance of the permission.
                // For now, just request directly or show a toast.
                Toast.makeText(this, "Por favor, conceda a permissão de notificação para receber atualizações de download.", Toast.LENGTH_LONG).show();
                // Then, request the permission
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            } else {
                // Directly ask for the permission
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }


    private void fetchConsolesData() {
        progressBarMain.setVisibility(View.VISIBLE);
        recyclerViewConsoles.setVisibility(View.GONE);
        textViewMainInfo.setVisibility(View.GONE);

        myrientScraper.fetchConsoles(new MyrientScraper.ScraperCallback<List<MyrientScraper.ConsoleItem>>() {
            @Override
            public void onCompleted(List<MyrientScraper.ConsoleItem> result) {
                progressBarMain.setVisibility(View.GONE);
                consoleItems.clear();
                consoleItems.addAll(result);
                consoleAdapter.updateData(result);

                if (result.isEmpty()) {
                    textViewMainInfo.setText("No consoles found or failed to parse.");
                    textViewMainInfo.setVisibility(View.VISIBLE);
                    recyclerViewConsoles.setVisibility(View.GONE);
                    Toast.makeText(MainActivity.this, "No consoles found or failed to parse.", Toast.LENGTH_LONG).show();
                } else {
                    recyclerViewConsoles.setVisibility(View.VISIBLE);
                    textViewMainInfo.setVisibility(View.GONE);
                }
            }

            @Override
            public void onError(Exception e) {
                progressBarMain.setVisibility(View.GONE);
                recyclerViewConsoles.setVisibility(View.GONE);
                textViewMainInfo.setText("Error fetching consoles: " + e.getMessage());
                textViewMainInfo.setVisibility(View.VISIBLE);
                Log.e(TAG, "Error fetching consoles", e);
                Toast.makeText(MainActivity.this, "Error fetching consoles: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onConsoleClick(MyrientScraper.ConsoleItem consoleItem) {
        Intent intent = new Intent(this, GameListActivity.class);
        intent.putExtra("CONSOLE_NAME", consoleItem.name);
        intent.putExtra("CONSOLE_URL", consoleItem.url);
        startActivity(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_download_manager) {
            Intent intent = new Intent(this, DownloadManagerActivity.class);
            startActivity(intent);
            return true;
        } else if (item.getItemId() == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
