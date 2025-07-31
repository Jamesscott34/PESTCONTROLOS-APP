package com.grpc.grpc;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SignatureCaptureActivity extends AppCompatActivity {
    
    private SimpleSignatureView signatureView;
    private Button clearButton, saveButton, cancelButton;
    private String signatureType; // "technician" or "customer"
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signature_capture);
        
        signatureType = getIntent().getStringExtra("SIGNATURE_TYPE");
        if (signatureType == null) {
            signatureType = "technician";
        }
        
        signatureView = findViewById(R.id.signatureView);
        clearButton = findViewById(R.id.clearButton);
        saveButton = findViewById(R.id.saveButton);
        cancelButton = findViewById(R.id.cancelButton);
        
        clearButton.setOnClickListener(v -> signatureView.clear());
        saveButton.setOnClickListener(v -> saveSignature());
        cancelButton.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
    }
    
    private void saveSignature() {
        if (signatureView.isEmpty()) {
            Toast.makeText(this, "Please draw a signature first", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Bitmap signatureBitmap = signatureView.getSignatureBitmap();
        if (signatureBitmap != null) {
            try {
                // Create signature file
                File signatureDir = new File(getExternalFilesDir(null), "SIGNATURES");
                if (!signatureDir.exists()) {
                    signatureDir.mkdirs();
                }
                
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
                String fileName = signatureType + "_signature_" + sdf.format(new Date()) + ".png";
                File signatureFile = new File(signatureDir, fileName);
                
                FileOutputStream fos = new FileOutputStream(signatureFile);
                signatureBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                fos.close();
                
                // Return the signature file URI
                Intent resultIntent = new Intent();
                resultIntent.putExtra("SIGNATURE_URI", Uri.fromFile(signatureFile));
                resultIntent.putExtra("SIGNATURE_TYPE", signatureType);
                setResult(RESULT_OK, resultIntent);
                finish();
                
            } catch (IOException e) {
                Toast.makeText(this, "Error saving signature: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    // Simple signature view that won't crash
    public static class SimpleSignatureView extends View {
        private Path path;
        private Paint paint;
        private float lastX, lastY;
        private boolean hasSignature = false;
        
        public SimpleSignatureView(android.content.Context context) {
            super(context);
            init();
        }
        
        public SimpleSignatureView(android.content.Context context, android.util.AttributeSet attrs) {
            super(context, attrs);
            init();
        }
        
        private void init() {
            path = new Path();
            paint = new Paint();
            paint.setColor(ContextCompat.getColor(getContext(), R.color.signature_ink));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(8f); // Thicker stroke for signature look
            paint.setAntiAlias(true);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeCap(Paint.Cap.ROUND);
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
            if (isEmpty()) {
                return null;
            }
            
            Bitmap bitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(ContextCompat.getColor(getContext(), R.color.signature_bg));
            canvas.drawPath(path, paint);
            return bitmap;
        }
    }
} 