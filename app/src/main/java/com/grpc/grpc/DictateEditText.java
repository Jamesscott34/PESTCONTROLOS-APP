package com.grpc.grpc;

import android.content.Context;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Locale;

public class DictateEditText extends FrameLayout {
    private EditText editText;
    private ImageButton dictateButton;
    private SpeechRecognizer speechRecognizer;
    private boolean isListening = false;
    private boolean isDictating = false;

    public DictateEditText(@NonNull Context context) {
        super(context);
        init(context);
    }

    public DictateEditText(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public DictateEditText(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.edittext_with_dictate, this, true);
        
        editText = findViewById(R.id.editText);
        dictateButton = findViewById(R.id.dictateButton);
        
        // Always show the dictate button
        dictateButton.setVisibility(VISIBLE);
        dictateButton.setAlpha(0.7f);
        
        // Show dictate button when EditText is focused
        editText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                dictateButton.setAlpha(1.0f);
            } else {
                dictateButton.setAlpha(0.7f);
                if (isDictating) {
                    stopDictation();
                }
            }
        });
        
        // Also show button when EditText is clicked
        editText.setOnClickListener(v -> {
            dictateButton.setAlpha(1.0f);
        });
        
        // Dictate button click listener
        dictateButton.setOnClickListener(v -> {
            if (!isDictating) {
                startDictation();
            } else {
                stopDictation();
            }
        });
        
        // Initialize speech recognizer
        setupSpeechRecognizer();
    }

    private void setupSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(getContext());
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(android.os.Bundle params) {
                isListening = true;
                updateDictateButton();
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
                updateDictateButton();
            }

            @Override
            public void onError(int error) {
                isListening = false;
                isDictating = false;
                updateDictateButton();
                // Silently handle errors without showing error messages
            }

            @Override
            public void onResults(android.os.Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String spokenText = matches.get(0);
                    // Insert at current cursor position (does not lock typing/editing)
                    int start = Math.max(0, editText.getSelectionStart());
                    int end = Math.max(0, editText.getSelectionEnd());
                    int insertPos = Math.min(start, end);

                    String prefixSpace = "";
                    if (insertPos > 0) {
                        CharSequence text = editText.getText();
                        if (text != null && insertPos <= text.length()) {
                            char before = text.charAt(insertPos - 1);
                            if (!Character.isWhitespace(before)) {
                                prefixSpace = " ";
                            }
                        }
                    }

                    editText.getText().replace(Math.min(start, end), Math.max(start, end), prefixSpace + spokenText);
                    editText.setSelection(Math.min(editText.getText().length(), insertPos + prefixSpace.length() + spokenText.length()));
                }
                
                // Continue listening for more input
                if (isDictating) {
                    startListening();
                }
            }

            @Override
            public void onPartialResults(android.os.Bundle partialResults) {}

            @Override
            public void onEvent(int eventType, android.os.Bundle params) {}
        });
    }

    private void startDictation() {
        isDictating = true;
        updateDictateButton();
        startListening();
    }

    private void stopDictation() {
        isDictating = false;
        isListening = false;
        updateDictateButton();
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
        }
    }

    private void startListening() {
        if (speechRecognizer != null) {
            android.content.Intent intent = new android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...");
            speechRecognizer.startListening(intent);
        }
    }

    private void updateDictateButton() {
        if (isDictating) {
            dictateButton.setBackgroundResource(android.R.drawable.ic_media_pause);
            dictateButton.setAlpha(1.0f);
        } else {
            dictateButton.setBackgroundResource(android.R.drawable.ic_btn_speak_now);
            dictateButton.setAlpha(0.8f);
        }
    }

    public EditText getEditText() {
        return editText;
    }

    public void setHint(String hint) {
        editText.setHint(hint);
    }

    public void setInputType(int inputType) {
        editText.setInputType(inputType);
    }

    public void setMinLines(int minLines) {
        editText.setMinLines(minLines);
    }

    public void setGravity(int gravity) {
        editText.setGravity(gravity);
    }



    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
    }
} 