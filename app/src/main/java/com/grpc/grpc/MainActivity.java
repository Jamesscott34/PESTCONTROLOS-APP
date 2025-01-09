package com.grpc.grpc;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private Button reportButton;
    private Button reportViewButton;
    private Button buttonOpenCalendar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        reportButton = findViewById(R.id.ReportButton);
        reportViewButton = findViewById(R.id.ReportViewButton);
        buttonOpenCalendar = findViewById(R.id.buttonOpenCalendar);

        reportButton.setOnClickListener(view ->
                startActivity(new Intent(this, ReportActivity.class)));

        reportViewButton.setOnClickListener(view ->
                startActivity(new Intent(this, ReportViewActivity.class)));

        buttonOpenCalendar.setOnClickListener(view ->
                startActivity(new Intent(this, CalendarActivity.class)));
    }
}
