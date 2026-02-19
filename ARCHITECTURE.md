# GRPC Field Operations Platform — Architecture Overview

**Android (Java) + Firebase + Optional Cloud Functions + LLM APIs**

Scope: production-style Android app for scheduling, contracts, jobs, reports (PDF), messaging, leads/commission, in-app notifications, and AI advisory.

---

## High-level stack

| Layer | Technology |
|-------|------------|
| **Client** | Android App (Java) |
| **Auth & data** | Firebase Authentication, Firestore, Firebase Storage |
| **Automation** | Cloud Functions (optional) |
| **AI** | LLM providers (Groq API / Hugging Face API), keys managed by admin via app |

---

## Client: Android app modules

- **Work View** — daily schedule, calendar, event types (jobs, contracts, follow-ups)
- **Contracts** — per-technician contract lists, status (Behind/Due/Up-to-date), report history
- **Jobs** — JobWork lifecycle, assignment, completion, follow-up scheduling
- **Follow-ups** — scheduling and completion
- **Reports & Action Forms** — PDF generation (iText), password encryption, signatures, image attachments
- **Messaging** — 1:1 and group chat
- **Leads & Commission** — lead capture, commission tracking, admin-paid and materials
- **In-app notifications** — notification records (no FCM push or OS notifications)
- **Settings / Admin tools** — API keys (LLM), user/role management, branding

**Role-aware UI:** super-admin (001), admin (002–003), technician (004+). Role stored in Firestore users directory (name, email, phone, title/role).

---

## Data & identity: Firebase and offline

- **Auth:** Firebase Authentication (email/password login). **Offline login** is available: use the app without Firebase as **"Offline User"**. For the offline user, the **main menu shows only Create Report, View Reports, and Logout**; all other buttons are hidden. In **View Reports**, **Stored Reports** is hidden for the offline user.
- **Database:** Firestore  
  - `users` (role/title), `[User] Contracts`, `JobWork`, `user_workview`, `Leads`, messages, `notifications/{user}/items`.
- **Storage:** Firebase Storage for PDFs organised by year folders (`ReportsYY/...`). Metadata links in Firestore for contract report history.

---

## Reports: PDF generation (iText)

- PDF generation using iText with **user-defined password encryption**; optional signature capture and image attachments. **All PDFs are automatically compressed** (no user option).
- **Create Report** supports two modes:
  - **Use GRPC Template** — default logo, watermark, title, fixed body layout.
  - **Use My Template** — same body layout as GRPC; custom logo, optional custom watermark (text or image), and custom header blocks (offline-only, stored locally).

### Offline PDF template (My Template)

- **Entry points:** (1) **Report selection**: **"Custom Report (PDF Template)"** opens PDF Template Settings; **"Create Custom Report"** opens the Create Report form with My Template pre-selected. (2) On **Create Report**, when the user is **not** logged in (offline), a **template selector** and **PDF Template Settings** button are shown.
- **Named templates:** In PDF Template Settings the user can enter a **template name** and **"Save as named template"** to save the current logo, watermark, and header blocks. **"View templates"** lists saved templates; **"Use"** opens the Create Report form with that template applied (same body form; PDF uses that template's headers/logo/watermark on save).
- **Template selector** on Create Report (offline only): [Use GRPC Template] [Use My Template].
- **When logged in** (Firebase Auth): the PDF template section is **hidden** on Create Report; reports always use the GRPC template.
- **PDF Template Settings** screen (offline): configure logo, watermark (on/off, text or image), and ordered header blocks (text with style H1/H2/BODY or image). Settings and named templates stored **locally** (SharedPreferences + files in `pdf_template/`); no Firebase.
- **Body layout** (margins, fonts, sections, footer, page numbers) is identical to GRPC in both modes; only header area and watermark differ when using My Template. If no images are selected for the report, no image section or placeholder is added.

**Key classes:**

| Class | Role |
|-------|------|
| `PdfTemplateSettings` | POJO: templateSelection (GRPC/MY_TEMPLATE), logoPath, watermark (enabled, type, text, imagePath), list of HeaderBlock (blockType, textStyle, text, imagePath). |
| `SavedTemplate` | Named template: id, name, logoPath, watermark*, headerBlocks; `toPdfTemplateSettings()` for PDF generation. |
| `PdfTemplateStorage` | Load/save default settings to SharedPreferences; header blocks as JSON; **saved templates** list (loadSavedTemplates, addSavedTemplate, getSavedTemplateById); asset files in `getFilesDir()/pdf_template/`. |
| `PDFReportGenerator` | `generatePDFReport(...)` (unchanged), `addReportBodyToDocument(document, content, context, imageUris)` (body + optional image section). |
| `PDFReportGeneratorWithTemplate` | `generatePdf(..., settings)`: GRPC → existing generator; MY_TEMPLATE → custom header + custom/default watermark + same body; PDFs always compressed. |
| `PdfTemplateSettingsActivity` | UI for logo, watermark, header blocks; template name; "Save as named template"; "View templates". |
| `ViewTemplatesActivity` | Lists saved templates; "Use" starts ReportActivity with `EXTRA_TEMPLATE_ID`; ReportActivity uses that template for the PDF header/logo/watermark. |

---

## Automation (optional): Cloud Functions

- Scheduled cleanup (e.g. message retention).
- Future: server-side validation, scheduled reminders, background maintenance.

**Design choice:** notifications are **in-app records only** (no FCM push or OS notifications).

---

## High-level data flow

```
┌─────────────────────────────────────────────────────────────────────────┐
│  Android App (Java)                                                     │
│  Work View, Contracts, Jobs, Reports (iText), Messaging,                │
│  Leads/Commission, In-app notifications, AI assistant                   │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│  Firebase Auth + Firestore                                               │
│  users (role/title), [User] Contracts, JobWork, user_workview,           │
│  Leads, messages, notifications/{user}/items                             │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│  Firebase Storage                                                        │
│  Encrypted PDFs under ReportsYY/...; metadata links for retrieval       │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
┌─────────────────────────────────────────────────────────────────────────┐
│  LLM Providers (external)                                                 │
│  Groq API / Hugging Face API (keys maintained by admin via app settings) │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
┌─────────────────────────────────────────────────────────────────────────┐
│  Cloud Functions (optional)                                               │
│  Scheduled cleanup, background automation (when enabled)                 │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Key workflows (summary)

- **Job assignment:** Admin creates JobWork + schedules item in user_workview → technician sees it in Work View → notification record written to `notifications/{user}/items`.
- **Report generation:** Technician completes form → iText generates PDF (GRPC or My Template or a saved named template) → PDF auto-compressed → user can set modification password → PDF saved locally and uploaded to Storage (`ReportsYY/`) → contract report history via year-scoped search. **Named template flow:** PDF Template Settings → Save as named template → View templates → Use → Create Report form → save → PDF uses that template's headers/logo/watermark.
- **Commission:** Technician adds commission/lead entry → admin updates paid status and materials cost → optional in-app notification to technician.
- **Messaging:** 1:1 and group chat in Firestore; optional timed retention via Cloud Functions cleanup.

---

## Multi-company deployment options

- **Option A (recommended for fast rollout + clean isolation):** Separate Firebase project per company. Same codebase; swap branding (logo, app name, package name) per company.
- **Option B (multi-tenant SaaS):** Single Firebase project with `companyId` partitioning and strict security rules. More scalable long-term; requires careful rules and indexing.

---

## Note

The README is the primary user-facing doc; this file is the single architecture reference including the AI module (LLM APIs + key management) and the offline PDF template (My Template) feature.
