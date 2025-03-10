package com.grpc.grpc;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.widget.ArrayAdapter;
import java.util.ArrayList;


/**
 * MessageAdapter.java
 *
 * This adapter is used for displaying chat messages in a ListView.
 * It takes an ArrayList of messages, extracts sender information,
 * message content, and timestamp, and formats them in a structured layout.
 *
 * Features:
 * - Parses messages to separate sender, message body, and timestamp
 * - Uses a custom layout for displaying chat messages
 * - Efficiently reuses views for performance optimization
 * - Ensures messages are displayed in a user-friendly format
 *
 * Author: James Scott
 */

public class MessageAdapter extends ArrayAdapter<String> {
    private Context mContext;
    private ArrayList<String> messages;

    public MessageAdapter(Context context, ArrayList<String> messages) {
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

        TextView senderText = convertView.findViewById(R.id.senderText);
        TextView bodyText = convertView.findViewById(R.id.bodyText);
        TextView timeText = convertView.findViewById(R.id.timeText);

        String fullMessage = messages.get(position);

        if (fullMessage.contains(": ")) {
            String[] parts = fullMessage.split(": ", 2);
            String senderTime = parts[0];
            String body = parts[1];

            if (senderTime.contains("(")) {
                int index = senderTime.lastIndexOf("(");
                String sender = senderTime.substring(0, index).trim();
                String time = senderTime.substring(index).trim();

                senderText.setText(sender);
                timeText.setText(time);
                bodyText.setText(body);
            }
        }

        return convertView;
    }
}