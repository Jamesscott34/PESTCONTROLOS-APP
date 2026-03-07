package com.grpc.grpc.messaging.ui;

import com.grpc.grpc.R;
import com.grpc.grpc.core.*;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;

import java.util.List;

public class ConversationListAdapter extends ArrayAdapter<MessagingConversationsActivity.ConversationItem> {

    public ConversationListAdapter(Context context, List<MessagingConversationsActivity.ConversationItem> items) {
        super(context, R.layout.item_conversation, items);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_conversation, parent, false);
        }

        MessagingConversationsActivity.ConversationItem item = getItem(position);
        if (item == null) return convertView;

        TextView icon = convertView.findViewById(R.id.conversationIcon);
        TextView name = convertView.findViewById(R.id.conversationName);
        TextView preview = convertView.findViewById(R.id.conversationPreview);
        View row = convertView;

        icon.setText(item.icon);
        name.setText(item.hasUnread ? item.displayName + " (unread)" : item.displayName);
        preview.setText(item.preview);

        if (item.hasUnread) {
            int unreadBg = MaterialColors.getColor(convertView, com.google.android.material.R.attr.colorPrimaryContainer);
            int unreadTint = MaterialColors.getColor(convertView, com.google.android.material.R.attr.colorOnPrimaryContainer);
            row.setBackgroundColor(unreadBg);
            name.setTextColor(unreadTint);
        } else {
            row.setBackgroundColor(ContextCompat.getColor(getContext(), android.R.color.transparent));
            name.setTextColor(MaterialColors.getColor(convertView, android.R.attr.textColorPrimary));
        }

        return convertView;
    }
}
