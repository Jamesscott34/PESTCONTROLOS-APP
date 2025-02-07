package com.grpc.grpc;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.firestore.*;
import java.util.HashMap;
import java.util.Map;

public class AddJobsActivity extends AppCompatActivity {
    private EditText techName, techMobile, techEmail, customerName, customerEmail, customerContact, issueDetails;
    private Button submitButton;
    private FirebaseFirestore db;
    private String userName, techMobileNumber, custName, custEmail, custContact, issueDetailsText; // Stores values for WhatsApp

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_jobs);

        techName = findViewById(R.id.techName);
        techMobile = findViewById(R.id.techMobile);
        techEmail = findViewById(R.id.techEmail);
        customerName = findViewById(R.id.customerName);
        customerEmail = findViewById(R.id.customerEmail);
        customerContact = findViewById(R.id.customerContact);
        issueDetails = findViewById(R.id.issueDetails);
        submitButton = findViewById(R.id.submitButton);

        db = FirebaseFirestore.getInstance();

        // Retrieve username from intent
        userName = getIntent().getStringExtra("USER_NAME");
        if (userName == null || userName.isEmpty()) {
            Toast.makeText(this, "Error: Username not found!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        submitButton.setOnClickListener(v -> validateAndSubmitJob());
    }

    private void validateAndSubmitJob() {
        String name = techName.getText().toString().trim();
        techMobileNumber = formatIrishMobile(techMobile.getText().toString().trim()); // Convert number format
        custName = customerName.getText().toString().trim();
        custEmail = customerEmail.getText().toString().trim();
        custContact = formatIrishMobile(customerContact.getText().toString().trim());
        issueDetailsText = issueDetails.getText().toString().trim();

        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(techMobileNumber) || TextUtils.isEmpty(custName) ||
                TextUtils.isEmpty(custContact) || TextUtils.isEmpty(issueDetailsText)) {
            Toast.makeText(this, "Please fill in all required fields", Toast.LENGTH_SHORT).show();
            return;
        }

        custEmail = custEmail.isEmpty() ? "N/A" : custEmail; // Default value if empty

        addJobToFirestore(name, custName, custEmail, custContact, issueDetailsText, techMobileNumber);
    }

    private void addJobToFirestore(String techName, String custName, String custEmail, String custContact, String issue, String techMobile) {
        Map<String, Object> job = new HashMap<>();
        job.put("AssignedTech", techName);
        job.put("CustomerName", custName);
        job.put("CustomerEmail", custEmail);
        job.put("CustomerContact", custContact);
        job.put("IssueDetails", issue);

        db.collection("JobWork").add(job)
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(this, "Job Added Successfully", Toast.LENGTH_SHORT).show();
                    clearInputFields();
                    returnToJobsActivity(); // Return to jobs first, then open WhatsApp
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to add job", Toast.LENGTH_SHORT).show());
    }

    private void returnToJobsActivity() {
        Intent intent = new Intent(AddJobsActivity.this, JobsActivity.class);
        intent.putExtra("USER_NAME", userName);
        startActivity(intent);
        finish();

        // **After navigating back to JobsActivity, open WhatsApp**
        new android.os.Handler().postDelayed(this::sendWhatsAppMessage, 1000);  // Delay by 1 second to ensure smooth transition
    }

    private void sendWhatsAppMessage() {
        if (techMobileNumber == null || techMobileNumber.isEmpty()) {
            Toast.makeText(this, "Technician mobile number is missing!", Toast.LENGTH_SHORT).show();
            return;
        }

        String message = "Hello, a new job has been assigned to you.\n\n" +
                "🔹 Customer Name: " + (custName != null ? custName : "N/A") + "\n" +
                "🔹 Customer Email: " + (custEmail != null ? custEmail : "N/A") + "\n" +
                "🔹 Customer Contact: " + (custContact != null ? custContact : "N/A") + "\n" +
                "🔹 Issue: " + (issueDetailsText != null ? issueDetailsText : "N/A") + "\n\n" +
                "📞 Please check your GRPC app for details.";

        String formattedNumber = techMobileNumber.replace("+", "").replace(" ", "").trim();

        try {
            Uri uri = Uri.parse("https://wa.me/" + formattedNumber + "?text=" + Uri.encode(message));
            Intent sendIntent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(sendIntent);
        } catch (Exception e) {
            Toast.makeText(this, "WhatsApp not installed or failed to open", Toast.LENGTH_SHORT).show();
        }
    }

    private String formatIrishMobile(String number) {
        if (number.startsWith("087") || number.startsWith("086") || number.startsWith("085") ||
                number.startsWith("089") || number.startsWith("083") || number.startsWith("088")) {
            return "+353" + number.substring(1);
        }
        return number;
    }


    private void clearInputFields() {
        techName.setText("");
        techMobile.setText("");
        techEmail.setText("");
        customerName.setText("");
        customerEmail.setText("");
        customerContact.setText("");
        issueDetails.setText("");
    }
}
