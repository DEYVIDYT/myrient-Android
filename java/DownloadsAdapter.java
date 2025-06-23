package com.example.myrientandroid;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import java.util.Locale; // Adicionado para formatação de velocidade

public class DownloadsAdapter extends RecyclerView.Adapter<DownloadsAdapter.DownloadViewHolder> {

    private List<DownloadProgressInfo> downloadList; // Mudado para DownloadProgressInfo
    private OnDownloadInteractionListener interactionListener; // Interface mais genérica

    public interface OnDownloadInteractionListener {
        void onCancelClick(String downloadId); // ID é String agora
        void onPauseClick(String downloadId); // Adicionado para o futuro
        void onResumeClick(String downloadId); // Adicionado para o futuro
    }

    public DownloadsAdapter(List<DownloadProgressInfo> downloadList, OnDownloadInteractionListener listener) {
        this.downloadList = downloadList;
        this.interactionListener = listener;
    }

    @NonNull
    @Override
    public DownloadViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_download, parent, false);
        return new DownloadViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DownloadViewHolder holder, int position) {
        DownloadProgressInfo item = downloadList.get(position);

        holder.fileNameTextView.setText(item.getFileName());
        holder.statusTextView.setText(item.getStatusText());

        String progressText = String.format(Locale.getDefault(), "%s (%d%%)",
                                item.getProgressSizeText(), item.getProgressPercentage());
        if (item.getStatus() == DownloadProgressInfo.DownloadStatus.DOWNLOADING && item.getDownloadSpeed() != null && !item.getDownloadSpeed().isEmpty()) {
            progressText += " - " + item.getDownloadSpeed();
        }
        holder.progressTextView.setText(progressText);
        holder.downloadProgressBar.setProgress(item.getProgressPercentage());

        // Visibilidade e ação do botão Cancelar
        if (item.isCancelable()) {
            holder.cancelButton.setVisibility(View.VISIBLE);
            holder.cancelButton.setOnClickListener(v -> {
                if (interactionListener != null) {
                    interactionListener.onCancelClick(item.getId());
                }
            });
        } else {
            holder.cancelButton.setVisibility(View.GONE);
        }

        // Lógica de visibilidade para botões Pausar/Continuar
        holder.pauseButton.setVisibility(item.isPausable() ? View.VISIBLE : View.GONE);
        holder.resumeButton.setVisibility(item.isResumable() ? View.VISIBLE : View.GONE);

        // Se o item não puder ser cancelado, mas puder ser pausado ou resumido,
        // o botão de cancelar pode estar escondido, então os outros botões não devem depender do seu alinhamento
        // se o cancelar estiver GONE. No layout atual, eles estão encadeados ao Cancelar.
        // Uma melhoria seria usar um LinearLayout horizontal para os botões ou ajustar constraints.
        // Por agora, a visibilidade individual é o foco.

        holder.pauseButton.setOnClickListener(v -> {
            if (interactionListener != null) {
                interactionListener.onPauseClick(item.getId());
            }
            // Toast.makeText(holder.itemView.getContext(), "Pausar: " + item.getFileName(), Toast.LENGTH_SHORT).show();
        });

        holder.resumeButton.setOnClickListener(v -> {
            if (interactionListener != null) {
                interactionListener.onResumeClick(item.getId());
            }
            // Toast.makeText(holder.itemView.getContext(), "Continuar: " + item.getFileName(), Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public int getItemCount() {
        return downloadList != null ? downloadList.size() : 0;
    }

    // Método para atualizar a lista (pode ser melhorado com DiffUtil no futuro)
    public void updateList(List<DownloadProgressInfo> newList) {
        this.downloadList = newList;
        notifyDataSetChanged();
    }

    static class DownloadViewHolder extends RecyclerView.ViewHolder {
        TextView fileNameTextView;
        TextView statusTextView;
        ProgressBar downloadProgressBar;
        TextView progressTextView;
        Button cancelButton;
        Button pauseButton; // Adicionado
        Button resumeButton; // Adicionado

        public DownloadViewHolder(@NonNull View itemView) {
            super(itemView);
            fileNameTextView = itemView.findViewById(R.id.textViewFileName);
            statusTextView = itemView.findViewById(R.id.textViewDownloadStatus);
            downloadProgressBar = itemView.findViewById(R.id.progressBarDownload);
            progressTextView = itemView.findViewById(R.id.textViewDownloadProgress);
            cancelButton = itemView.findViewById(R.id.buttonCancelDownload);
            pauseButton = itemView.findViewById(R.id.buttonPauseDownload); // Adicionado
            resumeButton = itemView.findViewById(R.id.buttonResumeDownload); // Adicionado
        }
    }
}
