package com.grpc.grpc;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.*;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class StencilMapActivity extends AppCompatActivity {
    private static final int PICK_IMAGE = 1;
    private static final int PICK_LOGO = 2;

    private ImageView imgMapPreview;
    private Bitmap selectedImage, logoImage, scaledBitmap;
    private List<DrawnItem> drawnItems = new ArrayList<>();
    private int circleColor = Color.RED;
    private int redCounter = 1, greenCounter = 1, blueCounter = 1;
    private String addressText = "";

    private float scaleFactor = 1.0f; // Default scale factor

    private ScaleGestureDetector scaleGestureDetector;
    private boolean addCircleMode = false;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stencil_map);

        imgMapPreview = findViewById(R.id.imgMapPreview);
        Button btnSelectImage = findViewById(R.id.btnSelectImage);
        Button btnSaveMap = findViewById(R.id.btnSaveMap);
        Button btnAddText = findViewById(R.id.btnAddText);
        Button btnAddLogo = findViewById(R.id.btnAddLogo);
        Button btnSetAddress = findViewById(R.id.btnSetAddress);
        Button btnUndo = findViewById(R.id.btnUndo);
        Button btnRed = findViewById(R.id.btnRed);
        Button btnGreen = findViewById(R.id.btnGreen);
        Button btnBlue = findViewById(R.id.btnBlue);


        btnSelectImage.setOnClickListener(v -> selectImage());
        btnSaveMap.setOnClickListener(v -> promptPremisesName());
        btnAddText.setOnClickListener(v -> promptText());
        btnAddLogo.setOnClickListener(v -> selectLogo());
        btnSetAddress.setOnClickListener(v -> promptAddress());
        btnUndo.setOnClickListener(v -> undoLastAction());

        btnRed.setOnClickListener(v -> circleColor = Color.RED);
        btnGreen.setOnClickListener(v -> circleColor = Color.GREEN);
        btnBlue.setOnClickListener(v -> circleColor = Color.BLUE);

        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleListener());

        imgMapPreview.setOnTouchListener((v, event) -> {
            scaleGestureDetector.onTouchEvent(event);
            if (event.getPointerCount() == 1 && event.getAction() == MotionEvent.ACTION_DOWN) {
                addCircle(event.getX(), event.getY());
            }
            return true;
        });
    }

    private void selectImage() {
        Intent pickIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(pickIntent, PICK_IMAGE);
    }

    private void selectLogo() {
        Intent pickIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(pickIntent, PICK_LOGO);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null) {
            try {
                Uri imageUri = data.getData();
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);

                if (requestCode == PICK_IMAGE) {
                    selectedImage = convertToGrayscale(bitmap);
                    imgMapPreview.setImageBitmap(selectedImage);
                } else if (requestCode == PICK_LOGO) {
                    logoImage = Bitmap.createScaledBitmap(bitmap, 200, 100, true);
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private Bitmap convertToGrayscale(Bitmap bitmap) {
        Bitmap grayBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(grayBitmap);
        Paint paint = new Paint();
        ColorMatrix colorMatrix = new ColorMatrix();
        colorMatrix.setSaturation(0);
        paint.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
        canvas.drawBitmap(bitmap, 0, 0, paint);
        return grayBitmap;
    }

    private void promptPremisesName() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter Premises Name");

        final EditText input = new EditText(this);
        builder.setView(input);

        builder.setPositiveButton("Save", (dialog, which) -> saveMap(input.getText().toString()));
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void saveMap(String premisesName) {
        if (selectedImage == null || premisesName.trim().isEmpty()) return;

        Bitmap finalImage = drawOnBitmap();
        File directory = new File(Environment.getExternalStorageDirectory(), "Premises Maps");
        if (!directory.exists()) {
            directory.mkdirs();
        }

        File file = new File(directory, premisesName + ".png");
        try (FileOutputStream fos = new FileOutputStream(file)) {
            finalImage.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void addCircle(float x, float y) {
        if (!addCircleMode) return;

        int number;
        if (circleColor == Color.RED) {
            number = redCounter++;
        } else if (circleColor == Color.GREEN) {
            number = greenCounter++;
        } else {
            number = blueCounter++;
        }
        drawnItems.add(new DrawnItem(x, y, circleColor, number, null));
        imgMapPreview.setImageBitmap(drawOnBitmap());
    }


    private void promptText() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter Text");

        final EditText input = new EditText(this);
        builder.setView(input);

        builder.setPositiveButton("OK", (dialog, which) -> {
            drawnItems.add(new DrawnItem(200, 200, Color.BLACK, -1, input.getText().toString()));
            imgMapPreview.setImageBitmap(drawOnBitmap());
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void promptAddress() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter Address");

        final EditText input = new EditText(this);
        builder.setView(input);

        builder.setPositiveButton("OK", (dialog, which) -> {
            addressText = input.getText().toString();
            imgMapPreview.setImageBitmap(drawOnBitmap());
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void undoLastAction() {
        if (!drawnItems.isEmpty()) {
            drawnItems.remove(drawnItems.size() - 1);
            imgMapPreview.setImageBitmap(drawOnBitmap());
        }
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            scaleFactor *= detector.getScaleFactor();
            scaleFactor = Math.max(0.5f, Math.min(scaleFactor, 3.0f));
            imgMapPreview.setScaleX(scaleFactor);
            imgMapPreview.setScaleY(scaleFactor);
            return true;
        }
    }

    static class DrawnItem {
        float x, y;
        int color, number;
        String text;

        DrawnItem(float x, float y, int color, int number, String text) {
            this.x = x;
            this.y = y;
            this.color = color;
            this.number = number;
            this.text = text;
        }
    }

    private Bitmap drawOnBitmap() {
        scaledBitmap = Bitmap.createScaledBitmap(selectedImage,
                (int) (selectedImage.getWidth() * scaleFactor),
                (int) (selectedImage.getHeight() * scaleFactor), true);

        Bitmap newBitmap = scaledBitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(newBitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setTextSize(50);
        paint.setTextAlign(Paint.Align.CENTER);

        for (DrawnItem item : drawnItems) {
            paint.setColor(item.color);
            if (item.text == null) {
                canvas.drawCircle(item.x * scaleFactor, item.y * scaleFactor, 40 * scaleFactor, paint);
                paint.setColor(Color.WHITE);
                canvas.drawText(String.valueOf(item.number), item.x * scaleFactor, item.y * scaleFactor + 15, paint);
            } else {
                canvas.drawText(item.text, item.x * scaleFactor, item.y * scaleFactor, paint);
            }
        }

        if (logoImage != null) {
            canvas.drawBitmap(logoImage, 20 * scaleFactor, 20 * scaleFactor, null);
        }

        if (!addressText.isEmpty()) {
            paint.setColor(Color.BLACK);
            canvas.drawText(addressText, 50 * scaleFactor, newBitmap.getHeight() - 50 * scaleFactor, paint);
        }

        return newBitmap;
    }
}
