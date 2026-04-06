# 📱 LeetCode Checker

**Your Personal Development Companion for Technical Interview Preparation**

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-purple.svg)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-blue.svg)](https://developer.android.com/jetpack/compose)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

---

## 🚀 Features

| Feature | Description |
|---------|-------------|
| 🎯 **LeetCode Tracking** | Fetch, analyze, and solve LeetCode problems with AI assistance |
| 🤖 **AI Interview Prep** | Practice coding interviews with Google Gemini AI |
| 💬 **Strategic Chatbot** | Deep analysis of companies, markets, and career strategies |
| 📰 **AI/ML News** | Stay updated with curated AI/ML news from multiple sources |
| 📊 **GitHub Integration** | View contributions, sync solutions to your repository |
| 🏆 **Profile Dashboard** | Unified view of GitHub, Credly, LinkedIn, and Medium |
| 🔄 **Local AI (Ollama)** | Run AI models locally for privacy and offline use |
| 📈 **Analytics & Goals** | Track progress, set goals, earn achievements |

---

## 📚 Documentation

| Document | Description |
|----------|-------------|
| 📖 **[USER_MANUAL.md](USER_MANUAL.md)** | Complete user guide with all features explained |
| 🔧 **[BUILD_GUIDE.md](BUILD_GUIDE.md)** | How to build APK from source |
| 📋 **[local.properties.template](local.properties.template)** | Configuration template with instructions |

---

## ⚡ Quick Start

### 1. Clone the Repository

```bash
git clone https://github.com/VigneshwaraChinnadurai/Google_Prep.git
cd Google_Prep/mobile_apps/leetcode_checker
```

### 2. Configure API Keys

```bash
# Copy template
cp local.properties.template local.properties

# Edit and fill in your values
# - GEMINI_API_KEY: Get from https://aistudio.google.com/app/apikey
# - GITHUB_TOKEN: Get from https://github.com/settings/tokens
```

### 3. Build

```bash
# Windows PowerShell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew assembleDebug

# macOS/Linux
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug
```

### 4. Install

APK location: `app/build/outputs/apk/debug/app-debug.apk`

---

## 🔑 Required API Keys

| Key | Required For | Get It From |
|-----|--------------|-------------|
| `GEMINI_API_KEY` | AI features (Interview Prep, Chatbot, Analysis) | [Google AI Studio](https://aistudio.google.com/app/apikey) |
| `GITHUB_TOKEN` | GitHub Profile, Solution Sync | [GitHub Settings](https://github.com/settings/tokens) |

---

## 📱 App Structure

```
┌─────────────────────────────────────────┐
│              LeetCode Checker           │
├─────────────────────────────────────────┤
│                                         │
│   🏠 Leetcode   - Fetch & analyze       │
│   🔧 Ollama     - Local AI              │
│   ⭐ Features   - Interview, News, etc  │
│   💬 Chatbot    - Strategic Analysis    │
│   👤 Profile    - GitHub, Credly, etc   │
│                                         │
└─────────────────────────────────────────┘
```

---

## 🛠️ Tech Stack

- **Language**: Kotlin 1.9
- **UI Framework**: Jetpack Compose (Material 3)
- **Architecture**: MVVM with StateFlow
- **Networking**: Retrofit + Moshi
- **AI Backend**: Google Gemini API / Ollama
- **Image Loading**: Coil
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 35 (Android 14)

---

## 📁 Project Structure

```
leetcode_checker/
├── app/
│   └── src/main/
│       ├── java/com/vignesh/leetcodechecker/
│       │   ├── api/           # API interfaces
│       │   ├── data/          # Data models
│       │   ├── pipeline/      # Analysis pipelines
│       │   ├── repository/    # Data repositories
│       │   ├── ui/            # Compose screens
│       │   └── viewmodel/     # ViewModels
│       └── AndroidManifest.xml
├── BUILD_GUIDE.md             # Build instructions
├── USER_MANUAL.md             # User documentation
├── local.properties.template  # Config template
└── README.md                  # This file
```

---

## 🔒 Security Notes

- ⚠️ **Never commit `local.properties`** - Contains API keys
- ⚠️ **Never commit `keystore.properties`** - Contains signing keys
- ✅ `.gitignore` is configured to exclude sensitive files
- ✅ Use the template files for sharing

---

## 📋 Configuration Reference

Create `local.properties` from template and configure:

```properties
# Required
sdk.dir=/path/to/Android/Sdk
GEMINI_API_KEY=AIza...
GITHUB_TOKEN=ghp_...
GITHUB_OWNER=YourUsername
GITHUB_REPO=YourRepo
GITHUB_BRANCH=main

# Optional
SETTINGS_UPDATE_PASSWORD=1234
OLLAMA_BASE_URL=http://127.0.0.1:11434
OLLAMA_MODEL=qwen2.5:3b
```

See [local.properties.template](local.properties.template) for detailed instructions.

---

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Submit a pull request

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 👨‍💻 Author

**Vigneshwara Chinnadurai**
- GitHub: [@VigneshwaraChinnadurai](https://github.com/VigneshwaraChinnadurai)
- LinkedIn: [vigneshwarac](https://www.linkedin.com/in/vigneshwarac/)
- Medium: [@rockingstarvic](https://medium.com/@rockingstarvic)

---

*Built with ❤️ for the developer community*
