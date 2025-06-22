package com.example.myrientandroid;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class GameAdapter extends RecyclerView.Adapter<GameAdapter.GameViewHolder> {

    private List<MyrientScraper.GameItem> gameList;
    private List<MyrientScraper.GameItem> gameListFiltered; // For search
    private OnGameInteractionListener listener;

    public interface OnGameInteractionListener {
        void onDownloadClick(MyrientScraper.GameItem gameItem);
    }

    public GameAdapter(List<MyrientScraper.GameItem> gameList, OnGameInteractionListener listener) {
        this.gameList = gameList;
        this.gameListFiltered = new ArrayList<>(gameList);
        this.listener = listener;
    }

    @NonNull
    @Override
    public GameViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_game, parent, false);
        return new GameViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GameViewHolder holder, int position) {
        MyrientScraper.GameItem gameItem = gameListFiltered.get(position);
        holder.gameNameTextView.setText(gameItem.name);
        holder.downloadButton.setOnClickListener(v -> listener.onDownloadClick(gameItem));
    }

    @Override
    public int getItemCount() {
        return gameListFiltered != null ? gameListFiltered.size() : 0;
    }

    public void updateData(List<MyrientScraper.GameItem> newGameList) {
        this.gameList.clear();
        this.gameList.addAll(newGameList);
        filter(""); // Apply empty filter to show all
    }

    public void filter(String query) {
        gameListFiltered.clear();
        if (query == null || query.isEmpty()) {
            gameListFiltered.addAll(gameList);
        } else {
            String lowerCaseQuery = query.toLowerCase();
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                gameListFiltered.addAll(
                    gameList.stream()
                            .filter(game -> game.name.toLowerCase().contains(lowerCaseQuery))
                            .collect(Collectors.toList())
                );
            } else {
                for (MyrientScraper.GameItem item : gameList) {
                    if (item.name.toLowerCase().contains(lowerCaseQuery)) {
                        gameListFiltered.add(item);
                    }
                }
            }
        }
        notifyDataSetChanged();
    }


    static class GameViewHolder extends RecyclerView.ViewHolder {
        TextView gameNameTextView;
        Button downloadButton;

        public GameViewHolder(@NonNull View itemView) {
            super(itemView);
            gameNameTextView = itemView.findViewById(R.id.textViewGameName);
            downloadButton = itemView.findViewById(R.id.buttonDownloadGame);
        }
    }
}
