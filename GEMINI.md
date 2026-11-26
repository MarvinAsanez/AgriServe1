# AgriServe Project Architecture & Development Guidelines

## 1. System Overview & Architecture

AgriServe is an integrated agricultural management platform developed for the Department of Agriculture (DA) and local municipal agriculture offices. It consists of:
1. **Serverless Cloud Backend**: Google Firebase (Cloud Firestore, Firebase Authentication, Cloud Functions v2 in TypeScript/Node.js 22).
2. **Web Administrative Portal (`AgriServe-Admin`)**: Modular, decoupled web dashboard built with HTML5, CSS3, and modern Vanilla JavaScript ES modules.
3. **Farmer Mobile Application (`AgriServe`)**: Native Android application written in Kotlin with Jetpack Compose, Material3, Navigation Compose, and StateFlow.

---

## 2. Strict Architectural Boundaries & Prohibitions

- **No Legacy PHP / MySQL / XAMPP**: Never generate or recommend PHP scripts, Apache `.htaccess` directives, MySQL relational SQL schemas, or cPanel/XAMPP configurations. All persistence resides in Cloud Firestore NoSQL, authenticated via Firebase Auth.
- **No Self-Service Farmer Registration Bypass**: Farmers cannot self-register directly into an active account. All incoming farmers must proceed through the **Verification Gate** (`VerificationGateScreen`), submit official credentials (government photo ID, contact details, RSBSA enrollment data), and await validation by Municipal Agriculture Office (MAO) staff.
- **Physical DA Inventory Scope**: The inventory system strictly monitors **physical DA warehouse inventory** (seed sacks, fertilizer bags, agricultural machinery, discount vouchers, disaster relief allocations). Do not implement speculative crop lifecycle simulation models.

---

## 3. Directory & Package Structure

### A. Web Dashboard & Cloud Functions (`AgriServe-Admin`)
- `public/`: Web pages (`*.html`). Every page strictly references external stylesheets and scripts.
- `public/assets/css/`: Central stylesheet (`style.css`).
- `public/assets/js/`: Decoupled JavaScript modules (`admin-role.js`, `farmer-applications.js`, `auth.js`, `utils.js`, etc.).
- `public/config/`: Firebase web client configuration (`firebase-config.js`).
- `functions/`: Cloud Functions v2 codebase.
  - `src/index.ts`: Primary callable/event functions & Gemini AI assistants.
  - `src/authOtp.ts`: Multi-channel (email/SMS) OTP verification.
  - `src/tools/firestoreTools.ts`: Administrative Firestore operations.
- `server/`: Biometric verification Express microservice.
- `firestore.rules`: Security rules enforcing role-based permissions (`isAuthenticated`, `isOwner`, `isAdmin`).

### B. Android Mobile Application (`AgriServe`)
Package root: `com.group7.agriserve`
- `ui/screens/`: Jetpack Compose UI screens (`VerificationGateScreen.kt`, `InventoryScreen.kt`, `CredentialSubmissionScreen.kt`, `RequestResourceScreen.kt`, etc.).
- `ui/navigation/Screen.kt`: Navigation routes and sealed class destinations.
- `ui/theme/`: Material3 color schemes, typography, and agricultural green palette.
- `viewmodel/`: StateFlow-backed ViewModels (`UserViewModel.kt`, `SignUpViewModel.kt`, etc.).
- `data/repository/`: Firestore data access layer and models.
- `util/`: Session managers, network monitors, and validation helpers.
- `res/values*/`: Multilingual string resources (English `values`, Tagalog `values-tl`, Bikol `values-bcl`).

---

## 4. Verification & Testing Standards

Every code modification must be verified using the following exact PowerShell commands before completing a turn:

### A. Cloud Functions & Web Backend
```powershell
# Navigate to web admin root
cd "C:\Users\Marvin\Desktop\AgriServe-Admin"

# Compile and verify Cloud Functions TypeScript code
npm --prefix functions run build
```

### B. Android Mobile Application (Gradle)
```powershell
# Set JDK 21 environment
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio2\jbr"

# Run Android Unit Tests
cd "C:\Users\Marvin\Desktop\AgriServe"
.\gradlew.bat testDebugUnitTest --console=plain

# Verify Kotlin compilation
.\gradlew.bat :app:compileDebugKotlin --console=plain

# Assemble Debug APK
.\gradlew.bat :app:assembleDebug --console=plain
```

---

## 5. Coding & Contribution Rules

1. **Jetpack Compose First**: In Android, avoid XML layouts for new screens. Compose with Material3 components, strictly handle lifecycle disposables, and use `findActivity()` safely via context wrappers.
2. **Localization Compliance**: Never hardcode user-facing strings in Compose screens. Place string resources in `res/values/strings.xml`, `res/values-tl/strings.xml`, and `res/values-bcl/strings.xml`.
3. **Decoupled Web Assets**: When creating or modifying web dashboard pages, keep HTML clean, put CSS in `public/assets/css/style.css`, and put JavaScript in dedicated modules under `public/assets/js/`.
4. **Security Rules Synchronization**: Whenever modifying Firestore schemas or collections (`farmers`, `requests`, `inventory`, `admins`), update and review `firestore.rules` to ensure unauthorized client-side access is blocked.

