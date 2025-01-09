package com.grpc.grpc;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.List;

public class ReportAdapter extends RecyclerView.Adapter<ReportAdapter.ReportViewHolder> {

    private final List<File> reportFiles;
    private final Context context;
    private final OnReportClickListener onReportClickListener;

    public interface OnReportClickListener {
        void onReportClick(File file);
        void onReportLongClick(File file);
    }

    public ReportAdapter(Context context, List<File> reportFiles, OnReportClickListener listener) {
        this.context = context;
        this.reportFiles = reportFiles;
        this.onReportClickListener = listener;
    }

    @NonNull
    @Override
    public ReportViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_1, parent, false);
        return new ReportViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ReportViewHolder holder, int position) {
        File reportFile = reportFiles.get(position);
        holder.reportName.setText(reportFile.getName());

        holder.itemView.setOnClickListener(v -> onReportClickListener.onReportClick(reportFile));
        holder.itemView.setOnLongClickListener(v -> {
            onReportClickListener.onReportLongClick(reportFile);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return reportFiles.size();
    }

    public static class ReportViewHolder extends RecyclerView.ViewHolder {
        TextView reportName;

        public ReportViewHolder(@NonNull View itemView) {
            super(itemView);
            reportName = itemView.findViewById(android.R.id.text1);
        }
    }
}
