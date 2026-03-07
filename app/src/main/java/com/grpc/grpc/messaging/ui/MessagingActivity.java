package com.grpc.grpc.messaging.ui;

import com.grpc.grpc.R;
import com.grpc.grpc.core.*;
import com.grpc.grpc.messaging.model.ChatMessage;

import android.app.AlertDialog;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * MessagingActivity - Per-conversation chat (1:1 or group).
 * Messages auto-delete after 30 min unless marked urgent.
 */
public class MessagingActivity extends AppCompatActivity {

    private static final int AUTO_DELETE_MINUTES = 30;

    private EditText messageInput;
    private Button sendButton, deleteAllButton;
    private CheckBox urgentCheckbox;
    private ListView messageListView;
    private TextView chatTitle, autoDeleteHint;

    private ArrayList<ChatMessage> messages;
    private MessageAdapter messageAdapter;
    private FirebaseFirestore firestore;

    private String userName;
    private String conversationId;
    private String conversationName;

    private String getLastSeenPrefKey() {
        return "CHAT_LAST_SEEN_" + conversationId;
    }

    private void markConversationSeen(long millis) {
        getSharedPreferences("GRPC", MODE_PRIVATE)
                .edit()
                .putLong(getLastSeenPrefKey(), Math.max(millis, System.currentTimeMillis()))
                .apply();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_messaging);

        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        userName = getIntent().getStringExtra("USER_NAME");

        // RBAC: hide messaging for everyone except super_admin.
        if (!SessionManager.isSuperAdmin(this)) {
            Toast.makeText(this, "Messaging is currently available only to super admin.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        conversationId = getIntent().getStringExtra("CONVERSATION_ID");
        conversationName = getIntent().getStringExtra("CONVERSATION_NAME");

        if (userName == null || userName.isEmpty() || conversationId == null || conversationId.isEmpty()) {
            Toast.makeText(this, "Error: Missing conversation data", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (conversationName == null) conversationName = "Chat";

        messageInput = findViewById(R.id.messageInput);
        sendButton = findViewById(R.id.sendButton);
        deleteAllButton = findViewById(R.id.deleteAllButton);
        urgentCheckbox = findViewById(R.id.urgentCheckbox);
        messageListView = findViewById(R.id.messageListView);
        chatTitle = findViewById(R.id.chatTitle);
        autoDeleteHint = findViewById(R.id.autoDeleteHint);

        chatTitle.setText(conversationName);
        autoDeleteHint.setText("Messages delete after " + AUTO_DELETE_MINUTES + " min. Check \"Urgent\" to keep.");

        messages = new ArrayList<>();
        messageAdapter = new MessageAdapter(this, messages);
        messageListView.setAdapter(messageAdapter);

        firestore = FirebaseFirestore.getInstance();

        sendButton.setOnClickListener(v -> sendMessage());
        deleteAllButton.setOnClickListener(v -> confirmDeleteAllMessages());

        loadMessages();

        messageListView.setOnItemClickListener((parent, view, position, id) ->
                copyMessage(messages.get(position)));

        messageListView.setOnItemLongClickListener((parent, view, position, id) -> {
            confirmDeleteMessage(position);
            return true;
        });

        Button backBtn = findViewById(R.id.backButton);
        if (backBtn != null) {
            backBtn.setOnClickListener(v -> finish());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Opening the conversation counts as "seen"
        markConversationSeen(System.currentTimeMillis());
    }

    private String getMessagesPath() {
        return "conversations/" + conversationId + "/messages";
    }

    private void sendMessage() {
        String messageText = messageInput.getText().toString().trim();
        if (TextUtils.isEmpty(messageText)) {
            Toast.makeText(this, "Please enter a message", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean isUrgent = urgentCheckbox.isChecked();

        Map<String, Object> message = new HashMap<>();
        message.put("sender", userName);
        message.put("body", messageText);
        message.put("timestamp", new Date());
        message.put("createdAt", new Date());
        message.put("isUrgent", isUrgent);

        sendButton.setEnabled(false);
        sendButton.setText("Sending...");

        firestore.collection(getMessagesPath()).add(message)
                .addOnSuccessListener(docRef -> {
                    Toast.makeText(this, "Message sent", Toast.LENGTH_SHORT).show();
                    messageInput.setText("");
                    urgentCheckbox.setChecked(false);
                    sendButton.setEnabled(true);
                    sendButton.setText("Send");
                })
                .addOnFailureListener(e -> {
                    Log.e("Messaging", "Send failed", e);
                    Toast.makeText(this, "Failed to send", Toast.LENGTH_SHORT).show();
                    sendButton.setEnabled(true);
                    sendButton.setText("Send");
                });
    }

    private void loadMessages() {
        firestore.collection(getMessagesPath())
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Log.e("Messaging", "Load error", error);
                        Toast.makeText(this, "Error loading messages", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (snapshots == null) return;

                    int newCount = 0;
                    for (DocumentChange change : snapshots.getDocumentChanges()) {
                        QueryDocumentSnapshot doc = change.getDocument();
                        if (change.getType() == DocumentChange.Type.REMOVED) {
                            removeMessageById(doc.getId());
                            continue;
                        }

                        String sender = doc.getString("sender");
                        String body = doc.getString("body");
                        Date timestamp = doc.getDate("timestamp");
                        Boolean isUrgent = doc.getBoolean("isUrgent");
                        if (isUrgent == null) isUrgent = false;

                        if (sender != null && body != null && timestamp != null) {
                            String formattedTime = new SimpleDateFormat("HH:mm dd/MM", Locale.getDefault())
                                    .format(timestamp);

                            if (!hasMessageId(doc.getId())) {
                                messages.add(new ChatMessage(doc.getId(), sender, body, formattedTime, isUrgent));
                                newCount++;
                            }
                        }
                    }

                    if (newCount > 0) {
                        messageAdapter.notifyDataSetChanged();
                        messageListView.post(() -> messageListView.setSelection(messages.size() - 1));
                    }

                    // Update last-seen to latest message timestamp while user is viewing the chat
                    if (!snapshots.isEmpty()) {
                        Date latestTs = snapshots.getDocuments()
                                .get(snapshots.getDocuments().size() - 1)
                                .getDate("timestamp");
                        if (latestTs != null) {
                            markConversationSeen(latestTs.getTime());
                        }
                    }
                });
    }

    private boolean hasMessageId(String id) {
        for (ChatMessage m : messages) {
            if (m.documentId.equals(id)) return true;
        }
        return false;
    }

    private void removeMessageById(String id) {
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).documentId.equals(id)) {
                messages.remove(i);
                messageAdapter.notifyDataSetChanged();
                return;
            }
        }
    }

    private void confirmDeleteAllMessages() {
        new AlertDialog.Builder(this)
                .setTitle("Delete All Messages")
                .setMessage("Delete all messages in this conversation?")
                .setPositiveButton("Yes", (d, w) -> deleteAllMessages())
                .setNegativeButton("No", null)
                .show();
    }

    private void deleteAllMessages() {
        firestore.collection(getMessagesPath()).get()
                .addOnSuccessListener(snap -> {
                    for (QueryDocumentSnapshot doc : snap) {
                        doc.getReference().delete();
                    }
                    messages.clear();
                    messageAdapter.notifyDataSetChanged();
                    Toast.makeText(this, "Messages deleted", Toast.LENGTH_SHORT).show();
                });
    }

    private void confirmDeleteMessage(int position) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Message")
                .setMessage("Delete this message?")
                .setPositiveButton("Yes", (d, w) -> deleteMessage(position))
                .setNegativeButton("No", null)
                .show();
    }

    private void deleteMessage(int position) {
        String docId = messages.get(position).documentId;
        firestore.collection(getMessagesPath()).document(docId).delete()
                .addOnSuccessListener(aVoid -> {
                    messages.remove(position);
                    messageAdapter.notifyDataSetChanged();
                });
    }

    private void copyMessage(ChatMessage msg) {
        String text = msg.sender + " (" + msg.formattedTime + "): " + msg.body;
        if (msg.isUrgent) text = "[URGENT] " + text;

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText("Message", text);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
    }
}
