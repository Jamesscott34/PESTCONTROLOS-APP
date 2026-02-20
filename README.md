# GRPC (GRPest Control) — Android + Firebase Platform

This repository contains a production-style Android application used by GRPest Control staff to manage **contracts**, **jobs**, **work scheduling**, **reports**, **messaging**, **leads/commission**, and **in-app notifications**, backed by **Firebase (Firestore + Storage)**. **Cloud Functions are optional** for automation (cleanup/schedules). **LLM APIs** (Groq / Hugging Face) power in-app AI Chat and report polish (AI Fix); keys are maintained by admin via app settings.

The solution is intentionally designed around **in-app notifications only** (no FCM push notifications and no Android system notifications) to keep notification delivery consistent and fully under application control.

For the **full architecture overview** (platform stack, data flow, key workflows, multi-company options, and offline PDF template), see **[ARCHITECTURE.md](ARCHITECTURE.md)**.

---

## Contents

- [Key capabilities](#key-capabilities)
  - [Login and offline mode](#login-and-offline-mode)
  - [AI features (Chat &amp; AI Fix)](#ai-features-chat--ai-fix)
- [Roles and user model](#roles-and-user-model)
- [How to use the app (by user)](#how-to-use-the-app-by-user)
- [Technology stack](#technology-stack)
- [Repository layout](#repository-layout)
- [Architecture (PDF reporting and offline template)](#architecture-pdf-reporting-and-offline-template)
- [Setup (end-to-end)](#setup-end-to-end)
  - [Prerequisites](#prerequisites)
  - [Package name / applicationId changes](#package-name--applicationid-changes)
  - [Firebase setup (Android + Console)](#firebase-setup-android--console)
  - [Firestore rules + collections](#firestore-rules--collections)
  - [Storage layout for reports](#storage-layout-for-reports)
  - [Environment variables &amp; build configuration](#environment-variables--build-configuration)
  - [Cloud Functions (optional automation)](#cloud-functions-optional-automation)
- [Operational behavior](#operational-behavior)
  - [In-app notifications model](#in-app-notifications-model)
  - [Work reminders](#work-reminders)
- [Branding](#branding)
- [Support](#support)
- [Security and repository hygiene](#security-and-repository-hygiene)
- [Troubleshooting](#troubleshooting)

---

## Key capabilities

### Login and offline mode

- **Firebase login:** Email/password sign-in via Firebase Authentication; user name derived from email and passed to Main Activity.
- **Offline login:** On the login screen, **"Offline login"** lets you use the app without Firebase. You enter as **"Offline User"**. The **main menu shows only Create Report, View Reports, and Logout**; all other buttons (Notifications, Work View, Contracts, etc.) are hidden. In **View Reports**, the **Stored Reports** option is hidden for the offline user.
- When **logged in** (Firebase Auth), the **Create Report** screen hides the PDF template section (Use GRPC Template / Use My Template and PDF Template Settings); reports always use the GRPC template. When **not logged in** (offline), that section is shown so you can choose GRPC or My Template and open PDF Template Settings.

### Work View (schedule)

- **Calendar-first scheduling** for technician field work.
- **Daily view** with fixed time slots (08:00–17:30, 30‑minute intervals) and a fast “tap a slot to add” workflow.
- **Calendar view** for browsing and jumping to dates.
- **Event types**: jobs, contracts, and follow‑ups (role dependent).
- **Completion flows**:
  - Confirmation prompt when marking routines/visits done.
  - Contract “last visit” updates and schedule cleanup for completed items.
- **Multi-user oversight**:
  - Regular technicians primarily manage their own calendar.
  - **Users 002 and 004** can view and manage multiple technicians’ work views (combined calendar behavior).
- **In-app reminders** (no system notifications):
  - WorkManager schedules an **in-app notification record** ~30 minutes before scheduled events.
  - Reminders are de‑duplicated and cancelled when events are completed/rescheduled.
- **WorkView: custom times and drag-and-drop**
  - **Create with custom duration:** Add Job/Contract/Follow‑up via Work View. When the time dialog opens, choose **“Custom time…”**. Enter start (e.g. 08:30 or 0830) and end (e.g. 15:00 or 1500) → event is created as 08:30–15:00.
  - **Adjust an existing event:** Tap the event → **Edit Time**. Change start and optionally end (e.g. 09:30–14:30) and save; duration is preserved if you only change one.
  - **Move an event (drag-and-drop):** In daily view, long‑press an event, drag it to another time slot bar, and release. Start time snaps to that slot; duration is preserved if the event had an end time.
- **Work View home screen widget:** A home screen widget shows **Work View** title, today’s date, and the **next 3 jobs** (time, name, address). Tap opens Work View. Data is cached when you open Work View so the widget can show today’s jobs even after logout. The widget layout **must not** use ImageView, drawable, or PNG references (RemoteViews restrictions). If the widget does not display, see **`app/src/main/assets/WIDGET_TROUBLESHOOTING.md`**.

### Contracts

- Contracts are stored per technician in Firestore collections named `"[User] Contracts"` (e.g. per user ID).
- **Search + status highlighting**:
  - “Behind / Due / Up‑to‑date” counters and filtering.
  - Visual status cues to help prioritise visits.
- **Actions**:
  - Open an address directly in **Google Maps**.
  - Update visits / mark done where permitted.
- **Contract reports by year**:
  - “View Reports” prompts for a year and searches within `ReportsYY` (e.g., `Reports26`) in Firebase Storage.
  - Matching is flexible (case/spacing/underscore tolerant).
- **Role-aware assignment**:
  - Admin users (002, 004) can add contracts to another technician’s list (and the assigned technician receives an in‑app notification record).

### Jobs

- **Service job creation + assignment** via Firestore `JobWork`.
- **Job lifecycle management**:
  - View job details (customer/contact/address/issue).
  - Acceptance/completion flows and follow‑up scheduling.
  - Payment capture fields and operational notes (where enabled in screens).
- **Work View integration**:
  - Create jobs directly from the calendar and automatically add them to the assigned technician’s work view.
- **Management tasks**:
  - Higher-level admin tasks are tracked in `ManagmentJobs` with their own view/edit flows.

### Reports (PDF)

- **PDF generation** across multiple operational templates (iText):
  - General service reports
  - Rodent reports (Initial / Routine / Call‑Out / External flows)
  - ERA documents (Toxic / Non‑Toxic)
- **File compression**: All generated PDFs (Action Forms, Create Report, custom templates) are **automatically compressed** for smaller file size (no user option).
- **Create Report (quotation)**:
  - **Password protection**: An optional checkbox “Password protect PDF” lets you set an owner password; the PDF is then encrypted (viewing/printing allowed; editing requires the password).
- **Storage model**:
  - Reports are organized in Firebase Storage under year folders like `ReportsYY/` (optionally with month subfolders).
  - Reports can also be generated/stored locally under the app’s external files directories (e.g. quotes, service agreements).
- **Viewing and sharing**:
  - Browse stored report lists, download/share PDFs.
  - Contract-specific search is scoped by year folder for speed and organisation.
- **Offline PDF template (Create Report)**:
  - On the **Report selection** screen: **"Custom Report (PDF Template)"** opens **PDF Template Settings**; **"Create Custom Report"** opens the Create Report form with a choice of **Default** or **Select template…** (saved templates). The Create Report screen does not show the PDF Template radio block; only the report form and template choice.
  - **Named templates:** In PDF Template Settings you enter a **template name** and tap **"Save template settings"** to save the current setup (main header, logo, watermark, header blocks) as a named template; the form is then **cleared** and the cleared state is saved, so the next time you open PDF Template Settings or create a new template you start with empty fields. **"View templates"** lists saved templates; tap **"Use"** to open Create Report with that template, or **Delete** (with confirmation) to remove one.
  - On the **Create Report** screen (when not logged in) you can choose **Use GRPC Template** or **Use My Template**, and open PDF Template Settings. **When logged in**, the PDF template section is **hidden**; reports always use the GRPC template.
  - **PDF Template Settings**: **Main header** (e.g. company name) and **main header colour** (Blue, Black, Dark Gray, Red, Green); logo; watermark (on/off, text or image); and an ordered list of header blocks (text with style H1/H2/BODY or image). All settings and named templates are stored **locally** (SharedPreferences + files in `pdf_template/`); no Firebase. Fully offline.
  - **My Template PDF layout:** At the top, **logo first** (same size as GRPC: 200×200), then **main header** text in the chosen colour. If no logo is selected, nothing is shown for the logo. Header blocks appear only in the body; watermark and footer use only the user’s content (no default watermark when disabled); footer text is **"Created by reporting system"**.
  - Body layout (margins, fonts, sections, table, footer, page numbers) is identical to the GRPC template in both modes; only the header area and watermark differ when using My Template. If no images are selected for the report, no image section or placeholder is added to the PDF.
  - **After saving a report:** A “Report Saved Successfully!” dialog offers **View**, **Share**, and (when **logged in**) **Upload to Firebase**. Tapping outside the dialog dismisses it. **Offline users** do not see the Upload to Firebase option in this dialog or in View Reports (file options).

### Quotations & service documents

- **Quotation generation**:
  - Point-based quotations (4pt/6pt/8pt/12pt).
  - General quotations and Bird quotations with line items.
  - Professional formatting with VAT/line totals and consistent branding.
- **Bird quotation**:
  - **30% deposit option**: A checkbox “30% deposit due before job (uncheck for total payment only)” controls the PDF: when **checked**, the PDF shows 30% due before the job and the remaining amount; when **unchecked**, only the total payment is shown.
  - **Email and number**: Company email and mobile on the Bird quotation are loaded from the **users database** (Firestore) for the logged-in user; they are not typed manually (add/update them in the users/staff data if missing).
- **Service agreements**:
  - Create and view service agreements with signature fields and stored PDFs.

### Leads & commission

- Lead capture and tracking in Firestore `Leads` with commission calculations and invoice/payment status fields.
- **Mark as paid** and **Add/Edit materials** are restricted to admins only (user IDs 001, 002, 004). User 003 can view leads assigned to them but cannot mark invoices as paid or edit materials cost.
- Admin-side edit flows can trigger in-app notifications to the affected technician (commission change audit trail behavior).

### Action Forms (password-protected PDFs)

- Generate **password-protected Action Form PDFs** (owner-password protected for editing restrictions) using iText.
- **File compression**: All Action Form PDFs are written with **full compression** (smaller file size).
- Optional **password protection** via a checkbox; when enabled, a dialog prompts for an owner password (required to edit the PDF).
- Optional inclusion of **technician and customer signatures**.
- Optional attachment of images to the PDF.

### Signature capture

- Dedicated signature capture screen for technician/customer signatures.
- Saves signatures as PNGs under app external storage and returns URIs to calling workflows (and/or embeds in generated PDFs).

### Messaging

- In-app conversations backed by Firestore:
  - 1:1 chats use conversation IDs like `james_ian`
  - A shared `group` conversation exists for team messages
- **Retention**:
  - Non-urgent messages are auto-deleted after ~30 minutes (optional automation via Cloud Functions).
  - Urgent messages can be retained (per-message flag).

### AI (Chat & report polish)

- **AI Chat**:
  - In-app chat powered by **Hugging Face** (via HF Router) or **Groq**; the app uses Groq if a Groq key is set, otherwise the Hugging Face key.
  - API keys are stored in Firestore at `AI-Chat/AI-API`: field **KEY** (Hugging Face) and **key-grog** (Groq). Only user 001 can update these via Settings (Update Hugging Face Key / Update Groq Key).
  - Replies are shown as plain text (no markdown tables/headings); long replies supported (e.g. max 2048 tokens).
- **AI Fix** — rewrites text in **Create Report** and **Action Form** (logged-in users only):
  - The **AI Fix** button is **only visible when the user is logged in** (Firebase Auth). **Offline users do not see** the AI Fix button.
  - When a logged-in user taps AI Fix, a dialog asks **which fields to update**: **Create Report** — Site Inspection and/or Recommendations; **Action Form** — Service Report and/or Recommendations. Only the selected fields are sent to the API and updated.
  - Text is made professional, grammatically correct, and free of asterisks or filler; a few sentences may be added where appropriate.
  - AI Fix uses the same Firestore API keys as AI Chat (KEY or key-grog). If no key is set, the app prompts that the admin must set one in AI Chat settings.

### In-app notifications (no push)

- Notification history is stored in Firestore under `notifications/{user}/items`.
- Home screen shows unread indicators for notifications/messages.
- Notifications can be deleted individually or cleared.
- Tapping a notification deep-links into the relevant screen (Work View / Contracts / Jobs / Messaging) and can open location context (Maps).
- **Design principle**: notifications are intentionally **in-app only** (no Android status-bar notifications, no FCM push requirement for core flows).

### Dark Mode

- Theme-aware UI using Material DayNight and attribute-based colors (`?android:attr/colorBackground`, `?attr/colorSurface`, `?android:attr/textColorPrimary/Secondary`) so screens render correctly in Light/Dark modes.
- Layouts avoid hardcoded white/black and instead use theme surfaces/“on-surface” text, with consistent button styling.

### AI features (Chat & AI Fix)

- **AI Chat**  
  In-app chat backed by AI (Hugging Face–style or Groq). Replies are plain text, no markdown. API keys are stored in Firestore at `AI-Chat/AI-API`:
  - **KEY** — Hugging Face token (see [Hugging Face tokens](https://huggingface.co/settings/tokens)).
  - **key-grog** — Groq API key (see [Groq Console](https://console.groq.com)).  
  If **key-grog** is set, the app uses Groq; otherwise it uses the Hugging Face key. Only user 001 can update these keys (via Settings in the app; Cloud Functions enforce admin-only writes).
- **AI Fix button**  
  Shown **only when the user is logged in** (offline users do not see it). In **Create Report** and **Action Form**:
  - Tap “✏️ AI Fix” to open a dialog: **choose which fields to update** (Create Report: Site Inspection and/or Recommendations; Action Form: Service Report and/or Recommendations). Only the selected fields are polished.
  - Text is made professional, grammatical, no asterisks/filler; a few sentences may be added where appropriate.  
  Uses the same Firestore API key as AI Chat (KEY or key-grog). The button is disabled until content exists in the relevant fields.

---

## Roles and user model

This app is role-aware and uses **user IDs** (001, 002, …) for permission checks; display names are still used in the UI and in Firestore (e.g. “Added By”, “AssignedTech”).

- **User IDs** (match Firestore `users` collection): **001**, **002**, **003**, **004** (extensible). Display names and contact details are loaded from the database.
- **Admins / oversight**: **001**, **002**, **004**
  - Can assign work to other technicians (jobs/contracts).
  - Can delete contracts and jobs.
  - Receive oversight notifications for certain actions (e.g. when 003 adds contracts).
- **Technician**: **003**
  - Can complete work, create reports, and add contracts to their own list.
  - **Cannot delete contracts or jobs** (the app will prompt to contact an administrator).
  - Can delete local reports (with an extra “uploaded to Firebase?” confirmation).

Notes:

- Contracts are stored per technician in collections like `User Contracts`
- Work View events are stored per technician in collections like `user_workview` etc.
- Notifications are stored in Firestore under `notifications/{user}/items`.

---

## How to use the app (by user)

### (Admin / Oversight)

- **Assign jobs**:
  - Use Jobs → Add Job (Job Work) and select the assigned technician from the dropdown.
  - The assigned technician receives an **in-app notification**.
- **Assign contracts**:
  - Use Contracts → Add Contract.
  - Use the Assign-to dropdown (per technician) to put the contract into the correct technician’s list.
  - The assigned technician receives an **in-app notification**.
- **Work View management**:
  - Use Work View to schedule jobs/contracts and mark them complete.
  - Reminders are scheduled ~30 minutes before scheduled jobs/contracts.
- **Admin-only actions**:
  - Delete/edit contracts.
  - Delete jobs (Job Work and Management Jobs).
- **Where to check activity**:
  - Notifications screen (in-app history).
  - Messaging (1:1 and group chat).
- **Commission / Leads** (users 002, 004):
  - Generate Lead / View Leads to manage commission tracking and invoice status.
  - Lead updates can notify the assigned technician.

### (Admin / Owner)

- Everything in Ian’s section, plus:
  - **James-only features (currently)**:
    - **Global Search** (search across Jobs/Contracts/Leads/Reports in one place).
- **Commission / Leads**:
  - Generate Lead / View Leads to manage commission tracking and invoice status.
  - Lead updates can notify the assigned technician.

### (Admin / Office)

- **Assign jobs** (Job Work / Management Jobs) using the dropdown to avoid typos.
- **Assign contracts** via the Assign-to dropdown.
- **Oversight workflows**:
  - Use Notifications to review what was assigned/changed.
  - Use Leads to track invoice status, materials cost, and commission.
- **Admin-only actions**:
  - Delete/edit contracts.
  - Delete jobs.

### (Technician)

- **Daily workflow**:
  - Work View is your schedule. Use it to see today’s work and upcoming jobs/contracts.
  - Tap events to open details, open Maps, and create reports when needed.
- **Job Work**:
  - Review jobs assigned to you.
  - Accept jobs, add addresses if required, and create reports.
- **Contracts**:
  - Add contracts to your own list.
  - Mark visits done and keep last/next visit accurate.
- **Reports**:
  - Create and upload PDFs.
  - You can delete local reports, but the app will ask you to confirm you uploaded to Firebase first.
- **Restrictions**:
  - You **cannot delete** contracts or jobs. If you try, you’ll see:
    - “To delete a contract get in touch with an administrator.”
    - “To delete a job get in touch with an administrator.”

---

## Technology stack

- **Android**: Java, XML layouts, AppCompat, RecyclerView, etc.
- **Firebase**:
  - **Firestore**: primary database (contracts, jobs, leads, messages, notifications, workview).
  - **Storage**: PDF report storage (`ReportsYY/...`).
  - **Cloud Functions (Node.js)**: optional automation (cleanup/schedules).
- **PDF**: iText (report generation; full compression for Action Forms and Create Report quotation PDFs; optional encryption for Action Forms and Create Report).

---

## Repository layout

```
grpc/
├── app/                         # Android app module
├── functions/                   # Firebase Cloud Functions (Node.js)
├── gradle.properties.template   # Template for build-time env variables
├── build-with-env.bat           # Windows build helper
├── build-with-env.sh            # Linux/macOS build helper
├── setup-env.bat                # Windows env setup helper
├── setup-env.sh                 # Linux/macOS env setup helper
└── .gitignore                   # Secret/build artifact exclusions
```

---

## Architecture (PDF reporting and offline template)

This section details the PDF report and **offline template** behaviour. For the full platform architecture (stack, data flow, workflows, multi-company options), see **[ARCHITECTURE.md](ARCHITECTURE.md)**.

- **Create Report (GRPC template)**  
  `ReportActivity` collects form data and, on save, calls `PDFReportGenerator.generatePDFReport(...)`. That method creates the PDF with a fixed logo (drawable), title “Good Riddance Pest Control Report”, and body built by `PDFReportGenerator.addReportBodyToDocument(...)`. A page event handler (`PdfWatermarkAndFooterHandler`) adds the default watermark and footer on every page. Images are added only when the user has selected at least one image (no placeholder when none are selected).

- **Offline PDF template (My Template)**  
  The same Create Report screen offers a template selector: **Use GRPC Template** or **Use My Template** (when not logged in). Template preferences are stored locally via `PdfTemplateStorage` (SharedPreferences + JSON for header blocks; logo and watermark image files in `context.getFilesDir()/pdf_template/`). The **PDF Template Settings** screen (`PdfTemplateSettingsActivity`) lets users set **main header** text and **main header colour** (Blue, Black, Dark Gray, Red, Green), logo, enable/configure watermark (text or image), and add/remove/reorder header blocks (text with style H1/H2/BODY or image). **Save template settings** saves the current setup as a named template, then **clears all fields** and persists that cleared state so the next time the user opens PDF Template Settings or creates a new template, the form is empty.

- **PDF generation flow**  
  When the user saves a report, `ReportActivity` either uses a selected saved template (when launched from View Templates → Use, via `EXTRA_TEMPLATE_ID`) or loads `PdfTemplateSettings` from `PdfTemplateStorage` and sets `templateSelection` from the radio choice (GRPC vs MY_TEMPLATE). It then calls `PDFReportGeneratorWithTemplate.generatePdf(..., settings)`. All PDFs are auto-compressed.
  - If **GRPC**: `PDFReportGeneratorWithTemplate` delegates to `PDFReportGenerator.generatePDFReport(...)` with no other changes.
  - If **MY_TEMPLATE**: `PDFReportGeneratorWithTemplate` creates the writer/document, registers a custom page handler (`CustomWatermarkAndFooterHandler`) for watermark and footer. The **custom header** is: **logo first** (same size as GRPC: 200×200, centre; nothing if no logo path), then **main header** paragraph (user text in chosen colour). Header blocks (text/image) are only in the body, not at the very top. Watermark uses only the user’s text or image when enabled; no default when disabled. Footer text is **"Created by reporting system"**. Then `PDFReportGenerator.addReportBodyToDocument(...)` is called so the body layout is identical to the GRPC report.

- **Named templates:** In PDF Template Settings, **Save template settings** saves the current setup as a named template and clears the form; **View templates** lists them (with **Delete** + confirm); **Use** opens Create Report with that template applied. All PDFs are **auto-compressed** (no checkbox).

- **Key classes**  
  - `PdfTemplateSettings` — POJO: templateSelection, mainHeaderText, mainHeaderColorHex, logoPath, watermark (enabled, type, text, imagePath), list of `HeaderBlock` (blockType, textStyle, text, imagePath).  
  - `SavedTemplate` — Named template (id, name, mainHeaderText, mainHeaderColorHex, logoPath, watermark*, headerBlocks); `toPdfTemplateSettings()` for generation.  
  - `PdfTemplateStorage` — Loads/saves settings; saved templates list (loadSavedTemplates, addSavedTemplate, getSavedTemplateById); SharedPreferences + JSON.  
  - `PDFReportGenerator` — existing: `generatePDFReport(...)` (unchanged), `addReportBodyToDocument(document, content, context, imageUris)` (body + optional image section).  
  - `PDFReportGeneratorWithTemplate` — `generatePdf(..., settings)`: GRPC path → existing generator; MY_TEMPLATE path → logo (200×200) then main header, custom watermark/footer, same body; auto-compress.  
  - `PdfTemplateSettingsActivity` — UI for main header + colour, logo, watermark, header blocks; template name; Save template settings; View templates.  
  - `ViewTemplatesActivity` — Lists saved templates; Use → ReportActivity with EXTRA_TEMPLATE_ID; Delete with confirmation.

---

## Setup (end-to-end)

### Prerequisites

- Android Studio (latest stable recommended)
- JDK (Android Studio embedded JDK is fine)
- A Firebase project with:
  - Firestore enabled
  - Storage enabled
- Cloud Functions enabled (optional, for automation)
- Firebase CLI (for deploying functions)

---

### Package name / applicationId changes

If you need to rename from `com.grpc.grpc`:

- Use Android Studio: **Refactor → Rename** on the Java/Kotlin package.
- Update `applicationId` in `app/build.gradle.kts` if you change the package.
- Update `AndroidManifest.xml` references if your tooling doesn’t do it automatically.
- Rebuild and verify the app launches.

Important: your Firebase Android app registration must match the final package name.

---

### Firebase setup (Android + Console)

1. In Firebase Console, create/select your project.
2. Add an **Android app** in Firebase:
   - Package name must match your app’s `applicationId`.
3. Download `google-services.json`.
4. Place it at:
   - `app/google-services.json`

`google-services.json` is intentionally ignored by git.

---

### Firestore rules + collections

#### Fixing “Access Denied” when viewing contracts

If your Firestore rules block access to the `"[User] Contracts"` collections, you can fix it in Firebase Console:

- Firebase Console → **Firestore Database** → **Rules**

**Development-only permissive rules (NOT for production):**

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /{document=**} {
      allow read, write: if true;
    }
  }
}
```

**Production baseline suggestion (adjust to your auth model):**

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /{userName} Contracts/{document} {
      allow read, write: if request.auth != null;
    }
  }
}
```

If collections do not exist yet, create them manually (at minimum create the `"[User] Contracts"` collections) and add a test document.

---

### Storage layout for reports

Contract report viewing expects year-based root folders in Firebase Storage:

- `Reports25/`
- `Reports26/`
- `Reports27/`
- …

Each `ReportsYY` folder may contain PDFs directly or be organized into month subfolders:

```
Reports26/
  January/
    <contract-name>_....pdf
  February/
    <contract-name>_....pdf
  <contract-name>_....pdf
```

The “View Reports” action:

- prompts for **year**, then
- searches only inside the selected `ReportsYY` folder,
- matching is flexible (ignores case/spaces/underscores).

---

### Environment variables & build configuration

This project supports build-time configuration via **Gradle properties → BuildConfig** (see `AppConfig.java`).

#### Key files

- `gradle.properties.template` (start here)
- `gradle.properties` (**not committed**; ignored by git)
- `app/build.gradle.kts` (defines BuildConfig fields)
- `AppConfig.java` (central accessors)

#### Quick start (recommended scripts)

Windows examples:

```batch
:: Production
build-with-env.bat -e production -v 1.0.0

:: Development
build-with-env.bat -e development -f my-dev-project

:: Staging
build-with-env.bat -e staging -v 1.1.0 -f staging-project
```

Linux/macOS examples:

```bash
./build-with-env.sh -e production -v 1.0.0
./build-with-env.sh -e development -f my-dev-project
./build-with-env.sh -e staging -v 1.1.0 -f staging-project
```

#### Manual configuration

1) Run setup script (creates a local `gradle.properties` from the template):

```bash
setup-env.bat
# or
./setup-env.sh
```

2) Edit `gradle.properties` as needed (examples):

```properties
# Firebase Configuration
FIREBASE_PROJECT_ID=your-project-id
FIREBASE_API_KEY=your-api-key
FIREBASE_STORAGE_BUCKET=your-project-id.appspot.com

# API Configuration (if used)
API_BASE_URL=https://your-api-url.com
APP_ENVIRONMENT=production

# Default credentials (optional/offline flows)
DEFAULT_USER_EMAIL=
DEFAULT_USER_PASSWORD=
```

3) Build:

```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

Important: environment variables are embedded at build time; changing them requires rebuilding the APK.

---

### Cloud Functions (optional automation)

Functions live in `functions/` and implement:

- scheduled cleanup (e.g. message retention)
- other server-side automation you may choose to enable

Typical deployment:

```bash
cd functions
npm install
firebase deploy --only functions
```

This repository uses **in-app only notifications**; FCM push sends are intentionally disabled.

Important: **in-app notification records are written by the Android client** (Firestore writes to `notifications/{user}/items`).
Some function triggers that previously wrote the same records are intentionally disabled to avoid duplicates.

---

## Operational behavior

### Work reminders

Push/system reminders are disabled by design. Any reminder behavior should be implemented as **in-app only** signals.

---

## Security and repository hygiene

Do **not** commit:

- `app/google-services.json`
- service account credentials (`service_account.json`, `*service_account*.json`)
- keystores/signing files (`*.jks`, `*.keystore`, `signing.properties`, etc.)
- `.env` files, `gradle.properties`, or any secrets
- build outputs (`app/build/`, `*.apk`, `*.aab`)

The included `.gitignore` is configured to prevent this. If sensitive files were committed previously, remove them from git history and rotate the secrets.

---

## Troubleshooting

### “Access denied” in Contracts

- Confirm Firestore rules allow reads for `"[User] Contracts"` collections.
- Confirm collection names match exactly: `[User] Contracts` per technician (e.g. from StaffDirectory.getContractsCollectionName(id)).

### “No reports found”

- Confirm Storage has the year folders (`Reports25`, `Reports26`, …).
- Confirm filenames contain the contract name (matching ignores spaces/underscores/case).

### Notifications not appearing

- Confirm Firestore writes are succeeding to `notifications/{user}/items`.
- Confirm you’re not expecting push/system notifications (this app uses in-app only).

### Environment variables not updating

- Values are embedded at build time. Run `./gradlew clean` and rebuild the APK after changing `gradle.properties`.

### Work View widget blank or not updating

- See **`app/src/main/assets/WIDGET_TROUBLESHOOTING.md`**. In short: do not add drawables/PNG to the widget layout; open Work View once to populate the cache; remove and re-add the widget to force an update; check Logcat for tag `WorkViewWidget` if the widget fails to update.

---

## Branding

### App logo

- **Android UI logo**: `app/src/main/res/drawable/logo.png`
  - Used on the **Login** screen and next to the **Welcome** text on the Home screen.
  - Recommended: square-ish PNG, at least \(200 \times 200\) px, transparent background if possible.

### App name / colors

- **App name and text**: `app/src/main/res/values/strings.xml`
- **Brand colors**: `app/src/main/res/values/colors.xml` and `app/src/main/res/values-night/colors.xml`
- **Theme**: `app/src/main/res/values/themes.xml` and `app/src/main/res/values-night/themes.xml`

---

## Support

If you need help setting up or operating this app:

- **Best place to start**: check `README.md` → **Setup**, **Troubleshooting**, and **Branding**
- **Bug reports**: include
  - the screen name + steps to reproduce
  - what you expected vs what happened
  - device + Android version
  - relevant Firestore document IDs (if applicable)
  - logs (Android Studio Logcat) with any errors

If you’re sharing this repo with third parties, ensure `google-services.json`, keystores, and any secrets remain **out of git**.
