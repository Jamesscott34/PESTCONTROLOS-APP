package com.grpc.grpc.messaging.ui;

import com.grpc.grpc.R;
import com.grpc.grpc.core.*;
import com.grpc.grpc.login.LoginActivity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.functions.FirebaseFunctions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import android.Manifest;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ChatActivity extends AppCompatActivity {

    private ScrollView messagesScrollView;
    private LinearLayout messagesContainer;
    private EditText messageInput;
    private Button sendButton, deleteButton, pestControlModeButton, generalModeButton, readBackButton;
    private ImageButton micButton;
    private ImageButton chatAdminSettingsButton;
    private SpeechRecognizer speechRecognizer;
    private TextToSpeech textToSpeech;
    private boolean isListening = false;
    private boolean isWaitingForAI = false;
    private static final int PERMISSION_REQUEST_CODE = 123;
    private boolean isPestControlMode = false; // false = General AI, true = Pest Control Expert
    private String lastAIResponse = ""; // Store the last AI response for read-back functionality

    private FirebaseFunctions firebaseFunctions;
    private boolean isAdmin;

    private String userName;
    private Handler mainHandler;

    /** Hugging Face Router (OpenAI-compatible). KEY = HF token in Firestore. */
    private static final String HF_ROUTER_CHAT_URL = "https://router.huggingface.co/v1/chat/completions";
    private static final String HF_ROUTER_MODEL = "openai/gpt-oss-20b:groq";
    /** Groq API when key-grog is set in Firestore. */
    private static final String GROQ_CHAT_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String GROQ_MODEL = "llama-3.1-8b-instant";
    /** Max tokens in the model reply; increase if responses get cut off. */
    private static final int MAX_TOKENS = 2048;
    private static final String PEST_SYSTEM_PROMPT = "You are a senior Irish pest control professional speaking to a licensed, professional colleague. You have decades of experience in Irish pest control and speak as one professional to another. You have expert knowledge of: Irish pest control legislation and regulations; CRRU guidelines; Irish rodenticide regulations and compliance; Irish wildlife protection laws; Irish building and health and safety regulations for pest control; Irish licensing, waste disposal, insurance and tax for pest control; rodents, birds, insects, wildlife; Irish-specific pest species and behaviors; proofing methods for rats, mice and other pests; Irish Wildlife Act 1976 and bird protection regulations. Use professional terminology, reference Irish regulations, give practical field-tested advice.\n\nFormat for mobile: plain text only. Use simple bullet points or short numbered lists. Do NOT use markdown headings (## or ###), tables, or horizontal rules. Use at most one blank line between paragraphs. Keep responses concise and complete; always finish your answer.";
    private static final String GENERAL_SYSTEM_PROMPT = "You are a knowledgeable, direct AI assistant that can answer any question: general knowledge, weather, science, history, business, health, travel, Irish pest control law, and more. Be direct and honest.\n\nFormat for mobile: plain text only. Use simple bullet points or short paragraphs. Do NOT use markdown headings, tables, or excessive line breaks. Use at most one blank line between paragraphs. Always complete your answer.";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Require Firebase Auth: callables need an authenticated user
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_chat);

        // Handle keyboard properly
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        // Use us-central1 to match deployed functions (updateOpenRouterKey writes AI key to Firestore)
        firebaseFunctions = FirebaseFunctions.getInstance("us-central1");
        isAdmin = false;

        // Get user name from intent
        userName = getIntent().getStringExtra("USER_NAME");
        if (userName == null || userName.isEmpty()) {
            userName = "User";
        }

        mainHandler = new Handler(Looper.getMainLooper());

        initializeViews();
        setupClickListeners();

        // Admin-only settings are driven by the authenticated user's role (no hardcoded StaffIDs).
        if (chatAdminSettingsButton != null) {
            chatAdminSettingsButton.setVisibility(View.GONE);
            chatAdminSettingsButton.setOnClickListener(v -> showUpdateApiKeyDialog());
            SessionManager.ensureLoaded(this, session -> mainHandler.post(() -> {
                isAdmin = session != null && session.isSuperAdmin;
                if (chatAdminSettingsButton != null) {
                    chatAdminSettingsButton.setVisibility(isAdmin ? View.VISIBLE : View.GONE);
                }
            }));
        }

        // Add initial mode message based on current mode
        if (isPestControlMode) {
            addMessage("🐛 Pest Control Expert Mode Active - Your senior Irish pest control colleague ready for professional advice.", false);
        } else {
            addMessage("🌍 General AI Mode Active - Ask me anything! Weather, science, history, travel, or any topic.", false);
        }
    }
    
    private void initializeViews() {
        messagesScrollView = findViewById(R.id.messagesScrollView);
        messagesContainer = findViewById(R.id.messagesContainer);
        messageInput = findViewById(R.id.messageInput);
        sendButton = findViewById(R.id.sendButton);
        micButton = findViewById(R.id.micButton);
        deleteButton = findViewById(R.id.deleteButton);
        pestControlModeButton = findViewById(R.id.pestControlModeButton);
        generalModeButton = findViewById(R.id.generalModeButton);
        readBackButton = findViewById(R.id.readBackButton);
        chatAdminSettingsButton = findViewById(R.id.chatAdminSettingsButton);
    }
    
    private void setupClickListeners() {
        sendButton.setOnClickListener(v -> sendMessage());
        
        micButton.setOnClickListener(v -> toggleVoiceInput());
        
        deleteButton.setOnClickListener(v -> {
            messagesContainer.removeAllViews();
            addMessage("🗑️ All messages deleted!", false);
        });
        
        pestControlModeButton.setOnClickListener(v -> {
            isPestControlMode = true;
            updateModeButtons();
            messagesContainer.removeAllViews();
            addMessage("🐛 Switched to Pest Control Expert mode. I'm now your senior Irish pest control colleague - professional advice, field experience, and legal compliance.", false);
        });
        
        generalModeButton.setOnClickListener(v -> {
            isPestControlMode = false;
            updateModeButtons();
            messagesContainer.removeAllViews();
            addMessage("🌍 Switched to General AI mode. I can answer ANY question - weather, science, history, business, or anything else!", false);
        });
        
        // Send message on Enter key
        messageInput.setOnEditorActionListener((v, actionId, event) -> {
            sendMessage();
            return true;
        });
        
        // Read back last AI response
        readBackButton.setOnClickListener(v -> readLastAIResponse());
    }
    
    private void updateModeButtons() {
        if (isPestControlMode) {
            pestControlModeButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    MaterialColors.getColor(pestControlModeButton, com.google.android.material.R.attr.colorSecondary)));
            generalModeButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    MaterialColors.getColor(generalModeButton, com.google.android.material.R.attr.colorSurface)));
        } else {
            pestControlModeButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    MaterialColors.getColor(pestControlModeButton, com.google.android.material.R.attr.colorSurface)));
            generalModeButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    MaterialColors.getColor(generalModeButton, com.google.android.material.R.attr.colorSecondary)));
        }
    }
    

    
    private void sendMessage() {
        String messageText = messageInput.getText().toString().trim();
        if (TextUtils.isEmpty(messageText) || isWaitingForAI) {
            return;
        }
        
        // Add user message
        addMessage(messageText, true);
        messageInput.setText("");
        
        // Send to AI
        sendToAI(messageText);
    }
    
    /** Cleans AI reply for display: fewer line breaks, no raw markdown. */
    private static String cleanReplyForDisplay(String text) {
        if (text == null) return "";
        String s = text.trim();
        if (s.isEmpty()) return s;
        s = s.replaceAll("(\n\\s*){3,}", "\n\n");
        s = s.replaceAll("(?m)^#+\\s*", "");
        s = s.replaceAll("(?m)^[-|\\s]+\\s*$", "");
        s = s.replaceAll("\\|\\s*", "  ");
        return s.trim();
    }

    private void addMessage(String text, boolean isUser) {
        // Remove typing indicator if it exists
        if (!isUser && messagesContainer.getChildCount() > 0) {
            View lastChild = messagesContainer.getChildAt(messagesContainer.getChildCount() - 1);
            if (lastChild instanceof TextView) {
                TextView lastText = (TextView) lastChild;
                if (lastText.getText().toString().equals("🤖 AI is thinking...")) {
                    messagesContainer.removeView(lastChild);
                }
            }
        }
        
        if (isUser) {
            // Create simple text view for user messages
            TextView messageView = new TextView(this);
            messageView.setText(text);
            messageView.setTextSize(16);
            messageView.setPadding(16, 12, 16, 12);
            messageView.setBackgroundResource(android.R.drawable.btn_default);
            messageView.setTextColor(MaterialColors.getColor(messageView, com.google.android.material.R.attr.colorOnSurface));
            messageView.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            ((LinearLayout.LayoutParams) messageView.getLayoutParams()).gravity = android.view.Gravity.END;
            messagesContainer.addView(messageView);
        } else {
            // Create AI message with long press to copy (show cleaned text, copy original)
            String displayText = cleanReplyForDisplay(text);
            TextView messageView = new TextView(this);
            messageView.setText(displayText);
            messageView.setTextSize(16);
            messageView.setPadding(16, 12, 16, 12);
            messageView.setBackgroundResource(R.drawable.message_bubble);
            messageView.setTextColor(MaterialColors.getColor(messageView, com.google.android.material.R.attr.colorOnSurface));
            messageView.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            ((LinearLayout.LayoutParams) messageView.getLayoutParams()).gravity = android.view.Gravity.START;
            
            // Make AI messages long-pressable to copy
            messageView.setOnLongClickListener(v -> {
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("AI Response", text);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, "Copied to clipboard!", Toast.LENGTH_SHORT).show();
                return true;
            });
            
            messagesContainer.addView(messageView);
        }
        
        scrollToBottom();
    }
    
    private void sendToAI(String userMessage) {
        isWaitingForAI = true;
        addMessage("🤖 AI is thinking...", false);

        // Keys in Firestore AI-Chat/AI-API: KEY (Hugging Face), key-grog (Groq). Prefer Groq if key-grog set, else HF. Super admin can update via settings.
        FirebaseFirestore.getInstance().document("AI-Chat/AI-API").get()
                .addOnSuccessListener(this, docSnap -> {
                    if (docSnap == null || !docSnap.exists()) {
                        isWaitingForAI = false;
                        removeTypingIndicator();
                        addMessage("Admin must set an API key first: settings icon → Update Hugging Face Key or Update Groq Key.", false);
                        return;
                    }
                    Object grogObj = docSnap.get("key-grog");
                    Object hfObj = docSnap.get("KEY");
                    String keyGrog = grogObj != null ? grogObj.toString().trim() : "";
                    String keyHf = hfObj != null ? hfObj.toString().trim() : "";
                    if (!keyGrog.isEmpty()) {
                        callGroq(keyGrog, userMessage);
                    } else if (!keyHf.isEmpty()) {
                        callHFRouter(keyHf, userMessage);
                    } else {
                        isWaitingForAI = false;
                        removeTypingIndicator();
                        addMessage("Admin must set an API key first: settings icon → Update Hugging Face Key or Update Groq Key.", false);
                    }
                })
                .addOnFailureListener(this, e -> {
                    isWaitingForAI = false;
                    removeTypingIndicator();
                    android.util.Log.w("ChatActivity", "Failed to get API key from Firestore", e);
                    addMessage("Could not load API key. Check your connection and try again.", false);
                });
    }

    /** Call Groq API (OpenAI-compatible). Used when key-grog is set in Firestore. */
    private void callGroq(String apiKey, String userMessage) {
        String systemContent = (isPestControlMode ? PEST_SYSTEM_PROMPT : GENERAL_SYSTEM_PROMPT)
                + " The user's name is " + (userName != null ? userName : "User") + ".";
        try {
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "system").put("content", systemContent));
            messages.put(new JSONObject().put("role", "user").put("content", userMessage));
            JSONObject body = new JSONObject()
                    .put("model", GROQ_MODEL)
                    .put("messages", messages)
                    .put("max_tokens", MAX_TOKENS);
            RequestBody requestBody = RequestBody.create(body.toString(), MediaType.parse("application/json"));
            Request request = new Request.Builder()
                    .url(GROQ_CHAT_URL)
                    .header("Authorization", "Bearer " + apiKey)
                    .post(requestBody)
                    .build();
            new OkHttpClient().newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    mainHandler.post(() -> {
                        isWaitingForAI = false;
                        removeTypingIndicator();
                        android.util.Log.w("ChatActivity", "Groq request failed", e);
                        addMessage("Network error. Please try again.", false);
                    });
                }
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String reply = "";
                    String errorDetail = null;
                    int code = response.code();
                    String json = response.body() != null ? response.body().string() : "";
                    if (response.isSuccessful()) {
                        try {
                            JSONObject obj = new JSONObject(json);
                            JSONArray choices = obj.optJSONArray("choices");
                            if (choices != null && choices.length() > 0) {
                                JSONObject msg = choices.getJSONObject(0).optJSONObject("message");
                                if (msg != null && msg.has("content")) {
                                    Object content = msg.get("content");
                                    reply = content != null ? content.toString() : "";
                                }
                            }
                        } catch (Exception e) {
                            android.util.Log.w("ChatActivity", "Parse Groq response", e);
                            errorDetail = "Invalid response from API.";
                        }
                    } else {
                        try {
                            JSONObject err = new JSONObject(json);
                            JSONObject error = err.optJSONObject("error");
                            if (error != null && error.has("message")) errorDetail = error.optString("message", "");
                        } catch (Exception ignored) { }
                        if (errorDetail == null) errorDetail = "HTTP " + code;
                    }
                    final String finalReply = reply != null ? reply.trim() : "";
                    final String finalError = errorDetail;
                    mainHandler.post(() -> {
                        isWaitingForAI = false;
                        removeTypingIndicator();
                        if (!finalReply.isEmpty()) {
                            lastAIResponse = finalReply;
                            addMessage(finalReply, false);
                        } else {
                            String userMsg = finalError != null ? finalError : "Sorry, no reply was returned. Please try again.";
                            if (code == 401) userMsg = "Invalid Groq key. Get a free key at console.groq.com and set it via Settings → Update Groq Key.";
                            else if (code == 429) userMsg = "Rate limited. Wait a moment and try again.";
                            addMessage(userMsg, false);
                        }
                    });
                }
            });
        } catch (Exception e) {
            isWaitingForAI = false;
            removeTypingIndicator();
            android.util.Log.w("ChatActivity", "Build Groq request", e);
            addMessage("Error preparing request. Please try again.", false);
        }
    }

    /** Call Hugging Face Router (OpenAI-compatible). base_url=v1, model=openai/gpt-oss-20b:groq. Key = HF token from Firestore. */
    private void callHFRouter(String apiKey, String userMessage) {
        String systemContent = (isPestControlMode ? PEST_SYSTEM_PROMPT : GENERAL_SYSTEM_PROMPT)
                + " The user's name is " + (userName != null ? userName : "User") + ".";
        try {
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "system").put("content", systemContent));
            messages.put(new JSONObject().put("role", "user").put("content", userMessage));
            JSONObject body = new JSONObject()
                    .put("model", HF_ROUTER_MODEL)
                    .put("messages", messages)
                    .put("max_tokens", MAX_TOKENS);
            RequestBody requestBody = RequestBody.create(body.toString(), MediaType.parse("application/json"));
            Request request = new Request.Builder()
                    .url(HF_ROUTER_CHAT_URL)
                    .header("Authorization", "Bearer " + apiKey)
                    .post(requestBody)
                    .build();
            new OkHttpClient().newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    mainHandler.post(() -> {
                        isWaitingForAI = false;
                        removeTypingIndicator();
                        android.util.Log.w("ChatActivity", "HF Router request failed", e);
                        addMessage("Network error. Please try again.", false);
                    });
                }
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String reply = "";
                    String errorDetail = null;
                    int code = response.code();
                    String json = response.body() != null ? response.body().string() : "";

                    if (response.isSuccessful()) {
                        try {
                            JSONObject obj = new JSONObject(json);
                            JSONArray choices = obj.optJSONArray("choices");
                            if (choices != null && choices.length() > 0) {
                                JSONObject msg = choices.getJSONObject(0).optJSONObject("message");
                                if (msg != null && msg.has("content")) {
                                    Object content = msg.get("content");
                                    reply = content != null ? content.toString() : "";
                                }
                            }
                            if (reply.isEmpty()) {
                                android.util.Log.w("ChatActivity", "HF Router 200 but no content. Response: " + (json.length() > 500 ? json.substring(0, 500) + "..." : json));
                            }
                        } catch (Exception e) {
                            android.util.Log.w("ChatActivity", "Parse HF Router response", e);
                            errorDetail = "Invalid response from API.";
                        }
                    } else {
                        try {
                            JSONObject err = new JSONObject(json);
                            JSONObject error = err.optJSONObject("error");
                            if (error != null && error.has("message")) errorDetail = error.optString("message", "");
                            if (errorDetail == null && err.has("message")) errorDetail = err.optString("message", "");
                        } catch (Exception ignored) { }
                        if (errorDetail == null) errorDetail = "HTTP " + code;
                        android.util.Log.w("ChatActivity", "HF Router error: " + code + " " + errorDetail + (json.isEmpty() ? "" : " body=" + (json.length() > 300 ? json.substring(0, 300) + "..." : json)));
                    }

                    final String finalReply = reply != null ? reply.trim() : "";
                    final String finalError = errorDetail;
                    mainHandler.post(() -> {
                        isWaitingForAI = false;
                        removeTypingIndicator();
                        if (!finalReply.isEmpty()) {
                            lastAIResponse = finalReply;
                            addMessage(finalReply, false);
                        } else {
                            String userMsg = finalError != null ? finalError : "Sorry, no reply was returned. Please try again.";
                            if (code == 401) userMsg = "Invalid API key. Use a Hugging Face token (huggingface.co/settings/tokens) and set it via Settings → Update API Key.";
                            else if (code == 429) userMsg = "Rate limited. Wait a moment and try again.";
                            addMessage(userMsg, false);
                        }
                    });
                }
            });
        } catch (Exception e) {
            isWaitingForAI = false;
            removeTypingIndicator();
            android.util.Log.w("ChatActivity", "Build HF Router request", e);
            addMessage("Error preparing request. Please try again.", false);
        }
    }

    private void removeTypingIndicator() {
        if (messagesContainer.getChildCount() > 0) {
            View lastChild = messagesContainer.getChildAt(messagesContainer.getChildCount() - 1);
            if (lastChild instanceof TextView) {
                TextView lastText = (TextView) lastChild;
                if ("🤖 AI is thinking...".equals(lastText.getText().toString())) {
                    messagesContainer.removeView(lastChild);
                }
            }
        }
    }

    /**
     * Admin-only: choose which key to update (Hugging Face or Grok), then show input dialog.
     */
    private void showUpdateApiKeyDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Update API Key")
                .setItems(new CharSequence[]{"Update Hugging Face Key", "Update Groq Key"}, (dialog, which) -> {
                    if (which == 0) showUpdateHfKeyDialog();
                    else showUpdateGrokKeyDialog();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showUpdateHfKeyDialog() {
        EditText keyInput = new EditText(this);
        keyInput.setHint("Hugging Face token (hf_...)");
        keyInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyInput.setMinEms(20);
        new AlertDialog.Builder(this)
                .setTitle("Update Hugging Face Key")
                .setMessage("Enter your Hugging Face token (huggingface.co/settings/tokens). Current key is not shown.")
                .setView(keyInput)
                .setPositiveButton("Update", (dialog, which) -> {
                    String newKey = keyInput.getText() != null ? keyInput.getText().toString().trim() : "";
                    if (newKey.isEmpty()) {
                        Toast.makeText(this, "Key cannot be empty", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    updateHfKeyOnBackend(newKey);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showUpdateGrokKeyDialog() {
        EditText keyInput = new EditText(this);
        keyInput.setHint("Groq API key (console.groq.com)");
        keyInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyInput.setMinEms(20);
        new AlertDialog.Builder(this)
                .setTitle("Update Groq Key")
                .setMessage("Enter your Groq API key (free at console.groq.com). Current key is not shown.")
                .setView(keyInput)
                .setPositiveButton("Update", (dialog, which) -> {
                    String newKey = keyInput.getText() != null ? keyInput.getText().toString().trim() : "";
                    if (newKey.isEmpty()) {
                        Toast.makeText(this, "Key cannot be empty", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    updateGrokKeyOnBackend(newKey);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void updateHfKeyOnBackend(String newKey) {
        Map<String, Object> data = new HashMap<>();
        data.put("newKey", newKey);
        firebaseFunctions.getHttpsCallable("updateOpenRouterKey").call(data)
                .addOnSuccessListener(this, result -> Toast.makeText(this, "Hugging Face key updated.", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(this, e -> Toast.makeText(this, "Update failed. Try again or check backend.", Toast.LENGTH_LONG).show());
    }

    private void updateGrokKeyOnBackend(String newKey) {
        Map<String, Object> data = new HashMap<>();
        data.put("newKey", newKey);
        firebaseFunctions.getHttpsCallable("updateGrokKey").call(data)
                .addOnSuccessListener(this, result -> Toast.makeText(this, "Groq key updated.", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(this, e -> Toast.makeText(this, "Update failed. Try again or check backend.", Toast.LENGTH_LONG).show());
    }
    
    private void toggleVoiceInput() {
        if (isListening) {
            stopVoiceInput();
        } else {
            startVoiceInput();
        }
    }
    
    private void startVoiceInput() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, 
                    new String[]{Manifest.permission.RECORD_AUDIO}, 
                    PERMISSION_REQUEST_CODE);
            return;
        }
        
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                    isListening = true;
                    micButton.setAlpha(0.5f);
                    Toast.makeText(ChatActivity.this, "Listening...", Toast.LENGTH_SHORT).show();
                }
                
                @Override
                public void onBeginningOfSpeech() {}
                
                @Override
                public void onRmsChanged(float rmsdB) {}
                
                @Override
                public void onBufferReceived(byte[] buffer) {}
                
                @Override
                public void onEndOfSpeech() {
                    isListening = false;
                    micButton.setAlpha(1.0f);
                }
                
                @Override
                public void onError(int error) {
                    isListening = false;
                    micButton.setAlpha(1.0f);
                    if (error == SpeechRecognizer.ERROR_NO_MATCH) {
                        Toast.makeText(ChatActivity.this, "No speech detected", Toast.LENGTH_SHORT).show();
                    }
                }
                
                @Override
                public void onResults(Bundle results) {
                    isListening = false;
                    micButton.setAlpha(1.0f);
                    
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        String spokenText = matches.get(0);
                        messageInput.setText(spokenText);
                        Toast.makeText(ChatActivity.this, "Voice input: " + spokenText, Toast.LENGTH_SHORT).show();
                    }
                }
                
                @Override
                public void onPartialResults(Bundle partialResults) {}
                
                @Override
                public void onEvent(int eventType, Bundle params) {}
            });
        }
        
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IE");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your message...");
        
        speechRecognizer.startListening(intent);
    }
    
    private void stopVoiceInput() {
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
        }
        isListening = false;
        micButton.setAlpha(1.0f);
    }
    
    private void scrollToBottom() {
        messagesScrollView.post(() -> {
            messagesScrollView.fullScroll(ScrollView.FOCUS_DOWN);
        });
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Ensure we scroll to bottom when activity resumes (keyboard appears/disappears)
        scrollToBottom();
    }
    
    /**
     * Read back the last AI response using Text-to-Speech
     */
    private void readLastAIResponse() {
        if (lastAIResponse.isEmpty()) {
            Toast.makeText(this, "No AI response to read back", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Initialize Text-to-Speech if not already done
        if (textToSpeech == null) {
            textToSpeech = new TextToSpeech(this, status -> {
                if (status == TextToSpeech.SUCCESS) {
                    // Call the actual reading logic after initialization
                    readAIResponseContent();
                } else {
                    Toast.makeText(this, "Text-to-Speech not available", Toast.LENGTH_SHORT).show();
                }
            });
            return;
        }
        
        // If TextToSpeech is already initialized, read the content directly
        readAIResponseContent();
    }
    
    /**
     * Actually read the AI response content using Text-to-Speech
     */
    private void readAIResponseContent() {
        if (textToSpeech != null && !lastAIResponse.isEmpty()) {
            textToSpeech.speak(lastAIResponse, TextToSpeech.QUEUE_FLUSH, null, "AI_RESPONSE_READBACK");
            Toast.makeText(this, "📖 Reading AI response...", Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        if (textToSpeech != null) {
            textToSpeech.shutdown();
        }
    }
} 