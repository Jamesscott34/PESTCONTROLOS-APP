# Deleting legacy Firestore data

You can safely delete the following from your Firestore database. The app uses the **shared** `contracts` collection and **Auth UID**-based `users` docs; legacy collections and numeric user IDs are no longer required.

---

## 1. Legacy “X Contracts” collections

These are **top-level collections** named like:

- `James Contracts`
- `Dean Contracts`
- `User A Contracts`
- Any collection whose name ends with ` Contracts` (with a space before “Contracts”)

**How to delete (Firebase Console):**

1. Open [Firebase Console](https://console.firebase.google.com) → your project → **Firestore Database**.
2. For each collection named `"… Contracts"`:
   - Open the collection.
   - Delete **every document** inside it (select and delete, or use the three-dots menu).
   - When the collection is empty, the collection itself disappears.

**Note:** There is no “delete collection” button; you must delete all documents first. If you have many documents, consider a one-off script (e.g. Node.js with Firebase Admin SDK) to delete the collection.

**App impact:** Contract listing and Work View use the shared `contracts` collection only. PDF export that merged legacy + shared will simply get no data from the removed collections (no crash).

---

## 2. Legacy staff user documents (e.g. 001, 002)

These are documents in the **`users`** collection whose **document IDs** are 3-digit numbers, for example:

- `users/001`
- `users/002`
- `users/003`
- etc.

**How to delete (Firebase Console):**

1. Firestore Database → open the **`users`** collection.
2. Find documents whose **ID** is exactly `001`, `002`, etc. (not the Auth UID).
3. Open each document → three dots (⋮) → **Delete document**.

**Do not delete** documents whose ID is a long alphanumeric string (Firebase Auth UID). Those are real user profiles.

**App impact:** The app already ignores numeric IDs when building the staff list (`StaffDirectory` skips `docId.matches("\\d{3}")`). Deleting these only removes obsolete data.

---

## Summary

| What to delete | Where | Safe? |
|----------------|--------|--------|
| Legacy “X Contracts” collections | Top-level collections named e.g. `James Contracts`, `Dean Contracts` | Yes – app uses `contracts` for main data |
| Legacy user docs 001, 002, … | `users/001`, `users/002`, … | Yes – app only uses `users/{authUid}` for staff |

After deletion, keep using the app as normal; real contracts live in `contracts` and real users in `users/{authUid}`.
