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
import android.view.View;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recyclerViewConsoles = findViewById(R.id.recyclerViewConsoles);
        progressBarMain = findViewById(R.id.progressBarMain);
        textViewMainInfo = findViewById(R.id.textViewMainInfo);

        recyclerViewConsoles.setLayoutManager(new LinearLayoutManager(this));
        consoleAdapter = new ConsoleAdapter(consoleItems, this);
        recyclerViewConsoles.setAdapter(consoleAdapter);

        myrientScraper = new MyrientScraper();
        fetchConsolesData();
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
}
