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
| 📰 **AI/ML News** | Live feed from arXiv (cs.AI, quant-ph), OpenAI News, and Hugging Face Blog |
| 📊 **GitHub Integration** | View contributions, sync solutions to your repository, all pushes on one configurable token |
| 🏆 **Profile Dashboard** | Unified view of GitHub, live Credly badges, LinkedIn, and Medium |
| 🔄 **Local AI (Ollama)** | Run AI models locally for privacy and offline use |
| 📈 **Analytics & Goals** | Track progress, set goals, earn achievements |
| 🗂️ **Proof Filing** | Weekly GitHub/LeetCode activity summaries, auto-pushed as markdown |

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

### 2. Configure Build-Time Settings

```bash
# Copy template
cp local.properties.template local.properties

# Edit and fill in your values
# - sdk.dir: path to your Android SDK
# - GEMINI_API_KEY: Get from https://aistudio.google.com/app/apikey
#   (only used as a compile-time default; see step 5)
```

`GITHUB_TOKEN` is **not** set here — see step 5.

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

### 5. Configure the GitHub Token (in-app)

The GitHub token isn't a build-time secret — it's entered once inside the app and stored only on your device, so you can rotate it without rebuilding:

1. Open the app → **Features** tab → **Global Settings** (first tile)
2. Generate a token at [github.com/settings/tokens](https://github.com/settings/tokens)
   - Fine-grained token: grant **Contents: Read and write** on the target repo
   - Classic token: `repo`, `read:user`, `user:email` scopes
3. Paste it into **Global GitHub Token**, tap **Test Token** to confirm write access, then **Save Settings**

This one token is used for every GitHub-touching feature: profile lookups, daily revision pushes, and Proof Filing.

---

## 🔑 Required API Keys

| Key | Required For | Configured Via | Get It From |
|-----|--------------|-----------------|-------------|
| `GEMINI_API_KEY` | AI features (Interview Prep, Chatbot, Analysis) | `local.properties` (compile-time), overridable in Global Settings | [Google AI Studio](https://aistudio.google.com/app/apikey) |
| GitHub token | GitHub Profile, Solution Sync, Proof Filing | **In-app only** — Global Settings screen | [GitHub Settings](https://github.com/settings/tokens) |

---

## 📱 App Structure

```
┌─────────────────────────────────────────┐
│              LeetCode Checker           │
├─────────────────────────────────────────┤
│                                         │
│   🏠 Leetcode    - Fetch & solve daily  │
│   🔧 Ollama      - Local AI challenges  │
│   🗂️ ProofFile   - Weekly activity log  │
│   ⭐ Features    - Hub: Interview,      │
│                    News, Chatbot,       │
│                    Settings, and more   │
│   👤 Profile     - GitHub, Credly, etc  │
│                                         │
└─────────────────────────────────────────┘
```

The Features tab is a hub screen — Strategic Chatbot, AI Hub, Analytics, Goals, and Global Settings all live one tap in from there rather than as their own bottom-nav tabs.

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
│       │   ├── ai/            # Recommendation/hints/knowledge-graph engines
│       │   ├── api/           # Retrofit API interfaces
│       │   ├── data/          # Data models & repositories (GitHub, LeetCode, AI News, Gemini, Ollama)
│       │   ├── llm/           # LLM provider abstraction (Gemini/Ollama/local llama.cpp)
│       │   ├── models/        # Shared data models (chatbot, pipeline)
│       │   ├── pipeline/      # Strategic-analysis / RAG pipeline
│       │   ├── prooffiling/   # Weekly proof-filing feature
│       │   ├── repository/    # Data repositories
│       │   ├── security/      # Uninstall protection (device admin)
│       │   ├── ui/            # Compose screens
│       │   ├── viewmodel/     # ViewModels
│       │   └── widget/        # Home-screen widget
│       └── AndroidManifest.xml
├── BUILD_GUIDE.md             # Build instructions
├── USER_MANUAL.md             # User documentation
├── local.properties.template  # Config template
└── README.md                  # This file
```

---

## 🔒 Security Notes

- ⚠️ **Never commit `local.properties`** - contains your Gemini key
- ⚠️ **Never commit `keystore.properties`** - contains signing keys
- ✅ `.gitignore` is configured to exclude sensitive files
- ✅ Use the template files for sharing
- ✅ The GitHub token is never baked into the compiled APK — it lives only in the app's on-device storage, entered via Global Settings

---

## 📋 Configuration Reference

Create `local.properties` from template and configure:

```properties
# Required
sdk.dir=/path/to/Android/Sdk
GEMINI_API_KEY=AIza...
GITHUB_OWNER=YourUsername
GITHUB_REPO=YourRepo
GITHUB_BRANCH=main

# Optional
SETTINGS_UPDATE_PASSWORD=1234
OLLAMA_BASE_URL=http://127.0.0.1:11434
OLLAMA_MODEL=qwen2.5:3b
```

The GitHub token is deliberately absent from this file — set it in-app via Global Settings (see Quick Start, step 5).

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
