# GRPC App — How to Use (Admin, Super Admin & Technician)

This guide covers the main dashboard, reports, contracts, jobs, Work View, quotations, service agreements, ERA, commission/leads, messaging, notifications, search, location, and what happens when you save.

---

## 1) After Login: Main Dashboard (MainActivity)

After login you land on the main menu with buttons into the core modules:

| Button | Opens |
|--------|--------|
| **Create Report** | Report hub (ReportSelectionActivity) |
| **View Reports** | View reports saved on the device and upload/share (ReportViewActivity / StoredReportsActivity) |
| **Contracts** | View/add/edit contracts + open contract reports (ContractsActivity / ViewContractActivity) |
| **Jobs** | Add/view jobs (JobsActivity / AddJobsActivity / ViewJobActivity) |
| **Work View** | Calendar scheduling; add contract/job to calendar; follow-ups (WorkViewActivity) |
| **Quotes / Quotation** | Quotations (QuotesActivity, GeneralQuotationActivity, BirdQuotationActivity, etc.) |
| **Service Agreements** | Service agreement creation & signature (ServiceAgreementActivity) |
| **ERA** | Toxic / Non-Toxic Environmental Risk Assessment (EnvironmentSelectionActivity, ToxicERAActivity, NonToxERAActivity) |
| **Commission / Leads** | Sales & lead tools (GenerateLeadsActivity + related views) |
| **Messaging / Chat** | Internal chat (MessagingConversationsActivity, MessagingActivity, ChatActivity) |
| **Notifications** | In-app notifications list (NotificationsActivity) |
| **Search** | Global search (SearchActivity) — users with canSearch = true |
| **Location Finder** | Map/location utilities (LocationFinderActivity) — admin-only |

**Role visibility (high level):**  
- **Technician**: sees own jobs/contracts, Work View, reports, quotes, ERA, messaging, notifications, leads (where enabled).  
- **Admin**: sees broader lists for jobs/contracts, can assign work, and can open the **Admin Dashboard** (contract + user summary) but not Employee Management.  
- **Super admin**: full access including **Admin Dashboard**, **Employee Management**, AI Chat key settings, and (if enabled) Search + Location Finder.

**Other entries from main menu:** Help (HelpReadmeActivity), Management Jobs (AddManagmentJobsActivity / ViewManagmentJobActivity), Behinds List (BehindsListViewActivity), Admin Dashboard (AdminDashboardActivity), Employee Management (EmployeeManagementActivity).

---

## 2) Reports: What Each Report Is (ReportSelectionActivity)

From **Create Report** you choose the type:

### A) Standard “Company Report” (ReportActivity)

Main service visit report with:

- Customer name / address / date  
- Visit type  
- Site inspection notes  
- Recommendations  
- Follow-up  
- Preparation / proofing notes  
- Technician field (auto-filled)

**Save behaviour:**

- PDF is generated into local storage:  
  `Android/data/<package>/files/GRPEST REPORTS/`
- After generating, the UI offers upload options to send the latest PDF to Firebase Storage into a chosen folder path.

**Upload path (ReportsXX + month):**

- During upload you choose a parent folder (e.g. Reports25, Reports26) and, if used, a month subfolder (January, February, etc.).
- This logic is in:
  - `ReportActivity.uploadReportToFirebase(folderPath)` — uploads the newest PDF
  - `ReportViewActivity.uploadFileToFirebase(...)` — uploads a selected file

So “ReportsXX + correct month” is chosen when you upload.

### B) Generic Report (GeneralReportActivity)

A simpler general-purpose report (less pest-routine specific). Use when you want a quick formal report without the full structured fields. From here you can open visit-type specific flows (e.g. Rodent Activity Internal/External Routine, Rodent Call Out, etc.) or continue with the generic template.

### C) Action Form (ActionFormActivity)

Form-style report for corrective actions / safety / customer acknowledgment. Generates PDFs and supports upload.

### D) Quotes / Quotations

- **Contract Quote Formats (QuotesActivity)**  
  - 4pt / 6pt / 8pt / 12pt templates (General4ptActivity, General6ptActivity, General8ptActivity, General12ptActivity)  
  - Generates a PDF quote and saves locally (and can be uploaded).

- **Custom Quote (GeneralQuotationActivity)**  
  - Multi-line custom quotation: company name, address, line items (description + price), optional VAT toggle (13.5% or 23%), optional images. Technician email/number and date are auto-filled. Output appears under “Good Riddance Pest Control” with technician name/title.

- **Catalog Quotation (GeneralQuotationFromCatalogActivity)**  
  - Builds a quotation from the catalog (e.g. sales.json) for consistent line items.

- **Bird Quotation (BirdQuotationActivity)**  
  - Bird-proofing specific (bird netting, spikes, etc.).

### E) Service Agreements (ServiceAgreementActivity)

Creates a service agreement with signature capture and branded PDF.

### F) ERA (Environmental Risk Assessments)

- **Toxic ERA** (ToxicERAActivity)  
- **Non-Toxic ERA** (NonToxERAActivity)  

Both generate PDFs with compliance/safety fields.

---

## 3) Where “Reports25 / Reports26” Show Up in Contracts

When you are in a contract and choose **View Reports**, the app searches for reports in a selected year folder (e.g. Reports26):

- It looks at files directly inside `Reports26/` and in month subfolders (e.g. `Reports26/January/…`).
- It matches report filenames containing the contract name (normalized).

So: if a report is uploaded to the correct ReportsYY folder (and month subfolder if used), it will appear when searching from the contract (ContractReportsActivity).

---

## 4) Contracts: View / Add / Assign / Create Report from a Contract

### A) View Contracts (ContractsActivity)

- Technicians see their contracts (by assigned tech / contract key logic).
- Admins see a broader list (by role logic).

### B) View Contract (ViewContractActivity)

From a contract you can:

- Open details  
- Create reports linked to that contract (contract fields auto-filled)  
- View past uploaded reports via the ReportsYY folder search  
- Add contract to Work View schedule  

### C) Add Contract (AddContractActivity)

- Technicians add contracts assigned to themselves.
- Admins can assign to other users.
- Create-report-from-contract should auto-fill customer fields from the contract and technician fields from the logged-in user (or assigned tech when admin creates on behalf of someone else). With UID-based assignment, autofill comes from `users/{authUid}` or `users/{assignedTechUid}` when admin creates for another tech.

---

## 5) Jobs: Add / View / Assign + Saving to Firestore

### A) Add a Job (AddJobsActivity)

On submit:

- Data is written to the Firestore collection **JobWork** (target rename: `jobwork`).
- Fields include: AssignedTech, customer name/email/phone, issue details, CreatedBy, CreatedAt.
- In-app notification can be created for the assigned tech via `NotificationUtils.writeInAppNotification(...)`.

### B) View Jobs (JobsActivity / ViewJobActivity)

- Technicians see their assigned jobs.
- Admins see all jobs (depending on rules and role).

---

## 6) Work View Calendar: Add Event, Add Contract/Job, Follow-ups, Mark Done

**WorkViewActivity** is the scheduling system:

- Pick date (CalendarView); toggle daily/weekly views.
- **Add Event:**  
  - Add from Contract (select from contract list)  
  - Add from Job (new or existing job)  
  - Add Follow-up
- Mark event/job done.
- Create report from calendar context (generic reporting path).

**Admin scheduling (target behaviour):**

- Admin selects a user, then can add a contract from that user’s contracts or a job (new or existing) and assign/reassign it. When a job is reassigned, the job’s assigned technician is updated. This aligns with UID-based assignment.

---

## 7) Commission & Leads

From the main menu, lead generation and commission tools (e.g. GenerateLeadsActivity):

- Add lead, assign tech / added-by.
- Commission entry can create a notification to admin (per your rules).

---

## 8) Notifications & Messaging: “Only I See My Notifications”

### A) Notifications (NotificationsActivity)

- In-app notifications screen.
- Notifications are stored per recipient:  
  `notifications/{recipientUid}/items/{notifId}`  
  so only that recipient can read them.

### B) Messaging (MessagingActivity / MessagingConversationsActivity)

- In-app chat. When someone sends you a message, a notification is created for the receiver UID so only you see it.

---

## 9) What Happens When You Save

| Action | Result |
|--------|--------|
| **Save a report** | PDF is generated locally into the **GRPEST REPORTS** folder. You can then upload it to Firebase Storage under `ReportsYY/` and optionally `ReportsYY/Month/`. The app keeps the original PDF name. |
| **Save a job** | Job is saved into the Firestore collection **JobWork** (target rename: jobwork). |
| **Add Work View event** | Event is saved to Firestore (structure is UID-based, e.g. workview or events by user). |

---

## 10) Other Features

- **Management Jobs:** AddManagmentJobsActivity / ViewManagmentJobActivity for management-specific jobs.
- **Behinds List:** BehindsListViewActivity — view contracts/jobs that are behind schedule.
- **Admin Dashboard:** AdminDashboardActivity — overview for admins/super_admins:\n  - **Contracts summary** (per-assigned-tech counts, total contracts).\n  - **Users summary** (all users in `users` collection; non-super_admins do not see super_admin accounts).\n  - **Reports folders** (create `ReportsYY` year folders in Firebase Storage).\n  - **Storage summary** (super_admin only; requires optional `storage_metadata/summary` doc).\n  - **Auth / sign-in summary** (static text for now: auth log not configured).
- **Employee Management:** EmployeeManagementActivity — create/update staff profiles, roles (super_admin).
- **PDF Template Settings / View Templates:** Configure and view PDF templates for reports.
- **Stored Reports / Folder contents:** Browse and upload files; folder structure in Firebase Storage (e.g. ReportsYY/Month).
- **Location Finder:** Admin-only; view technicians’ last known location (LocationFinderActivity).
- **Search:** Users with canSearch = true; global search across contracts, reports (local + link to stored), commission (leads), work view (SearchActivity). Results are clickable and open the relevant screen.
- **Help:** In-app help with role-specific sections (HelpReadmeActivity).
- **AI Chat:** Uses API keys stored in Firestore (super_admin can update in Chat settings).

---

*Document generated for GRPC Android app. Update this file when flows or screens change.*
