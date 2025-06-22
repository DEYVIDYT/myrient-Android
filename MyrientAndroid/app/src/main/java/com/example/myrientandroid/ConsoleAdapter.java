package com.example.myrientandroid;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class ConsoleAdapter extends RecyclerView.Adapter<ConsoleAdapter.ConsoleViewHolder> {

    private List<MyrientScraper.ConsoleItem> consoleList;
    private OnConsoleClickListener listener;

    public interface OnConsoleClickListener {
        void onConsoleClick(MyrientScraper.ConsoleItem consoleItem);
    }

    public ConsoleAdapter(List<MyrientScraper.ConsoleItem> consoleList, OnConsoleClickListener listener) {
        this.consoleList = consoleList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ConsoleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_console, parent, false);
        return new ConsoleViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ConsoleViewHolder holder, int position) {
        MyrientScraper.ConsoleItem consoleItem = consoleList.get(position);
        holder.consoleNameTextView.setText(consoleItem.name);
        holder.itemView.setOnClickListener(v -> listener.onConsoleClick(consoleItem));
    }

    @Override
    public int getItemCount() {
        return consoleList != null ? consoleList.size() : 0;
    }

    public void updateData(List<MyrientScraper.ConsoleItem> newConsoleList) {
        this.consoleList = newConsoleList;
        notifyDataSetChanged(); // For simplicity, replace with DiffUtil for better performance
    }

    static class ConsoleViewHolder extends RecyclerView.ViewHolder {
        TextView consoleNameTextView;

        public ConsoleViewHolder(@NonNull View itemView) {
            super(itemView);
            consoleNameTextView = itemView.findViewById(R.id.textViewConsoleName);
        }
    }
}
