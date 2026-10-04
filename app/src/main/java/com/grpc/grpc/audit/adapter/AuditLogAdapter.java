package com.grpc.grpc.audit.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.grpc.grpc.R;
import com.grpc.grpc.audit.model.AuditLogEntry;

import java.util.ArrayList;
import java.util.List;

public class AuditLogAdapter extends BaseAdapter {

    public static final int TYPE_HEADER = 0;
    public static final int TYPE_ENTRY = 1;

    public static final class Row {
        public final boolean header;
        public final String headerText;
        public final AuditLogEntry entry;

        public static Row header(String text) {
            return new Row(true, text, null);
        }

        public static Row entry(AuditLogEntry entry) {
            return new Row(false, "", entry);
        }

        private Row(boolean header, String headerText, AuditLogEntry entry) {
            this.header = header;
            this.headerText = headerText == null ? "" : headerText;
            this.entry = entry;
        }
    }

    private final LayoutInflater inflater;
    private final List<Row> rows = new ArrayList<>();

    public AuditLogAdapter(Context context) {
        inflater = LayoutInflater.from(context);
    }

    public void setRows(List<Row> next) {
        rows.clear();
        if (next != null) rows.addAll(next);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return rows.size();
    }

    @Override
    public Row getItem(int position) {
        return rows.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).header ? TYPE_HEADER : TYPE_ENTRY;
    }

    @Override
    public boolean isEnabled(int position) {
        return !rows.get(position).header;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Row row = rows.get(position);
        if (row.header) {
            View view = convertView;
            if (view == null) view = inflater.inflate(R.layout.item_audit_log_header, parent, false);
            TextView title = view.findViewById(R.id.auditHeaderText);
            title.setText(row.headerText);
            return view;
        }
        View view = convertView;
        if (view == null) view = inflater.inflate(R.layout.item_audit_log, parent, false);
        AuditLogEntry entry = row.entry;
        TextView time = view.findViewById(R.id.auditTime);
        TextView name = view.findViewById(R.id.auditActor);
        TextView action = view.findViewById(R.id.auditAction);
        TextView detail = view.findViewById(R.id.auditDetail);
        time.setText(entry.displayTime());
        name.setText(entry.displayName());
        action.setText(entry.displayAction());
        String subject = entry.displayDetail();
        detail.setText(subject);
        detail.setVisibility(subject.isEmpty() ? View.GONE : View.VISIBLE);
        return view;
    }
}
