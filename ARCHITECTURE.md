# GRPC Field Operations Platform — Updated Architecture

This architecture document supplements the updated README. It provides a **technical overview** of how the GRPest Control app is structured and how data flows between the Android client, Firebase services and external providers. It also highlights optional modules like Cloud Functions and AI integration, and offers guidance for multi‑company deployments.

---

## Component diagram

![GRPC component diagram](grpc_architecture_component_diagram.png)

*Diagram (repo root). Mermaid source: [docs/component_diagram.mmd](docs/component_diagram.mmd). Export to SVG: [docs/README.md](docs/README.md).*

---

## High‑Level Stack

| Layer                    | Technology/Responsibility                                                                                                           |
| ------------------------ | ----------------------------------------------------------------------------------------------------------------------------------- |
| **Client**         | Android application written in Java. Handles UI, business logic, PDF generation, WorkManager tasks and local caching.              |
| **Authentication** | Firebase Authentication using email/password. Offline users skip Firebase completely and operate in local mode.                    |
| **Data**           | Firestore collections for users, jobs, contracts, leads/commission, work view events, messages, notifications and location updates. |
| **Storage**        | Firebase Storage for PDF reports, quotations, action forms and service agreements. Files are organised by year.                    |
| **Automation**     | Cloud Functions (Node.js) for scheduled cleanup (e.g. message deletion) and future server‑side tasks.                              |
| **AI Services**    | External large‑language models via Groq API or Hugging Face API. API keys are stored in Firestore and accessed by the app.        |

---

## Client Modules

The Android app is organised into a number of activities and helper classes. The core modules include:

| Module / Activity                           | Key Responsibilities                                                                                                                                                                                                                                                  |
| ------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **SessionManager**                    | Loads the staff profile (`users/{StaffID}`), normalises roles, caches session data (including ContractKey), and enforces role checks across the app. Resolves UID→StaffID then loads authoritative profile. |
| **StaffDirectory**                    | Firestore-backed staff list: fetches `users` (filtered by 3-digit StaffID docs), builds ContractKey↔StaffID caches, provides deduplicated owner options for spinners (display = Name when available). No hardcoded staff IDs. |
| **WorkViewActivity**                  | Daily calendar with half‑hour slots. Displays jobs, contract visits and follow‑ups. Supports adding, editing and dragging events; combined calendars for admins; schedules in‑app reminders via WorkManager.                                                     |
| **ContractsActivity**                 | Lists per‑technician contracts with status counters (Behind/Due/Up‑to‑date). Supports searching, adding and assigning contracts. Admins can assign to other technicians. Contract reports are linked to Storage.                                                |
| **JobsActivity / AddJobsActivity**    | Views and creates service jobs. Admins assign technicians; technicians can accept jobs and mark them complete. A separate **Management Jobs** module tracks office tasks.                                                                                      |
| **FollowUpActivity**                  | Schedules and completes follow‑up visits. Integrated with Work View.                                                                                                                                                                                                |
| **ReportActivity / PDF generators**   | Collects report data and calls `PDFReportGenerator` or `PDFReportGeneratorWithTemplate`. Handles rodent, general, quotation, action form, bird quotation and service agreement flows. Applies password protection, compression, and optional signatures/images. |
| **PdfTemplateSettingsActivity**       | Offline users build custom headers with logos, header blocks and watermarks. Templates are saved locally via `PdfTemplateStorage` and can be reused.                                                                                                               |
| **ServiceAgreementActivity**          | Captures service contract details with signature fields and generates a PDF via `ServiceAgreementGenerator`.                                                                                                                                                        |
| **GeneralQuotationActivity**          | Collects quotation line items (4pt/6pt/8pt/12pt) and generates a PDF.                                                                                                                                                                                                 |
| **BirdQuotationActivity**             | Generates bird‑control quotations with an optional 30 % deposit and auto‑populates company contact info from the staff profile.                                                                                                                                    |
| **GenerateLeadsActivity**             | Adds leads with commission details. Admin screens allow marking invoices as paid and updating materials costs.                                                                                                                                                       |
| **MessagingActivity & Conversations** | Implements Firestore‑based 1:1 and group chat. Messages can be flagged urgent (not auto‑deleted) or non‑urgent (cleaned up by Cloud Functions). `MessageAdapter` and `ConversationListAdapter` handle UI lists. Conversation IDs are derived from participant ContractKeys; `group` is used for the team chat.                                                |
| **NotificationsActivity**             | Displays the user’s notification inbox (in‑app only). Supports deep linking into other modules.                                                                                                                                                                    |
| **LocationFinderActivity**            | Admin‑only screen for viewing technicians’ last known locations. Reads from `last_locations` collection in Firestore or from local cache when offline.                                                                                                           |
| **SearchActivity**                    | Admin‑only global search across contracts, jobs, leads and reports.                                                                                                                                                                                                  |
| **EnvironmentSelectionActivity**      | Lets technicians choose between Toxic and Non‑Toxic Environment Risk Assessment workflows, then launches the corresponding ERA activity.                                                                                                                             |
| **InAppReminderWorker**               | Schedules and shows in‑app reminders before jobs, visits and follow‑ups.                                                                                                                                                                                            |
| **LastLocationUpdateWorker**          | Publishes the device’s last known GPS position to Firestore every 15 minutes.                                                                                                                                                                                       |
| **LastLocationCleanupWorker**         | Deletes location records older than 30 minutes from Firestore. Caches last location locally for offline viewing.                                                                                                                                                    |

Smaller helper classes (`ActiveUserContext`, `TenantBranding`, etc.) provide cross‑cutting concerns such as theming, user details and brand customisation. `ActiveUserContext` is scoped by Firebase UID so cached identity does not leak across logins on the same device.

---

## Data Model and Identity

### Firestore Collections

| Collection / Document                      | Purpose                                                                                                                                         |
| ------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------- |
| `users/{StaffID}`                        | Authoritative staff profile with `Role`, `Name`, `Email`, `Number`, `ContractKey` and optional `Can*` flags.                        |
| `users/{uid}`                            | Maps Firebase Auth UID → `StaffID`. Used on login to resolve the staff record.                                                               |
| `{ContractKey} Contracts/{contractId}`   | Contracts assigned to a technician. Documents include client name, address, status counters and last/next visit dates.                         |
| `JobWork/{jobId}`                        | Service jobs created from Jobs screens or Work View. Contains customer details, issues, acceptance/completion flags and follow‑up scheduling. Assigned technician stored as ContractKey. |
| `user_workview/{...}/{eventId}`          | Work View events (jobs, contracts, follow‑ups) by technician. Includes start/end timestamps, type and metadata.                               |
| `Leads/{leadId}`                         | Leads captured by technicians with commission values, invoice number, payment status and materials cost.                                        |
| `notifications/{StaffID}/items/{itemId}` | In‑app notifications fan‑out per staff member. Contains title, body, timestamp and action metadata (deep link target).                       |
| `messages/{conversationId}/{messageId}`  | Chat messages. Each document stores sender, content, timestamp and a flag for urgency/retention.                                               |
| `last_locations/{userKey}`               | GPS location updates for admins. Each document stores latitude, longitude, accuracy and timestamps.                                            |
| `AI-Chat/AI-API`                         | Stores the current Hugging Face token (`KEY`) and Groq API key (`key-grog`). Only super_admin (or authorised admin) may read/write.        |
| `pdf_template/` (local)                  | Saved offline PDF templates. Stored locally (not in Firestore); listed here to emphasise separation.                                           |

### Identity Resolution

On login the app resolves **UID → StaffID** (via `users/{uid}` or email match), then loads **users/{StaffID}** and populates `SessionManager` with Role, **ContractKey** and permissions. **ContractKey** is the internal identifier for spinners (assigned tech/owner), message routing, work view assignment and contract collections; display names come from Firestore for UI only. On logout, session data, staff caches, work view cache, widget cache and location cache are cleared so the next user on the same device does not see the previous user’s identity.

### Offline Mode

When the user chooses **Offline login**, no Firestore or Storage access occurs. Data is read/written from local SQLite databases, SharedPreferences and the file system. The offline user does not map to a staff profile and has no role permissions. Only report generation and viewing local PDFs are available. When returning online, users must log in normally to upload or sync data.

---

## Reports and PDF Templates

### Built‑In Templates

- **Rodent reports** — initial, routine, call‑out and external flows. These share a common layout with species details, site inspection, recommendations and optional photo attachments.
- **General reports** — 4‑point, 6‑point, 8‑point and 12‑point forms with narrative sections and tables. Suitable for custom service types.
- **Action forms** — password‑protected forms summarising actions taken. Include optional signatures and images.
- **Quotations** — point‑based and bird‑specific quotations. Bird quotations include an optional 30 % deposit and automatically load company email/phone from the user’s profile. General quotations support line items and VAT calculations.
- **Service agreements** — agreements with signature fields for both technician and client. Generated via `ServiceAgreementGenerator`.

All PDFs are created using iText. They are compressed automatically and can be encrypted with an owner password. When a report is saved, the user can choose to view, share or upload it. Offline users cannot upload; they are reminded to upload later from the Stored Reports screen.

### Offline PDF Template (“My Template”)

Offline users (not logged in) have the option to customise report headers. The `PdfTemplateSettingsActivity` allows setting:

| Field                      | Description                                                                                                                          |
| -------------------------- | ------------------------------------------------------------------------------------------------------------------------------------ |
| **Main header text** | A string (e.g. company name). Colour choices include blue, black, dark gray, red or green.                                          |
| **Logo**             | An image loaded from device storage. Displayed at 200 × 200 px above the main header.                                           |
| **Watermark**        | Optional watermark text or image. When disabled, no watermark is applied.                                                           |
| **Header blocks**    | An ordered list of text or image blocks with styles (H1, H2 or body text). Used to build a structured header below the main header. |

Users can save the current setup as a **named template**. Templates are stored in the `pdf_template/` directory and referenced by ID. The Create Report screen (offline only) lets the user choose **Use GRPC Template** or **Use My Template** and select one of their saved templates. Logged‑in users always use the fixed GRPC template for consistency and branding.

---

## Location Sharing

Location updates are implemented entirely on the client via WorkManager tasks:

1. **LastLocationUpdateWorker** runs on a periodic basis (every 15 minutes). It requests the device’s last known location from Android’s fused location provider, then writes a document to `last_locations/{userKey}` with latitude, longitude, accuracy, provider and timestamps. The `userKey` is derived from the user’s ContractKey (or identifier). A copy of the JSON is cached locally in `SharedPreferences` for offline access.
2. **LastLocationCleanupWorker** runs every 30 minutes. It checks the stored location document’s `clientTimestampMs` and deletes it if older than 30 minutes.
3. **LocationFinderActivity** (admin‑only) reads the last known location from Firestore or local cache and displays it on a map. Firestore rules should restrict reads to authorised admins to protect staff privacy.

The location module is optional and can be disabled at runtime by withholding the GPS permission or removing the WorkManager scheduling calls.

---

## Messaging and Notifications

### Messaging

Messages are stored under `messages/{conversationId}/{messageId}`. A conversation ID is derived from the participants’ ContractKeys (or participant identifiers), and `group` is used for the team chat. Each message document contains:

- `senderId` (sender identifier)
- `content` (plain text)
- `timestamp` (server timestamp)
- `urgent` (boolean flag — urgent messages are not auto‑deleted)

The app displays conversations via `MessagingActivity` (list of messages) and `MessagingConversationsActivity` (list of conversations). Input fields allow dictation (via `DictateEditText`) and attachments can be added in future.

### Notifications

Notification records are created whenever key events happen: new jobs, contract assignments, lead updates, etc. The record structure includes a title, body, timestamp, `type` (e.g. job, contract, lead) and an optional payload for deep linking. On save, the app writes the notification to the primary recipient’s collection (`notifications/{StaffID}/items`) and also writes a copy to admin staff (derived from Firestore roles) for oversight. Firestore rules restrict each user to reading their own notifications. There are no FCM or system notifications; the app’s own `NotificationUtils` schedules reminders and shows in‑app pop‑ups at appropriate times.

---

## AI Integration

The AI features are optional but provide value to technicians and admins:

- **Chat** — implemented in `ChatActivity`. It sends user prompts to either the Groq API or the Hugging Face inference API depending on which API key is present in Firestore. The keys reside in `AI-Chat/AI-API` under fields `KEY` (Hugging Face token) and `key-grog` (Groq token). Only the **super_admin** role (or authorised admin) may update these values via Settings in the app. Replies are shown as plain text (no markdown). The app limits token length to stay within API quotas.
- **AI Fix** — integrated into Create Report and Action Form screens. When invoked, it collects the selected text fields, sends them to the same LLM provider and replaces the text with an improved version (professional tone, correct grammar, no filler). Offline users cannot invoke AI Fix because it requires API connectivity.

Both features should be used responsibly. API keys must be kept private and rotated regularly. Costs can accrue for each call; monitor usage via the respective provider consoles.

---

## Automation and Background Tasks

Several background tasks keep the app’s data fresh and responsive:

| Worker/Function                 | Purpose                                                                            |
| ------------------------------- | ---------------------------------------------------------------------------------- |
| `InAppReminderWorker`         | Schedules and displays reminders ~30 minutes before events in Work View.          |
| `WorkViewPopupReminderWorker` | Shows immediate pop‑ups when an event becomes due (e.g. at the event start time). |
| `WorkViewWidgetHelper`        | Updates the home screen widget with the next three jobs.                           |
| `LastLocationUpdateWorker`    | Publishes location updates to Firestore and caches them locally.                   |
| `LastLocationCleanupWorker`   | Deletes stale location updates.                                                    |
| Cloud Function: message cleanup | When deployed, deletes non‑urgent messages older than ~30 minutes.               |

These tasks are scheduled using Android’s WorkManager or Cloud Functions. You can disable them by removing their scheduling calls.

---

## Key Workflows (Data Flow)

### Job Assignment

1. **Admin creates a job** via the Jobs screen or Work View. They select a technician from the dropdown (options from Firestore, stored value = ContractKey).
2. The app writes the job document to `JobWork/{jobId}` and adds a corresponding work event in `user_workview`.
3. A notification record is written to `notifications/{StaffID}/items` and fanned out to admin inboxes.
4. The technician sees the new job in Work View; a reminder is scheduled via WorkManager.

### Report Generation

1. The user completes a report form (rodent, general, quotation, action form or service agreement). Optional images and signatures are captured.
2. The appropriate generator class (`PDFReportGenerator`, `BirdQuotationPDFGenerator`, `ServiceAgreementGenerator`, etc.) constructs the PDF via iText. If the user selected **My Template** (offline only), `PDFReportGeneratorWithTemplate` applies custom headers/watermarks before adding the body.
3. The resulting PDF is compressed and optionally encrypted with an owner password.
4. The user sees a success dialog with **View**, **Share** and (if logged in) **Upload to Firebase**. Uploading writes the file to `ReportsYY/` in Storage and optionally updates contract history metadata in Firestore.

### Location Update

1. Every 15 minutes, `LastLocationUpdateWorker` obtains the last known location from Android’s fused location API and writes it to `last_locations/{userKey}`. A local cache is also updated.
2. Every 30 minutes, `LastLocationCleanupWorker` checks each document’s age and deletes it if older than 30 minutes. This prevents stale location data from lingering.
3. When an admin opens Location Finder, the app reads the document and displays the marker on a map. Offline fallback uses the cached JSON.

### Leads and Commission

1. A technician adds a lead via **Generate Lead**. The document is written to `Leads/{leadId}` with default values for payment status and materials cost.
2. Admins can edit leads via **View Leads**. They update the payment status or materials cost; the app may trigger a notification to the assigned technician.
3. Commission reports and invoices are generated outside the app (future integration could generate commission PDFs via iText).

### Messaging & Notifications

1. When a user sends a message, a new document is written under `messages/{conversationId}/{messageId}`. If the message is non‑urgent, a Cloud Function may delete it after a timeout.
2. When an event occurs (job created, contract assigned, lead updated, AI key changed, etc.), the app creates a notification record for the relevant users. Each record is written to `notifications/{StaffID}/items`. The user’s app shows a badge on the home screen and lists the notification in `NotificationsActivity`. Tapping the notification opens the appropriate screen.

---

## Multi‑Company Deployment Options

### Option A — Separate Firebase Projects (Recommended)

Create a separate Firebase project for each client company. Replace the logo, app name and package name for each build (use `build-with-env` scripts). This provides clean data isolation, simpler security rules and independent scaling. Use identical codebases but point them at different projects via environment variables and `google-services.json`.

### Option B — Multi‑Tenant SaaS

A single Firebase project can host multiple companies by adding a `companyId` field to every document and enforcing tenant isolation via Firestore rules. This reduces operational overhead but increases rule complexity. You must add indexes for every query that filters by `companyId` and ensure that admins can only access data for their tenant. Additional features like per‑tenant API keys and branding must be handled at runtime via `TenantBranding` and environment variables.

---

## Notes

- The Android app is tightly coupled with Firestore schemas. Changing collection names or document structures requires code changes in `StaffDirectory`, `FirebaseHelper`, `ReportDatabaseHelper` and PDF generators.
- The staff CRM/portal at [https://grpcstaff.ie](https://grpcstaff.ie) shares the same backend and expects the same Firestore structure. Any schema changes must be coordinated with the web portal.
- The updated README includes detailed setup instructions, security practices and troubleshooting. Use it as the primary user‑facing documentation. This architecture file complements it with design rationale and data flows.
- **Diagrams:** Component and sequence diagrams (login, report, AI) are in Mermaid format; see [docs/component_diagram.mmd](docs/component_diagram.mmd) and [docs/README.md](docs/README.md) for export to PNG/SVG.
