package com.grpc.grpc;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class GeneralReportActivity extends AppCompatActivity {

    private EditText nameInput, addressInput, dateInput;
    private Spinner visitTypeSpinner;
    private Button saveButton;

    private String userName;

    private final String[] visitTypes = {
            "Initial Setup",
            "Routine",
            "Rodent Activity Internal Routine",
            "Rodent Activity External Routine",
            "Rodent Call Out External",
            "Rodent Call Out Internal"
    };

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_general_report_creator);

        userName = getIntent().getStringExtra("USER_NAME");

        nameInput = findViewById(R.id.nameInput);
        addressInput = findViewById(R.id.addressInput);
        dateInput = findViewById(R.id.dateInput);
        visitTypeSpinner = findViewById(R.id.visitTypeSpinner);
        saveButton = findViewById(R.id.saveButton);

        // Auto-fill date only
        dateInput.setText(new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date()));

        // Dropdown setup
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, visitTypes);
        visitTypeSpinner.setAdapter(adapter);

        saveButton.setOnClickListener(v -> handleCreateReport());
    }

    private void handleCreateReport() {
        String companyName = nameInput.getText().toString().trim();
        String address = addressInput.getText().toString().trim();
        String date = dateInput.getText().toString().trim();
        String visitType = visitTypeSpinner.getSelectedItem().toString();

        if (companyName.isEmpty() || address.isEmpty()) {
            Toast.makeText(this, "Please enter both name and address", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent;

        switch (visitType) {
            case "Initial Setup":
                intent = new Intent(this, RodentInitialActivity.class);
                break;
            case "Routine":
                intent = new Intent(this, RodentRoutineActivity.class);
                break;
            case "Rodent Activity Internal Routine":
                intent = new Intent(this, RodentActivityRoutine.class);
                break;
            case "Rodent Activity External Routine":
                intent = new Intent(this, RodentActivityExternalRoutine.class);
                break;
            case "Rodent Call Out Internal":
                intent = new Intent(this, RodentCallOutActivity.class);
                break;
            case "Rodent Call Out External":
                intent = new Intent(this, RodentCallOutExternalActivity.class);
                break;
            default:
                Toast.makeText(this, "Unknown visit type", Toast.LENGTH_SHORT).show();
                return;
        }

        intent.putExtra("USER_NAME", userName);
        intent.putExtra("COMPANY_NAME", companyName);
        intent.putExtra("ADDRESS", address);
        intent.putExtra("ROUTINE_TYPE", visitType);

        startActivity(intent);
        finish();
    }
}
