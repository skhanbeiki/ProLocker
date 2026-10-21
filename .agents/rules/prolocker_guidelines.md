# ProLocker Project Guidelines & Memory Rule

## 1. Project Identification
- **Package Name:** `com.carbon.prolocker`
- **Application Name:** ProLocker
- **Target Flavors:** `bazaar`, `myket`, `googleplay`
- **Compile / Target / Min SDK:** 36 / 36 / 24

## 2. Tech Stack & Architecture
- **Language & UI:** Kotlin 2.2.10, Jetpack Compose (Material 3), Navigation Compose, Lottie, Coil.
- **Dependency Injection:** Koin (`com.carbon.prolocker.di.appModule`).
- **Database:** Room DB (`app_locker_db` via `AppDatabase`).
- **Preferences:** Proto DataStore (`user_prefs.pb` serialized via `UserPreferences`).
- **Networking:** Ktor Client with OkHttp engine and `kotlinx.serialization.json`.

## 3. Core App Locking Mechanism
- Shared with AppLocker core: `AppMonitorAccessibilityService`, `ForegroundAppDetector`, `HybridDetectionEngine`, `LockService`, `OverlayWindowManager`, `LockSessionManager`, `CameraCaptureManager`, `StealthModeManager`.

## 4. ProLocker Exclusive Modules
1. **Tools Hub (`feature/tools/ToolsScreen.kt`):**
   - Central grid dashboard showcasing Hero AppLock card, Lottie icons, and quick navigation to all sub-tools.
2. **Call Blocker (`feature/callblocker`):**
   - `ProLockerCallScreeningService` (Android 10+ Telecom API) & `ProLockerCallReceiver` (Android 9 broadcast).
   - Rules filtering (exact, prefix, suffix, substring) with call log recording in Room (`CallBlockerDao`).
3. **Backup & Restore (`feature/backup`):**
   - Complete backup/restore engine for Contacts, Call Logs, SMS, and Application APK extraction/sharing (`BackupRepository`).
   - Supports JSON format, SAF Document Tree, public storage, and Persian Jalali dates (`PersianDate`).
4. **Privacy Auditor (`feature/privacyauditor`):**
   - Scans installed applications for sensitive/dangerous permissions (Camera, Mic, Location, SMS, Contacts, Storage, Call Log).
   - Computes Privacy Health Score and categorizes apps into High, Medium, and Low risk levels.
5. **Exit Ad Handling (`ad/ExitAdHelper.kt`):**
   - Implements back-press interstitial ad triggers driven by Remote Config counters.
