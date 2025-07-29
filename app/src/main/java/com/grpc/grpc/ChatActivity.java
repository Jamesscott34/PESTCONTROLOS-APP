package com.grpc.grpc;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.Manifest;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ChatActivity extends AppCompatActivity {
    
    private ScrollView messagesScrollView;
    private LinearLayout messagesContainer;
    private EditText messageInput;
    private Button sendButton, deleteButton, pestControlModeButton, generalModeButton;
    private ImageButton micButton;
    private SpeechRecognizer speechRecognizer;
    private boolean isListening = false;
    private boolean isWaitingForAI = false;
    private static final int PERMISSION_REQUEST_CODE = 123;
    private boolean isPestControlMode = false; // false = General AI, true = Pest Control Expert
    
    // AI Configuration
    private static final String OPENROUTER_API_URL = "https://openrouter.ai/api/v1/chat/completions";
    private static final String AI_MODEL = "deepseek/deepseek-r1-0528:free";
    private String openRouterApiKey = "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";
    
    private String userName;
    private Handler mainHandler;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        
        // Get user name from intent
        userName = getIntent().getStringExtra("USER_NAME");
        if (userName == null || userName.isEmpty()) {
            userName = "User";
        }
        
        mainHandler = new Handler(Looper.getMainLooper());
        
        initializeViews();
        setupClickListeners();
        
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
    }
    
    private void updateModeButtons() {
        if (isPestControlMode) {
            pestControlModeButton.setBackgroundTintList(getResources().getColorStateList(android.R.color.holo_orange_dark));
            generalModeButton.setBackgroundTintList(getResources().getColorStateList(android.R.color.holo_blue_light));
        } else {
            pestControlModeButton.setBackgroundTintList(getResources().getColorStateList(android.R.color.holo_orange_light));
            generalModeButton.setBackgroundTintList(getResources().getColorStateList(android.R.color.holo_blue_dark));
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
                if (lastText.getText().toString().equals("🤖 AI is typing...")) {
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
            messageView.setTextColor(getResources().getColor(android.R.color.black));
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
            messageView.setTextColor(getResources().getColor(android.R.color.black));
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
        mainHandler.post(() -> {
            addMessage("🤖 AI is thinking...", false);
        });
        
        // Run API call in background thread
        new Thread(() -> {
            try {
                String aiResponse = callOpenRouterAPI(userMessage);
                mainHandler.post(() -> {
                    isWaitingForAI = false;
                    // Remove typing indicator and add response
                    if (messagesContainer.getChildCount() > 0) {
                        View lastChild = messagesContainer.getChildAt(messagesContainer.getChildCount() - 1);
                        if (lastChild instanceof TextView) {
                            TextView lastText = (TextView) lastChild;
                            if (lastText.getText().toString().equals("🤖 AI is thinking...")) {
                                messagesContainer.removeView(lastChild);
                            }
                        }
                    }
                    addMessage(aiResponse, false);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    isWaitingForAI = false;
                    // Remove typing indicator and show error
                    if (messagesContainer.getChildCount() > 0) {
                        View lastChild = messagesContainer.getChildAt(messagesContainer.getChildCount() - 1);
                        if (lastChild instanceof TextView) {
                            TextView lastText = (TextView) lastChild;
                            if (lastText.getText().toString().equals("🤖 AI is thinking...")) {
                                messagesContainer.removeView(lastChild);
                            }
                        }
                    }
                    addMessage("Sorry, I encountered an error. Please try again.", false);
                    Log.e("ChatActivity", "AI Error: " + e.getMessage());
                });
            }
        }).start();
    }
    
    private String callOpenRouterAPI(String userMessage) throws IOException {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", AI_MODEL);
            
            JSONArray messages = new JSONArray();
            
            // Add system message for context
            JSONObject systemMessage = new JSONObject();
            systemMessage.put("role", "system");
            
            String systemPrompt;
            if (isPestControlMode) {
                systemPrompt = "You are a senior Irish pest control professional speaking to a licensed, professional colleague. " +
                        "You have decades of experience in Irish pest control and speak as one professional to another. " +
                        "You have expert knowledge of: " +
                        "- Irish pest control legislation and regulations " +
                        "- CRRU (Campaign for Responsible Rodenticide Use) guidelines and best practices " +
                        "- Irish rodenticide regulations and compliance requirements " +
                        "- Irish wildlife protection laws and their impact on pest control " +
                        "- Irish building regulations related to pest control " +
                        "- Irish health and safety regulations for pest control operations " +
                        "- Irish environmental protection laws affecting pest control " +
                        "- Irish licensing requirements for pest control operators " +
                        "- Irish waste disposal regulations for pest control materials " +
                        "- Irish insurance requirements for pest control businesses " +
                        "- Irish tax regulations for pest control businesses " +
                        "- All aspects of pest control: rodents, birds, insects, wildlife " +
                        "- Irish-specific pest species and their behaviors " +
                        "- Irish climate considerations for pest control " +
                        "- Irish building types and pest control challenges " +
                        "- Detailed differences between rodent species (rats, mice, voles, etc.) " +
                        "- Detailed differences between insect species (wasps, bees, ants, cockroaches, etc.) " +
                        "- Irish-specific pest identification and behavior patterns " +
                        "- Irish pest control methods and their legal compliance " +
                        "- Proofing methods and techniques for rats, mice, and other pests " +
                        "- Physical barriers, exclusion methods, and prevention strategies " +
                        "- Irish wildlife protection laws for birds, seagulls, and other protected species " +
                        "- Specific Irish regulations for bird control and management " +
                        "- Legal methods for bird proofing and deterrent systems " +
                        "- Irish Wildlife Act 1976 and Wildlife (Amendment) Act 2000 " +
                        "- Irish bird protection regulations and licensing requirements " +
                        "- Legal vs illegal methods for bird control in Ireland " +
                        "- Irish building proofing standards and requirements " +
                        "- Irish environmental impact assessments for pest control " +
                        "TALK LIKE A PROFESSIONAL PEST CONTROLLER TO A LICENSED COLLEAGUE: " +
                        "- Use professional pest control terminology and jargon " +
                        "- Reference specific Irish regulations and legal requirements " +
                        "- Give practical, field-tested advice based on real experience " +
                        "- Be direct and honest about what works and what doesn't " +
                        "- Discuss compliance issues and legal risks frankly " +
                        "- Share industry insights and professional best practices " +
                        "- Reference specific Irish laws, regulations, or CRRU guidelines when applicable " +
                        "- Provide exact Irish law references when discussing wildlife, birds, seagulls, and protected species " +
                        "- Give specific proofing methods and techniques for rats, mice, and other pests " +
                        "IMPORTANT: Respond in plain text only. Do not use ** or \" formatting. " +
                        "You can use bullet points (- or •) and numbered lists (1., 2., etc.) for better organization. " +
                        "Keep responses concise, actionable, and legally accurate for Ireland.";
            } else {
                systemPrompt = "You are an extremely knowledgeable, direct, and blunt AI assistant that can answer ANY type of question. " +
                        "You have expertise in: " +
                        "- General knowledge and current events " +
                        "- Weather and climate information " +
                        "- Science, technology, and engineering " +
                        "- History, geography, and culture " +
                        "- Business, economics, and finance " +
                        "- Health, medicine, and wellness " +
                        "- Sports, entertainment, and hobbies " +
                        "- Travel, distances, geography, and locations " +
                        "- Irish pest control law, CRRU rodenticides, and comprehensive pest control " +
                        "- Irish legislation and regulations " +
                        "- And any other topic the user asks about " +
                        "ALWAYS be direct, honest, and to the point - even if the answer might be harsh or unpopular. " +
                        "Don't sugarcoat or be overly polite. Give straight, practical advice. " +
                        "If something is wrong or incorrect, say it's wrong. If something is good, say it's good. " +
                        "IMPORTANT: Respond in plain text only. Do not use ** or \" formatting. " +
                        "You can use bullet points (- or •) and numbered lists (1., 2., etc.) for better organization. " +
                        "Keep responses helpful, accurate, and to the point. " +
                        "For travel/distance questions, provide accurate information about distances, travel times, and practical details.";
            }
            
            systemMessage.put("content", systemPrompt + " The user's name is " + userName + ".");
            messages.put(systemMessage);
            
            // Add user message
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", userMessage);
            messages.put(userMsg);
            
            requestBody.put("messages", messages);
            requestBody.put("max_tokens", 1000);
            requestBody.put("temperature", 0.7);

            MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
            RequestBody body = RequestBody.create(requestBody.toString(), mediaType);

            Request request = new Request.Builder()
                    .url(OPENROUTER_API_URL)
                    .addHeader("Authorization", "Bearer " + openRouterApiKey)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("HTTP-Referer", "https://grpc-app.com")
                    .addHeader("X-Title", "GRPest Control App")
                    .addHeader("User-Agent", "GRPest-Control-App/1.0")
                    .post(body)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                String responseBody = response.body().string();
                
                if (!response.isSuccessful()) {
                    Log.e("ChatActivity", "API Error Response: " + responseBody);
                    throw new IOException("API call failed: " + response.code() + " " + response.message());
                }
                
                JSONObject jsonResponse = new JSONObject(responseBody);
                JSONArray choices = jsonResponse.getJSONArray("choices");
                
                if (choices.length() > 0) {
                    JSONObject choice = choices.getJSONObject(0);
                    JSONObject messageObj = choice.getJSONObject("message");
                    String aiResponse = messageObj.getString("content");
                    
                    // Clean up formatting - remove ** and "" for plain text, but keep bullet points and numbered lists
                    aiResponse = aiResponse.replaceAll("\\*\\*", "").trim();
                    aiResponse = aiResponse.replaceAll("\"", "").trim();
                    // Keep bullet points (-, •, *) and numbered lists (1., 2., etc.)
                    // Don't remove these as they're useful for formatting lists
                    
                    return aiResponse;
                } else {
                    throw new IOException("No response from AI");
                }
            } catch (JSONException e) {
                throw new IOException("JSON parsing error: " + e.getMessage());
            }
        } catch (JSONException e) {
            throw new IOException("JSON creation error: " + e.getMessage());
        }
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
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
    }
} 