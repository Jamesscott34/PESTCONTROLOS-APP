package com.grpc.grpc;

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
import com.google.firebase.functions.FirebaseFunctions;

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

        firebaseFunctions = FirebaseFunctions.getInstance();
        isAdmin = false;

        // Get user name from intent
        userName = getIntent().getStringExtra("USER_NAME");
        if (userName == null || userName.isEmpty()) {
            userName = "User";
        }

        mainHandler = new Handler(Looper.getMainLooper());

        initializeViews();
        setupClickListeners();

        // Admin = user 001 in users collection. Fetch and compare email to current user.
        if (chatAdminSettingsButton != null) {
            chatAdminSettingsButton.setVisibility(View.GONE);
            chatAdminSettingsButton.setOnClickListener(v -> showUpdateApiKeyDialog());
            String currentEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail();
            StaffDirectory.fetchById(this, StaffDirectory.ADMIN_USER_ID, profile -> {
                mainHandler.post(() -> {
                    isAdmin = profile != null && profile.email != null
                            && profile.email.trim().equalsIgnoreCase(currentEmail != null ? currentEmail.trim() : "");
                    if (chatAdminSettingsButton != null) {
                        chatAdminSettingsButton.setVisibility(isAdmin ? View.VISIBLE : View.GONE);
                    }
                });
            });
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
            // Create AI message with long press to copy
            TextView messageView = new TextView(this);
            messageView.setText(text);
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

        // Show typing indicator immediately
        addMessage("🤖 AI is thinking...", false);

        Map<String, Object> data = new HashMap<>();
        data.put("message", userMessage);
        data.put("mode", isPestControlMode ? "pestControl" : "general");
        data.put("userName", userName != null ? userName : "User");

        firebaseFunctions.getHttpsCallable("aiChat").call(data)
                .addOnSuccessListener(this, result -> {
                    isWaitingForAI = false;
                    removeTypingIndicator();
                    String reply = null;
                    if (result != null && result.getData() != null) {
                        Object dataResult = result.getData();
                        if (dataResult instanceof Map) {
                            Object r = ((Map<?, ?>) dataResult).get("reply");
                            if (r != null) reply = r.toString();
                        }
                    }
                    if (reply != null && !reply.isEmpty()) {
                        lastAIResponse = reply;
                        addMessage(reply, false);
                    } else {
                        addMessage("Sorry, no reply was returned. Please try again.", false);
                    }
                })
                .addOnFailureListener(this, e -> {
                    isWaitingForAI = false;
                    removeTypingIndicator();
                    addMessage("Sorry, I encountered an error. Please try again.", false);
                });
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
     * Admin-only: dialog to update OpenRouter API key via Firebase callable.
     * The app never stores or displays the current key.
     */
    private void showUpdateApiKeyDialog() {
        EditText keyInput = new EditText(this);
        keyInput.setHint("Enter new OpenRouter API key");
        keyInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyInput.setMinEms(20);

        new AlertDialog.Builder(this)
                .setTitle("Update API Key")
                .setMessage("Enter the new OpenRouter API key. The current key is not shown for security.")
                .setView(keyInput)
                .setPositiveButton("Update", (dialog, which) -> {
                    String newKey = keyInput.getText() != null ? keyInput.getText().toString().trim() : "";
                    if (newKey.isEmpty()) {
                        Toast.makeText(this, "Key cannot be empty", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    updateOpenRouterKeyOnBackend(newKey);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void updateOpenRouterKeyOnBackend(String newKey) {
        Map<String, Object> data = new HashMap<>();
        data.put("newKey", newKey);

        firebaseFunctions.getHttpsCallable("updateOpenRouterKey").call(data)
                .addOnSuccessListener(this, result -> {
                    Toast.makeText(this, "API key updated successfully.", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(this, e -> {
                    Toast.makeText(this, "Update failed. Try again or check backend.", Toast.LENGTH_LONG).show();
                });
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