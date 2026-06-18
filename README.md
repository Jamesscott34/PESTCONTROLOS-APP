# PestControlOS — Android Field Operations Platform

**Version:** 2.1.2 &nbsp;|&nbsp; **Min SDK:** 27 (Android 8.1) &nbsp;|&nbsp; **Target SDK:** 35 &nbsp;|&nbsp; **Language:** Java

PestControlOS is a multi-tenant Android application built for pest control field operations. It covers the full service lifecycle: scheduling, contracts, job management, PDF report generation, quotations, service agreements, environmental risk assessments, invoicing, team messaging, lead tracking, site mapping, and staff location monitoring — all backed by Firebase and producing professional PDFs on-device via iText7.

The platform supports multiple independent tenants through Android product flavours (each with its own Firebase project), a full offline mode, and a time-limited demo mode. A companion web CRM shares the same Firestore backend.

---

## Table of Contents

1. [Architecture overview](#architecture-overview)
2. [Feature overview](#feature-overview)
3. [Build flavours](#build-flavours)
4. [User roles and permissions](#user-roles-and-permissions)
5. [Main screen — button reference](#main-screen--button-reference)
6. [Report creation hub](#report-creation-hub)
7. [Admin dashboard](#admin-dashboard)
8. [Data model](#data-model)
9. [Technology stack](#technology-stack)
10. [Project structure](#project-structure)
11. [Setup](#setup)
12. [CI/CD](#cicd-github-actions)
13. [Security](#security)
14. [Background workers](#background-workers)
15. [Offline mode](#offline-mode)
16. [Multi-tenant deployment](#multi-tenant-deployment)
17. [Troubleshooting](#troubleshooting)
18. [Backlog](#backlog)

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Android Client (Java)                        │
│                                                                     │
│  UI Layer          Business Logic        Data / Workers             │
│  ─────────         ──────────────        ────────────               │
│  Activities        SessionManager        WorkManager tasks          │
│  RecyclerViews     StaffDirectory        LocationSharing            │
│  Canvas views      TenantBranding        ReportDatabaseHelper       │
│  PDF previewer     ContractReportSync    WorkViewLocalEventStore     │
└───────────┬──────────────┬──────────────────────┬───────────────────┘
            │              │                      │
     ┌──────▼──────┐ ┌─────▼──────┐      ┌───────▼────────┐
     │  Firebase   │ │  Firebase  │      │ Firebase       │
     │    Auth     │ │ Firestore  │      │ Storage        │
     │(email/pwd)  │ │(real-time) │      │(PDFs, images)  │
     └─────────────┘ └─────┬──────┘      └────────────────┘
                           │
                    ┌──────▼──────────┐
                    │ Cloud Functions  │
                    │ (Node.js —      │
                    │  message cleanup│
                    │  scheduled jobs)│
                    └─────────────────┘
                           │
                    ┌──────▼──────────┐
                    │  External APIs  │
                    │  Google Maps    │
                    │  Google Places  │
                    └─────────────────┘
```

**Identity resolution on login:**
`Firebase Auth UID → users/{uid} (StaffID lookup) → users/{StaffID} (profile + RBAC) → SessionManager (in-memory + SharedPreferences cache)`

**Offline persistence:** Firestore disk cache (`PersistentCacheSettings`) is enabled at app start. All previously fetched data is served from SQLite when offline; writes are queued and synced automatically on reconnect.

See [`ARCHITECTURE.md`](ARCHITECTURE.md) for the full component diagram and Firestore data-flow documentation.

---

## Feature Overview

| Feature | Description |
|---------|-------------|
| **Work View** | Daily calendar (08:00–17:30) with half-hour slots. Add, edit, and drag jobs, contract visits, and follow-ups. Admins see all technicians combined. Home screen widget shows the next 3 events. WorkManager reminders fire ~30 min before each event. |
| **Contracts** | Per-technician contract collections with Behind / Due / Up-to-date status counters and colour coding. Mark routine done, log call-outs, view per-year visit totals, view individual visit history timeline, create linked reports, and navigate to site via Maps. Behinds and Due list PDF export. Admin can reassign contracts between technicians. |
| **Visit History Timeline** | Each contract maintains a `visitHistory` subcollection. Every routine or call-out confirmation writes a timestamped log entry (type, date, technician). The "View Visits" button shows a scrollable chronological list alongside yearly counter totals. |
| **Job Work** | Admins create and assign jobs; technicians accept and complete them. Management jobs track internal office tasks. All jobs feed into Work View and trigger in-app notifications on assignment. |
| **Service Reports** | Rodent (initial, routine, call-out, external-routine), general (4/6/8/12-point), and action form reports. iText7 PDFs with company logo, optional owner-password encryption, customer signatures, and photo attachments. AI Fix rewrites selected text to professional standard. |
| **Quotations** | Point-based pest control quotes (4/6/8/12pt), bird-control quotes with optional 30% deposit, catalog-driven general quotes, and custom line-item quotes. Full VAT calculation and PDF generation. |
| **Service Agreements** | Formal agreements with dual signature fields (technician + client). Generated as encrypted PDF and uploaded to Firebase Storage. |
| **Environmental Risk Assessment (ERA)** | Toxic, Non-Toxic, and Bird-Proofing ERA workflows with full PDF output for regulatory compliance. |
| **Invoicing** | Create invoices linked to contracts. Templates for initial setup, monthly maintenance, feature update, and custom. VAT/totals preview. Uploaded to Storage and written to Firestore ledger. |
| **Leads / Commission** | Technicians log leads with commission values. Admins mark invoices paid and update materials costs. In-app notifications on updates. |
| **Site Maps** | Draw and annotate site floor plans on a custom canvas (`SiteMapCanvasView`). Import a photo or floor plan image as the canvas background via **Set Background**. Add pest control markers (internal, external, fly units, insect), lines, shapes, and text labels. Saved as a full-page landscape PDF. Maps are stored and listed per contract. |
| **PDF Editor** | Annotate existing PDFs with freehand ink and text overlays. Multi-page viewer with per-page annotation layer. Export a flattened PDF. |
| **PDF Converter** | Convert any PDF to a Word (.docx) document or extract plain text. Uses ML Kit OCR for scanned pages. Gated by `canConvert` permission. |
| **Cloud File Browser** | Firebase Storage drill-down browser for reports, contracts, management jobs, and general folders. Multi-select with action bar. Long-press selects all visible files. Move selected files to any folder with a live "X files remaining" counter in the action bar. Delete selected files. Upload new files. |
| **Messaging** | Firestore-based 1:1 and group chat. Urgent flag preserves messages from Cloud Function cleanup. |
| **Speech Dictation** | `DictateEditText` widget adds a **🎤 Dictate** button below each text field (shown on focus only). Speech is handled inside the widget — the mic never overlaps the input. Used in service reports and action forms. |
| **Notifications** | In-app notification inbox per user with deep links into Jobs, Contracts, Work View, Messaging, and Leads. **Preferences** screen lets each user filter by notification type (contract reminders, job updates, daily PDFs, etc.). Disabled types are hidden from the inbox but remain in Firestore. |
| **First-Login Onboarding** | Four-slide walkthrough (`OnboardingActivity`) shown once per Firebase auth UID on first login. Covers Work View, reports/maps, and notifications. Skippable; not shown for offline users. |
| **Daily Summary Card** | `MainActivity` shows a once-per-day card (keyed by auth UID) with overdue contracts, contracts due within 7 days, and open assigned jobs. Dismissible; not shown for offline users or when all counts are zero. |
| **Location Finder** | Admin-only view of all staff last known GPS positions. Updated every ~10–15 minutes by `LastLocationUpdateWorker` (requests a fresh GPS fix when last-known location is stale). `LastLocationCleanupWorker` marks records as `stale` after 30 minutes but never deletes them — the last position always remains visible. Stale locations show a warning with age in minutes. Falls back to local cache when offline. |
| **Global Search** | Admin search across jobs, contracts, leads, and reports in Firestore. Gated by `canSearch` permission flag. |
| **Route Planner** | Multi-stop route optimisation via `RouterActivity` and `GoogleDirectionsClient`. PDF export of the best route via `BestRoutePdfGenerator`. |
| **Bug / Feature Requests** | Staff submit bugs or feature requests. Super_admin sets cost, days, and status. Clients agree or disagree on feature quotes. Full lifecycle management with long-press edit. |
| **Employee Management** | Super_admin creates, edits, and deactivates staff. Sets role and individual `Can*` permission flags. |
| **Admin Dashboard** | Contracts summary, users list, bug/feature request tracker, Storage folder management, and upload/download metrics. |
| **Offline PDF Templates** | Offline users build custom report headers with logo, watermark, colour choice, and ordered header blocks. Templates are saved locally and reused across sessions. |
| **Email Compose** | In-app email composition with configurable templates. Can attach PDFs from Storage or local files. |
| **Home Screen Widget** | Displays the next 3 Work View events. Updated automatically whenever Work View changes. |
| **Session Management** | RBAC session loaded from Firestore on login and persisted to SharedPreferences. Remember Me (7-day token) is auto-enabled on every successful login and cleared on explicit logout. 5-minute background timeout triggers re-authentication. |

---

## Build Flavours

The app uses a single `tenant` flavour dimension. Each flavour targets a separate Firebase project via its own `google-services.json`.

| Flavour | Notes |
|---------|-------|
| `company1` | Primary production tenant. Base `applicationId` unchanged. |
| `company2` | Separate production tenant. Suffix `.company2`. |
| `company3` | Separate production tenant. Suffix `.company3`. |
| `company4` | Separate production tenant. Suffix `.company4`. |
| `company5` | Separate production tenant. Suffix `.company5`. |
| `demo` | Firebase access expires after a configurable number of days. Max 3 saved offline templates. 30-day offline trial then redirect. |
| `offline` | No Firebase. Local SQLite and file system only. Max 3 saved offline templates. 30-day trial then redirect. |

Runtime build flags (`BuildConfig`): `IS_OFFLINE`, `IS_DEMO`, `OFFLINE_TRIAL_DAYS`, `DEMO_FIREBASE_EXPIRY_DAYS`, `MAX_SAVED_TEMPLATES`.

---

## User Roles and Permissions

Role is stored in `users/{StaffID}` and normalised to `tech`, `admin`, or `super_admin`.

| Capability | `tech` | `admin` | `super_admin` |
|------------|:------:|:-------:|:-------------:|
| Create / view reports | ✓ | ✓ | ✓ |
| Work View (own schedule) | ✓ | ✓ | ✓ |
| Contracts (own) | ✓ | ✓ | ✓ |
| Jobs (accept, complete) | ✓ | ✓ | ✓ |
| Notifications | ✓ | ✓ | ✓ |
| Admin Dashboard | — | ✓ | ✓ |
| Assign contracts / jobs | — | ✓ | ✓ |
| View all technicians' Work View | — | ✓ | ✓ |
| Delete jobs / contracts | — | ✓ | ✓ |
| Commission / Leads | `canAccessCommissionLeads` | ✓ | ✓ |
| Messaging | `canMessage` | `canMessage` | ✓ |
| Global search | `canSearch` | `canSearch` | ✓ |
| Location Finder | — | `canUseLocationFinder` | ✓ |
| Move cloud files | `canMove` | `canMove` | ✓ |
| Employee management | — | — | ✓ |
| Bug/feature lifecycle | — | Submit + view | Full |
| PDF converter | `canConvert` | `canConvert` | ✓ |
| PDF editor | `canConvert` | `canConvert` | ✓ |
| Invoicing | `canInvoice` | ✓ | ✓ |

Optional `Can*` flags on the user document override role defaults: `canSearch`, `canMessage`, `canMap`, `canRoute`, `canUseLocationFinder`, `canAccessCommissionLeads`, `canBugReport`, `canConvert`, `canInvoice`, `canMove`.

---

## Main Screen — Button Reference

| Button | Destination | Visibility |
|--------|-------------|------------|
| Notifications | `NotificationsActivity` (+ **Preferences** for type filters) | All authenticated users |
| Search | `SearchActivity` | `canSearch` |
| Dashboard | `AdminDashboardActivity` | admin, super_admin |
| Employee | `EmployeeManagementActivity` | super_admin |
| Create Report | `ReportSelectionActivity` | All users |
| View Reports | `PDFSelectionActivity` | All users |
| Work View | `WorkViewActivity` | All authenticated users |
| Location Finder | `LocationFinderActivity` | `canUseLocationFinder` |
| Contracts | `ContractsActivity` | All authenticated users |
| Commission | `LeadsSelectionActivity` | `canAccessCommissionLeads` |
| Job Work | `JobsActivity` | All authenticated users |
| Messaging | `MessagingConversationsActivity` | `canMessage` |
| Maps | `MapsPlaceholderActivity` | `canMap` |
| Visit (website) | Browser — tenant staff portal | Configurable per flavour |
| How to Use App | `HelpReadmeActivity` | All (hidden if demo expired / offline) |
| Logout | `LoginActivity` | All users |

---

## Report Creation Hub

`ReportSelectionActivity` routes to:

| Option | Activity |
|--------|----------|
| Service Report | `ReportActivity` |
| General Report | `GeneralReportActivity` |
| Action Form | `ActionFormActivity` |
| Contract Quotations (4/6/8/12pt) | `QuotesActivity` |
| Bird Quote | `BirdQuotationActivity` |
| Custom Quote | `GeneralQuotationActivity` |
| General Quotation (catalog) | `GeneralQuotationFromCatalogActivity` |
| Service Agreement | `ServiceAgreementActivity` |
| ERA | `EnvironmentSelectionActivity` → Toxic / Non-Toxic / Bird-Proofing |
| Custom Template Settings | `PdfTemplateSettingsActivity` |
| Create Custom Report | `ReportActivity` with `USE_MY_TEMPLATE` flag |

---

## Admin Dashboard

`AdminDashboardActivity` — visible to admin and super_admin.

- **Contracts summary** — per-user contract counts and totals.
- **Users summary** — list all staff; tap to change role.
- **Bug / Feature requests** — submit, view, set cost/days/status, mark complete, client agree/disagree, long-press edit (super_admin only for management actions).
- **Reports folders** — create `ReportsYY` folder in Firebase Storage.
- **Storage metrics** — upload/download counts and file sizes (super_admin).

---

## Data Model

### Firestore Collections

| Collection / Path | Purpose |
|-------------------|---------|
| `users/{StaffID}` | Authoritative staff profile: Role, Name, Email, Number, ContractKey, Can* flags, rememberMeUntilMs, canRemember. |
| `users/{uid}` | Maps Firebase Auth UID → StaffID on login. |
| `{ContractKey} Contracts/{contractId}` | Per-technician contracts: client name, address, status, last/next visit, yearly visit counters. |
| `{ContractKey} Contracts/{contractId}/visitHistory/{id}` | Individual visit log: type (Routine/Callout), date, tech name, server timestamp. |
| `JobWork/{jobId}` | Service jobs: customer details, assigned tech (ContractKey), status flags, follow-up data. |
| `user_workview/{tech}/{eventId}` | Work View events by technician: type, start/end timestamps, metadata. |
| `Leads/{leadId}` | Leads with commission values, payment status, invoice number, materials cost. |
| `notifications/{StaffID}/items/{itemId}` | In-app notification records with type, deep-link payload, and timestamp. |
| `messages/{conversationId}/{messageId}` | Chat messages: sender, content, timestamp, urgent flag. |
| `last_locations/{userKey}` | Last GPS position per technician: lat, lng, accuracy, timestamps, `stale` flag (set after 30 min without update; document is never deleted). |

### Identity Resolution

On login: `UID → StaffID` → load `users/{StaffID}` → populate `SessionManager` with Role, ContractKey, and Can* flags. ContractKey is the internal identifier used for collection names, job assignment, message routing, and Work View. On logout: session, staff cache, Work View cache, widget cache, and location cache are all cleared to prevent cross-user data leakage on shared devices.

---

## Technology Stack

| Layer | Details |
|-------|---------|
| Language | Java (Android SDK) |
| UI | XML layouts, RecyclerView, Material Components, custom Canvas views |
| Auth | Firebase Authentication (email/password) |
| Database | Firestore with `PersistentCacheSettings` disk cache for offline support |
| Storage | Firebase Storage (PDFs, images, maps) |
| Background | WorkManager (reminders, location, widget sync) |
| Release builds | R8 code shrinking + resource shrinking (`isMinifyEnabled = true`) with ProGuard rules for Firebase, iText7, POI, ML Kit, OkHttp, and Firestore model classes |
| PDF generation | iText7 7.1.15 (kernel, layout, io) |
| PDF to Word | Apache POI OOXML |
| PDF OCR | Google ML Kit Text Recognition |
| Location | Google Play Services Fused Location Provider |
| Networking | OkHttp (AI API calls) |
| Maps / routing | Google Maps SDK + Directions API |
| App Check | Play Integrity (production), Debug provider (debug builds) |
| Cloud Functions | Node.js (message cleanup, scheduled admin tasks) |

---

## Project Structure

```
grpc/
├── app/
│   └── src/
│       ├── main/
│       │   ├── java/com/grpc/grpc/
│       │   │   ├── admin/          # AdminDashboardActivity, EmployeeManagement
│       │   │   ├── billing/        # Invoice creation, PDF generator, list
│       │   │   ├── bugreport/      # Bug/feature request submit and view
│       │   │   ├── contracts/      # ContractsActivity, ViewContract, AddContract,
│       │   │   │                   #   BehindsListView, PDF generators
│       │   │   ├── converter/      # PDF → Word and PDF → text
│       │   │   ├── core/           # SessionManager, StaffDirectory, DictateEditText,
│       │   │   │                   #   TenantBranding, RememberMeManager, helpers
│       │   │   ├── email/          # EmailComposeActivity, EmailTemplateService
│       │   │   ├── era/            # Toxic / NonTox / BirdProofing ERA + PDF generators
│       │   │   ├── files/          # FolderContentsActivity, HelpReadmeActivity
│       │   │   ├── generalreports/ # General 4/6/8/12pt report activities
│       │   │   ├── jobs/           # JobsActivity, AddJobs, ViewJob, Management jobs
│       │   │   │   └── rodent/     # Rodent-specific job workflows
│       │   │   ├── leads/          # LeadsSelectionActivity, ViewLeads, GenerateLeads
│       │   │   ├── location/       # LocationFinderActivity, LocationSharing, workers
│       │   │   ├── login/          # LoginActivity (auto Remember Me)
│       │   │   ├── main/           # MainActivity (daily summary card)
│       │   │   ├── maps/           # SiteMapEditorActivity, SiteMapCanvasView,
│       │   │   │                   #   MapsListActivity, MapsUtil (full-page PDF)
│       │   │   ├── messaging/      # MessagingActivity, Conversations, Notifications,
│       │   │   │                   #   NotificationPreferencesActivity
│       │   │   ├── onboarding/     # OnboardingActivity (first-login walkthrough)
│       │   │   ├── pdfeditor/      # PdfEditorActivity, overlay, page adapter
│       │   │   ├── quotations/     # BirdQuotation, GeneralQuotation, catalog
│       │   │   ├── reports/        # ReportActivity, PDFReportGenerator, ActionForm,
│       │   │   │                   #   FollowUp, templates, CloudStorageBrowserActivity
│       │   │   ├── routes/         # RouterActivity, BestRoutePdfGenerator
│       │   │   ├── safety/         # SafetyStatementActivity + PDF generator
│       │   │   ├── search/         # SearchActivity, GlobalSearchAdapter
│       │   │   ├── serviceagreements/ # ServiceAgreementActivity + PDF generator
│       │   │   └── workview/       # WorkViewActivity, widget, events, workers
│       │   │
│       │   └── assets/
│       │       ├── product_lists.json
│       │       ├── recommendations.json
│       │       ├── sales.json
│       │       └── service_report_templates.json
│       │
│       ├── grpc/           # Production flavor: google-services.json (gitignored)
│       ├── demo/           # Demo flavor: google-services.json (gitignored)
│       ├── offline/        # Offline flavor (no Firebase)
│       ├── company2/       # Tenant flavor
│       ├── company3/       # Tenant flavor
│       ├── company4/       # Tenant flavor
│       └── company5/       # Tenant flavor
│
├── functions/              # Firebase Cloud Functions (Node.js, optional)
├── firestore.rules         # Firestore security rules
├── firestore.indexes.json  # Composite index definitions
├── storage.rules           # Firebase Storage security rules
├── ARCHITECTURE.md         # Full technical architecture and data-flow docs
├── gradle.properties.template  # Copy to gradle.properties and fill in keys
├── .github/workflows/android.yml  # CI: lint, unit tests, debug APK on push/PR to main
└── build-with-env.sh / .bat    # Helper scripts for CI key injection
```

---

## Setup

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Firebase project with Authentication (email/password), Firestore, and Storage enabled
- Google Maps API key

### Steps

**1. Clone**
```bash
git clone <repo-url>
cd grpc
```

**2. Configure keys**

Copy the template and fill in your values:
```bash
cp gradle.properties.template gradle.properties
```
```properties
MAPS_API_KEY=your_google_maps_key
FIREBASE_PROJECT_ID=your-project-id
FIREBASE_API_KEY=your-api-key
```
`gradle.properties` is gitignored. Never commit it.

**3. Add `google-services.json`**

Place the correct file for each flavour:
```
app/src/grpc/google-services.json
app/src/demo/google-services.json
app/src/company2/google-services.json
# etc.
```
All `google-services.json` files are gitignored.

**4. Firebase Console setup**

- Enable Firestore and Storage.
- Create Storage folders: `Reports25/`, `Reports26/`, etc. (or use the in-app Dashboard).
- Seed the first super_admin user at `users/{StaffID}` with `Role: super_admin` and all `Can*` flags set to `true`.
- Enable App Check with Play Integrity for production.

**5. Deploy Firestore rules**
```bash
firebase deploy --only firestore:rules,firestore:indexes
```

**6. (Optional) Deploy Cloud Functions**
```bash
cd functions && npm install && firebase deploy --only functions
```

**7. Build**
```bash
# Debug
./gradlew assembleGrpcDebug

# Release (requires signing config in gradle.properties)
./gradlew assembleGrpcRelease
```

Available variants: `Company1`, `Company2`, `Company3`, `Company4`, `Company5`, `Demo`, `Offline`.

### CI/CD (GitHub Actions)

The workflow at `.github/workflows/android.yml` runs on every push and pull request to `main`:

1. Lint (`lintCompany1Debug`)
2. Unit tests (`testCompany1DebugUnitTest`)
3. Assemble debug APK (`assembleCompany1Debug`)
4. Upload APK artifact (7-day retention)

**Required repository secrets** (Settings → Secrets and variables → Actions):

| Secret | Purpose |
|--------|---------|
| `GOOGLE_SERVICES_JSON` | Base64-encoded `app/src/company1/google-services.json` |
| `MAPS_API_KEY` | Google Maps API key |
| `FIREBASE_PROJECT_ID` | Firebase project ID |

Encode the JSON file:
```bash
base64 -i app/src/company1/google-services.json | tr -d '\n'
```

At build time the workflow injects `google-services.json` and appends keys to `gradle.properties` from `gradle.properties.template`.

---

## Security

- **Never commit** `google-services.json`, `gradle.properties`, API keys, keystores, or service account files. All are covered in `.gitignore`.
- **Firestore rules** must restrict reads/writes by `request.auth.uid` and role. The `firestore.rules` file is the reference. Never deploy `allow read, write: if true` to production.
- **App Check** is wired up — enforce it in Firebase Console for Firestore and Storage to block unauthorised clients.
- **ProGuard/R8 is enabled for release builds** (`isMinifyEnabled = true`, `shrinkResources = true`). Keep rules in `app/proguard-rules.pro` cover Firebase, iText7, Apache POI, ML Kit, OkHttp, WorkManager, and Firestore model classes. Mapping files are written to `app/build/outputs/mapping/` after each release build.
- **Local data** (offline reports, SQLite, SharedPreferences) is not encrypted at rest. For regulated deployments use `EncryptedSharedPreferences` and SQLCipher.
- **MFA** is strongly recommended for admin and super_admin accounts. Firebase Authentication supports TOTP MFA.
- **Rotate** all API keys (LLM, Maps, Firebase service accounts) on a regular schedule.
- **Firestore subcollection rules** — if you implement visit history or other subcollections, explicitly add security rules for the subcollection path, as parent collection rules do not automatically cascade.

---

## Background Workers

| Worker | Trigger | Purpose |
|--------|---------|---------|
| `InAppReminderWorker` | Scheduled per event | Shows in-app reminder ~30 min before a Work View event. |
| `WorkViewPopupReminderWorker` | Scheduled per event | Shows immediate popup when an event is due. |
| `WorkViewWidgetHelper` | On every Work View change | Updates the home screen widget with the next 3 events. |
| `LastLocationUpdateWorker` | Periodic (~10–15 min) | Writes device GPS to `last_locations/{userKey}` in Firestore. Requests a fresh `getCurrentLocation` fix when `getLastLocation()` returns null. Caches locally for offline display. |
| `LastLocationCleanupWorker` | Periodic (~30 min) | Sets `stale: true` on location documents older than 30 minutes. Documents are never deleted — admins always see the last known position. |
| `ContractReminderWorker` | Scheduled | Sends in-app reminders for contracts approaching their due date. |

All workers use Android WorkManager. Disable any by removing the scheduling call in `LocationSharing` or `WorkViewPopupReminderScheduler`.

---

## Offline Mode

When offline login is used (offline flavour or offline login button), no Firestore or Storage calls are made. Everything reads and writes to local SQLite (`ReportDatabaseHelper`), SharedPreferences, and the app's files directory.

**Available offline:** create and view all report types (rodent, general, quotations, ERA, service agreements, action forms), custom PDF templates, and viewing locally stored PDFs.

**Not available offline:** Work View, Contracts, Jobs, Messaging, Notifications, Firebase Storage upload, AI Fix / Chat, Location Finder.

**Firestore offline persistence** (online users): Firestore's `PersistentCacheSettings` disk cache is enabled at app start for all non-offline flavours. Previously fetched data is served from the local SQLite cache when connectivity is lost. Writes are queued and synced automatically on reconnect. This means contract lists, job data, and user profiles remain visible even when connectivity drops mid-shift.

When returning online after an offline session, previously generated PDFs can be uploaded from **Stored Reports**.

---

## Multi-Tenant Deployment

### Option A — Separate Firebase Projects (Recommended)

Each client company gets its own Firebase project, `google-services.json`, and flavour in `build.gradle.kts`. Data is fully isolated, security rules are simple, and tenants scale independently.

### Option B — Multi-Tenant SaaS (Single Project)

Add a `companyId` field to every Firestore document and enforce tenant isolation in security rules. Requires composite indexes on every query. Higher operational efficiency, significantly higher rule complexity.

---

## Troubleshooting

| Symptom | Check |
|---------|-------|
| "Access Denied" on Contracts | Firestore rules for `{ContractKey} Contracts`; user's ContractKey matches the collection name; collection exists. |
| No reports in View Reports | Storage folder for the current year (e.g. `Reports26/`) exists; PDFs were uploaded; offline users should check local files. |
| Notifications not appearing | Firestore writes to `notifications/{StaffID}/items` are succeeding; this is in-app only — no FCM push. |
| Wrong names in spinners | `users` documents have correct `ContractKey` and `Name`; clear app data or re-login to flush `StaffDirectory` cache. |
| Work View widget not updating | `WorkViewWidgetHelper.updateWidget()` is called after every save; check WorkManager logs. |
| Location not updating | GPS permission granted; `LastLocationUpdateWorker` is scheduled; Firestore rules allow write to `last_locations/{userKey}`. |
| Location shows as outdated | Expected after 30 min without a device update — `stale` flag is set but coordinates remain. Check the technician device has location permission and is online. |
| Dictate button not visible | Tap the text field first — the **🎤 Dictate** button appears below the field on focus only. |
| Onboarding shows every login | Check `ONBOARDING_SHOWN_{uid}` in SharedPreferences `GRPC`; onboarding is written on first display of `OnboardingActivity`. |
| Daily summary card missing | Card shows once per day per UID; hidden when all counts are zero or user is offline. Requires a valid `ContractKey` in session. |
| Notification type still visible after disabling | Return to `NotificationsActivity` — list reloads on resume. Firestore data is unchanged; only display is filtered. |
| Build fails — `MAPS_API_KEY` missing | Copy `gradle.properties.template` to `gradle.properties` and fill in the key. |
| Remember Me not persisting | `canRemember: true` is written to `users/{uid}` on login; check Firestore rules allow that write. |
| Move files fails silently | Check `finishMoveBatch` is defined in `CloudStorageBrowserActivity`; verify Storage rules allow write to destination folder. |

---

## Backlog

### Quick Wins

- **FCM push notifications.** `FirebaseMessagingServiceGRPC` is wired up but push is not sent. Add a Cloud Function that sends FCM when jobs are assigned, messages arrive, or contracts fall overdue.
- **Swipe-to-refresh on lists.** Wrap `ContractsActivity`, `JobsActivity`, and `StoredReportsActivity` in `SwipeRefreshLayout`.
- **Export leads to CSV.** A single "Export CSV" button in `ViewLeadsActivity` with `FileProvider` share. No backend changes needed.
- **Photo compression.** Compress images before embedding in PDFs and before uploading to Storage (`Bitmap.compress(WEBP_LOSSY, 75, ...)`).
- **Offline report upload queue indicator.** Show a badge on the main screen for PDFs pending upload. Counter stored in SharedPreferences.

### Medium Effort

- **Contract visit history PDF export.** Add an "Export History" button in the visit history dialog that generates a simple PDF listing all visits for that contract.
- **Recurring job scheduling.** Add a "Repeat" option (weekly/monthly/custom) when creating a job. Create future `JobWork` and Work View documents at the chosen interval.
- **Biometric login.** Add `BiometricPrompt` after a successful first email/password login. Pairs well with the existing `RememberMeManager`.
- **Job completion photos.** Add a camera capture step when marking a job done. Photo is attached to the `JobWork` document and optionally embedded in the report.
- **Offline sync queue with auto-upload.** `WorkManager` periodic task detects connectivity and auto-uploads queued PDFs, notifying the user on completion.

### Larger Features

- **Time tracking.** Add "Start job" and "End job" timestamps in `ViewJobActivity`. Surface total hours per technician per week in the Dashboard.
- **Google Calendar sync.** Export Work View events to Google Calendar via the Calendar API.
- **Address autocomplete.** Call Google Places Autocomplete in `AddJobsActivity` and `AddContractActivity` to suggest addresses as the user types.
- **Customer-facing report QR code.** Generate a short-lived signed URL when a report is uploaded; encode it as a QR code on the last PDF page.
- **Equipment maintenance log.** A simple `Equipment` Firestore collection with service intervals and a `MaintenanceDueWorker`.
- **In-app analytics charts.** Firestore-backed charts: contracts by status over time, jobs completed per week, lead conversion rate.

---

## Support

For issues, open a ticket in the repository. Include device logs (with sensitive data removed) and exact steps to reproduce.
