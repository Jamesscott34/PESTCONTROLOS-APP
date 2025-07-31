package com.grpc.grpc;

/**
 * Represents a chat message with optional urgent flag.
 */
public class ChatMessage {
    public final String documentId;
    public final String sender;
    public final String body;
    public final String formattedTime;
    public final boolean isUrgent;

    public ChatMessage(String documentId, String sender, String body, String formattedTime, boolean isUrgent) {
        this.documentId = documentId;
        this.sender = sender;
        this.body = body;
        this.formattedTime = formattedTime;
        this.isUrgent = isUrgent;
    }
}
