package com.grpc.grpc;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.property.TextAlignment;
import com.itextpdf.layout.property.UnitValue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class General8ptActivity extends AppCompatActivity {

    private String userName;
    private String userEmail;
    private String userMobile;

    private EditText companyNameInput;
    private EditText companyAddressInput;
    private EditText companyContactInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_general8pt);

        // Retrieve the username
        userName = getIntent().getStringExtra("USER_NAME");

        // Set user-specific details
        if ("Ian".equalsIgnoreCase(userName)) {
            userEmail = "ian@grpestcontrol.ie";
            userMobile = "0879134971";
        } else if ("James".equalsIgnoreCase(userName)) {
            userEmail = "james@grpestcontrol.ie";
            userMobile = "0879000271";
        }

        // Display welcome message
        TextView welcomeTextView = findViewById(R.id.welcomeTextView);
        welcomeTextView.setText("Welcome, " + userName + "!");

        // Input fields for company details
        companyNameInput = findViewById(R.id.companyNameInput);
        companyAddressInput = findViewById(R.id.companyAddressInput);
        companyContactInput = findViewById(R.id.companyContactInput);

        // Generate PDF button
        Button generatePdfButton = findViewById(R.id.generatePdfButton);
        generatePdfButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                generateQuote();
            }
        });
    }

    private void generateQuote() {
        String companyName = companyNameInput.getText().toString().trim();
        String companyAddress = companyAddressInput.getText().toString().trim();
        String companyContact = companyContactInput.getText().toString().trim();

        if (companyName.isEmpty() || companyAddress.isEmpty() || companyContact.isEmpty()) {
            Toast.makeText(this, "Please fill in all company details", Toast.LENGTH_SHORT).show();
            return;
        }

        // Generate a random 4-digit quote number
        String quoteNumber = String.format("%04d", new Random().nextInt(10000));

        // Descriptions and prices for the quote
        List<String> descriptions = new ArrayList<>();
        List<Double> lineTotals = new ArrayList<>();

        // Manually entered descriptions
        // Manually entered descriptions
        descriptions.add("Annual Service Fee for Pest Management Solutions:\n" +
                "This fee covers a comprehensive yearly pest control program tailored to your site’s unique requirements. " +
                "Our service includes regular inspections, proactive treatments, and ongoing monitoring to ensure a pest-free environment. " +
                "We utilize advanced methods and eco-friendly solutions to deliver effective and sustainable pest management results.");
        lineTotals.add(700.0);

        descriptions.add("Internal Monitors Placement and Maintenance:\n" +
                "Our skilled technicians will strategically place internal monitors on-site, targeting high-risk areas to detect pest activity. " +
                "This approach ensures early identification of infestations, enabling timely intervention and reducing potential damage. " +
                "Monitors will be reviewed and maintained regularly during each service visit, ensuring continuous monitoring and effectiveness.");
        lineTotals.add(0.0);

        descriptions.add("Maintenance of a Single Fly Control Unit:\n" +
                "This service includes comprehensive maintenance of a single, high-performance fly control unit already installed on-site. " +
                "The package covers four scheduled yearly service visits to ensure the unit's optimal performance, " +
                "along with one complimentary bulb change per year. " +
                "Designed to effectively capture and manage flying insects, the unit helps maintain hygienic conditions in critical areas. " +
                "Our team will inspect unit during each visit to maximize efficiency and ensure compliance with health and safety standards.");
        lineTotals.add(0.0);


        descriptions.add("External Pest Control Measures:\n" +
                "This service includes tailored external pest control solutions to address issues specific to your premises. " +
                "The price covers the maintenance and servicing of two existing external pest control units already installed on-site. " +
                "Services provided include preventive treatments, perimeter inspections, and protective measures to minimize the risk of pests entering the building. " +
                "Our skilled technicians will assess the site during each visit to ensure the units remain effective, " +
                "and implement strategies that align with your operational needs to maintain a pest-free environment.");
        lineTotals.add(0.0);

        descriptions.add("Additional External Treatments or Equipment:\n" +
                "Additional external pest control treatments or equipment installations are available upon request. " +
                "These services, charged at €25 per item plus VAT, ensure enhanced protection and are recommended for sites with heightened exposure to pests. " +
                "Our experts will advise on the necessity and frequency of these additional measures during the initial assessment.");
        lineTotals.add(0.0);

        descriptions.add("Additional Fly Control Units:\n" +
                "Any additional units currently on-site are charged quarterly at €40 per unit plus VAT. " +
                "If new units are required, they can be acquired at an additional fee, with installation and maintenance included. " +
                "These units are highly effective in maintaining pest-free environments, particularly in high-traffic or food preparation areas. " +
                "Our team ensures expert placement and a tailored maintenance plan to guarantee peak performance and compliance with health and safety standards.");
        lineTotals.add(0.0);

        descriptions.add("Callouts for Pest Control Services:\n" +
                "This service includes two complimentary callouts per year during working hours for pest control maintenance, adjustments, or inspections as needed. " +
                "Any additional callouts beyond the included two will be charged at a discounted rate of €100 per visit plus VAT. " +
                "Our expert team ensures timely and efficient responses during each callout, addressing pest issues effectively and maintaining a safe, pest-free environment. " +
                "This service is ideal for ongoing support and ensures that your premises remain compliant with health and safety standards.");
        lineTotals.add(0.0);


        File pdfFile = generateQuotationReport(
                companyName + "_" + quoteNumber, // File name
                companyAddress,                  // Address
                getQuotationDescription(),       // Call the getQuotationDescription() function here
                descriptions,                    // Descriptions
                lineTotals,                      // Line totals
                userEmail,                       // User email
                userMobile,                      // User mobile
                companyName,                     // Company name
                companyContact,                  // Company contact
                General8ptActivity.this          // Explicitly pass the activity as Context
        );

        if (pdfFile != null) {
            Toast.makeText(this, "PDF Quote Generated: " + pdfFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
            clearInputFields();
            navigateBackToQuotesActivity();
        } else {
            Toast.makeText(this, "Failed to generate PDF quote", Toast.LENGTH_SHORT).show();
        }
    }

    private void clearInputFields() {
        companyNameInput.setText("");
        companyAddressInput.setText("");
        companyContactInput.setText("");
    }

    private void navigateBackToQuotesActivity() {
        Intent intent = new Intent(General8ptActivity.this, QuotesActivity.class);
        intent.putExtra("USER_NAME", userName);
        startActivity(intent);
        finish();
    }

    public static File generateQuotationReport(
            String fileName, String address, String quoteDescription,
            List<String> descriptions, List<Double> lineTotals,
            String userEmail, String mobileNumber,
            String companyName, String companyContact, Context context) {

        File quotesFolder = new File(context.getExternalFilesDir(null), "GRPEST_QUOTES");

        if (!quotesFolder.exists() && !quotesFolder.mkdirs()) {
            Toast.makeText(context, "Error creating quotes folder", Toast.LENGTH_SHORT).show();
            return null;
        }

        File pdfFile = new File(quotesFolder, fileName + ".pdf");

        try (PdfWriter writer = new PdfWriter(new FileOutputStream(pdfFile));
             PdfDocument pdfDocument = new PdfDocument(writer);
             Document document = new Document(pdfDocument)) {

            pdfDocument.addEventHandler(PdfDocumentEvent.END_PAGE, new PDFReportGenerator.PdfWatermarkAndFooterHandler(context));

            // Add logo and header
            int logoResourceId = context.getResources().getIdentifier("logo", "drawable", context.getPackageName());
            ImageData logoData = ImageDataFactory.create(context.getResources().openRawResource(logoResourceId).readAllBytes());
            Image logo = new Image(logoData).scaleToFit(200, 200);

            // Header Table
            float[] headerWidths = {1, 1};
            Table headerTable = new Table(headerWidths).setWidth(UnitValue.createPercentValue(100));

            // Left Section: Logo and Company Info
            Cell leftCell = new Cell().setBorder(Border.NO_BORDER);
            leftCell.add(logo);
            leftCell.add(new Paragraph("\nGood Riddance Pest Control").setBold().setFontSize(16));
            leftCell.add(new Paragraph("Name: " + (userEmail.contains("ian") ? "Ian Winston" : "James Scott")).setFontSize(12));
            leftCell.add(new Paragraph("Mobile: " + mobileNumber).setFontSize(12));
            leftCell.add(new Paragraph("Email: " + userEmail).setFontSize(12));
            leftCell.add(new Paragraph("Website: grpestcontrol.ie").setFontSize(12));
            headerTable.addCell(leftCell);

            // Right Section: Date, Quote Info, and Company Info
            SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
            String currentDate = sdf.format(new Date());

            Cell rightCell = new Cell().setBorder(Border.NO_BORDER);
            rightCell.add(new Paragraph("Date: " + currentDate).setFontSize(12).setBold());
            rightCell.add(new Paragraph("Quote Number: " + fileName.split("_")[1]).setFontSize(12));
            rightCell.add(new Paragraph("Quote Valid for 30 Days").setFontSize(12).setItalic());
            rightCell.add(new Paragraph("\nCompany Name:").setBold());
            rightCell.add(new Paragraph(companyName).setFontSize(12));
            rightCell.add(new Paragraph("Address:").setBold());
            rightCell.add(new Paragraph(address).setFontSize(12));
            rightCell.add(new Paragraph("Contact: " + companyContact).setFontSize(12));
            headerTable.addCell(rightCell);

            document.add(headerTable);

            // Quote Description Section
            document.add(new Paragraph("\nQuote Description:").setFontSize(14).setBold().setUnderline());
            document.add(new Paragraph(quoteDescription).setFontSize(12));

            // Line Items Table Setup
            float[] columnWidths = {4, 2, 2, 2};
            Table table = new Table(columnWidths).setWidth(UnitValue.createPercentValue(100));
            table.addHeaderCell(new Cell().add(new Paragraph("Description").setBold()));
            table.addHeaderCell(new Cell().add(new Paragraph("Line Total (€)").setBold()));
            table.addHeaderCell(new Cell().add(new Paragraph("VAT (23%) (€)").setBold()));
            table.addHeaderCell(new Cell().add(new Paragraph("Total (€)").setBold()));

            double grandTotal = 0;
            double firstQuarterPayment = 0;

            for (int i = 0; i < descriptions.size(); i++) {
                double lineTotal = lineTotals.get(i);
                double vatAmount = lineTotal * 0.23;
                double total = lineTotal + vatAmount;

                if (i == 0) {
                    firstQuarterPayment = total / 4;
                }

                table.addCell(new Paragraph(descriptions.get(i)));
                table.addCell(new Paragraph(String.format("€%.2f", lineTotal)));
                table.addCell(new Paragraph(String.format("€%.2f", vatAmount)));
                table.addCell(new Paragraph(String.format("€%.2f", total)));

                grandTotal += total;
            }

            document.add(table);

            // Payment Summary
            document.add(new Paragraph("\nPayment Summary").setFontSize(14).setBold().setUnderline());
            document.add(new Paragraph("First Quarter Payment: €" + String.format("%.2f", firstQuarterPayment)).setFontSize(12));
            document.add(new Paragraph("Total Payment Due: €" + String.format("%.2f", grandTotal)).setFontSize(12).setBold());

            document.close();
            return pdfFile;

        } catch (IOException e) {
            Toast.makeText(context, "Error generating PDF", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
            return null;
        }
    }
    private String getQuotationDescription() {
        return "Quotation for Comprehensive Pest Management Services\n\n"
                + "This quotation outlines the pest control services tailored to meet the specific needs of your premises. "
                + "Our offerings are designed to ensure a safe, pest-free environment through reliable, efficient, and environmentally friendly solutions. "
                + "The details below summarize the scope of work, service descriptions, and associated costs.\n\n"
                + "Scope of Services:\n"
                + "1. Initial Inspection: Thorough site inspection to identify existing pest infestations and potential entry points.\n"
                + "2. Treatment Plan: Implementation of appropriate treatments for pest elimination, including preventive measures.\n"
                + "3. Scheduled Maintenance: Regular follow-up visits for ongoing monitoring and maintenance of a pest-free environment.\n"
                + "4. Emergency Support: On-call service for urgent pest control needs.\n"
                + "   - 24-Hour Response Time: Guaranteed response time for internal pest activity.\n"
                + "   - 72-Hour Response Time: Guaranteed response time for external pest activity.\n"
                + "5. Detailed Reporting: Comprehensive documentation of each service visit, including treatments applied and recommendations for further action.\n\n"
                + "8pt Contract:\n"
                + "This quotation also includes a 8pt service contract that ensures a structured and consistent approach to pest control management. The 8pt contract includes:\n"
                + "1. Initial assessment and customized treatment planning.\n"
                + "2. Installation of monitoring devices to detect pest activity.\n"
                + "3. Regular servicing and inspection of all pest control equipment.\n"
                + "4. Emergency response as per agreed response times.\n"
                + "5. Preventive measures to reduce future infestations.\n"
                + "6. Safe and eco-friendly pest control treatments.\n"
                + "7. Eight Visit a Year on site every 6 weeks.\n\n"
                + "Additional Benefits:\n"
                + "1. **Staff Discounts**: All pest control services are offered to your staff at discounted rates, ensuring comprehensive coverage for personal properties.\n"
                + "2. **Discounted Spray Treatments**: All-inclusive pest spray treatments are available at a reduced price, offering exceptional value while maintaining high service standards.\n\n"
                + "Why Choose Us:\n"
                + "- Licensed and certified pest control specialists.\n"
                + "- Use of environmentally friendly and safe pest management solutions.\n"
                + "- Flexible service schedules to minimize disruption to your daily operations.\n"
                + "- Guaranteed customer satisfaction with a commitment to excellence.\n\n"
                + "We appreciate the opportunity to serve you and are confident in our ability to deliver quality pest control solutions. "
                + "Should you have any questions or require further details, please do not hesitate to contact us.";
    }

}
