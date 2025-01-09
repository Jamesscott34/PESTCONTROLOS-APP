package com.grpc.grpc;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;



import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ReportActivity extends AppCompatActivity {

    private EditText nameInput, addressInput, dateInput, visitTypeInput, siteInspectionInput, recommendationsInput, followUpInput, prepInput, techInput;
    private Button saveButton, backButton;
    private Uri selectedImageUri;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report);

        nameInput = findViewById(R.id.nameInput);
        addressInput = findViewById(R.id.addressInput);
        dateInput = findViewById(R.id.dateInput);
        visitTypeInput = findViewById(R.id.visitTypeInput);
        siteInspectionInput = findViewById(R.id.siteInspectionInput);
        recommendationsInput = findViewById(R.id.recommendationsInput);
        followUpInput = findViewById(R.id.followUpInput);
        prepInput = findViewById(R.id.prepInput);
        techInput = findViewById(R.id.techInput);
        saveButton = findViewById(R.id.saveButton);
        backButton = findViewById(R.id.backButton);


        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault());
        dateInput.setText(sdf.format(new Date()));

        ReportDatabaseHelper dbHelper = new ReportDatabaseHelper(this);
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        saveButton.setOnClickListener(view -> {
            String reportName = nameInput.getText().toString();
            String content = "Premise Name: " + nameInput.getText().toString()
                    + "\nAddress: " + addressInput.getText().toString()
                    + "\nDate: " + dateInput.getText().toString()
                    + "\nVisit Type: " + visitTypeInput.getText().toString()
                    + "\nSite Inspection: " + siteInspectionInput.getText().toString()
                    + "\nRecommendations: " + recommendationsInput.getText().toString()
                    + "\nFollow-Up: " + followUpInput.getText().toString()
                    + "\nPrep: " + prepInput.getText().toString()
                    + "\nTech: " + techInput.getText().toString();

            ContentValues values = new ContentValues();
            values.put("name", nameInput.getText().toString());
            values.put("address", addressInput.getText().toString());
            values.put("date", dateInput.getText().toString());
            values.put("visit_type", visitTypeInput.getText().toString());
            values.put("site_inspection", siteInspectionInput.getText().toString());
            values.put("recommendations", recommendationsInput.getText().toString());
            values.put("follow_up", followUpInput.getText().toString());
            values.put("prep", prepInput.getText().toString());
            values.put("tech", techInput.getText().toString());

            long newRowId = db.insert("CompanyReports", null, values);
            if (newRowId != -1) {
                Toast.makeText(this, "Company Report Saved Successfully!", Toast.LENGTH_SHORT).show();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    PDFReportGenerator.generatePDFReport("Company", reportName, content, (Context) this, (List<Uri>) selectedImageUri);
                }
            } else {
                Toast.makeText(this, "Error Saving Report!", Toast.LENGTH_SHORT).show();
            }
        });


        backButton.setOnClickListener(view -> {
            Intent intent = new Intent(ReportActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        });
    }

}
