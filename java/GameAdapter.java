package com.example.myrientandroid;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class GameAdapter extends RecyclerView.Adapter<GameAdapter.GameViewHolder> {

    private List<MyrientScraper.GameItem> gameList;
    private OnGameInteractionListener listener;

    public interface OnGameInteractionListener {
        void onDownloadClick(MyrientScraper.GameItem gameItem);
    }

    public GameAdapter(List<MyrientScraper.GameItem> gameList, OnGameInteractionListener listener) {
        this.gameList = gameList;
        this.listener = listener;
        setHasStableIds(true); // Otimização para IDs estáveis
    }

    @NonNull
    @Override
    public GameViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_game, parent, false);
        return new GameViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GameViewHolder holder, int position) {
        MyrientScraper.GameItem gameItem = gameList.get(position);
        holder.bind(gameItem, listener);
    }

    @Override
    public int getItemCount() {
        return gameList != null ? gameList.size() : 0;
    }

    @Override
    public long getItemId(int position) {
        // Usa o hash do URL como ID estável
        return gameList.get(position).downloadUrl.hashCode();
    }

    static class GameViewHolder extends RecyclerView.ViewHolder {
        private TextView gameNameTextView;
        private Button downloadButton;
        private ImageView gameIcon;

        public GameViewHolder(@NonNull View itemView) {
            super(itemView);
            gameNameTextView = itemView.findViewById(R.id.textViewGameName);
            downloadButton = itemView.findViewById(R.id.buttonDownloadGame);
            gameIcon = itemView.findViewById(R.id.imageViewGameIcon);
        }

        public void bind(MyrientScraper.GameItem gameItem, OnGameInteractionListener listener) {
            gameNameTextView.setText(gameItem.name);
            
            // Remove listener anterior para evitar vazamentos
            downloadButton.setOnClickListener(null);
            downloadButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDownloadClick(gameItem);
                }
            });
        }
    }
}

