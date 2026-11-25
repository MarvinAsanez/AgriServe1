---
name: compose-screen-scaffold
description: Scaffolds modular Kotlin Jetpack Compose screens, ViewModels, and navigation routes in the AgriServe Android application, verifying compilation and unit tests with Gradle.
---

# Jetpack Compose Screen Scaffold Workflow

Use this skill whenever you need to build, refactor, or extend screens, user flows, navigation destinations, or state management in the AgriServe Android application (`C:\Users\Marvin\Desktop\AgriServe`).

## 1. Architectural Guardrails
- **No Self-Service Farmer Bypass**: Farmers cannot bypass the admin-verification flow. New farmers must land on `VerificationGateScreen.kt`, submit credentials via `CredentialSubmissionScreen.kt`, and wait for DA staff approval.
- **Compose Only**: All new screens must use pure Jetpack Compose with Material3. Avoid legacy Android XML view inflation.
- **Package Standards**:
  - Screens: `com.group7.agriserve.ui.screens.{Name}Screen.kt`
  - Routes: `com.group7.agriserve.ui.navigation.Screen`
  - ViewModels: `com.group7.agriserve.viewmodel.{Name}ViewModel.kt`
  - Theme: `com.group7.agriserve.ui.theme` (AgriServe green branding)
  - Strings: Localized in `res/values/strings.xml`, `values-tl/strings.xml`, and `values-bcl/strings.xml`.

---

## 2. Step-by-Step Implementation Workflow

### Step 1: Define Navigation Route
1. Open `C:\Users\Marvin\Desktop\AgriServe\app\src\main\java\com\group7\agriserve\ui\navigation\Screen.kt`.
2. Add a new sealed class object with a unique route:
   ```kotlin
   data object MyFeature : Screen("my_feature_route")
   ```

### Step 2: Create the Compose Screen
1. Create `app/src/main/java/com/group7/agriserve/ui/screens/{Name}Screen.kt`.
2. Ensure standard parameter conventions:
   ```kotlin
   @OptIn(ExperimentalMaterial3Api::class)
   @Composable
   fun MyFeatureScreen(
       userViewModel: UserViewModel,
       onNavigateBack: () -> Unit,
       onNavigateToNext: () -> Unit = {}
   ) {
       val context = LocalContext.current
       // ... UI logic with Material3 components
   }
   ```
3. Use `context.findActivity()` safely via context wrapper when accessing host activity instances.

### Step 3: Register in Navigation Host (`MainActivity.kt`)
1. Open `app/src/main/java/com/group7/agriserve/MainActivity.kt`.
2. Add a `composable(Screen.MyFeature.route) { ... }` block inside the `NavHost`.
3. Pass necessary callbacks and shared ViewModels (`userViewModel`).

### Step 4: Add Multilingual String Resources
Add strings to all 3 primary localization directories:
- `app/src/main/res/values/strings.xml` (English)
- `app/src/main/res/values-tl/strings.xml` and `values-fil/strings.xml` (Tagalog/Filipino)
- `app/src/main/res/values-bcl/strings.xml` and `values-bik/strings.xml` (Bikol)

### Step 5: Verification & Compilation Standard
Always run Gradle verification to ensure zero Kotlin compiler errors and passing tests:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio2\jbr"
cd "C:\Users\Marvin\Desktop\AgriServe"

# Run unit tests
.\gradlew.bat testDebugUnitTest --console=plain

# Verify Kotlin compilation
.\gradlew.bat :app:compileDebugKotlin --console=plain

# Optional full APK assemble
.\gradlew.bat :app:assembleDebug --console=plain
```
Ensure the build exits with `BUILD SUCCESSFUL`.

