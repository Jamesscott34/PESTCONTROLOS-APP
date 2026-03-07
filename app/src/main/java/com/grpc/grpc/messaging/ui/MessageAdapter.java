package com.grpc.grpc.messaging.ui;

import com.grpc.grpc.R;
import com.grpc.grpc.messaging.model.ChatMessage;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;

/**
 * MessageAdapter - Displays chat messages with optional urgent badge.
 */
public class MessageAdapter extends ArrayAdapter<ChatMessage> {
    private final Context mContext;
    private final ArrayList<ChatMessage> messages;

    public MessageAdapter(Context context, ArrayList<ChatMessage> messages) {
        super(context, R.layout.message_item, messages);
        this.mContext = context;
        this.messages = messages;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(mContext).inflate(R.layout.message_item, parent, false);
        }

        ChatMessage msg = messages.get(position);

        TextView senderText = convertView.findViewById(R.id.senderText);
        TextView bodyText = convertView.findViewById(R.id.bodyText);
        TextView timeText = convertView.findViewById(R.id.timeText);
        TextView urgentBadge = convertView.findViewById(R.id.urgentBadge);

        senderText.setText(msg.sender);
        bodyText.setText(msg.body);
        timeText.setText(msg.formattedTime);
        urgentBadge.setVisibility(msg.isUrgent ? View.VISIBLE : View.GONE);

        return convertView;
    }
}
