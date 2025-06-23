package com.example.myrientandroid;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

import java.io.File;

public class SettingsActivity extends AppCompatActivity {

    private static final String TAG = "SettingsActivity";
    private static final String PREFS_NAME = "MyrientDownloaderPrefs";
    
    // Preference keys
    public static final String PREF_DOWNLOAD_PATH = "download_path";
    public static final String PREF_CONCURRENT_DOWNLOADS = "concurrent_downloads";
    public static final String PREF_NOTIFICATIONS_ENABLED = "notifications_enabled";
    public static final String PREF_WIFI_ONLY = "wifi_only";
    
    // Default values
    private static final String DEFAULT_DOWNLOAD_PATH = "/storage/emulated/0/Download/MyrientDownloads";
    private static final int DEFAULT_CONCURRENT_DOWNLOADS = 3;
    private static final boolean DEFAULT_NOTIFICATIONS_ENABLED = true;
    private static final boolean DEFAULT_WIFI_ONLY = false;

    // UI Components
    private TextView tvDownloadPath;
    private Button btnChooseFolder;
    private SeekBar seekbarConcurrentDownloads;
    private TextView tvConcurrentDownloads;
    private Switch switchNotifications;
    private Switch switchWifiOnly;
    private Button btnResetSettings;
    private Button btnSaveSettings;

    // Preferences
    private SharedPreferences sharedPreferences;

    // Activity result launcher for folder picker
    private ActivityResultLauncher<Intent> folderPickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Setup action bar
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle("Configurações");
        }

        // Initialize preferences
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Initialize UI components
        initializeViews();

        // Setup folder picker launcher
        setupFolderPickerLauncher();

        // Load current settings
        loadSettings();

        // Setup listeners
        setupListeners();
    }

    private void initializeViews() {
        tvDownloadPath = findViewById(R.id.tv_download_path);
        btnChooseFolder = findViewById(R.id.btn_choose_folder);
        seekbarConcurrentDownloads = findViewById(R.id.seekbar_concurrent_downloads);
        tvConcurrentDownloads = findViewById(R.id.tv_concurrent_downloads);
        switchNotifications = findViewById(R.id.switch_notifications);
        switchWifiOnly = findViewById(R.id.switch_wifi_only);
        btnResetSettings = findViewById(R.id.btn_reset_settings);
        btnSaveSettings = findViewById(R.id.btn_save_settings);
    }

    private void setupFolderPickerLauncher() {
        folderPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Intent data = result.getData();
                    if (data != null) {
                        Uri uri = data.getData();
                        if (uri != null) {
                            handleFolderSelection(uri);
                        }
                    }
                }
            }
        );
    }

    private void loadSettings() {
        // Load download path
        String downloadPath = sharedPreferences.getString(PREF_DOWNLOAD_PATH, DEFAULT_DOWNLOAD_PATH);
        tvDownloadPath.setText(downloadPath);

        // Load concurrent downloads
        int concurrentDownloads = sharedPreferences.getInt(PREF_CONCURRENT_DOWNLOADS, DEFAULT_CONCURRENT_DOWNLOADS);
        seekbarConcurrentDownloads.setProgress(concurrentDownloads - 1); // SeekBar is 0-based, but we want 1-6
        tvConcurrentDownloads.setText(String.valueOf(concurrentDownloads));

        // Load notifications setting
        boolean notificationsEnabled = sharedPreferences.getBoolean(PREF_NOTIFICATIONS_ENABLED, DEFAULT_NOTIFICATIONS_ENABLED);
        switchNotifications.setChecked(notificationsEnabled);

        // Load WiFi only setting
        boolean wifiOnly = sharedPreferences.getBoolean(PREF_WIFI_ONLY, DEFAULT_WIFI_ONLY);
        switchWifiOnly.setChecked(wifiOnly);
    }

    private void setupListeners() {
        // Folder chooser button
        btnChooseFolder.setOnClickListener(v -> openFolderPicker());

        // Concurrent downloads seekbar
        seekbarConcurrentDownloads.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = progress + 1; // Convert 0-5 to 1-6
                tvConcurrentDownloads.setText(String.valueOf(value));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Reset settings button
        btnResetSettings.setOnClickListener(v -> resetToDefaults());

        // Save settings button
        btnSaveSettings.setOnClickListener(v -> saveSettings());
    }

    private void openFolderPicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            
            // Set initial directory to Downloads if possible
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, 
                    Uri.fromFile(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)));
            }
            
            folderPickerLauncher.launch(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error opening folder picker", e);
            Toast.makeText(this, "Erro ao abrir seletor de pasta", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleFolderSelection(Uri uri) {
        try {
            // Take persistable permission
            getContentResolver().takePersistableUriPermission(uri, 
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

            // Get the document file
            DocumentFile documentFile = DocumentFile.fromTreeUri(this, uri);
            if (documentFile != null && documentFile.exists()) {
                String path = uri.toString();
                tvDownloadPath.setText(path);
                Toast.makeText(this, "Pasta selecionada com sucesso", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Pasta inválida selecionada", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling folder selection", e);
            Toast.makeText(this, "Erro ao processar pasta selecionada", Toast.LENGTH_SHORT).show();
        }
    }

    private void resetToDefaults() {
        tvDownloadPath.setText(DEFAULT_DOWNLOAD_PATH);
        seekbarConcurrentDownloads.setProgress(DEFAULT_CONCURRENT_DOWNLOADS - 1);
        tvConcurrentDownloads.setText(String.valueOf(DEFAULT_CONCURRENT_DOWNLOADS));
        switchNotifications.setChecked(DEFAULT_NOTIFICATIONS_ENABLED);
        switchWifiOnly.setChecked(DEFAULT_WIFI_ONLY);
        
        Toast.makeText(this, "Configurações restauradas para os padrões", Toast.LENGTH_SHORT).show();
    }

    private void saveSettings() {
        SharedPreferences.Editor editor = sharedPreferences.edit();

        // Save download path
        editor.putString(PREF_DOWNLOAD_PATH, tvDownloadPath.getText().toString());

        // Save concurrent downloads
        int concurrentDownloads = seekbarConcurrentDownloads.getProgress() + 1;
        editor.putInt(PREF_CONCURRENT_DOWNLOADS, concurrentDownloads);

        // Save notifications setting
        editor.putBoolean(PREF_NOTIFICATIONS_ENABLED, switchNotifications.isChecked());

        // Save WiFi only setting
        editor.putBoolean(PREF_WIFI_ONLY, switchWifiOnly.isChecked());

        // Apply changes
        editor.apply();

        Toast.makeText(this, "Configurações salvas com sucesso", Toast.LENGTH_SHORT).show();
        
        // Finish activity and return to previous screen
        finish();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // Static helper methods for other classes to access preferences
    public static String getDownloadPath(android.content.Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getString(PREF_DOWNLOAD_PATH, DEFAULT_DOWNLOAD_PATH);
    }

    public static int getConcurrentDownloads(android.content.Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getInt(PREF_CONCURRENT_DOWNLOADS, DEFAULT_CONCURRENT_DOWNLOADS);
    }

    public static boolean areNotificationsEnabled(android.content.Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getBoolean(PREF_NOTIFICATIONS_ENABLED, DEFAULT_NOTIFICATIONS_ENABLED);
    }

    public static boolean isWifiOnlyEnabled(android.content.Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getBoolean(PREF_WIFI_ONLY, DEFAULT_WIFI_ONLY);
    }
}

