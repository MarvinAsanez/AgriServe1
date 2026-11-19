---
name: firebase-backend-feature
description: Adds or updates Cloud Firestore schemas, security rules (firestore.rules), and Firebase Cloud Functions v2 triggers or HTTP callables, verifying TypeScript builds and security integrity.
---

# Firebase Backend Feature Workflow

Use this skill whenever you need to add, update, or audit Firestore data collections, modify `firestore.rules`, or implement backend Cloud Functions in `functions/src/`.

## 1. Pre-Flight Architecture Rules
- **Serverless Only**: Never introduce relational SQL schemas, PHP scripts, or local MySQL tables. All data must reside in Cloud Firestore.
- **Collection Standards**:
  - `farmers`: Verified farmer records, agricultural profile, contact details, and RSBSA data.
  - `farmer_applications`: Submitted credentials pending DA staff validation.
  - `inventory`: Physical DA stock records (seeds, machinery, fertilizers, vouchers).
  - `requests`: Assistance and supply requests submitted by verified farmers.
  - `admins`: Administrative accounts and role levels (`admin`, `super_admin`, `staff`).
- **Role-Based Security**: All new collections must have explicit security rules in `firestore.rules`. Never leave open (`allow read, write: if true;`) rules.

---

## 2. Step-by-Step Implementation Workflow

### Step 1: Design Firestore Document Schema
- Define TypeScript types/interfaces in `functions/src/` or JSDoc in web client modules.
- Ensure timestamps use Firestore `FieldValue.serverTimestamp()`.
- Add auditing fields where relevant (`createdAt`, `updatedAt`, `reviewedBy`, `status`).

### Step 2: Update `firestore.rules`
1. Open `C:\Users\Marvin\Desktop\AgriServe-Admin\firestore.rules`.
2. Locate the relevant collection block or create a new `match /collectionName/{docId}` block.
3. Apply existing helper functions:
   - `isAuthenticated()`: Verifies user is signed in.
   - `isOwner(userId)`: Verifies caller's UID matches the resource owner.
   - `isAdmin()`: Verifies caller has Admin role in custom claims, user doc, or `admins` collection.
4. Keep rules synchronized between `AgriServe-Admin` and `AgriServe` if replicated.

### Step 3: Implement Cloud Functions v2
1. Add new callable functions or event triggers in `functions/src/`:
   - Use `onCall` for client-triggered RPCs requiring authentication.
   - Use `onDocumentCreated`, `onDocumentUpdated`, or `onDocumentDeleted` for Firestore event triggers.
2. If multi-channel OTP or email delivery is needed, integrate with `functions/src/authOtp.ts`.
3. If GenAI capabilities are required, import `@google/generative-ai` with Gemini models (`gemini-2.0-flash` or `gemini-1.5-flash`).

### Step 4: Verification & Compilation Standard
Always run the TypeScript build check before concluding the turn:

```powershell
cd "C:\Users\Marvin\Desktop\AgriServe-Admin"
npm --prefix functions run build
```

Ensure the command exits with code `0`. If any type errors or missing dependencies occur, resolve them immediately.

### Step 5: Web Client Module Integration
If the feature connects to the web dashboard:
- Add UI markup cleanly in `public/{page}.html`.
- Implement client-side logic in a dedicated module under `public/assets/js/{feature}.js`.
- Reuse `firebase-config.js` for initialized Firestore (`db`) and Auth (`auth`) instances.
