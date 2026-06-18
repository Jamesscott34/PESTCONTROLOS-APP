package com.grpc.grpc.maps.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.grpc.grpc.R;
import com.grpc.grpc.core.ContractStorageUploader;
import com.grpc.grpc.core.SessionManager;
import com.grpc.grpc.maps.util.MapsUtil;

import java.io.File;

public class SiteMapEditorActivity extends AppCompatActivity {
    private ActivityResultLauncher<String> imagePickerLauncher;
    private SiteMapCanvasView canvasView;
    private TextView shapeSizeText;
    private String contractId;
    private String companyName;
    private String address;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!SessionManager.canMap(this)) {
            Toast.makeText(this, "You do not have permission to create maps.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri == null || canvasView == null) {
                        return;
                    }
                    try {
                        Bitmap bitmap = BitmapFactory.decodeStream(
                                getContentResolver().openInputStream(uri));
                        if (bitmap != null) {
                            canvasView.setBackgroundImage(bitmap);
                        }
                    } catch (Exception e) {
                        Toast.makeText(this, "Failed to load image.", Toast.LENGTH_SHORT).show();
                    }
                });

        setContentView(R.layout.activity_site_map_editor);

        contractId = getIntent().getStringExtra("CONTRACT_ID");
        companyName = getIntent().getStringExtra("COMPANY_NAME");
        address = getIntent().getStringExtra("ADDRESS");

        TextView titleText = findViewById(R.id.mapEditorTitleText);
        TextView customerText = findViewById(R.id.mapCustomerText);
        TextView addressText = findViewById(R.id.mapAddressText);
        shapeSizeText = findViewById(R.id.textShapeSizeValue);
        canvasView = findViewById(R.id.siteMapCanvasView);
        canvasView.setOnShapeSizeChangedListener(label -> {
            if (shapeSizeText != null) {
                shapeSizeText.setText("Size: " + label);
            }
        });

        titleText.setText(hasContract() ? "Create Contract Map" : "Create Map");
        customerText.setText("Customer: " + safe(companyName));
        addressText.setText("Address: " + safe(address));

        bindToolButton(R.id.buttonToolLine, SiteMapCanvasView.TOOL_LINE);
        bindToolButton(R.id.buttonToolInternal, SiteMapCanvasView.TOOL_INTERNAL);
        bindToolButton(R.id.buttonToolExternal, SiteMapCanvasView.TOOL_EXTERNAL);
        bindToolButton(R.id.buttonToolFly, SiteMapCanvasView.TOOL_FLY);
        bindToolButton(R.id.buttonToolInsect, SiteMapCanvasView.TOOL_INSECT);
        bindToolButton(R.id.buttonToolErase, SiteMapCanvasView.TOOL_ERASE);
        bindToolButton(R.id.buttonToolShape, SiteMapCanvasView.TOOL_SHAPE);

        Spinner shapeSpinner = findViewById(R.id.spinnerShapeType);
        if (shapeSpinner != null) {
            android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                    this,
                    android.R.layout.simple_spinner_item,
                    new String[]{"Rectangle", "Square", "Circle", "Triangle", "Diamond"}
            );
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            shapeSpinner.setAdapter(adapter);
            shapeSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                    canvasView.setPendingShapeType(position);
                }

                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {
                }
            });
        }

        Button labelButton = findViewById(R.id.buttonToolLabel);
        labelButton.setOnClickListener(v -> promptForLabel());
        Button smallerShapeButton = findViewById(R.id.buttonShapeSmaller);
        Button largerShapeButton = findViewById(R.id.buttonShapeLarger);

        Button setBackgroundButton = findViewById(R.id.buttonSetBackground);
        Button undoButton = findViewById(R.id.buttonUndoMap);
        Button clearButton = findViewById(R.id.buttonClearMap);
        Button saveButton = findViewById(R.id.buttonSaveMap);
        Button backButton = findViewById(R.id.buttonBackMap);

        smallerShapeButton.setOnClickListener(v -> {
            canvasView.adjustShapeSize(-10f);
            updateShapeSizeText();
        });
        largerShapeButton.setOnClickListener(v -> {
            canvasView.adjustShapeSize(10f);
            updateShapeSizeText();
        });
        setBackgroundButton.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));
        undoButton.setOnClickListener(v -> canvasView.undoLast());
        clearButton.setOnClickListener(v -> canvasView.clearAll());
        backButton.setOnClickListener(v -> finish());
        saveButton.setOnClickListener(v -> saveMap());
        updateShapeSizeText();
    }

    private void bindToolButton(int buttonId, int tool) {
        Button button = findViewById(buttonId);
        button.setOnClickListener(v -> canvasView.setActiveTool(tool));
    }

    private void promptForLabel() {
        final EditText input = new EditText(this);
        input.setHint("Enter map label");
        new AlertDialog.Builder(this)
                .setTitle("Add Label")
                .setView(input)
                .setPositiveButton("Place Label", (dialog, which) -> {
                    String label = input.getText() != null ? input.getText().toString().trim() : "";
                    if (label.isEmpty()) {
                        Toast.makeText(this, "Enter a label first.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    canvasView.setPendingLabelText(label);
                    canvasView.setActiveTool(SiteMapCanvasView.TOOL_LABEL);
                    Toast.makeText(this, "Tap the map to place the label.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateShapeSizeText() {
        if (shapeSizeText != null && canvasView != null) {
            shapeSizeText.setText("Size: " + canvasView.getShapeSizeLabel());
        }
    }

    private void saveMap() {
        if (canvasView == null || !canvasView.hasContent()) {
            Toast.makeText(this, "Add lines or markers before saving the map.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Bitmap bitmap = canvasView.renderToBitmap();
            File pdfFile = MapsUtil.generateLandscapeMapPdf(this, companyName, address, bitmap);
            if (hasContract() && ContractStorageUploader.shouldAutoUpload(contractId)) {
                ContractStorageUploader.uploadContractReport(
                 