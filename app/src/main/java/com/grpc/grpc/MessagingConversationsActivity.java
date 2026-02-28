package com.grpc.grpc;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MessagingConversationsActivity
 *
 * WhatsApp-style conversation list. Users can tap to open:
 * - Individual chats with staff
 * - Group chat: All users
 *
 * Author: GRPC
 */
public class MessagingConversationsActivity extends AppCompatActivity {

    private static final String GROUP_ID = "group";

    private static final String PREFS_NAME = "GRPC";
    private static final String PREFIX_LAST_SEEN = "CHAT_LAST_SEEN_";

    private ListView conversationsListView;
    private String userName;
    private List<StaffDirectory.OwnerOption> allUserOptions = new ArrayList<>();
    private List<ConversationItem> conversationItems;
    private ConversationListAdapter adapter;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_messaging_conversations);
        if (DemoFirebaseExpiryHelper.finishIfBlocked(this)) return;

        userName = getIntent().getStringExtra("USER_NAME");
        if (userName == null || userName.isEmpty()) {
            Toast.makeText(this, "Error: User not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        db = FirebaseFirestore.getInstance();
        conversationsListView = findViewById(R.id.conversationsListView);
        Button backButton = findViewById(R.id.backButton);

        conversationItems = new ArrayList<>();
        adapter = new ConversationListAdapter(this, conversationItems);
        conversationsListView.setAdapter(adapter);

        conversationsListView.setOnItemClickListener((parent, view, position, id) -> {
            ConversationItem item = conversationItems.get(position);
            openChat(item.conversationId, item.displayName);
        });

        backButton.setOnClickListener(v -> finish());

        loadUsersAndRefreshList();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh unread state when returning from a chat
        if (conversationItems != null && adapter != null) {
            loadUnreadStatesAndRefreshList();
        }
    }

    /**
     * Build list with hasUnread=false; unread state is filled in by loadUnreadStatesAndRefreshList().
     * Uses ownerKey (ContractKey or StaffID when no ContractKey) for conversation ID; display = ContractKey or user's Name when no ContractKey.
     */
    private List<ConversationItem> buildConversationListWithUnreadPlaceholder() {
        List<ConversationItem> items = new ArrayList<>();
        String me = userName != null ? userName.trim().toLowerCase() : "";
        for (StaffDirectory.OwnerOption o : allUserOptions) {
            if (o == null) continue;
            String key = o.ownerKey != null ? o.ownerKey.trim() : "";
            if (key.isEmpty() || key.equalsIgnoreCase(me)) continue;
            String convId = getConversationId(userName, key);
            String displayName = (o.display != null && !o.display.trim().isEmpty()) ? o.display.trim() : key;
            items.add(new ConversationItem(convId, displayName, "💬", "Chat with " + displayName, false));
        }
        items.add(new ConversationItem(GROUP_ID, "Group", "👥", "Group chat with everyone", false));
        return items;
    }

    private void loadUsersAndRefreshList() {
        // Messages: include all users; use Name when no ContractKey (fetchOwnerOptionsForMessaging).
        StaffDirectory.fetchOwnerOptionsForMessaging(this, options -> runOnUiThread(() -> {
            allUserOptions.clear();
            if (options != null) {
                for (StaffDirectory.OwnerOption o : options) {
                    if (o == null) continue;
                    String key = o.ownerKey != null ? o.ownerKey.trim() : "";
                    if (!key.isEmpty()) allUserOptions.add(o);
                }
            }
            loadUnreadStatesAndRefreshList();
        }));
    }

    /**
     * For each conversation, check if latest message is from the other person and newer than last-seen.
     * Then rebuild the list with correct hasUnread and refresh the adapter.
     */
    private void loadUnreadStatesAndRefreshList() {
        final List<ConversationItem> placeholder = buildConversationListWithUnreadPlaceholder();
        final int count = placeholder.size();
        final Map<String, Boolean> unreadByConvId = new HashMap<>();
        final int[] completed = {0};

        for (ConversationItem item : placeholder) {
            String convId = item.conversationId;
            checkConversationHasUnread(convId, hasUnread -> {
                unreadByConvId.put(convId, hasUnread);
                completed[0]++;
                if (completed[0] >= count) {
                    List<ConversationItem> withUnread = new ArrayList<>();
                    for (ConversationItem p : placeholder) {
                        boolean unread = Boolean.TRUE.equals(unreadByConvId.get(p.conversationId));
                        withUnread.add(new ConversationItem(
                                p.conversationId, p.displayName, p.icon, p.preview, unread));
                    }
                    conversationItems.clear();
                    conversationItems.addAll(withUnread);
                    adapter.notifyDataSetChanged();
                }
            });
        }
    }

    private void checkConversationHasUnread(String conversationId, UnreadCallback callback) {
        String prefKey = PREFIX_LAST_SEEN + conversationId;
        long lastSeen = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getLong(prefKey, 0L);

        db.collection("conversations")
                .document(conversationId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot == null || snapshot.isEmpty()) {
                        if (callback != null) callback.onResult(false);
                        return;
                    }
                    DocumentSnapshot doc = snapshot.getDocuments().get(0);
                    java.util.Date ts = doc.getDate("timestamp");
                    String sender = doc.getString("sender");
                    long latest = ts != null ? ts.getTime() : 0L;
                    boolean fromOther = sender != null && !sender.equalsIgnoreCase(userName);
                    boolean hasUnread = fromOther && latest > lastSeen;
                    if (callback != null) callback.onResult(hasUnread);
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onResult(false);
                });
    }

    private interface UnreadCallback {
        void onResult(boolean hasUnread);
    }

    /**
     * Get consistent conversation ID for 1:1 chat (sorted names).
     */
    public static String getConversationId(String user1, String user2) {
        String[] names = {sanitizeConversationPart(user1), sanitizeConversationPart(user2)};
        Arrays.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names[0] + "_" + names[1];
    }

    private static String sanitizeConversationPart(String v) {
        if (v == null) return "";
        String s = v.trim().toLowerCase();
        // Firestore doc ids can contain spaces, but keeping them normalized avoids accidental duplicates.
        s = s.replaceAll("\\s+", "_");
        s = s.replace("/", "_");
        return s;
    }

    private void openChat(String conversationId, String displayName) {
        Intent intent = new Intent(this, MessagingActivity.class);
        intent.putExtra("USER_NAME", userName);
        intent.putExtra("CONVERSATION_ID", conversationId);
        intent.putExtra("CONVERSATION_NAME", displayName);
        startActivity(intent);
    }

    public static class ConversationItem {
        public final String conversationId;
        public final String displayName;
        public final String icon;
        public final String preview;
        public final boolean hasUnread;

        public ConversationItem(String conversationId, String displayName, String icon, String preview, boolean hasUnread) {
            this.conversationId = conversationId;
            this.displayName = displayName;
            this.icon = icon;
            this.preview = preview;
            this.hasUnread = hasUnread;
        }
    }
}
