# GRPC (GRPest Control) — Android + Firebase Platform

This repository contains a production-style Android application used by GRPest Control staff to manage **contracts**, **jobs**, **work scheduling**, **reports**, **messaging**, **leads/commission**, and **in-app notifications**, backed by **Firebase (Firestore + Storage)**. **Cloud Functions are optional** for automation (cleanup/schedules).

The solution is intentionally designed around **in-app notifications only** (no FCM push notifications and no Android system notifications) to keep notification delivery consistent and fully under application control.

---

## Contents

- [Key capabilities](#key-capabilities)
- [Roles and user model](#roles-and-user-model)
- [How to use the app (by user)](#how-to-use-the-app-by-user)
- [Technology stack](#technology-stack)
- [Repository layout](#repository-layout)
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
  - **Ian/Kristine** can view and manage multiple technicians’ work views (combined calendar behavior).
- **In-app reminders** (no system notifications):
  - WorkManager schedules an **in-app notification record** ~30 minutes before scheduled events.
  - Reminders are de‑duplicated and cancelled when events are completed/rescheduled.
- **WorkView: custom times and drag-and-drop**
  - **Create with custom duration:** Add Job/Contract/Follow‑up via Work View. When the time dialog opens, choose **“Custom time…”**. Enter start (e.g. 08:30 or 0830) and end (e.g. 15:00 or 1500) → event is created as 08:30–15:00.
  - **Adjust an existing event:** Tap the event → **Edit Time**. Change start and optionally end (e.g. 09:30–14:30) and save; duration is preserved if you only change one.
  - **Move an event (drag-and-drop):** In daily view, long‑press an event, drag it to another time slot bar, and release. Start time snaps to that slot; duration is preserved if the event had an end time.

### Contracts

- Contracts are stored per technician in Firestore collections named `"[User] Contracts"` (e.g. `Ian Contracts`).
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
  - Ian/Kristine can add contracts to another technician’s list (and the assigned technician receives an in‑app notification record).

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
- **Storage model**:
  - Reports are organized in Firebase Storage under year folders like `ReportsYY/` (optionally with month subfolders).
  - Reports can also be generated/stored locally under the app’s external files directories (e.g. quotes, service agreements).
- **Viewing and sharing**:
  - Browse stored report lists, download/share PDFs.
  - Contract-specific search is scoped by year folder for speed and organisation.

### Quotations & service documents

- **Quotation generation**:
  - Point-based quotations (4pt/6pt/8pt/12pt).
  - General quotations and Bird quotations with line items.
  - Professional formatting with VAT/line totals and consistent branding.
- **Service agreements**:
  - Create and view service agreements with signature fields and stored PDFs.

### Leads & commission

- Lead capture and tracking in Firestore `Leads` with commission calculations and invoice/payment status fields.
- Admin-side edit flows can trigger in-app notifications to the affected technician (commission change audit trail behavior).

### Action Forms (password-protected PDFs)

- Generate **password-protected Action Form PDFs** (owner-password protected for editing restrictions) using iText.
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

### In-app notifications (no push)

- Notification history is stored in Firestore under `notifications/{user}/items`.
- Home screen shows unread indicators for notifications/messages.
- Notifications can be deleted individually or cleared.
- Tapping a notification deep-links into the relevant screen (Work View / Contracts / Jobs / Messaging) and can open location context (Maps).
- **Design principle**: notifications are intentionally **in-app only** (no Android status-bar notifications, no FCM push requirement for core flows).

### Dark Mode

- Theme-aware UI using Material DayNight and attribute-based colors (`?android:attr/colorBackground`, `?attr/colorSurface`, `?android:attr/textColorPrimary/Secondary`) so screens render correctly in Light/Dark modes.
- Layouts avoid hardcoded white/black and instead use theme surfaces/“on-surface” text, with consistent button styling.

---

## Roles and user model

This app is role-aware and uses simple “known user” rules for what each staff member can see/do.

- **Admins / oversight**: **James**, **Ian**, **Kristine**
  - Can assign work to other technicians (jobs/contracts).
  - Can delete contracts and jobs.
  - Receive oversight notifications for certain actions (e.g. when Dean adds contracts).
- **Technician**: **Dean**
  - Can complete work, create reports, and add contracts to his own list.
  - **Cannot delete contracts or jobs** (the app will prompt to contact Ian/Kristine).
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
  - Use the Assign-to dropdown (James / Dean / Ian) to put the contract into the correct technician’s list.
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

### (Admin / Owner)

- Everything in Ian’s section, plus:
  - **James-only features (currently)**:
    - **Global Search** (search across Jobs/Contracts/Leads/Reports in one place).
- **Commission / Leads**:
  - Generate Lead / View Leads to manage commission tracking and invoice status.
  - Lead updates can notify the assigned technician.

### (Admin / Office)

- **Assign jobs** (Job Work / Management Jobs) using the dropdown to avoid typos.
- **Assign contracts** to James/Dean/Ian via the Assign-to dropdown.
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
    - “To delete a contract get in touch with Ian or Kristine.”
    - “To delete a job get in touch with Ian or Kristine.”

---

## Technology stack

- **Android**: Java, XML layouts, AppCompat, RecyclerView, etc.
- **Firebase**:
  - **Firestore**: primary database (contracts, jobs, leads, messages, notifications, workview).
  - **Storage**: PDF report storage (`ReportsYY/...`).
  - **Cloud Functions (Node.js)**: optional automation (cleanup/schedules).
- **PDF**: iText (report generation; Action Forms support encryption).

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
- Confirm collection names match exactly: `Ian Contracts`, `James Contracts`, `Dean Contracts`, `Kristine Contracts`.

### “No reports found”

- Confirm Storage has the year folders (`Reports25`, `Reports26`, …).
- Confirm filenames contain the contract name (matching ignores spaces/underscores/case).

### Notifications not appearing

- Confirm Firestore writes are succeeding to `notifications/{user}/items`.
- Confirm you’re not expecting push/system notifications (this app uses in-app only).

### Environment variables not updating

- Values are embedded at build time. Run `./gradlew clean` and rebuild the APK after changing `gradle.properties`.

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
