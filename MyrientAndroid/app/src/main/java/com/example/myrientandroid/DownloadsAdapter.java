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

public class DownloadsAdapter extends RecyclerView.Adapter<DownloadsAdapter.DownloadViewHolder> {

    private List<DownloadItem> downloadList;
    private OnDownloadCancelListener cancelListener;

    public interface OnDownloadCancelListener {
        void onCancelClick(long downloadId);
    }

    public DownloadsAdapter(List<DownloadItem> downloadList, OnDownloadCancelListener cancelListener) {
        this.downloadList = downloadList;
        this.cancelListener = cancelListener;
    }

    @NonNull
    @Override
    public DownloadViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_download, parent, false);
        return new DownloadViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DownloadViewHolder holder, int position) {
        DownloadItem item = downloadList.get(position);

        holder.fileNameTextView.setText(item.getFileName());
        holder.statusTextView.setText(item.getStatusText());
        holder.progressTextView.setText(item.getProgressSizeText() + " (" + item.getProgressPercentage() + "%)");
        holder.downloadProgressBar.setProgress(item.getProgressPercentage());

        if (item.isCancelable()) {
            holder.cancelButton.setVisibility(View.VISIBLE);
            holder.cancelButton.setOnClickListener(v -> {
                if (cancelListener != null) {
                    cancelListener.onCancelClick(item.getId());
                }
            });
        } else {
            holder.cancelButton.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return downloadList != null ? downloadList.size() : 0;
    }

    static class DownloadViewHolder extends RecyclerView.ViewHolder {
        TextView fileNameTextView;
        TextView statusTextView;
        ProgressBar downloadProgressBar;
        TextView progressTextView;
        Button cancelButton;

        public DownloadViewHolder(@NonNull View itemView) {
            super(itemView);
            fileNameTextView = itemView.findViewById(R.id.textViewFileName);
            statusTextView = itemView.findViewById(R.id.textViewDownloadStatus);
            downloadProgressBar = itemView.findViewById(R.id.progressBarDownload);
            progressTextView = itemView.findViewById(R.id.textViewDownloadProgress);
            cancelButton = itemView.findViewById(R.id.buttonCancelDownload);
        }
    }
}
