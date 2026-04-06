# 📦 Build Guide - LeetCode Checker App

This guide explains how to build the LeetCode Checker app for Android (APK) and provides information about iOS builds.

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Initial Setup](#initial-setup)
3. [Building Debug APK](#building-debug-apk)
4. [Building Release APK](#building-release-apk)
5. [Installing on Android Device](#installing-on-android-device)
6. [iOS Build Information](#ios-build-information)
7. [Troubleshooting](#troubleshooting)

---

## Prerequisites

### Required Software

| Software | Version | Download Link |
|----------|---------|---------------|
| Android Studio | 2023.1+ (Hedgehog or newer) | [developer.android.com/studio](https://developer.android.com/studio) |
| JDK | 17+ | Bundled with Android Studio |
| Git | Any recent version | [git-scm.com](https://git-scm.com/) |

### Required Accounts & API Keys

1. **Google Gemini API Key** (for AI features)
   - Get it free at [aistudio.google.com/app/apikey](https://aistudio.google.com/app/apikey)

2. **GitHub Personal Access Token** (for GitHub integration)
   - Generate at [github.com/settings/tokens](https://github.com/settings/tokens)
   - Select scopes: `repo`, `read:user`, `user:email`

---

## Initial Setup

### Step 1: Clone the Repository

```bash
git clone https://github.com/VigneshwaraChinnadurai/Google_Prep.git
cd Google_Prep/mobile_apps/leetcode_checker
```

### Step 2: Configure Local Properties

1. Copy the template file:
   ```bash
   # Windows
   copy local.properties.template local.properties
   
   # macOS/Linux
   cp local.properties.template local.properties
   ```

2. Open `local.properties` in a text editor and fill in your values:
   ```properties
   sdk.dir=C\:\\Users\\YourUsername\\AppData\\Local\\Android\\Sdk
   GEMINI_API_KEY=AIza...your_key_here
   GITHUB_TOKEN=ghp_...your_token_here
   GITHUB_OWNER=YourGitHubUsername
   GITHUB_REPO=YourRepoName
   GITHUB_BRANCH=main
   SETTINGS_UPDATE_PASSWORD=1234
   ```

### Step 3: Open in Android Studio

1. Open Android Studio
2. Select **File → Open**
3. Navigate to `mobile_apps/leetcode_checker` folder
4. Click **OK** and wait for Gradle sync to complete

---

## Building Debug APK

### Method 1: Using Android Studio (Recommended for beginners)

1. Open the project in Android Studio
2. Wait for Gradle sync to complete (see bottom status bar)
3. Go to **Build → Build Bundle(s) / APK(s) → Build APK(s)**
4. Wait for build to complete
5. Click **locate** in the notification to find the APK

The APK will be at:
```
app/build/outputs/apk/debug/app-debug.apk
```

### Method 2: Using Command Line

**Windows (PowerShell):**
```powershell
cd mobile_apps\leetcode_checker
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew assembleDebug
```

**macOS/Linux:**
```bash
cd mobile_apps/leetcode_checker
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"  # macOS
# export JAVA_HOME="/opt/android-studio/jbr"  # Linux
./gradlew assembleDebug
```

### Build Output

After successful build:
- **APK location:** `app/build/outputs/apk/debug/app-debug.apk`
- **Size:** ~15-25 MB

---

## Building Release APK

Release APKs are optimized and signed for distribution.

### Step 1: Create a Keystore

```bash
# Run this command (you'll be prompted for passwords and details)
keytool -genkey -v -keystore leetcode-checker-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias leetcode-checker
```

Keep your keystore file and passwords **secure** - you'll need them for all future updates.

### Step 2: Create keystore.properties

Create a file named `keystore.properties` in the `leetcode_checker` folder:

```properties
storeFile=leetcode-checker-release.jks
storePassword=your_keystore_password
keyAlias=leetcode-checker
keyPassword=your_key_password
```

⚠️ **Never commit this file to version control!**

### Step 3: Build Release APK

**Windows (PowerShell):**
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew assembleRelease
```

**macOS/Linux:**
```bash
./gradlew assembleRelease
```

### Release APK Location

```
app/build/outputs/apk/release/app-release.apk
```

---

## Installing on Android Device

### Method 1: Direct Install via USB

1. Enable **Developer Options** on your Android device:
   - Go to **Settings → About Phone**
   - Tap **Build Number** 7 times
   
2. Enable **USB Debugging**:
   - Go to **Settings → Developer Options**
   - Enable **USB Debugging**

3. Connect your device via USB

4. Install using ADB:
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

### Method 2: Transfer and Install

1. Copy the APK to your device (via USB, email, cloud storage, etc.)
2. On your device, enable **Install from Unknown Sources**:
   - Go to **Settings → Security** (or **Privacy**)
   - Enable **Unknown Sources** or allow your file manager
3. Open the APK file on your device
4. Tap **Install**

### Method 3: Wireless Install (Android 11+)

1. Enable **Wireless Debugging** in Developer Options
2. Pair your device:
   ```bash
   adb pair <ip>:<port>  # Use pairing code shown on device
   adb connect <ip>:<port>
   adb install app-debug.apk
   ```

---

## iOS Build Information

### ⚠️ Current Status: Android Only

This app is built with **Jetpack Compose** (Android-native UI framework) and **does not support iOS** directly.

### Options for iOS Users

#### Option 1: Cross-Platform Rewrite
The app would need to be rewritten using a cross-platform framework:
- **Kotlin Multiplatform (KMP)** - Share business logic, native UI
- **Flutter** - Single codebase, custom UI
- **React Native** - JavaScript-based, native components

This is a significant undertaking requiring:
- Rewriting UI in cross-platform framework
- Adapting platform-specific features
- Apple Developer Account ($99/year)
- macOS computer for iOS builds

#### Option 2: Web App
Consider creating a Progressive Web App (PWA) that works on both platforms.

#### Option 3: iOS-Native Build
Build a native iOS app using Swift/SwiftUI. This would be a separate project.

### If You Want to Build for iOS

You would need:
1. **macOS** computer (required for iOS development)
2. **Xcode** (free from Mac App Store)
3. **Apple Developer Account** ($99/year for App Store distribution)
4. Rewrite the app in **Swift/SwiftUI** or use a cross-platform framework

---

## Troubleshooting

### Common Issues

#### 1. "SDK location not found"
**Solution:** Ensure `sdk.dir` is correctly set in `local.properties`:
```properties
# Windows (use escaped backslashes or forward slashes)
sdk.dir=C\:\\Users\\YourName\\AppData\\Local\\Android\\Sdk
# OR
sdk.dir=C:/Users/YourName/AppData/Local/Android/Sdk

# macOS
sdk.dir=/Users/YourName/Library/Android/sdk

# Linux
sdk.dir=/home/YourName/Android/Sdk
```

#### 2. "Not enough memory to run compilation"
**Solution:** Increase Gradle memory. Add to `gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8
```

Or run with explicit memory:
```bash
./gradlew assembleDebug -Dorg.gradle.jvmargs=-Xmx4g
```

#### 3. "JAVA_HOME is not set"
**Solution:** Set JAVA_HOME to Android Studio's bundled JDK:
```powershell
# Windows PowerShell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"

# macOS (add to ~/.zshrc or ~/.bash_profile)
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

# Linux
export JAVA_HOME="/opt/android-studio/jbr"
```

#### 4. Gradle sync fails
**Solutions:**
- Ensure you have internet connection (first build downloads dependencies)
- Try **File → Invalidate Caches and Restart** in Android Studio
- Delete `.gradle` folder and rebuild

#### 5. "API key not working"
**Solutions:**
- Ensure there are no extra spaces in `local.properties`
- Gemini API: Check key is valid at [aistudio.google.com](https://aistudio.google.com)
- GitHub Token: Ensure required scopes are selected

#### 6. App crashes on launch
**Solutions:**
- Check all required fields in `local.properties` are filled
- Ensure API keys are valid
- Check Android version (minimum SDK 24 / Android 7.0)

### Getting Help

If you encounter issues not listed here:

1. Check the error message in Android Studio's **Build** tab
2. Search the error on [Stack Overflow](https://stackoverflow.com)
3. Open an issue on the GitHub repository

---

## Build Configurations

### Debug vs Release

| Feature | Debug | Release |
|---------|-------|---------|
| Debuggable | ✅ Yes | ❌ No |
| Minified | ❌ No | ✅ Yes |
| Obfuscated | ❌ No | ✅ Yes |
| Signed | Debug key | Release key |
| Size | Larger | Smaller |
| Performance | Normal | Optimized |

### Minimum Requirements

- **Android Version:** 7.0 (API 24) or higher
- **Architecture:** ARM64 (arm64-v8a), ARM32 (armeabi-v7a), x86_64
- **Storage:** ~50 MB
- **RAM:** 2 GB minimum, 4 GB recommended

---

## Version History

| Version | Date | Notes |
|---------|------|-------|
| 1.0 | 2026 | Initial release |

---

*Last updated: April 2026*
