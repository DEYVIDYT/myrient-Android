package com.example.myrientandroid;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class MyrientScraper {

    private static final String BASE_URL = "https://myrient.erista.me/files/Redump/";
    private final OkHttpClient client = new OkHttpClient();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public interface ScraperCallback<T> {
        void onCompleted(T result);
        void onError(Exception e);
    }

    public static class ConsoleItem {
        public final String name;
        public final String url;

        public ConsoleItem(String name, String url) {
            this.name = name;
            this.url = url;
        }
    }

    public static class GameItem {
        public final String name;
        public final String downloadUrl;

        public GameItem(String name, String downloadUrl) {
            this.name = name;
            this.downloadUrl = downloadUrl;
        }
    }

    public void fetchConsoles(ScraperCallback<List<ConsoleItem>> callback) {
        executorService.execute(() -> {
            try {
                Request request = new Request.Builder().url(BASE_URL).build();
                Response response = client.newCall(request).execute();
                ResponseBody body = response.body();
                if (!response.isSuccessful() || body == null) {
                    throw new IOException("Unexpected code " + response);
                }

                String html = body.string();
                Document doc = Jsoup.parse(html, BASE_URL);
                Elements links = doc.select("a[href]"); // Select all 'a' tags with an 'href' attribute

                List<ConsoleItem> consoles = new ArrayList<>();
                for (Element link : links) {
                    String href = link.attr("href");
                    String name = link.text().trim();
                    // Filter out parent directory, other non-console links
                    if (!href.startsWith("?") && !href.startsWith("/") && href.endsWith("/") && !name.equals("[To Parent Directory]")) {
                        // Remove trailing slash for cleaner name
                        String consoleName = name.substring(0, name.length() -1);
                        consoles.add(new ConsoleItem(consoleName, BASE_URL + href));
                    }
                }
                // Simulate main thread callback
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> callback.onCompleted(consoles));
            } catch (IOException e) {
                 new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> callback.onError(e));
            }
        });
    }

    public void fetchGamesForConsole(String consoleUrl, ScraperCallback<List<GameItem>> callback) {
        executorService.execute(() -> {
            try {
                Request request = new Request.Builder().url(consoleUrl).build();
                Response response = client.newCall(request).execute();
                ResponseBody body = response.body();
                if (!response.isSuccessful() || body == null) {
                    throw new IOException("Unexpected code " + response);
                }

                String html = body.string();
                Document doc = Jsoup.parse(html, consoleUrl);
                Elements links = doc.select("a[href]");

                List<GameItem> games = new ArrayList<>();
                for (Element link : links) {
                    String href = link.attr("href");
                    String name = link.text().trim();
                    // Filter out non-game files (like parent dir, other metadata files)
                    // This assumes game files don't end with '/' and are not special links
                    if (!href.startsWith("?") && !href.startsWith("/") && !href.endsWith("/") && !name.equals("[To Parent Directory]")) {
                         // Basic filter to exclude common non-game files, might need refinement
                        if (!name.toLowerCase().endsWith(".txt") &&
                            !name.toLowerCase().endsWith(".dat") &&
                            !name.toLowerCase().endsWith(".xml") &&
                            !name.toLowerCase().endsWith(".cue") && // Example: CUE sheets are often with CD images
                            !name.toLowerCase().endsWith(".sfv") &&
                            !name.toLowerCase().endsWith(".md5")) {
                            games.add(new GameItem(name, consoleUrl + href));
                        }
                    }
                }
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> callback.onCompleted(games));
            } catch (IOException e) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> callback.onError(e));
            }
        });
    }
}
