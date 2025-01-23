package com.grpc.grpc;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class QuotesActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_general_quote);

        // Initialize Buttons
        Button button8ptContact = findViewById(R.id.button8ptContact);
        Button button6ptContract = findViewById(R.id.button6ptContract);
        Button button4ptContract = findViewById(R.id.button4ptContract);

        // Set Click Listeners for Each Button
        button8ptContact.setOnClickListener(view ->
                Toast.makeText(QuotesActivity.this, "8pt Contact clicked", Toast.LENGTH_SHORT).show()
        );

        button6ptContract.setOnClickListener(view ->
                Toast.makeText(QuotesActivity.this, "6pt Contract clicked", Toast.LENGTH_SHORT).show()
        );

        button4ptContract.setOnClickListener(view ->
                Toast.makeText(QuotesActivity.this, "4pt Contract clicked", Toast.LENGTH_SHORT).show()
        );
    }
}
