package com.grpc.grpc;

import android.app.AlertDialog;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

/**
 * MessagingActivity.java
 *
 * This activity provides a real-time chat feature using Firebase Firestore.
 * Users can send, receive, copy, and delete messages. Messages are stored in Firestore
 * and displayed in a ListView using a custom adapter.
 *
 * Features:
 * - Send and receive messages in real-time
 * - Store messages in Firebase Firestore
 * - Display messages in a structured format with sender, timestamp, and content
 * - Copy messages to clipboard with a single tap
 * - Delete individual messages or clear all messages
 * - Uses Firebase Authentication to identify the sender
 *
 * Author: James Scott
 */


public class MessagingActivity extends AppCompatActivity {

    private EditText messageInput;
    private Button sendButton, deleteAllButton;
    private ListView messageListView;
    private ArrayList<String> messages;
    private ArrayList<String> messageIds;
    private MessageAdapter messageAdapter;
    private FirebaseFirestore firestore;
    private FirebaseUser currentUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_messaging);
        
        // Handle system UI for Samsung devices with navigation buttons
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setFlags(
                android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            );
        }

        messageInput = findViewById(R.id.messageInput);
        sendButton = findViewById(R.id.sendButton);
        deleteAllButton = findViewById(R.id.deleteAllButton);
        messageListView = findViewById(R.id.messageListView);

        messages = new ArrayList<>();
        messageIds = new ArrayList<>();
        messageAdapter = new MessageAdapter(this, messages);
        messageListView.setAdapter(messageAdapter);

        firestore = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        sendButton.setOnClickListener(view -> sendMessage());
        deleteAllButton.setOnClickListener(view -> confirmDeleteAllMessages());

        loadMessages();

        messageListView.setOnItemClickListener((parent, view, position, id) -> copyMessage(messages.get(position)));

        messageListView.setOnItemLongClickListener((parent, view, position, id) -> {
            confirmDeleteMessage(position);
            return true;
        });
    }

    private void sendMessage() {
        String messageText = messageInput.getText().toString().trim();
        if (TextUtils.isEmpty(messageText) || currentUser == null) {
            Toast.makeText(this, "Please enter a message", Toast.LENGTH_SHORT).show();
            return;
        }

        String senderName = currentUser.getEmail().split("@")[0];

        HashMap<String, Object> message = new HashMap<>();
        message.put("sender", senderName);
        message.put("body", messageText);
        message.put("timestamp", Timestamp.now());

        // Show sending indicator
        sendButton.setEnabled(false);
        sendButton.setText("Sending...");

        firestore.collection("messages").add(message)
                .addOnSuccessListener(documentReference -> {
                    Log.d("Firestore", "Message sent successfully");
                    Toast.makeText(this, "✅ Message sent", Toast.LENGTH_SHORT).show();
                    messageInput.setText("");
                    sendButton.setEnabled(true);
                    sendButton.setText("Send");
                })
                .addOnFailureListener(e -> {
                    Log.e("Firestore", "Error sending message", e);
                    Toast.makeText(this, "❌ Failed to send message", Toast.LENGTH_SHORT).show();
                    sendButton.setEnabled(true);
                    sendButton.setText("Send");
                });
    }


    private void loadMessages() {
        firestore.collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Log.e("Firestore", "Error loading messages", error);
                        Toast.makeText(this, "❌ Error loading messages", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (snapshots == null || snapshots.isEmpty()) {
                        Log.d("Firestore", "No messages found.");
                        return;
                    }

                    int newMessageCount = 0;
                    for (DocumentChange change : snapshots.getDocumentChanges()) {
                        QueryDocumentSnapshot doc = change.getDocument();
                        String sender = doc.getString("sender");
                        String body = doc.getString("body");
                        Timestamp timestamp = doc.getTimestamp("timestamp");

                        if (sender != null && body != null && timestamp != null) {
                            String formattedTime = new SimpleDateFormat("HH:mm:ss dd/MM/yyyy", Locale.getDefault())
                                    .format(timestamp.toDate());
                            String fullMessage = String.format("%s (%s): %s", sender, formattedTime, body);

                            // Avoid adding duplicates
                            if (!messageIds.contains(doc.getId())) {
                                messages.add(fullMessage);
                                messageIds.add(doc.getId());
                                newMessageCount++;
                            }
                        }
                    }
                    
                    if (newMessageCount > 0) {
                        messageAdapter.notifyDataSetChanged();
                        // Auto-scroll to bottom for new messages
                        messageListView.post(() -> messageListView.setSelection(messages.size() - 1));
                        
                        // Show notification for new messages (if not from current user)
                        if (newMessageCount > 0 && currentUser != null) {
                            String currentSender = currentUser.getEmail().split("@")[0];
                            boolean hasNewMessagesFromOthers = false;
                            
                            // Check if any new messages are from other users
                            for (int i = messages.size() - newMessageCount; i < messages.size(); i++) {
                                String message = messages.get(i);
                                if (!message.startsWith(currentSender + " (")) {
                                    hasNewMessagesFromOthers = true;
                                    break;
                                }
                            }
                            
                            if (hasNewMessagesFromOthers) {
                                Toast.makeText(this, "💬 New messages received", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                });
    }


    private void confirmDeleteAllMessages() {
        new AlertDialog.Builder(this)
                .setTitle("Delete All Messages")
                .setMessage("Are you sure you want to delete all messages?")
                .setPositiveButton("Yes", (dialog, which) -> deleteAllMessages())
                .setNegativeButton("No", null)
                .show();
    }

    private void deleteAllMessages() {
        firestore.collection("messages").get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        doc.getReference().delete();
                    }
                    messages.clear();
                    messageIds.clear();
                    messageAdapter.notifyDataSetChanged();
                });
    }

    private void confirmDeleteMessage(int position) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Message")
                .setMessage("Are you sure you want to delete this message?")
                .setPositiveButton("Yes", (dialog, which) -> deleteMessage(position))
                .setNegativeButton("No", null)
                .show();
    }

    private void deleteMessage(int position) {
        String messageId = messageIds.get(position);
        firestore.collection("messages").document(messageId).delete()
                .addOnSuccessListener(aVoid -> {
                    messages.remove(position);
                    messageIds.remove(position);
                    messageAdapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e -> Log.e("Firestore", "Error deleting message", e));
    }

    private void copyMessage(String message) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText("Copied Message", message);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "Message copied", Toast.LENGTH_SHORT).show();
    }
}