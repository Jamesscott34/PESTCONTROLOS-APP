# Branding audit (pre-flavors)

This file records **where GRPC branding strings were hardcoded** before the multi-tenant refactor.

## Required phrase scan (source only)

### "Good Riddance Pest Control Report"

- `app/src/main/java/com/grpc/grpc/PDFReportGenerator.java` (line ~155)
- `app/src/main/java/com/grpc/grpc/CallOutActivity.java` (line ~240)
- `app/src/main/java/com/grpc/grpc/ReportViewActivity.java` (line ~719)
- `app/src/main/java/com/grpc/grpc/RodentInitialActivity.java` (line ~217)
- `app/src/main/java/com/grpc/grpc/RodentRoutineActivity.java` (line ~219)
- `app/src/main/java/com/grpc/grpc/RodentCallOutActivity.java` (line ~223)
- `app/src/main/java/com/grpc/grpc/RodentJobActivity.java` (line ~223)
- `app/src/main/java/com/grpc/grpc/RodentActivityRoutine.java` (line ~217)
- `app/src/main/java/com/grpc/grpc/RodentActivityExternalRoutine.java` (line ~217)
- `app/src/main/java/com/grpc/grpc/RodentCallOutExternalActivity.java` (line ~222)
- `app/src/main/java/com/grpc/grpc/RodentCallOutExternalActivity.java` (line ~222)

### "Good Riddance Pest Control"

Examples (non-exhaustive; see repo search for full list):

- `app/src/main/java/com/grpc/grpc/ActionFormPdfGenerator.java` (title)
- `app/src/main/java/com/grpc/grpc/PDFQuotationReportGenerator.java` (header)
- `app/src/main/java/com/grpc/grpc/BirdQuotationPDFGenerator.java` (header)
- `app/src/main/java/com/grpc/grpc/General4ptActivity.java` / `General6ptActivity.java` / `General8ptActivity.java` / `General12ptActivity.java` (header)
- `app/src/main/java/com/grpc/grpc/GeneralQuotationPDF.java` (header)
- `app/src/main/java/com/grpc/grpc/PDFReportGenerator.java` (footer)
- `app/src/main/java/com/grpc/grpc/ReportViewActivity.java` (footer)
- `app/src/main/java/com/grpc/grpc/FollowUpActivity.java` (follow-up title + website line)
- `app/src/main/java/com/grpc/grpc/ContractListPDFGenerator.java` / `BehindsListPDFGenerator.java` / `DueListPDFGenerator.java` (auto-generated note)
- `app/src/main/java/com/grpc/grpc/ToxicERAPDFGenerator.java` / `NonToxERAPDFGenerator.java` (company copy)
- `app/src/main/res/layout/activity_pdf_template_settings.xml` (example hint text)

### "Created by reporting system"

- `app/src/main/java/com/grpc/grpc/PDFReportGeneratorWithTemplate.java` (default footer fallback, line ~270)
- `app/src/main/res/layout/activity_pdf_template_settings.xml` (footer hint)
- `README.md` (documentation only)

### "GRPC" (branding, not just prefs keys)

- `app/src/main/AndroidManifest.xml` (application `android:label="GRPC"`)
- Various source files contain `"GRPC"` in author tags and SharedPreferences name `"GRPC"` (prefs key; not branding)

### "GRPest"

- `app/src/main/AndroidManifest.xml` (comment header)
- `app/src/main/java/com/grpc/grpc/AppConfig.java` (`APP_NAME`, notification channel)
- Various Javadoc/comments and build scripts/templates

## Preservation intent

- **GRPC production behavior must remain identical** after refactor.
- All changes to branding text should be **source-of-text only** (resource lookups), not PDF layout changes.

