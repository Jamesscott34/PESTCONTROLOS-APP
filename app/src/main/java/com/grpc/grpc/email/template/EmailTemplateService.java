package com.grpc.grpc.email.template;

import android.content.Context;

import com.grpc.grpc.core.TenantBranding;
import com.grpc.grpc.email.model.EmailTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class EmailTemplateService {

    private EmailTemplateService() {
    }

    public static List<EmailTemplate> templates(Context context) {
        String companyName = TenantBranding.companyName(context);
        List<EmailTemplate> templates = new ArrayList<>();
        templates.add(new EmailTemplate(
                "service-report",
                "Service Report",
                "Service Report - {{customerName}}",
                "Dear {{customerName}},\n\n" +
                        "Please find attached your latest service report for {{address}}.\n\n" +
                        "Service Details:\n" +
                        "- Service Date: {{serviceDate}}\n" +
                        "- Technician: {{technicianName}}\n" +
                        "- Service Type: {{serviceType}}\n\n" +
                        "If you have any questions or concerns, please don't hesitate to contact us.\n\n" +
                        "Best regards,\n" +
                        companyName + " Team"
        ));
        templates.add(new EmailTemplate(
                "visit-reminder",
                "Visit Reminder",
                "Upcoming Visit Reminder - {{customerName}}",
                "Dear {{customerName}},\n\n" +
                        "This is a reminder that your next pest control visit is scheduled for {{visitDate}} at {{visitTime}}.\n\n" +
                        "Visit Details:\n" +
                        "- Address: {{address}}\n" +
                        "- Technician: {{technicianName}}\n" +
                        "- Service Type: {{serviceType}}\n\n" +
                        "Please ensure someone is available to provide access to the property.\n\n" +
                        "If you need to reschedule, please contact us as soon as possible.\n\n" +
                        "Best regards,\n" +
                        companyName + " Team"
        ));
        templates.add(new EmailTemplate(
                "requires-visit",
                "Requires Visit",
                "Service Visit Required - {{customerName}}",
                "Dear {{customerName}},\n\n" +
                        "Our records show that {{address}} requires a pest control visit.\n\n" +
                        "Last Visit: {{lastVisit}}\n" +
                        "Technician: {{technicianName}}\n\n" +
                        "Please contact us to arrange a suitable appointment.\n\n" +
                        "Best regards,\n" +
                        companyName + " Team"
        ));
        templates.add(new EmailTemplate(
                "invoice",
                "Invoice",
                "Invoice - {{customerName}}",
                "Dear {{customerName}},\n\n" +
                        "Please find attached your invoice for {{address}}.\n\n" +
                        "Invoice Details:\n" +
                        "- Invoice Number: {{invoiceNumber}}\n" +
                        "- Amount: {{invoiceAmount}}\n\n" +
                        "If you have any questions, please contact us.\n\n" +
                        "Best regards,\n" +
                        companyName + " Team"
        ));
        templates.add(new EmailTemplate(
                "quotation",
                "Quotations",
                "Quotation - {{customerName}}",
                "Dear {{customerName}},\n\n" +
                        "Please find attached our quotation for {{address}}.\n\n" +
                        "Quotation Details:\n" +
                        "- Date: {{quotationDate}}\n" +
                        "- Prepared by: {{technicianName}}\n\n" +
                        "If you would like to proceed or have any questions, please contact us.\n\n" +
                        "Best regards,\n" +
                        companyName + " Team"
        ));
        templates.add(new EmailTemplate("custom", "Custom", "", ""));
        return templates;
    }

    public static EmailTemplate process(EmailTemplate template, Map<String, String> variables) {
        String subject = template.subject;
        String body = template.body;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            String value = entry.getValue() != null ? entry.getValue() : "";
            subject = subject.replace(placeholder, value);
            body = body.replace(placeholder, value);
        }
        return new EmailTemplate(template.id, template.name, subject, body);
    }
}
