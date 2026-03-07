# GRPC Android App — Complete Feature-Based Reorganisation Plan

**Status:** Stage 1 (core) **COMPLETED**. Remaining stages are planning only until approved.  
**Product flavors:** grpc, demo, offline (preserved; only `app/src/main` and shared resources are reorganised).  
**Approval required:** Implementation will start only after your explicit approval.

---

## PART 1 — FULL FEATURE INVENTORY

Scanned: all Java in `app/src/main/java/com/grpc/grpc/`, all layouts in `app/src/main/res/layout/`, `AndroidManifest.xml`, and build configuration.

### 1. LOGIN (standalone)
- **Activities:** `LoginActivity`
- **Helpers/Models:** Uses `SessionManager`, `ActiveUserContext`, `StaffDirectory`, `WorkViewLocalEventStore`, `WorkViewWidgetHelper`, `LocationSharing`, `BrandingAssets` (all shared). BuildConfig.IS_OFFLINE for offline flavor.
- **Workers/Services:** None in-feature.
- **Layouts:** `activity_login.xml`
- **Notes:** Entry point; clears session and redirects to MainActivity (or offline flow). Standalone feature.

### 2. MAIN (standalone)
- **Activities:** `MainActivity`
- **Helpers/Models:** Uses SessionManager, ActiveUserContext, StaffDirectory, DemoFirebaseExpiryHelper, LocationSharing, WorkViewPopupReminderScheduler, DailyContractPdfHelper, OfflineTrialHelper, BrandingAssets, Firebase (Messaging/Firestore). No in-feature models.
- **Workers/Services:** None in-feature (uses shared workers).
- **Layouts:** `activity_main.xml` (plus `layout-sw600dp/activity_main.xml`)
- **Notes:** Hub; navigates to all other features. Standalone.

### 3. CONTRACTS
- **Activities:** `ContractsActivity`, `ViewContractActivity`, `AddContractActivity`, `ContractReportsActivity`, `BehindsListViewActivity`
- **Adapters:** `ContractSelectionAdapter`
- **Models:** None (uses Firestore documents / in-memory data).
- **PDF/Helpers:** `ContractListPDFGenerator`, `BehindsListPDFGenerator`, `DailyContractPdfHelper` (also used by WorkView for daily PDF scheduling — **shared**).
- **Layouts:** `activity_contract_selection.xml`, `activity_view_contract.xml`, `activity_add_contract.xml`, `activity_contract_reports.xml`, `activity_behinds_list_view.xml`; `activity_report_viewer.xml` (shared with reports/quotations), `dialog_contract_selection.xml` (used by WorkView and contracts).
- **Notes:** Contract list, view/add contract, contract reports, behinds list. `DailyContractPdfHelper` used by main/workview → classify as **shared**.

### 4. REPORTS
- **Activities:** `ReportActivity`, `ReportViewActivity`, `ReportSelectionActivity`, `CreateReportActivity`, `PDFSelectionActivity`, `ActionFormActivity`, `FollowUpActivity`, `StoredReportsActivity`, `ReportViewActivity` (viewer)
- **Adapters:** `ReportAdapter`
- **Models:** None (SQLite / Firestore / file paths).
- **Helpers/DB:** `ReportDatabaseHelper`, `PdfTemplateStorage`, `PdfTemplateSettings`, `SavedTemplate`, `PdfTemplateSettingsActivity`, `ViewTemplatesActivity`
- **PDF generators:** `PDFReportGenerator`, `PDFReportGeneratorWithTemplate`, `ActionFormPdfGenerator`
- **Layouts:** `activity_report.xml`, `activity_report_viewer.xml`, `activity_report_selection.xml`, `activity_quotation_report.xml` (CreateReport), `activity_action_form.xml`, `activity_follow_up.xml`, `activity_stored_reports.xml`, `activity_pdf_selection.xml`, `activity_pdf_template_settings.xml`, `activity_view_templates.xml`, `item_report_file.xml`, `dialog_search_with_list.xml` (shared with contract reports / stored reports)
- **Notes:** Core report creation, selection, viewing, action form, follow-up, stored reports, PDF/template settings. Report viewer shared with quotations/ERA/behinds.

### 5. QUOTATIONS
- **Activities:** `QuotesActivity`, `QuotationViewActivity`, `BirdQuotationActivity`, `GeneralQuotationActivity`, `GeneralQuotationFromCatalogActivity`
- **Adapters:** `BirdMaterialsAdapter`
- **Models:** `BirdMaterialItem`, `SalesCatalog`, `GeneralQuotationPDF`
- **Helpers/Loaders:** `SalesCatalogLoader` (flavor-specific sales.json)
- **PDF generators:** `BirdQuotationPDFGenerator`, `PDFQuotationReportGenerator`
- **Layouts:** `activity_general_quote.xml`, `activity_quotation_viewer.xml` (same as report_viewer), `activity_bird_quotation.xml`, `activity_general_quotation.xml`, `activity_general_quotation_from_catalog.xml`, `item_bird_material.xml`
- **Notes:** Quotation list, viewer, bird quote, general quote, catalog-based quote. Uses shared `activity_report_viewer.xml`.

### 6. JOBS
- **Activities:** `JobsActivity`, `AddJobsActivity`, `ViewJobActivity`, `AddManagmentJobsActivity`, `ViewManagmentJobActivity`, `AddJobFromCalendarActivity`
- **Adapters:** None (spinners/lists use ArrayAdapter or in-activity logic).
- **Models:** Job data from Firestore.
- **Layouts:** `activity_jobs_selection.xml`, `activity_add_jobs.xml`, `activity_view_jobs.xml`, `activity_add_managment_jobs.xml`, `activity_view_managment_jobs.xml`, `activity_add_job_from_calendar.xml`
- **Notes:** Job list, add/view job, management jobs, add job from calendar.

### 7. WORKVIEW
- **Activities:** `WorkViewActivity`
- **Adapters:** `WorkEventAdapter`
- **Models:** `WorkEvent`
- **Helpers/Storage:** `WorkViewLocalEventStore`, `WorkViewWidgetHelper`, `WorkViewPopupReminderScheduler`
- **Workers:** `WorkViewPopupReminderWorker`
- **Widget:** `WorkViewWidgetProvider`
- **Layouts:** `activity_work_view.xml`, `widget_workview.xml`, `item_work_event.xml`; uses `dialog_contract_selection.xml`
- **Notes:** Calendar/work view, widget, reminders. Depends on contracts (dialog), reports, add follow-up, add job from calendar.

### 8. MESSAGING / NOTIFICATIONS
- **Activities:** `MessagingConversationsActivity`, `MessagingActivity`, `NotificationsActivity`, `ChatActivity`
- **Adapters:** `ConversationListAdapter`, `MessageAdapter`
- **Models:** `ChatMessage` (messaging/chat)
- **Service:** `FirebaseMessagingServiceGRPC`
- **Helpers:** `NotificationUtils`
- **Layouts:** `activity_messaging_conversations.xml`, `activity_messaging.xml`, `activity_notifications.xml`, `activity_chat.xml`, `item_conversation.xml`, `message_item.xml`, `item_message.xml` (ChatActivity uses message_item; item_message exists — verify which is used)
- **Notes:** Conversation list, per-conversation chat, notification history, AI chat. Messaging is super_admin only; notifications for all.

### 9. LEADS / COMMISSIONS
- **Activities:** `LeadsSelectionActivity`, `ViewLeadsActivity`, `GenerateLeadsActivity`
- **Adapters:** None (spinners/lists in activities).
- **Layouts:** `activity_leads_selection.xml`, `activity_view_leads.xml`, `activity_add_leads.xml`
- **Notes:** Lead selection, view leads, generate leads. Role-gated (commission access).

### 10. SERVICE AGREEMENTS
- **Activities:** `ServiceAgreementActivity`, `ServiceAgreementViewActivity`
- **PDF:** `ServiceAgreementGenerator`
- **Layouts:** `activity_service_agreement.xml`, `activity_service_agreements_view.xml`
- **Notes:** Create and view service agreements; uses shared `PdfPasswordPrompt`.

### 11. SEARCH
- **Activities:** `SearchActivity`
- **Adapters:** `GlobalSearchAdapter`, `SearchResultsAdapter` (SearchResultsAdapter uses system layout; may be legacy or alternate). Internal model: `GlobalSearchItem` / `GlobalSearchKind` (in GlobalSearchAdapter).
- **Layouts:** `activity_search.xml`, `item_search_header.xml`, `item_search_result.xml`
- **Notes:** Global search (admin-only). Searches jobs, contracts, leads, reports.

### 12. LOCATION
- **Activities:** `LocationFinderActivity`
- **Helpers:** `LocationSharing`
- **Workers:** `LastLocationUpdateWorker`, `LastLocationCleanupWorker`
- **Layouts:** `activity_location_finder.xml`
- **Notes:** Admin-only location finder; technicians publish location via workers.

### 13. FILES / FOLDERS / HELP / TEMPLATES
- **Activities:** `FolderContentsActivity`, `StoredReportsActivity` (also under reports), `HelpReadmeActivity`, `PdfTemplateSettingsActivity`, `ViewTemplatesActivity`
- **Adapters:** `FolderAdapter`, `FileAdapter`
- **Helpers/Models:** `PdfTemplateStorage`, `PdfTemplateSettings`, `SavedTemplate` (overlap with reports)
- **Layouts:** `activity_folder_contents.xml`, `activity_help_readme.xml`, `folder_item.xml`, `file_item.xml` (plus report/template layouts already listed under reports)
- **Notes:** Folder contents, stored reports (reports feature), help README, PDF template settings/templates. `StoredReportsActivity`/template files can stay with reports; folder/help as “files/help”.

### 14. ERA (ENVIRONMENTAL RISK ASSESSMENT)
- **Activities:** `EnvironmentSelectionActivity`, `NonToxERAActivity`, `ToxicERAActivity`, `ERAViewActivity`
- **PDF generators:** `ToxicERAPDFGenerator`, `NonToxERAPDFGenerator`
- **Layouts:** `activity_era.xml`, `activity_nontox_era.xml` (used by both Toxic and NonTox), `activity_era_viewer.xml`; `activity_toxic_era.xml` exists but **not referenced** in code (Toxic uses activity_nontox_era) — **unclear / manual decision**
- **Notes:** ERA selection, non-tox/tox entry, ERA viewer. Shared layout for both ERA types.

### 15. GENERAL REPORTS (POINT SIZES)
- **Activities:** `GeneralReportActivity`, `General4ptActivity`, `General6ptActivity`, `General8ptActivity`, `General12ptActivity`
- **Layouts:** `activity_general_report_creator.xml`, `activity_general4pt.xml`, `activity_general6pt.xml`, `activity_general8pt.xml`, `activity_general12pt.xml`
- **Notes:** General report and 4/6/8/12pt report types. Use shared `PdfPasswordPrompt`, `HorizontalSwipeGuard`.

### 16. RODENT (JOBS SUBTYPE)
- **Activities:** `RodentRoutineActivity`, `RodentActivityRoutine`, `RodentActivityExternalRoutine`, `RodentCallOutActivity`, `RodentCallOutExternalActivity`, `RodentInitialActivity`, `RodentJobActivity`, `CallOutActivity`
- **Layouts:** No dedicated layouts found; likely reuse report/generic layouts or programmatic UI.
- **Notes:** Rodent-specific flows (routine, call-out, initial, job). Could sit under **jobs** or as a separate **rodent** feature; recommended as **jobs/rodent** sub-feature for clarity.

### 17. ADMIN / DASHBOARD
- **Activities:** `AdminDashboardActivity`, `EmployeeManagementActivity`
- **Layouts:** `activity_admin_dashboard.xml`, `activity_employee_management.xml`, `item_simple_card.xml` (shared with employee management)
- **Notes:** Admin dashboard and employee management (super_admin). Could be grouped as **main** or **admin**; treated as separate **admin** feature.

### 18. SHARED / CORE / UTILITIES
- **Application:** `GrpcApplication`
- **Session / identity / RBAC:** `SessionManager`, `ActiveUserContext`, `UserRepository`
- **Firebase / backend:** `FirebaseHelper`, `FirestorePaths`
- **Staff / branding:** `StaffDirectory`, `TenantBranding`, `BrandingAssets`
- **Config / flavor:** `AppConfig`, `OfflineTrialHelper`, `DemoFirebaseExpiryHelper`
- **UI utilities:** `DictateEditText`, `HorizontalSwipeGuard`, `PdfPasswordPrompt`, `SignatureCaptureActivity`
- **PDF (shared):** `DailyContractPdfHelper` (contracts + workview/main)
- **Layouts:** `edittext_with_dictate.xml` (DictateEditText), `activity_signature_capture.xml`
- **Notes:** Used across multiple features. Keep in `shared` or `core` package. `SignatureCaptureActivity` used by ERA (and potentially service agreement via layout); treat as shared.

### Unclear / manual decision
- **activity_toxic_era.xml:** Present in res/layout but not referenced; ToxicERAActivity uses `activity_nontox_era.xml`. Decide: remove, or wire to Toxic if needed.
- **SearchResultsAdapter:** Uses `android.R.layout.simple_list_item_1`; `GlobalSearchAdapter` uses `item_search_header.xml` and `item_search_result.xml`. Confirm if SearchResultsAdapter is still used or can be removed.
- **item_message.xml vs message_item.xml:** Both exist; MessageAdapter uses `message_item`. Verify `item_message` usage and consolidate if redundant.
- **StoredReportsActivity / template activities:** Listed under both Reports and Files; recommend keeping with Reports and referencing from “files/help” only for FolderContentsActivity and HelpReadmeActivity.
- **Rodent:** Keep as sub-feature under jobs or top-level “rodent”; recommended under **jobs** (e.g. `jobs/rodent`).

---

## PART 2 — PROPOSED FINAL TREE STRUCTURE

### Java package structure (feature-based)

```
com.grpc.grpc
├── login
│   └── LoginActivity.java
├── main
│   └── MainActivity.java
├── contracts
│   ├── ui
│   │   ├── ContractsActivity.java
│   │   ├── ViewContractActivity.java
│   │   ├── AddContractActivity.java
│   │   ├── ContractReportsActivity.java
│   │   ├── BehindsListViewActivity.java
│   │   └── ContractSelectionAdapter.java
│   ├── data
│   │   └── (none; Firestore-driven)
│   └── pdf
│       ├── ContractListPDFGenerator.java
│       └── BehindsListPDFGenerator.java
├── reports
│   ├── ui
│   │   ├── ReportActivity.java
│   │   ├── ReportViewActivity.java
│   │   ├── ReportSelectionActivity.java
│   │   ├── CreateReportActivity.java
│   │   ├── PDFSelectionActivity.java
│   │   ├── ActionFormActivity.java
│   │   ├── FollowUpActivity.java
│   │   ├── StoredReportsActivity.java
│   │   ├── PdfTemplateSettingsActivity.java
│   │   ├── ViewTemplatesActivity.java
│   │   └── ReportAdapter.java
│   ├── data
│   │   ├── ReportDatabaseHelper.java
│   │   ├── PdfTemplateStorage.java
│   │   ├── PdfTemplateSettings.java
│   │   └── SavedTemplate.java
│   └── pdf
│       ├── PDFReportGenerator.java
│       ├── PDFReportGeneratorWithTemplate.java
│       └── ActionFormPdfGenerator.java
├── quotations
│   ├── ui
│   │   ├── QuotesActivity.java
│   │   ├── QuotationViewActivity.java
│   │   ├── BirdQuotationActivity.java
│   │   ├── GeneralQuotationActivity.java
│   │   ├── GeneralQuotationFromCatalogActivity.java
│   │   └── BirdMaterialsAdapter.java
│   ├── model
│   │   ├── BirdMaterialItem.java
│   │   ├── SalesCatalog.java
│   │   └── GeneralQuotationPDF.java
│   ├── data
│   │   └── SalesCatalogLoader.java
│   └── pdf
│       ├── BirdQuotationPDFGenerator.java
│       └── PDFQuotationReportGenerator.java
├── jobs
│   ├── ui
│   │   ├── JobsActivity.java
│   │   ├── AddJobsActivity.java
│   │   ├── ViewJobActivity.java
│   │   ├── AddManagmentJobsActivity.java
│   │   ├── ViewManagmentJobActivity.java
│   │   └── AddJobFromCalendarActivity.java
│   └── rodent
│       ├── RodentRoutineActivity.java
│       ├── RodentActivityRoutine.java
│       ├── RodentActivityExternalRoutine.java
│       ├── RodentCallOutActivity.java
│       ├── RodentCallOutExternalActivity.java
│       ├── RodentInitialActivity.java
│       ├── RodentJobActivity.java
│       └── CallOutActivity.java
├── workview
│   ├── ui
│   │   ├── WorkViewActivity.java
│   │   ├── WorkViewWidgetProvider.java
│   │   └── WorkEventAdapter.java
│   ├── model
│   │   └── WorkEvent.java
│   ├── data
│   │   ├── WorkViewLocalEventStore.java
│   │   ├── WorkViewWidgetHelper.java
│   │   └── WorkViewPopupReminderScheduler.java
│   └── worker
│       └── WorkViewPopupReminderWorker.java
├── messaging
│   ├── ui
│   │   ├── MessagingConversationsActivity.java
│   │   ├── MessagingActivity.java
│   │   ├── NotificationsActivity.java
│   │   ├── ChatActivity.java
│   │   ├── ConversationListAdapter.java
│   │   └── MessageAdapter.java
│   ├── model
│   │   └── ChatMessage.java
│   ├── service
│   │   └── FirebaseMessagingServiceGRPC.java
│   └── NotificationUtils.java
├── leads
│   └── ui
│       ├── LeadsSelectionActivity.java
│       ├── ViewLeadsActivity.java
│       └── GenerateLeadsActivity.java
├── serviceagreements
│   ├── ui
│   │   ├── ServiceAgreementActivity.java
│   │   └── ServiceAgreementViewActivity.java
│   └── pdf
│       └── ServiceAgreementGenerator.java
├── search
│   ├── ui
│   │   ├── SearchActivity.java
│   │   ├── GlobalSearchAdapter.java
│   │   └── SearchResultsAdapter.java
│   └── (GlobalSearchItem/GlobalSearchKind in adapter or model)
├── location
│   ├── ui
│   │   └── LocationFinderActivity.java
│   ├── LocationSharing.java
│   └── worker
│       ├── LastLocationUpdateWorker.java
│       └── LastLocationCleanupWorker.java
├── files
│   ├── ui
│   │   ├── FolderContentsActivity.java
│   │   ├── HelpReadmeActivity.java
│   │   ├── FolderAdapter.java
│   │   └── FileAdapter.java
│   └── (StoredReportsActivity/templates in reports)
├── era
│   ├── ui
│   │   ├── EnvironmentSelectionActivity.java
│   │   ├── NonToxERAActivity.java
│   │   ├── ToxicERAActivity.java
│   │   ├── ERAViewActivity.java
│   │   └── SignatureCaptureActivity.java  (optional: move to shared if preferred)
│   └── pdf
│       ├── ToxicERAPDFGenerator.java
│       └── NonToxERAPDFGenerator.java
├── generalreports
│   └── ui
│       ├── GeneralReportActivity.java
│       ├── General4ptActivity.java
│       ├── General6ptActivity.java
│       ├── General8ptActivity.java
│       └── General12ptActivity.java
├── admin
│   └── ui
│       ├── AdminDashboardActivity.java
│       └── EmployeeManagementActivity.java
└── core
    ├── GrpcApplication.java
    ├── SessionManager.java
    ├── ActiveUserContext.java
    ├── UserRepository.java
    ├── FirebaseHelper.java
    ├── FirestorePaths.java
    ├── StaffDirectory.java
    ├── TenantBranding.java
    ├── BrandingAssets.java
    ├── AppConfig.java
    ├── OfflineTrialHelper.java
    ├── DemoFirebaseExpiryHelper.java
    ├── DailyContractPdfHelper.java
    ├── DictateEditText.java
    ├── HorizontalSwipeGuard.java
    ├── PdfPasswordPrompt.java
    └── SignatureCaptureActivity.java  (if not in era)
```

**Note:** `SignatureCaptureActivity` can live in `era.ui` or in `core`; plan keeps it in `core` because it is reusable (ERA + possible future use). Same for `DailyContractPdfHelper` in `core`.

### XML layout structure (proposed)

Layouts stay under `res/layout/`; optional grouping by prefix (no physical folders in res). Suggested naming for clarity (rename only if you approve):

```
res/layout/
├── activity_login.xml
├── activity_main.xml
├── activity_contract_selection.xml
├── activity_view_contract.xml
├── activity_add_contract.xml
├── activity_contract_reports.xml
├── activity_behinds_list_view.xml
├── dialog_contract_selection.xml
├── activity_report.xml
├── activity_report_viewer.xml
├── activity_report_selection.xml
├── activity_quotation_report.xml
├── activity_action_form.xml
├── activity_follow_up.xml
├── activity_stored_reports.xml
├── activity_pdf_selection.xml
├── activity_pdf_template_settings.xml
├── activity_view_templates.xml
├── item_report_file.xml
├── dialog_search_with_list.xml
├── activity_general_quote.xml
├── activity_quotation_viewer.xml
├── activity_bird_quotation.xml
├── activity_general_quotation.xml
├── activity_general_quotation_from_catalog.xml
├── item_bird_material.xml
├── activity_jobs_selection.xml
├── activity_add_jobs.xml
├── activity_view_jobs.xml
├── activity_add_managment_jobs.xml
├── activity_view_managment_jobs.xml
├── activity_add_job_from_calendar.xml
├── activity_work_view.xml
├── widget_workview.xml
├── item_work_event.xml
├── activity_messaging_conversations.xml
├── activity_messaging.xml
├── activity_notifications.xml
├── activity_chat.xml
├── item_conversation.xml
├── message_item.xml
├── item_message.xml
├── activity_leads_selection.xml
├── activity_view_leads.xml
├── activity_add_leads.xml
├── activity_service_agreement.xml
├── activity_service_agreements_view.xml
├── activity_search.xml
├── item_search_header.xml
├── item_search_result.xml
├── activity_location_finder.xml
├── activity_folder_contents.xml
├── activity_help_readme.xml
├── folder_item.xml
├── file_item.xml
├── activity_era.xml
├── activity_nontox_era.xml
├── activity_era_viewer.xml
├── activity_toxic_era.xml
├── activity_general_report_creator.xml
├── activity_general4pt.xml
├── activity_general6pt.xml
├── activity_general8pt.xml
├── activity_general12pt.xml
├── activity_admin_dashboard.xml
├── activity_employee_management.xml
├── item_simple_card.xml
├── activity_signature_capture.xml
├── edittext_with_dictate.xml
```

No deletion or move of layout files in this phase; optional renames only after approval. Flavor layouts (e.g. `layout-sw600dp`) remain as-is.

---

## PART 3 — FILE-BY-FILE MIGRATION MAP

Format: **Current path** → **Proposed package** — Reason.

### Login
- `LoginActivity.java` → `com.grpc.grpc.login.LoginActivity` — Entry point; login feature.

### Main
- `MainActivity.java` → `com.grpc.grpc.main.MainActivity` — Home hub; main feature.

### Contracts
- `ContractsActivity.java` → `com.grpc.grpc.contracts.ui.ContractsActivity`
- `ViewContractActivity.java` → `com.grpc.grpc.contracts.ui.ViewContractActivity`
- `AddContractActivity.java` → `com.grpc.grpc.contracts.ui.AddContractActivity`
- `ContractReportsActivity.java` → `com.grpc.grpc.contracts.ui.ContractReportsActivity`
- `BehindsListViewActivity.java` → `com.grpc.grpc.contracts.ui.BehindsListViewActivity`
- `ContractSelectionAdapter.java` → `com.grpc.grpc.contracts.ui.ContractSelectionAdapter`
- `ContractListPDFGenerator.java` → `com.grpc.grpc.contracts.pdf.ContractListPDFGenerator`
- `BehindsListPDFGenerator.java` → `com.grpc.grpc.contracts.pdf.BehindsListPDFGenerator`

### Reports
- `ReportActivity.java` → `com.grpc.grpc.reports.ui.ReportActivity`
- `ReportViewActivity.java` → `com.grpc.grpc.reports.ui.ReportViewActivity`
- `ReportSelectionActivity.java` → `com.grpc.grpc.reports.ui.ReportSelectionActivity`
- `CreateReportActivity.java` → `com.grpc.grpc.reports.ui.CreateReportActivity`
- `PDFSelectionActivity.java` → `com.grpc.grpc.reports.ui.PDFSelectionActivity`
- `ActionFormActivity.java` → `com.grpc.grpc.reports.ui.ActionFormActivity`
- `FollowUpActivity.java` → `com.grpc.grpc.reports.ui.FollowUpActivity`
- `StoredReportsActivity.java` → `com.grpc.grpc.reports.ui.StoredReportsActivity`
- `PdfTemplateSettingsActivity.java` → `com.grpc.grpc.reports.ui.PdfTemplateSettingsActivity`
- `ViewTemplatesActivity.java` → `com.grpc.grpc.reports.ui.ViewTemplatesActivity`
- `ReportAdapter.java` → `com.grpc.grpc.reports.ui.ReportAdapter`
- `ReportDatabaseHelper.java` → `com.grpc.grpc.reports.data.ReportDatabaseHelper`
- `PdfTemplateStorage.java` → `com.grpc.grpc.reports.data.PdfTemplateStorage`
- `PdfTemplateSettings.java` → `com.grpc.grpc.reports.data.PdfTemplateSettings`
- `SavedTemplate.java` → `com.grpc.grpc.reports.data.SavedTemplate`
- `PDFReportGenerator.java` → `com.grpc.grpc.reports.pdf.PDFReportGenerator`
- `PDFReportGeneratorWithTemplate.java` → `com.grpc.grpc.reports.pdf.PDFReportGeneratorWithTemplate`
- `ActionFormPdfGenerator.java` → `com.grpc.grpc.reports.pdf.ActionFormPdfGenerator`

### Quotations
- `QuotesActivity.java` → `com.grpc.grpc.quotations.ui.QuotesActivity`
- `QuotationViewActivity.java` → `com.grpc.grpc.quotations.ui.QuotationViewActivity`
- `BirdQuotationActivity.java` → `com.grpc.grpc.quotations.ui.BirdQuotationActivity`
- `GeneralQuotationActivity.java` → `com.grpc.grpc.quotations.ui.GeneralQuotationActivity`
- `GeneralQuotationFromCatalogActivity.java` → `com.grpc.grpc.quotations.ui.GeneralQuotationFromCatalogActivity`
- `BirdMaterialsAdapter.java` → `com.grpc.grpc.quotations.ui.BirdMaterialsAdapter`
- `BirdMaterialItem.java` → `com.grpc.grpc.quotations.model.BirdMaterialItem`
- `SalesCatalog.java` → `com.grpc.grpc.quotations.model.SalesCatalog`
- `GeneralQuotationPDF.java` → `com.grpc.grpc.quotations.model.GeneralQuotationPDF`
- `SalesCatalogLoader.java` → `com.grpc.grpc.quotations.data.SalesCatalogLoader`
- `BirdQuotationPDFGenerator.java` → `com.grpc.grpc.quotations.pdf.BirdQuotationPDFGenerator`
- `PDFQuotationReportGenerator.java` → `com.grpc.grpc.quotations.pdf.PDFQuotationReportGenerator`

### Jobs
- `JobsActivity.java` → `com.grpc.grpc.jobs.ui.JobsActivity`
- `AddJobsActivity.java` → `com.grpc.grpc.jobs.ui.AddJobsActivity`
- `ViewJobActivity.java` → `com.grpc.grpc.jobs.ui.ViewJobActivity`
- `AddManagmentJobsActivity.java` → `com.grpc.grpc.jobs.ui.AddManagmentJobsActivity`
- `ViewManagmentJobActivity.java` → `com.grpc.grpc.jobs.ui.ViewManagmentJobActivity`
- `AddJobFromCalendarActivity.java` → `com.grpc.grpc.jobs.ui.AddJobFromCalendarActivity`
- `RodentRoutineActivity.java` → `com.grpc.grpc.jobs.rodent.RodentRoutineActivity`
- `RodentActivityRoutine.java` → `com.grpc.grpc.jobs.rodent.RodentActivityRoutine`
- `RodentActivityExternalRoutine.java` → `com.grpc.grpc.jobs.rodent.RodentActivityExternalRoutine`
- `RodentCallOutActivity.java` → `com.grpc.grpc.jobs.rodent.RodentCallOutActivity`
- `RodentCallOutExternalActivity.java` → `com.grpc.grpc.jobs.rodent.RodentCallOutExternalActivity`
- `RodentInitialActivity.java` → `com.grpc.grpc.jobs.rodent.RodentInitialActivity`
- `RodentJobActivity.java` → `com.grpc.grpc.jobs.rodent.RodentJobActivity`
- `CallOutActivity.java` → `com.grpc.grpc.jobs.rodent.CallOutActivity`

### WorkView
- `WorkViewActivity.java` → `com.grpc.grpc.workview.ui.WorkViewActivity`
- `WorkViewWidgetProvider.java` → `com.grpc.grpc.workview.ui.WorkViewWidgetProvider`
- `WorkEventAdapter.java` → `com.grpc.grpc.workview.ui.WorkEventAdapter`
- `WorkEvent.java` → `com.grpc.grpc.workview.model.WorkEvent`
- `WorkViewLocalEventStore.java` → `com.grpc.grpc.workview.data.WorkViewLocalEventStore`
- `WorkViewWidgetHelper.java` → `com.grpc.grpc.workview.data.WorkViewWidgetHelper`
- `WorkViewPopupReminderScheduler.java` → `com.grpc.grpc.workview.data.WorkViewPopupReminderScheduler`
- `WorkViewPopupReminderWorker.java` → `com.grpc.grpc.workview.worker.WorkViewPopupReminderWorker`

### Messaging / Notifications
- `MessagingConversationsActivity.java` → `com.grpc.grpc.messaging.ui.MessagingConversationsActivity`
- `MessagingActivity.java` → `com.grpc.grpc.messaging.ui.MessagingActivity`
- `NotificationsActivity.java` → `com.grpc.grpc.messaging.ui.NotificationsActivity`
- `ChatActivity.java` → `com.grpc.grpc.messaging.ui.ChatActivity`
- `ConversationListAdapter.java` → `com.grpc.grpc.messaging.ui.ConversationListAdapter`
- `MessageAdapter.java` → `com.grpc.grpc.messaging.ui.MessageAdapter`
- `ChatMessage.java` → `com.grpc.grpc.messaging.model.ChatMessage`
- `FirebaseMessagingServiceGRPC.java` → `com.grpc.grpc.messaging.service.FirebaseMessagingServiceGRPC`
- `NotificationUtils.java` → `com.grpc.grpc.messaging.NotificationUtils`

### Leads
- `LeadsSelectionActivity.java` → `com.grpc.grpc.leads.ui.LeadsSelectionActivity`
- `ViewLeadsActivity.java` → `com.grpc.grpc.leads.ui.ViewLeadsActivity`
- `GenerateLeadsActivity.java` → `com.grpc.grpc.leads.ui.GenerateLeadsActivity`

### Service agreements
- `ServiceAgreementActivity.java` → `com.grpc.grpc.serviceagreements.ui.ServiceAgreementActivity`
- `ServiceAgreementViewActivity.java` → `com.grpc.grpc.serviceagreements.ui.ServiceAgreementViewActivity`
- `ServiceAgreementGenerator.java` → `com.grpc.grpc.serviceagreements.pdf.ServiceAgreementGenerator`

### Search
- `SearchActivity.java` → `com.grpc.grpc.search.ui.SearchActivity`
- `GlobalSearchAdapter.java` → `com.grpc.grpc.search.ui.GlobalSearchAdapter`
- `SearchResultsAdapter.java` → `com.grpc.grpc.search.ui.SearchResultsAdapter`

### Location
- `LocationFinderActivity.java` → `com.grpc.grpc.location.ui.LocationFinderActivity`
- `LocationSharing.java` → `com.grpc.grpc.location.LocationSharing`
- `LastLocationUpdateWorker.java` → `com.grpc.grpc.location.worker.LastLocationUpdateWorker`
- `LastLocationCleanupWorker.java` → `com.grpc.grpc.location.worker.LastLocationCleanupWorker`

### Files / Help
- `FolderContentsActivity.java` → `com.grpc.grpc.files.ui.FolderContentsActivity`
- `HelpReadmeActivity.java` → `com.grpc.grpc.files.ui.HelpReadmeActivity`
- `FolderAdapter.java` → `com.grpc.grpc.files.ui.FolderAdapter`
- `FileAdapter.java` → `com.grpc.grpc.files.ui.FileAdapter`

### ERA
- `EnvironmentSelectionActivity.java` → `com.grpc.grpc.era.ui.EnvironmentSelectionActivity`
- `NonToxERAActivity.java` → `com.grpc.grpc.era.ui.NonToxERAActivity`
- `ToxicERAActivity.java` → `com.grpc.grpc.era.ui.ToxicERAActivity`
- `ERAViewActivity.java` → `com.grpc.grpc.era.ui.ERAViewActivity`
- `ToxicERAPDFGenerator.java` → `com.grpc.grpc.era.pdf.ToxicERAPDFGenerator`
- `NonToxERAPDFGenerator.java` → `com.grpc.grpc.era.pdf.NonToxERAPDFGenerator`

### General reports
- `GeneralReportActivity.java` → `com.grpc.grpc.generalreports.ui.GeneralReportActivity`
- `General4ptActivity.java` → `com.grpc.grpc.generalreports.ui.General4ptActivity`
- `General6ptActivity.java` → `com.grpc.grpc.generalreports.ui.General6ptActivity`
- `General8ptActivity.java` → `com.grpc.grpc.generalreports.ui.General8ptActivity`
- `General12ptActivity.java` → `com.grpc.grpc.generalreports.ui.General12ptActivity`

### Admin
- `AdminDashboardActivity.java` → `com.grpc.grpc.admin.ui.AdminDashboardActivity`
- `EmployeeManagementActivity.java` → `com.grpc.grpc.admin.ui.EmployeeManagementActivity`

### Core / shared
- `GrpcApplication.java` → `com.grpc.grpc.core.GrpcApplication`
- `SessionManager.java` → `com.grpc.grpc.core.SessionManager`
- `ActiveUserContext.java` → `com.grpc.grpc.core.ActiveUserContext`
- `UserRepository.java` → `com.grpc.grpc.core.UserRepository`
- `FirebaseHelper.java` → `com.grpc.grpc.core.FirebaseHelper`
- `FirestorePaths.java` → `com.grpc.grpc.core.FirestorePaths`
- `StaffDirectory.java` → `com.grpc.grpc.core.StaffDirectory`
- `TenantBranding.java` → `com.grpc.grpc.core.TenantBranding`
- `BrandingAssets.java` → `com.grpc.grpc.core.BrandingAssets`
- `AppConfig.java` → `com.grpc.grpc.core.AppConfig`
- `OfflineTrialHelper.java` → `com.grpc.grpc.core.OfflineTrialHelper`
- `DemoFirebaseExpiryHelper.java` → `com.grpc.grpc.core.DemoFirebaseExpiryHelper`
- `DailyContractPdfHelper.java` → `com.grpc.grpc.core.DailyContractPdfHelper`
- `DictateEditText.java` → `com.grpc.grpc.core.DictateEditText`
- `HorizontalSwipeGuard.java` → `com.grpc.grpc.core.HorizontalSwipeGuard`
- `PdfPasswordPrompt.java` → `com.grpc.grpc.core.PdfPasswordPrompt`
- `SignatureCaptureActivity.java` → `com.grpc.grpc.core.SignatureCaptureActivity`

### Worker (shared, in-app reminder)
- `InAppReminderWorker.java` → `com.grpc.grpc.core.worker.InAppReminderWorker` (or keep in core if single worker)

**Manifest:** All `<activity android:name=".X">` and `<service>` must be updated to full class names (e.g. `com.grpc.grpc.login.LoginActivity`). `GrpcApplication` → `com.grpc.grpc.core.GrpcApplication`. Receiver `WorkViewWidgetProvider` → `com.grpc.grpc.workview.ui.WorkViewWidgetProvider`.

---

## PART 4 — STAGED MIGRATION PLAN

Each stage: move/rename files, update package declarations, fix all imports and manifest references, then build (all three flavors: grpc, demo, offline).

### Stage 1 — core (shared)

**Why first:** All features depend on core; moving it first avoids later churn when fixing imports from moved features.

- **Files to move:** GrpcApplication, SessionManager, ActiveUserContext, UserRepository, FirebaseHelper, FirestorePaths, StaffDirectory, TenantBranding, BrandingAssets, AppConfig, OfflineTrialHelper, DemoFirebaseExpiryHelper, DailyContractPdfHelper, DictateEditText, HorizontalSwipeGuard, PdfPasswordPrompt, SignatureCaptureActivity, InAppReminderWorker (if grouped).
- **New package:** `com.grpc.grpc.core` (and optionally `com.grpc.grpc.core.worker`).
- **XML:** None to rename; `activity_signature_capture.xml`, `edittext_with_dictate.xml` stay in res/layout.
- **Imports:** Every file that references these classes must use `com.grpc.grpc.core.*` (or full names).
- **Manifest:** Update `android:name=".GrpcApplication"` to `com.grpc.grpc.core.GrpcApplication`. Update any activity/service/receiver that is moved to full class name.
- **Build:** `./gradlew assembleGrpcDebug assembleDemoDebug assembleOfflineDebug` (or Windows equivalent). Fix any remaining references.

### Stage 2 — login

- **Files to move:** LoginActivity → `com.grpc.grpc.login`.
- **XML:** None to rename.
- **Imports:** LoginActivity imports from core; update to `com.grpc.grpc.core.*`.
- **Manifest:** `LoginActivity` → `com.grpc.grpc.login.LoginActivity`.
- **Build:** Same as above.

### Stage 3 — main

- **Files to move:** MainActivity → `com.grpc.grpc.main`.
- **Imports:** MainActivity imports core and many feature activities; update package for core; feature activities still in root until their stage — so only core imports change in this stage.
- **Manifest:** `MainActivity` → `com.grpc.grpc.main.MainActivity`.
- **Build:** Same.

### Stage 4 — contracts

- **Files to move:** All contracts UI, ContractSelectionAdapter, ContractListPDFGenerator, BehindsListPDFGenerator to `com.grpc.grpc.contracts.ui` and `com.grpc.grpc.contracts.pdf`.
- **Imports:** Update within contracts package; update MainActivity and any other starters (e.g. WorkViewActivity) to use `com.grpc.grpc.contracts.ui.*` and `com.grpc.grpc.contracts.pdf.*`.
- **Manifest:** All five activities + any in contracts → full class names under `com.grpc.grpc.contracts.*`.
- **Build:** Same.

### Stage 5 — reports

- **Files to move:** All reports UI, data, and PDF classes to `com.grpc.grpc.reports.ui`, `com.grpc.grpc.reports.data`, `com.grpc.grpc.reports.pdf`.
- **Imports:** Reports classes import core and each other; update. Other modules (main, workview, PDFSelection, etc.) that start report activities must use new packages.
- **Manifest:** All report-related activities → full class names under `com.grpc.grpc.reports.*`.
- **Build:** Same.

### Stage 6 — quotations

- **Files to move:** All quotations UI, model, data, PDF to `com.grpc.grpc.quotations.*`.
- **Imports:** Quotations use core and SalesCatalogLoader; others that launch QuotesActivity, QuotationViewActivity, BirdQuotationActivity, etc. need updated imports.
- **Manifest:** All quotation activities → full class names under `com.grpc.grpc.quotations.*`.
- **Build:** Same.

### Stage 7 — jobs (including rodent)

- **Files to move:** Jobs UI + rodent activities to `com.grpc.grpc.jobs.ui` and `com.grpc.grpc.jobs.rodent`.
- **Imports:** MainActivity, WorkViewActivity, and any other starters of job activities need new imports.
- **Manifest:** All job and rodent activities → full class names under `com.grpc.grpc.jobs.*`.
- **Build:** Same.

### Stage 8 — workview

- **Files to move:** WorkView UI, model, data, worker, widget to `com.grpc.grpc.workview.*`.
- **Imports:** MainActivity, WorkViewPopupReminderWorker (if launched from core), and any references to WorkViewLocalEventStore, WorkViewWidgetHelper, etc. need new imports.
- **Manifest:** WorkViewActivity, WorkViewWidgetProvider → full class names under `com.grpc.grpc.workview.*`.
- **Build:** Same.

### Stage 9 — messaging / notifications

- **Files to move:** All messaging UI, model, service, NotificationUtils to `com.grpc.grpc.messaging.*`.
- **Imports:** MainActivity, GrpcApplication (if it starts login), FirebaseMessagingServiceGRPC references; update to new packages.
- **Manifest:** MessagingConversationsActivity, MessagingActivity, NotificationsActivity, ChatActivity, FirebaseMessagingServiceGRPC → full class names under `com.grpc.grpc.messaging.*`.
- **Build:** Same.

### Stage 10 — leads

- **Files to move:** LeadsSelectionActivity, ViewLeadsActivity, GenerateLeadsActivity to `com.grpc.grpc.leads.ui`.
- **Imports:** MainActivity and any other callers.
- **Manifest:** All three activities → full class names under `com.grpc.grpc.leads.*`.
- **Build:** Same.

### Stage 11 — service agreements

- **Files to move:** ServiceAgreementActivity, ServiceAgreementViewActivity, ServiceAgreementGenerator to `com.grpc.grpc.serviceagreements.ui` and `com.grpc.grpc.serviceagreements.pdf`.
- **Imports:** MainActivity, ReportSelectionActivity, PDFSelectionActivity, etc.
- **Manifest:** Both activities → full class names under `com.grpc.grpc.serviceagreements.*`.
- **Build:** Same.

### Stage 12 — search

- **Files to move:** SearchActivity, GlobalSearchAdapter, SearchResultsAdapter to `com.grpc.grpc.search.ui`.
- **Imports:** MainActivity; SearchActivity may use GlobalSearchItem/GlobalSearchKind (keep in same package or move to search.model).
- **Manifest:** SearchActivity → full class name under `com.grpc.grpc.search.*`.
- **Build:** Same.

### Stage 13 — location

- **Files to move:** LocationFinderActivity, LocationSharing, LastLocationUpdateWorker, LastLocationCleanupWorker to `com.grpc.grpc.location.*`.
- **Imports:** MainActivity, GrpcApplication (if location scheduled from app), core (LocationSharing used from MainActivity/core).
- **Manifest:** LocationFinderActivity → full class name; workers are referenced by WorkManager (class name in code).
- **Build:** Same.

### Stage 14 — files / help

- **Files to move:** FolderContentsActivity, HelpReadmeActivity, FolderAdapter, FileAdapter to `com.grpc.grpc.files.ui`.
- **Imports:** MainActivity and any callers.
- **Manifest:** FolderContentsActivity, HelpReadmeActivity → full class names under `com.grpc.grpc.files.*`.
- **Build:** Same.

### Stage 15 — ERA

- **Files to move:** EnvironmentSelectionActivity, NonToxERAActivity, ToxicERAActivity, ERAViewActivity, ToxicERAPDFGenerator, NonToxERAPDFGenerator to `com.grpc.grpc.era.ui` and `com.grpc.grpc.era.pdf`.
- **Imports:** ReportSelectionActivity, PDFSelectionActivity, core (SignatureCaptureActivity in core).
- **Manifest:** All four activities → full class names under `com.grpc.grpc.era.*`.
- **Build:** Same.

### Stage 16 — general reports

- **Files to move:** GeneralReportActivity, General4ptActivity, General6ptActivity, General8ptActivity, General12ptActivity to `com.grpc.grpc.generalreports.ui`.
- **Imports:** ReportSelectionActivity, WorkViewActivity (GeneralReportActivity), core (PdfPasswordPrompt, HorizontalSwipeGuard).
- **Manifest:** All five activities → full class names under `com.grpc.grpc.generalreports.*`.
- **Build:** Same.

### Stage 17 — admin

- **Files to move:** AdminDashboardActivity, EmployeeManagementActivity to `com.grpc.grpc.admin.ui`.
- **Imports:** MainActivity.
- **Manifest:** Both activities → full class names under `com.grpc.grpc.admin.*`.
- **Build:** Same.

After all stages: no Java files remain in `com.grpc.grpc` root; only feature packages and `com.grpc.grpc.core`. Manifest and all references use full class names. Optional: run a single “clean + full build” and fix any leftover references or proguard rules.

---

## PART 5 — APPROVAL CHECKPOINT

No code or file moves have been performed. This document is the plan only.

Please confirm:

1. **Structure:** Are you happy with the feature list and the proposed package tree (login, main, contracts, reports, quotations, jobs, workview, messaging, leads, serviceagreements, search, location, files, era, generalreports, admin, core)?
2. **Stage order:** Is the order (core → login → main → contracts → … → admin) acceptable, or do you want a different sequence (e.g. main before login, or reports before contracts)?
3. **Decisions:**  
   - Keep `SignatureCaptureActivity` in **core** (reusable) or move to **era**?  
   - Keep **rodent** as sub-feature under **jobs** or as a top-level **rodent** feature?  
   - Resolve `activity_toxic_era.xml` (unused) and `SearchResultsAdapter` / `item_message.xml` before or during migration?
4. **Layouts:** Keep current layout file names and only change Java packages, or also rename layout files (e.g. to a stricter prefix convention) in a later phase?

**Are you happy with this structure and stage order, or do you want anything changed before I begin implementation?**

Once you approve (and answer the optional points if you care), implementation can proceed one stage at a time with build verification after each stage.
