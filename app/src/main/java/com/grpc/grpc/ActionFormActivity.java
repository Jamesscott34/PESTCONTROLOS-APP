package com.grpc.grpc;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.ToggleButton;
import android.widget.LinearLayout;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;

import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

// AI and Voice Recognition Imports
import okhttp3.*;
import okhttp3.Response;
import org.json.JSONObject;
import org.json.JSONArray;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.os.Handler;
import java.util.concurrent.TimeUnit;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import java.io.FileOutputStream;

public class ActionFormActivity extends AppCompatActivity {

    // UI Components - Action Form specific fields
    private DictateEditText serviceTypeInput, serviceNumberInput, premisesNameInput, premisesAddressInput;
    private DictateEditText prepInput, serviceReportInput, recommendationsInput;
    private DictateEditText followUp1Input;
    private DictateEditText roleInput;
    private EditText dateTimeInput;

    // User context and session management
    private String userName;
    private static final int PERMISSION_REQUEST_CODE = 123;

    // Action Buttons
    private Button saveButton, backButton, selectImageButton, aiPolishButton, readBackButton;
    private Button technicianSignatureButton, customerSignatureButton;
    private ToggleButton followUpToggle;
    private LinearLayout followUpContainer;

    // Data Management
    private List<Uri> selectedImageUris = new ArrayList<>();
    private Uri technicianSignatureUri = null;
    private Uri customerSignatureUri = null;
    private String lastAIResponse = ""; // Store the last AI response for read-back functionality

    // AI and Voice Recognition Components
    private static final String OPENROUTER_API_URL = "https://openrouter.ai/api/v1/chat/completions";
    private static final String AI_MODEL = "mistralai/mistral-nemo";
    private String openRouterApiKey = "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";
    
    private SpeechRecognizer speechRecognizer;
    private TextToSpeech textToSpeech;
    private boolean isListening = false;
    
    // Interactive Voice System
    private String pendingField = "";
    private boolean isInteractiveMode = false;
    private Handler autoProgressHandler = new Handler();
    private int currentFieldIndex = 0;
    private static final String[] AUTO_PROGRESS_FIELDS = {
        "service type", "service number", "premises name", "premises address",
        "prep", "service report", "recommendations", "follow up 1", "role"
    };

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_action_form);
        
        // Performance optimizations
        getWindow().setFlags(
            android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        );
        
        // Handle keyboard properly
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        // User authentication
        userName = getIntent().getStringExtra("USER_NAME");
        if (userName == null || userName.isEmpty()) {
            Toast.makeText(this, "Error: User name not found!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize UI components on main thread (light operations)
        initializeInputFields();
        initializeButtons();
        setCurrentDateTime();
        setupWelcomeMessage();
        
        // Initialize SpeechRecognizer on main thread (required)
        initializeSpeechRecognizer();
        
        // Initialize TextToSpeech on background thread
        new Thread(() -> {
            initializeTextToSpeech();
        }).start();
    }

    private void initializeInputFields() {
        serviceTypeInput = findViewById(R.id.serviceTypeInput);
        serviceNumberInput = findViewById(R.id.serviceNumberInput);
        premisesNameInput = findViewById(R.id.premisesNameInput);
        premisesAddressInput = findViewById(R.id.premisesAddressInput);
        prepInput = findViewById(R.id.prepInput);
        serviceReportInput = findViewById(R.id.serviceReportInput);
        recommendationsInput = findViewById(R.id.recommendationsInput);
        followUp1Input = findViewById(R.id.followUp1Input);
        roleInput = findViewById(R.id.roleInput);
        dateTimeInput = findViewById(R.id.dateTimeInput);

        // Configure hints and settings
        serviceTypeInput.setHint("Enter Service Type");
        serviceNumberInput.setHint("Enter Service Number");
        premisesNameInput.setHint("Enter Premises Name");
        premisesAddressInput.setHint("Enter Premises Address");
        premisesAddressInput.setMinLines(3);
        premisesAddressInput.setGravity(android.view.Gravity.TOP);
        prepInput.setHint("Enter Prep Details");
        prepInput.setMinLines(3);
        prepInput.setGravity(android.view.Gravity.TOP);
        serviceReportInput.setHint("Enter Service Report");
        serviceReportInput.setMinLines(3);
        serviceReportInput.setGravity(android.view.Gravity.TOP);
        recommendationsInput.setHint("Enter Recommendations");
        recommendationsInput.setMinLines(3);
        recommendationsInput.setGravity(android.view.Gravity.TOP);
        followUp1Input.setHint("Enter Follow-Up Details");
        roleInput.setHint("Enter Role");

        // Auto-fill premises name and address if provided from intent
        String companyName = getIntent().getStringExtra("COMPANY_NAME");
        String address = getIntent().getStringExtra("ADDRESS");
        
        if (companyName != null && !companyName.isEmpty() && !companyName.equals("N/A")) {
            premisesNameInput.getEditText().setText(companyName);
        }
        
        if (address != null && !address.isEmpty() && !address.equals("N/A")) {
            premisesAddressInput.getEditText().setText(address);
        }
    }

    private void initializeButtons() {
        saveButton = findViewById(R.id.saveButton);
        backButton = findViewById(R.id.backButton);
        selectImageButton = findViewById(R.id.selectImageButton);
        aiPolishButton = findViewById(R.id.aiPolishButton);
        readBackButton = findViewById(R.id.readBackButton);
        technicianSignatureButton = findViewById(R.id.technicianSignatureButton);
        customerSignatureButton = findViewById(R.id.customerSignatureButton);
        followUpToggle = findViewById(R.id.followUpToggle);
        followUpContainer = findViewById(R.id.followUpContainer);

        saveButton.setOnClickListener(v -> saveActionForm());
        backButton.setOnClickListener(v -> {
            Intent intent = new Intent(ActionFormActivity.this, ReportSelectionActivity.class);
            intent.putExtra("USER_NAME", userName);
            startActivity(intent);
            finish();
        });
        selectImageButton.setOnClickListener(v -> openImageSelector());
        aiPolishButton.setOnClickListener(v -> {
            // Check if there's a stored AI response to read back
            if (!lastAIResponse.isEmpty()) {
                readAIResponseBack();
            } else if (openRouterApiKey.isEmpty()) {
                requestOpenRouterApiKey();
            } else {
                polishWithAI();
            }
        });
        readBackButton.setOnClickListener(v -> readFormBack());
        
        // Signature capture buttons
        technicianSignatureButton.setOnClickListener(v -> captureSignature("technician"));
        customerSignatureButton.setOnClickListener(v -> captureSignature("customer"));
        
        // Follow-up toggle
        followUpToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                followUpContainer.setVisibility(android.view.View.VISIBLE);
            } else {
                followUpContainer.setVisibility(android.view.View.GONE);
            }
        });
    }

    private void setCurrentDateTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault());
        String currentDateTime = sdf.format(new Date());
        if (dateTimeInput != null) {
            dateTimeInput.setText(currentDateTime);
        }
    }

    private void setupWelcomeMessage() {
        android.widget.TextView welcomeTextView = findViewById(R.id.welcomeTextView);
        if (welcomeTextView != null) {
            welcomeTextView.setText("Welcome, " + userName + "!");
        }
    }

    private void initializeSpeechRecognizer() {
        // Initialize SpeechRecognizer on main thread (required)
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            setupSpeechRecognizer();
        }
    }
    
    private void initializeTextToSpeech() {
        // Initialize TextToSpeech on background thread
        textToSpeech = new TextToSpeech(this, status -> {
            runOnUiThread(() -> {
                if (status != TextToSpeech.SUCCESS) {
                    Toast.makeText(this, "Text-to-Speech not available", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void setupSpeechRecognizer() {
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                runOnUiThread(() -> {
                    isListening = true;
                    Toast.makeText(ActionFormActivity.this, "🎙️ Listening... Speak now!", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onBeginningOfSpeech() {}

            @Override
            public void onRmsChanged(float rmsdB) {}

            @Override
            public void onBufferReceived(byte[] buffer) {}

            @Override
            public void onEndOfSpeech() {}

            @Override
            public void onError(int error) {
                runOnUiThread(() -> {
                    isListening = false;
                    if (error == SpeechRecognizer.ERROR_NO_MATCH) {
                        Toast.makeText(ActionFormActivity.this, "No speech detected. Try again.", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String spokenText = matches.get(0).toLowerCase();
                    processVoiceCommand(spokenText);
                }
                runOnUiThread(() -> isListening = false);
            }

            @Override
            public void onPartialResults(Bundle partialResults) {}

            @Override
            public void onEvent(int eventType, Bundle params) {}
        });
    }

    private void processVoiceCommand(String spokenText) {
        runOnUiThread(() -> {
            String lowerText = spokenText.toLowerCase();
            
            if (isInteractiveMode && !pendingField.isEmpty()) {
                fillFieldWithContent(pendingField, spokenText);
                if (currentFieldIndex < AUTO_PROGRESS_FIELDS.length) {
                    currentFieldIndex++;
                    new Handler().postDelayed(() -> progressToNextField(), 1000);
                } else {
                    isInteractiveMode = false;
                    pendingField = "";
                    currentFieldIndex = 0;
                }
                return;
            }
            
            // Process specific field commands
            if (lowerText.contains("service type")) {
                String content = extractContentAfterCommand(spokenText, "service type");
                if (!content.isEmpty()) {
                    serviceTypeInput.getEditText().setText(content);
                    Toast.makeText(this, "✅ Service Type filled: " + content, Toast.LENGTH_SHORT).show();
                } else {
                    askForFieldContent("service type");
                }
            } else if (lowerText.contains("service number")) {
                String content = extractContentAfterCommand(spokenText, "service number");
                if (!content.isEmpty()) {
                    serviceNumberInput.getEditText().setText(content);
                    Toast.makeText(this, "✅ Service Number filled: " + content, Toast.LENGTH_SHORT).show();
                } else {
                    askForFieldContent("service number");
                }
            } else if (lowerText.contains("premises name")) {
                String content = extractContentAfterCommand(spokenText, "premises name");
                if (!content.isEmpty()) {
                    premisesNameInput.getEditText().setText(content);
                    Toast.makeText(this, "✅ Premises Name filled: " + content, Toast.LENGTH_SHORT).show();
                } else {
                    askForFieldContent("premises name");
                }
            } else if (lowerText.contains("premises address")) {
                String content = extractContentAfterCommand(spokenText, "premises address");
                if (!content.isEmpty()) {
                    premisesAddressInput.getEditText().setText(content);
                    Toast.makeText(this, "✅ Premises Address filled: " + content, Toast.LENGTH_SHORT).show();
                } else {
                    askForFieldContent("premises address");
                }
            } else if (lowerText.contains("prep")) {
                String content = extractContentAfterCommand(spokenText, "prep");
                if (!content.isEmpty()) {
                    prepInput.getEditText().setText(content);
                    Toast.makeText(this, "✅ Prep filled: " + content, Toast.LENGTH_SHORT).show();
                } else {
                    askForFieldContent("prep");
                }
            } else if (lowerText.contains("service report")) {
                String content = extractContentAfterCommand(spokenText, "service report");
                if (!content.isEmpty()) {
                    serviceReportInput.getEditText().setText(content);
                    Toast.makeText(this, "✅ Service Report filled: " + content, Toast.LENGTH_SHORT).show();
                } else {
                    askForFieldContent("service report");
                }
            } else if (lowerText.contains("recommendations")) {
                String content = extractContentAfterCommand(spokenText, "recommendations");
                if (!content.isEmpty()) {
                    recommendationsInput.getEditText().setText(content);
                    Toast.makeText(this, "✅ Recommendations filled: " + content, Toast.LENGTH_SHORT).show();
                } else {
                    askForFieldContent("recommendations");
                }
            } else if (lowerText.contains("follow up 1")) {
                String content = extractContentAfterCommand(spokenText, "follow up 1");
                if (!content.isEmpty()) {
                    followUp1Input.getEditText().setText(content);
                    Toast.makeText(this, "✅ Follow-Up 1 filled: " + content, Toast.LENGTH_SHORT).show();
                } else {
                    askForFieldContent("follow up 1");
                }
            } else if (lowerText.contains("role")) {
                String content = extractContentAfterCommand(spokenText, "role");
                if (!content.isEmpty()) {
                    roleInput.getEditText().setText(content);
                    Toast.makeText(this, "✅ Role filled: " + content, Toast.LENGTH_SHORT).show();
                } else {
                    askForFieldContent("role");
                }
            } else if (lowerText.contains("polish form") || lowerText.contains("ai polish")) {
                Toast.makeText(this, "🤖 Starting AI polish...", Toast.LENGTH_SHORT).show();
                polishWithAI();
            } else if (lowerText.contains("read back") || lowerText.contains("read form")) {
                Toast.makeText(this, "📖 Reading form back...", Toast.LENGTH_SHORT).show();
                readFormBack();
            } else if (lowerText.contains("auto fill") || lowerText.contains("auto fill all")) {
                startAutoProgression();
            } else {
                Toast.makeText(this, "❌ Command not recognized. Try: 'Enter [field name] [content]', 'Polish form', 'Read back', or 'Auto fill all'", Toast.LENGTH_LONG).show();
            }
        });
    }

    private String extractContentAfterCommand(String spokenText, String... commands) {
        for (String command : commands) {
            int index = spokenText.indexOf(command);
            if (index != -1) {
                String afterCommand = spokenText.substring(index + command.length()).trim();
                afterCommand = afterCommand.replaceAll("^(is|are|was|were|the|a|an)\\s+", "");
                afterCommand = afterCommand.replaceAll("\\s+", " ").trim();
                return afterCommand;
            }
        }
        return "";
    }

    private void askForFieldContent(String fieldName) {
        pendingField = fieldName;
        isInteractiveMode = true;
        
        String question = "What is the " + fieldName + "?";
        textToSpeech.speak(question, TextToSpeech.QUEUE_FLUSH, null, "FIELD_QUESTION");
        Toast.makeText(this, "🎙️ " + question, Toast.LENGTH_SHORT).show();
        startVoiceRecognition();
    }

    private void fillFieldWithContent(String fieldName, String content) {
        switch (fieldName.toLowerCase()) {
            case "service type":
                serviceTypeInput.getEditText().setText(content);
                break;
            case "service number":
                serviceNumberInput.getEditText().setText(content);
                break;
            case "premises name":
                premisesNameInput.getEditText().setText(content);
                break;
            case "premises address":
                premisesAddressInput.getEditText().setText(content);
                break;
            case "prep":
                prepInput.getEditText().setText(content);
                break;
            case "service report":
                serviceReportInput.getEditText().setText(content);
                break;
            case "recommendations":
                recommendationsInput.getEditText().setText(content);
                break;
            case "follow up 1":
                followUp1Input.getEditText().setText(content);
                break;
            case "role":
                roleInput.getEditText().setText(content);
                break;
        }
        
        String confirmation = fieldName + " filled with " + content;
        textToSpeech.speak(confirmation, TextToSpeech.QUEUE_FLUSH, null, "FIELD_CONFIRMATION");
        Toast.makeText(this, "✅ " + confirmation, Toast.LENGTH_SHORT).show();
    }

    private void startAutoProgression() {
        currentFieldIndex = 0;
        isInteractiveMode = true;
        Toast.makeText(this, "🔄 Starting auto-fill mode. Will progress through all fields.", Toast.LENGTH_SHORT).show();
        progressToNextField();
    }

    private void progressToNextField() {
        if (currentFieldIndex >= AUTO_PROGRESS_FIELDS.length) {
            isInteractiveMode = false;
            currentFieldIndex = 0;
            Toast.makeText(this, "✅ Auto-fill completed!", Toast.LENGTH_SHORT).show();
            if (textToSpeech != null) {
                textToSpeech.speak("Auto-fill completed. All fields have been processed.", TextToSpeech.QUEUE_FLUSH, null, "AUTO_COMPLETE");
            }
            return;
        }

        String currentField = AUTO_PROGRESS_FIELDS[currentFieldIndex];
        pendingField = currentField;
        
        String question = "What is the " + currentField + "?";
        if (textToSpeech != null) {
            textToSpeech.speak(question, TextToSpeech.QUEUE_FLUSH, null, "AUTO_QUESTION");
        }
        
        Toast.makeText(this, "🎙️ " + question, Toast.LENGTH_SHORT).show();
        startVoiceRecognition();
    }

    private void startVoiceRecognition() {
        if (speechRecognizer != null) {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your command...");
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 15000);
            speechRecognizer.startListening(intent);
            
            autoProgressHandler.postDelayed(() -> {
                if (isListening && isInteractiveMode) {
                    progressToNextField();
                }
            }, 15000);
        }
    }

    private void openImageSelector() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, 1);
    }
    
    private void captureSignature(String signatureType) {
        showSignatureDialog(signatureType);
    }
    
    private void showSignatureDialog(String signatureType) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Draw " + (signatureType.equals("technician") ? "Technician" : "Customer") + " Signature");
        
        // Create the signature view
        SignatureDrawingView signatureView = new SignatureDrawingView(this);
        signatureView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                400)); // Fixed height for the drawing area
        
        // Create dialog layout
        LinearLayout dialogLayout = new LinearLayout(this);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(20, 20, 20, 20);
        dialogLayout.addView(signatureView);
        
        // Add instruction text
        TextView instructionText = new TextView(this);
        instructionText.setText("Draw your signature in the box above");
        instructionText.setGravity(android.view.Gravity.CENTER);
        instructionText.setPadding(0, 10, 0, 0);
        dialogLayout.addView(instructionText);
        
        builder.setView(dialogLayout);
        builder.setPositiveButton("Save", (dialog, which) -> {
            if (!signatureView.isEmpty()) {
                // Capture the signature as bitmap and save to file
                Bitmap signatureBitmap = signatureView.getSignatureBitmap();
                Uri signatureUri = saveSignatureToFile(signatureBitmap, signatureType);
                
                if (signatureUri != null) {
                    if (signatureType.equals("technician")) {
                        technicianSignatureUri = signatureUri;
                        Toast.makeText(this, "Technician signature saved!", Toast.LENGTH_SHORT).show();
                    } else {
                        customerSignatureUri = signatureUri;
                        Toast.makeText(this, "Customer signature saved!", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, "Error saving signature", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Please draw a signature first", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Clear", (dialog, which) -> {
            signatureView.clear();
        });
        builder.setNeutralButton("Cancel", (dialog, which) -> {
            dialog.dismiss();
        });
        
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    
    private Uri saveSignatureToFile(Bitmap bitmap, String signatureType) {
        try {
            // Create signatures folder
            File signaturesFolder = new File(getExternalFilesDir(null), "SIGNATURES");
            if (!signaturesFolder.exists()) {
                signaturesFolder.mkdirs();
            }
            
            // Create unique filename
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String filename = signatureType + "_signature_" + timestamp + ".png";
            File signatureFile = new File(signaturesFolder, filename);
            
            // Save bitmap to file
            FileOutputStream fos = new FileOutputStream(signatureFile);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();
            
            // Return the file URI
            return Uri.fromFile(signatureFile);
            
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
    
    // Simple signature drawing view for the dialog
    private static class SignatureDrawingView extends View {
        private Path path;
        private Paint paint;
        private float lastX, lastY;
        private boolean hasSignature = false;
        
        public SignatureDrawingView(Context context) {
            super(context);
            init();
        }
        
        private void init() {
            path = new Path();
            paint = new Paint();
            paint.setColor(Color.BLACK);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(8f);
            paint.setAntiAlias(true);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeCap(Paint.Cap.ROUND);
            
            setBackgroundColor(Color.WHITE);
        }
        
        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX();
            float y = event.getY();
            
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    path.moveTo(x, y);
                    lastX = x;
                    lastY = y;
                    hasSignature = true;
                    break;
                case MotionEvent.ACTION_MOVE:
                    path.quadTo(lastX, lastY, (x + lastX) / 2, (y + lastY) / 2);
                    lastX = x;
                    lastY = y;
                    break;
                case MotionEvent.ACTION_UP:
                    path.lineTo(x, y);
                    break;
            }
            
            invalidate();
            return true;
        }
        
        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawPath(path, paint);
        }
        
        public void clear() {
            path.reset();
            hasSignature = false;
            invalidate();
        }
        
        public boolean isEmpty() {
            return !hasSignature;
        }

        public Bitmap getSignatureBitmap() {
            Bitmap bitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            draw(canvas);
            return bitmap;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 1 && resultCode == RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                int count = data.getClipData().getItemCount();
                for (int i = 0; i < count; i++) {
                    Uri imageUri = data.getClipData().getItemAt(i).getUri();
                    selectedImageUris.add(imageUri);
                }
            } else if (data.getData() != null) {
                selectedImageUris.add(data.getData());
            }
            Toast.makeText(this, selectedImageUris.size() + " images selected!", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveActionForm() {
        String premisesName = premisesNameInput.getEditText().getText().toString();
        if (premisesName.isEmpty()) {
            Toast.makeText(this, "Please enter Premises Name", Toast.LENGTH_SHORT).show();
            return;
        }

        // Ask user to create password
        requestPasswordCreation();
    }
    
    private void requestPasswordCreation() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🔒 Create Password for Action Form")
                .setMessage("Please create a password for your Action Form PDF.\n\n" +
                        "This password will be required to edit the PDF.");
        
        View passwordInputView = createPasswordInputView();
        builder.setView(passwordInputView);
        
        builder.setPositiveButton("Create PDF", (dialog, which) -> {
            // Find the EditText fields by casting the LinearLayout children
            LinearLayout layout = (LinearLayout) passwordInputView;
            EditText passwordInput = (EditText) layout.getChildAt(0);
            EditText confirmPasswordInput = (EditText) layout.getChildAt(1);
            
            String password = passwordInput.getText().toString();
            String confirmPassword = confirmPasswordInput.getText().toString();
            
            if (password.trim().isEmpty()) {
                Toast.makeText(this, "Please enter a password", Toast.LENGTH_SHORT).show();
                return;
            }
            
            if (password.length() < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                return;
            }
            
            if (!password.equals(confirmPassword)) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Create the password-protected PDF
            createPasswordProtectedPDF(password);
        });
        
        builder.setNegativeButton("Cancel", (dialog, which) -> {
            dialog.dismiss();
        });
        
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    
    private android.view.View createPasswordInputView() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 20);
        
        EditText passwordInput = new EditText(this);
        passwordInput.setHint("Enter password (min 6 characters)");
        passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordInput.setPadding(20, 20, 20, 20);
        
        EditText confirmPasswordInput = new EditText(this);
        confirmPasswordInput.setHint("Confirm password");
        confirmPasswordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        confirmPasswordInput.setPadding(20, 20, 20, 20);
        
        layout.addView(passwordInput);
        layout.addView(confirmPasswordInput);
        
        return layout;
    }
    
    private void createPasswordProtectedPDF(String password) {
        // Generate password-protected PDF
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Get follow-up content
            String followUp1 = followUp1Input.getEditText().getText().toString();
            
            ActionFormPdfGenerator.generatePasswordProtectedPDF(
                premisesNameInput.getEditText().getText().toString(),
                dateTimeInput.getText().toString(),
                serviceTypeInput.getEditText().getText().toString(),
                serviceNumberInput.getEditText().getText().toString(),
                premisesAddressInput.getEditText().getText().toString(),
                prepInput.getEditText().getText().toString(),
                serviceReportInput.getEditText().getText().toString(),
                recommendationsInput.getEditText().getText().toString(),
                followUp1,
                "", // followUp2 - not used in new design
                "", // followUp3 - not used in new design
                roleInput.getEditText().getText().toString(),
                password,
                this,
                !selectedImageUris.isEmpty() ? selectedImageUris : null,
                technicianSignatureUri,
                customerSignatureUri,
                followUpToggle.isChecked() // Pass toggle state to PDF generator
            );

            // Show success dialog
            showSuccessDialog(password);
            
            // Delete signature images after successful PDF creation
            deleteSignatureImages();
            
            // Clear fields after successful save
            clearInputFields();
        } else {
            Toast.makeText(this, "PDF generation requires Android 13 or higher", Toast.LENGTH_SHORT).show();
        }
    }

    private String generateSecurePassword() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private void showSuccessDialog(String password) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🔒 Action Form PDF Created Successfully!")
                .setMessage("Your Action Form PDF has been created with password protection.\n\n" +
                        "📄 Viewing: Anyone can view the PDF\n" +
                        "✏️ Editing: Requires password\n\n" +
                        "🔑 Owner Password: " + password + "\n\n" +
                        "⚠️ Please save this password securely!")
                .setPositiveButton("Share PDF", (dialog, which) -> {
                    shareActionForm();
                })
                .setNegativeButton("Upload to Firebase", (dialog, which) -> {
                    showFirebaseFolderSelectionPopup();
                })
                .setNeutralButton("OK", (dialog, which) -> {
                    dialog.dismiss();
                })
                .setCancelable(false);
        
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void shareActionForm() {
        try {
            File reportsFolder = new File(getExternalFilesDir(null), "GRPEST REPORTS");
            if (!reportsFolder.exists()) {
                Toast.makeText(this, "Report folder not found!", Toast.LENGTH_SHORT).show();
                return;
            }

            File[] files = reportsFolder.listFiles((dir, name) -> name.endsWith(".pdf"));
            if (files == null || files.length == 0) {
                Toast.makeText(this, "No PDF report found to share!", Toast.LENGTH_SHORT).show();
                return;
            }

            File latestFile = files[files.length - 1];
            for (File file : files) {
                if (file.lastModified() > latestFile.lastModified()) {
                    latestFile = file;
                }
            }

            Uri fileUri = androidx.core.content.FileProvider.getUriForFile(
                    this,
                    "com.grpc.grpc.fileprovider",
                    latestFile
            );

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/pdf");
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(shareIntent, "Share Action Form"));
        } catch (Exception e) {
            Toast.makeText(this, "No application available to share the report.", Toast.LENGTH_SHORT).show();
        }
    }

    private void showFirebaseFolderSelectionPopup() {
        FirebaseStorage storage = FirebaseStorage.getInstance();
        StorageReference storageRef = storage.getReference();

        storageRef.listAll().addOnSuccessListener(listResult -> {
            List<String> folderList = new ArrayList<>();
            for (StorageReference prefix : listResult.getPrefixes()) {
                String folderName = prefix.getName();
                if (!folderName.equals("backup")) {
                    folderList.add(folderName);
                }
            }

            if (folderList.isEmpty()) {
                Toast.makeText(this, "No available folders to select.", Toast.LENGTH_SHORT).show();
                return;
            }

            String[] foldersArray = folderList.toArray(new String[0]);

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Select a Parent Folder");
            builder.setItems(foldersArray, (dialog, which) -> {
                String selectedFolder = foldersArray[which];
                uploadActionFormToFirebase(selectedFolder);
            });

            builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
            builder.show();

        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Failed to load folders: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    private void uploadActionFormToFirebase(String folderPath) {
        File reportsFolder = new File(getExternalFilesDir(null), "GRPEST REPORTS");
        if (!reportsFolder.exists()) {
            Toast.makeText(this, "Report folder not found!", Toast.LENGTH_SHORT).show();
            return;
        }

        File[] files = reportsFolder.listFiles((dir, name) -> name.endsWith(".pdf"));
        if (files == null || files.length == 0) {
            Toast.makeText(this, "No PDF report found to upload!", Toast.LENGTH_SHORT).show();
            return;
        }

        File latestFile = files[files.length - 1];
        for (File file : files) {
            if (file.lastModified() > latestFile.lastModified()) {
                latestFile = file;
            }
        }

        FirebaseStorage storage = FirebaseStorage.getInstance();
        StorageReference storageReference = storage.getReference();
        StorageReference fileRef = storageReference.child(folderPath + "/" + latestFile.getName());

        UploadTask uploadTask = fileRef.putFile(Uri.fromFile(latestFile));
        uploadTask.addOnSuccessListener(taskSnapshot -> {
            Toast.makeText(this, "Action Form uploaded successfully to " + folderPath, Toast.LENGTH_SHORT).show();
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    private void clearInputFields() {
        serviceTypeInput.getEditText().setText("");
        serviceNumberInput.getEditText().setText("");
        premisesNameInput.getEditText().setText("");
        premisesAddressInput.getEditText().setText("");
        prepInput.getEditText().setText("");
        serviceReportInput.getEditText().setText("");
        recommendationsInput.getEditText().setText("");
        followUp1Input.getEditText().setText("");
        roleInput.getEditText().setText("");
        selectedImageUris.clear();
        technicianSignatureUri = null;
        customerSignatureUri = null;
        followUpToggle.setChecked(false);
        followUpContainer.setVisibility(android.view.View.GONE);
        setCurrentDateTime();
    }

    private void requestOpenRouterApiKey() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🤖 AI Polish Setup")
                .setMessage("To use AI polishing, please enter your OpenRouter API key:")
                .setView(createApiKeyInputView())
                .setPositiveButton("Save", (dialog, which) -> {})
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .setCancelable(false);
        
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private android.view.View createApiKeyInputView() {
        EditText apiKeyInput = new EditText(this);
        apiKeyInput.setHint("Enter your OpenRouter API key");
        apiKeyInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        apiKeyInput.setPadding(50, 20, 50, 20);
        
        apiKeyInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(android.text.Editable s) {
                openRouterApiKey = s.toString().trim();
            }
        });
        
        return apiKeyInput;
    }

    private void polishWithAI() {
        String serviceReport = serviceReportInput.getEditText().getText().toString().trim();
        String recommendations = recommendationsInput.getEditText().getText().toString().trim();
        
        if (serviceReport.isEmpty() && recommendations.isEmpty()) {
            Toast.makeText(this, "Please enter some content in Service Report or Recommendations first", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (openRouterApiKey.isEmpty()) {
            Toast.makeText(this, "Please set your OpenRouter API key first", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Toast.makeText(this, "🤖 AI is polishing your Action Form...", Toast.LENGTH_SHORT).show();
        aiPolishButton.setEnabled(false);
        aiPolishButton.setText("🤖 Polishing...");
        
        String prompt = "Rewrite the following text to make it sound more professional, slightly longer, and more fluid. " +
                "Ensure the grammar is correct throughout. Keep the original meaning and all key information intact, " +
                "but improve the sentence structure, vocabulary, and tone to reflect the voice of a confident, experienced professional. " +
                "Expand naturally where appropriate without adding unrelated content. " +
                "Return the result as plain text only — do not include quotation marks, asterisks, or colons in the output formatting.\n\n";
        
        if (!serviceReport.isEmpty()) {
            prompt += "Service Report: " + serviceReport + "\n\n";
        } else {
            prompt += "Service Report: No service report details noted\n\n";
        }
        
        if (!recommendations.isEmpty()) {
            prompt += "Recommendations: " + recommendations + "\n\n";
        } else {
            prompt += "Recommendations: No recommendations noted\n\n";
        }
        
        prompt += "Please provide the enhanced content in this exact format:\n\n" +
                "Service Report: [professional content with improved grammar]\n\n" +
                "Recommendations: [professional recommendations with improved grammar]\n\n" +
                "Do not include any other text, labels, or formatting symbols. Return plain text only.";
        
        final String finalPrompt = prompt;
        new Thread(() -> {
            try {
                String response = callOpenRouterAPI(finalPrompt);
                updateUIWithAIPolish(response);
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "AI polishing failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    aiPolishButton.setEnabled(true);
                    aiPolishButton.setText("🤖 AI Polish Form");
                });
            }
        }).start();
    }

    private String callOpenRouterAPI(String prompt) throws IOException {
        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();

        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", AI_MODEL);
            
            JSONArray messages = new JSONArray();
            JSONObject message = new JSONObject();
            message.put("role", "user");
            message.put("content", prompt);
            messages.put(message);
            requestBody.put("messages", messages);
            requestBody.put("max_tokens", 4000);
            requestBody.put("temperature", 0.8);

            okhttp3.RequestBody body = okhttp3.RequestBody.create(
                    requestBody.toString(),
                    okhttp3.MediaType.parse("application/json; charset=utf-8")
            );

            okhttp3.Request request = new okhttp3.Request.Builder()
                    .url(OPENROUTER_API_URL)
                    .addHeader("Authorization", "Bearer " + openRouterApiKey)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("HTTP-Referer", "https://grpc-app.com")
                    .addHeader("X-Title", "GRPest Control App")
                    .addHeader("User-Agent", "GRPest-Control-App/1.0")
                    .post(body)
                    .build();

            try (okhttp3.Response response = client.newCall(request).execute()) {
                String responseBody = response.body().string();
                
                if (!response.isSuccessful()) {
                    Log.e("ActionFormActivity", "API Error Response: " + responseBody);
                    throw new IOException("API call failed: " + response.code() + " " + response.message() + "\nResponse: " + responseBody);
                }
                
                JSONObject jsonResponse = new JSONObject(responseBody);
                JSONArray choices = jsonResponse.getJSONArray("choices");
                
                if (choices.length() > 0) {
                    JSONObject choice = choices.getJSONObject(0);
                    JSONObject messageObj = choice.getJSONObject("message");
                    return messageObj.getString("content");
                } else {
                    throw new IOException("No response content from AI");
                }
            }
        } catch (org.json.JSONException e) {
            throw new IOException("JSON parsing error: " + e.getMessage());
        }
    }

    private void updateUIWithAIPolish(String aiResponse) {
        // Store the AI response for read-back functionality
        lastAIResponse = aiResponse;
        
        runOnUiThread(() -> {
            try {
                // Parse the AI response for Service Report and Recommendations only
                String serviceReport = "";
                String recommendations = "";
                
                // Extract Service Report
                if (aiResponse.contains("Service Report:")) {
                    int startIndex = aiResponse.indexOf("Service Report:") + "Service Report:".length();
                    int endIndex = aiResponse.indexOf("Recommendations:");
                    if (endIndex == -1) endIndex = aiResponse.length();
                    
                    serviceReport = aiResponse.substring(startIndex, endIndex).trim();
                    serviceReport = serviceReport.replaceAll("\\*\\*", "").trim();
                    serviceReport = serviceReport.replaceAll("\"", "").trim();
                }
                
                // Extract Recommendations
                if (aiResponse.contains("Recommendations:")) {
                    int startIndex = aiResponse.indexOf("Recommendations:") + "Recommendations:".length();
                    int endIndex = aiResponse.length();
                    
                    recommendations = aiResponse.substring(startIndex, endIndex).trim();
                    recommendations = recommendations.replaceAll("\\*\\*", "").trim();
                    recommendations = recommendations.replaceAll("\"", "").trim();
                }
                
                // Update UI fields (only Service Report and Recommendations)
                if (!serviceReport.isEmpty()) {
                    serviceReportInput.getEditText().setText(serviceReport);
                }
                if (!recommendations.isEmpty()) {
                    recommendationsInput.getEditText().setText(recommendations);
                }
                
                Toast.makeText(this, "✅ AI polishing completed! Tap AI button to hear response.", Toast.LENGTH_LONG).show();
                
            } catch (Exception e) {
                Toast.makeText(this, "Error parsing AI response: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            } finally {
                aiPolishButton.setEnabled(true);
                aiPolishButton.setText("🤖 AI Polish Form");
            }
        });
    }

    /**
     * Reads back the last AI response using Text-to-Speech
     */
    private void readAIResponseBack() {
        if (textToSpeech == null) {
            textToSpeech = new TextToSpeech(this, status -> {
                if (status == TextToSpeech.SUCCESS) {
                    readAIResponseContent();
                } else {
                    Toast.makeText(this, "Text-to-Speech not available", Toast.LENGTH_SHORT).show();
                }
            });
            return;
        }
        
        readAIResponseContent();
    }
    
    /**
     * Reads the AI response content using Text-to-Speech
     */
    private void readAIResponseContent() {
        if (lastAIResponse.isEmpty()) {
            Toast.makeText(this, "No AI response to read back", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Clean up the AI response for better speech
        String cleanResponse = lastAIResponse
                .replaceAll("\\*\\*", "")
                .replaceAll("\"", "")
                .replaceAll("Service Report:", "Service Report section:")
                .replaceAll("Recommendations:", "Recommendations section:")
                .trim();
        
        textToSpeech.speak(cleanResponse, TextToSpeech.QUEUE_FLUSH, null, "AI_RESPONSE_READBACK");
        Toast.makeText(this, "🔊 Reading AI response...", Toast.LENGTH_SHORT).show();
        
        // Reset the AI response after reading it back
        lastAIResponse = "";
        aiPolishButton.setText("🤖 AI Polish Form");
    }

    private void readFormBack() {
        if (textToSpeech == null) {
            textToSpeech = new TextToSpeech(this, status -> {
                if (status == TextToSpeech.SUCCESS) {
                    readFormContent();
                } else {
                    Toast.makeText(this, "Text-to-Speech not available", Toast.LENGTH_SHORT).show();
                }
            });
            return;
        }
        
        readFormContent();
    }
    
    private void readFormContent() {
        StringBuilder formText = new StringBuilder();
        formText.append("Date and Time: ").append(dateTimeInput.getText().toString()).append(". ");
        formText.append("Service Type: ").append(serviceTypeInput.getEditText().getText().toString()).append(". ");
        formText.append("Service Number: ").append(serviceNumberInput.getEditText().getText().toString()).append(". ");
        formText.append("Premises Name: ").append(premisesNameInput.getEditText().getText().toString()).append(". ");
        formText.append("Premises Address: ").append(premisesAddressInput.getEditText().getText().toString()).append(". ");
        formText.append("Prep: ").append(prepInput.getEditText().getText().toString()).append(". ");
        formText.append("Service Report: ").append(serviceReportInput.getEditText().getText().toString()).append(". ");
        formText.append("Recommendations: ").append(recommendationsInput.getEditText().getText().toString()).append(". ");
        
        // Only include follow-up if toggle is on
        if (followUpToggle.isChecked()) {
            formText.append("Follow-Up Details: ").append(followUp1Input.getEditText().getText().toString()).append(". ");
        }
        
        formText.append("Role: ").append(roleInput.getEditText().getText().toString()).append(". ");
        
        textToSpeech.speak(formText.toString(), TextToSpeech.QUEUE_FLUSH, null, "FORM_READBACK");
        Toast.makeText(this, "📖 Reading Action Form back...", Toast.LENGTH_SHORT).show();
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

    private void deleteSignatureImages() {
        try {
            // Delete technician signature if exists
            if (technicianSignatureUri != null) {
                File techSigFile = new File(technicianSignatureUri.getPath());
                if (techSigFile.exists()) {
                    boolean deleted = techSigFile.delete();
                    if (deleted) {
                        Log.d("ActionForm", "Technician signature deleted successfully");
                    }
                }
                technicianSignatureUri = null;
            }
            
            // Delete customer signature if exists
            if (customerSignatureUri != null) {
                File customerSigFile = new File(customerSignatureUri.getPath());
                if (customerSigFile.exists()) {
                    boolean deleted = customerSigFile.delete();
                    if (deleted) {
                        Log.d("ActionForm", "Customer signature deleted successfully");
                    }
                }
                customerSignatureUri = null;
            }
            
            // Clean up any remaining signature files in the SIGNATURES folder
            File signaturesFolder = new File(getExternalFilesDir(null), "SIGNATURES");
            if (signaturesFolder.exists() && signaturesFolder.isDirectory()) {
                File[] signatureFiles = signaturesFolder.listFiles();
                if (signatureFiles != null) {
                    for (File file : signatureFiles) {
                        if (file.getName().endsWith(".png")) {
                            boolean deleted = file.delete();
                            if (deleted) {
                                Log.d("ActionForm", "Cleaned up signature file: " + file.getName());
                            }
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            Log.e("ActionForm", "Error deleting signature images", e);
        }
    }
} 