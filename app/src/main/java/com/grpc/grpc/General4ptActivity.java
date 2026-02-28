package com.grpc.grpc;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.pdf.EncryptionConstants;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.WriterProperties;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
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

/**
 * General4ptActivity.java
 *
 * This activity generates a comprehensive pest management quotation report.
 * Users can input company details, and a structured quote is created based on predefined services.
 * The generated quotation includes pest control services, monitoring plans, pricing, and contract details.
 * The report is saved as a PDF and includes a professional format with a company logo and payment structure.
 *
 * Features:
 * - Allows users to enter company details for the quotation
 * - Generates a structured PDF report with pre-defined services
 * - Automatically assigns quarterly payments and VAT calculations
 * - Saves the generated quote and allows navigation back to the main quote activity
 *
 * Author: GRPC
 */

public class General4ptActivity extends AppCompatActivity {

    private static final String PREF_KEY_ANNUAL_FEE = "CONTRACT_QUOTE_ANNUAL_FEE_4PT";
    private static final double DEFAULT_ANNUAL_FEE = 500.0;

    private String userName;
    private String userEmail;
    private String userMobile;
    private String staffDisplayName;
    private String staffTitle;

    private EditText companyNameInput;
    private EditText companyAddressInput;
    private EditText companyContactInput;
    private EditText annualServiceFeeInput;
    private CheckBox passwordProtectCheckbox;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_general4pt);

        // Retrieve the username
        userName = getIntent().getStringExtra("USER_NAME");

        // Default from saved login (no hardcoded emails)
        userEmail = getSharedPreferences("GRPC", MODE_PRIVATE).getString("USER_EMAIL", "");
        userMobile = "";
        staffDisplayName = userName;
        staffTitle = "";

        // Fetch Email/Name/Title from centralized session (users/{StaffID})
        SessionManager.ensureLoaded(this, session -> {
            String email = SessionManager.getEmail(this);
            String mobile = SessionManager.getNumber(this);
            String name = SessionManager.getName(this);
            String title = SessionManager.getTitle(this);
            if (email != null && !email.isEmpty()) userEmail = email;
            if (mobile != null && !mobile.isEmpty()) userMobile = mobile;
            if (name != null && !name.isEmpty()) staffDisplayName = name;
            if (title != null && !title.isEmpty()) staffTitle = title;
        });

        // Display welcome message
        TextView welcomeTextView = findViewById(R.id.welcomeTextView);
        if (welcomeTextView != null) {
            welcomeTextView.setText("Welcome, " + userName + "!");
        }
        SessionManager.ensureLoaded(this, session -> runOnUiThread(() -> {
            if (welcomeTextView == null) return;
            String name = SessionManager.getName(this);
            if (name != null && !name.trim().isEmpty()) {
                welcomeTextView.setText("Welcome, " + name.trim() + "!");
            }
        }));

        // Input fields for company details
        companyNameInput = findViewById(R.id.companyNameInput);
        companyAddressInput = findViewById(R.id.companyAddressInput);
        companyContactInput = findViewById(R.id.companyContactInput);
        annualServiceFeeInput = findViewById(R.id.annualServiceFeeInput);
        passwordProtectCheckbox = findViewById(R.id.passwordProtectCheckbox);

        // Allow a custom price (persisted per device)
        if (annualServiceFeeInput != null) {
            String saved = getSharedPreferences("GRPC", MODE_PRIVATE)
                    .getString(PREF_KEY_ANNUAL_FEE, String.valueOf(DEFAULT_ANNUAL_FEE));
            annualServiceFeeInput.setText(saved);
        }

        // Generate PDF button
        Button generatePdfButton = findViewById(R.id.generatePdfButton);
        generatePdfButton.setOnClickListener(v -> {
            if (passwordProtectCheckbox != null && passwordProtectCheckbox.isChecked()) {
                PdfPasswordPrompt.prompt(this, pw -> generateQuote(pw));
            } else {
                generateQuote(null);
            }
        });
    }

    private void generateQuote(String ownerPassword) {
        if (userEmail == null || userEmail.trim().isEmpty()) {
            Toast.makeText(this, "Loading your profile email from Firebase. Please try again in a moment.", Toast.LENGTH_SHORT).show();
            return;
        }
        String companyName = companyNameInput.getText().toString().trim();
        String companyAddress = companyAddressInput.getText().toString().trim();
        String companyContact = companyContactInput.getText().toString().trim();
        String annualFeeStr = annualServiceFeeInput != null ? annualServiceFeeInput.getText().toString().trim() : "";

        if (companyName.isEmpty() || companyAddress.isEmpty() || companyContact.isEmpty()) {
            Toast.makeText(this, "Please fill in all company details", Toast.LENGTH_SHORT).show();
            return;
        }

        double annualFee = DEFAULT_ANNUAL_FEE;
        if (!annualFeeStr.isEmpty()) {
            try {
                annualFee = Double.parseDouble(annualFeeStr);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid annual service fee", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        getSharedPreferences("GRPC", MODE_PRIVATE).edit()
                .putString(PREF_KEY_ANNUAL_FEE, String.valueOf(annualFee))
                .apply();

        // Generate a random 4-digit quote number
        String quoteNumber = String.format("%04d", new Random().nextInt(10000));

        // Descriptions and prices for the quote
        List<String> descriptions = new ArrayList<>();
        List<Double> lineTotals = new ArrayList<>();

        // Manually entered descriptions
        descriptions.add("Annual Service Fee for Pest Management Solutions:\n" +
                "This fee covers a comprehensive yearly pest control program tailored to your site’s unique requirements. " +
                "Our service includes regular inspections, proactive treatments, and ongoing monitoring to ensure a pest-free environment. " +
                "We utilize advanced methods and eco-friendly solutions to deliver effective and sustainable pest management results.");
        lineTotals.add(annualFee);

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
                "This service includes two complimentary callouts per year during working hours  for pest control maintenance, adjustments, or inspections as needed. " +
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
                staffDisplayName,                // Staff name (from Firebase)
                staffTitle,                      // Staff title (from Firebase)
                companyName,                     // Company name
                companyContact,                  // Company contact
                General4ptActivity.this,         // Explicitly pass the activity as Context
                ownerPassword
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
        Intent intent = new Intent(General4ptActivity.this, QuotesActivity.class);
        intent.putExtra("USER_NAME", userName);
        startActivity(intent);
        finish();
    }

    public static File generateQuotationReport(
            String fileName, String address, String quoteDescription,
            List<String> descriptions, List<Double> lineTotals,
            String userEmail, String mobileNumber,
            String staffName, String staffTitle,
            String companyName, String companyContact, Context context) {
        return generateQuotationReport(fileName, address, quoteDescription, descriptions, lineTotals, userEmail, mobileNumber, staffName, staffTitle, companyName, companyContact, context, null);
    }

    public static File generateQuotationReport(
            String fileName, String address, String quoteDescription,
            List<String> descriptions, List<Double> lineTotals,
            String userEmail, String mobileNumber,
            String staffName, String staffTitle,
            String companyName, String companyContact, Context context,
            String ownerPassword) {

        File quotesFolder = new File(context.getExternalFilesDir(null), "GRPEST_QUOTES");

        if (!quotesFolder.exists() && !quotesFolder.mkdirs()) {
            Toast.makeText(context, "Error creating quotes folder", Toast.LENGTH_SHORT).show();
            return null;
        }

        File pdfFile = new File(quotesFolder, fileName + ".pdf");

        WriterProperties writerProperties = new WriterProperties();
        writerProperties.setFullCompressionMode(true);
        if (ownerPassword != null && !ownerPassword.trim().isEmpty()) {
            writerProperties.setStandardEncryption(
                    null,
                    ownerPassword.trim().getBytes(),
                    EncryptionConstants.ALLOW_PRINTING | EncryptionConstants.ALLOW_COPY,
                    EncryptionConstants.ENCRYPTION_AES_128
            );
        }

        try (PdfWriter writer = new PdfWriter(new FileOutputStream(pdfFile), writerProperties);
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
            leftCell.add(new Paragraph("\n" + TenantBranding.companyName(context)).setBold().setFontSize(16));
            leftCell.add(new Paragraph("Name: " + (staffName != null && !staffName.isEmpty() ? staffName : "")).setFontSize(12));
            if (staffTitle != null && !staffTitle.isEmpty()) {
                leftCell.add(new Paragraph("Title: " + staffTitle).setFontSize(12));
            }
            leftCell.add(new Paragraph("Mobile: " + mobileNumber).setFontSize(12));
            leftCell.add(new Paragraph("Email: " + userEmail).setFontSize(12));
            leftCell.add(new Paragraph("Website: " + TenantBranding.companyWebsiteShort(context)).setFontSize(12));
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
            document.add(new Paragraph("\nPayment Summary").setFontSize(16).setBold().setUnderline());
            document.add(new Paragraph("Quarterly Payments: €" + String.format("%.2f", firstQuarterPayment)).setFontSize(14));
            document.add(new Paragraph("First Quarter Payment Due: €" + String.format("%.2f", firstQuarterPayment)).setFontSize(16).setBold());

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
                + "4pt Contract:\n"
                + "This quotation also includes a 4pt service contract that ensures a structured and consistent approach to pest control management. The 4pt contract includes:\n"
                + "1. Initial assessment and customized treatment planning.\n"
                + "2. Installation of monitoring devices to detect pest activity.\n"
                + "3. Regular servicing and inspection of all pest control equipment.\n"
                + "4. Emergency response as per agreed response times.\n"
                + "5. Preventive measures to reduce future infestations.\n"
                + "6. Safe and eco-friendly pest control treatments.\n"
                + "7. Four Visit a Year on site every 12 weeks.\n\n"
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
