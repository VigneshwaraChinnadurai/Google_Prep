# 📱 LeetCode Checker - User Manual

## Complete Guide to Your Personal Development Companion App

---

## Table of Contents

1. [Introduction](#introduction)
2. [Getting Started](#getting-started)
3. [Main Navigation](#main-navigation)
4. [LeetCode Tab](#leetcode-tab)
5. [Ollama Tab](#ollama-tab)
6. [Features Tab](#features-tab)
7. [Chatbot Tab](#chatbot-tab)
8. [Profile Tab](#profile-tab)
9. [Settings & Configuration](#settings--configuration)
10. [Tips & Best Practices](#tips--best-practices)
11. [FAQ](#faq)

---

## Introduction

### What is LeetCode Checker?

LeetCode Checker is a comprehensive Android application designed to help software engineers, data scientists, and developers prepare for technical interviews. It combines LeetCode practice tracking, AI-powered interview preparation, strategic analysis tools, and personal profile management into a single unified app.

### Key Features

| Feature | Description |
|---------|-------------|
| 🎯 **LeetCode Tracking** | Fetch and analyze LeetCode problems with AI assistance |
| 🤖 **AI Interview Prep** | Practice coding interviews with Gemini AI |
| 💬 **Strategic Chatbot** | Deep analysis of companies, markets, and strategies |
| 📰 **AI/ML News** | Stay updated with latest AI/ML developments |
| 📊 **GitHub Integration** | View contributions, sync solutions |
| 🏆 **Profile Dashboard** | Unified view of GitHub, Credly, LinkedIn, Medium |
| 🔄 **Local AI (Ollama)** | Run AI models locally for privacy |

### Who Is This For?

- Software Engineers preparing for FAANG interviews
- Data Scientists brushing up on algorithms
- Students learning Data Structures & Algorithms
- Anyone wanting to track their coding progress

---

## Getting Started

### First Launch

1. **Install the App**
   - Build from source (see BUILD_GUIDE.md) or install provided APK
   - Grant requested permissions (internet, storage if prompted)

2. **Initial Configuration**
   - The app uses API keys configured during build
   - If features don't work, check that local.properties was configured correctly

### Understanding the Interface

The app uses a **bottom navigation bar** with 5 main tabs:

```
┌─────────────────────────────────────────┐
│                                         │
│           [Content Area]                │
│                                         │
├─────────────────────────────────────────┤
│ 🏠       🔧       ⭐       ⚙️       👤   │
│Leetcode  Ollama  Features  Chatbot  Profile│
└─────────────────────────────────────────┘
```

---

## Main Navigation

### Bottom Navigation Tabs

| Icon | Tab | Purpose |
|------|-----|---------|
| 🏠 | **Leetcode** | Fetch and analyze LeetCode problems using Gemini AI |
| 🔧 | **Ollama** | Same features but using local Ollama AI |
| ⭐ | **Features** | Hub for all additional features (Interview Prep, News, etc.) |
| ⚙️ | **Chatbot** | Strategic analysis chatbot for deep research |
| 👤 | **Profile** | Your unified profile (GitHub, Credly, LinkedIn, Medium) |

---

## LeetCode Tab

### Overview

The LeetCode tab helps you fetch LeetCode problems, analyze them with AI, and track your progress.

### Features

#### 1. Problem Fetcher
- Enter a LeetCode problem URL or number
- Fetches problem description, examples, and constraints
- Works with any public LeetCode problem

**How to use:**
1. Tap the URL input field
2. Paste a LeetCode URL (e.g., `https://leetcode.com/problems/two-sum/`)
3. Tap **Fetch** to retrieve the problem

#### 2. AI Analysis
Once a problem is fetched, you can:
- **Analyze**: Get AI-powered solution hints and approach explanation
- **Full Solution**: Get a complete solution with explanation
- **Optimize**: Ask for more efficient approaches

#### 3. Solution Input
- Paste your solution code
- Get feedback on correctness and efficiency
- Compare with optimal approaches

#### 4. Pipeline Logs (Testcase Validation)
- View detailed execution logs
- **Copy feature**: Long-press or tap to copy log content
- Logs are scrollable and selectable

#### 5. GitHub Sync
- Sync your solutions to your GitHub repository
- Automatically creates dated folders
- Includes problem description and your solution

### Tips for LeetCode Tab

- 📝 Start with Easy problems to build confidence
- 🔄 Use the refresh button to re-analyze
- 📋 Copy solutions to your local IDE for testing
- 🎯 Focus on understanding patterns, not memorizing solutions

---

## Ollama Tab

### Overview

The Ollama tab provides the same LeetCode features but uses **local AI models** instead of cloud-based Gemini. This is ideal for:
- Privacy-conscious users
- Working offline (once models are downloaded)
- Unlimited usage without API costs

### Setup Requirements

1. **Install Ollama** on your computer
   - Download from [ollama.ai](https://ollama.ai)
   - Works on Windows, macOS, Linux

2. **Pull a model**:
   ```bash
   ollama pull qwen2.5:3b
   ```

3. **Configure the app**:
   - Ensure `OLLAMA_BASE_URL` in local.properties points to your Ollama instance
   - Default: `http://127.0.0.1:11434`
   - For mobile access, use your computer's local IP address

### Supported Models

| Model | Size | Speed | Quality |
|-------|------|-------|---------|
| qwen2.5:3b | 2.3 GB | Fast | Good |
| llama3.2:3b | 2.0 GB | Fast | Good |
| mistral:7b | 4.1 GB | Medium | Great |
| codellama:7b | 3.8 GB | Medium | Great for code |

### Using Ollama Tab

The interface is identical to the LeetCode tab. Just:
1. Enter a problem URL
2. Fetch the problem
3. Use AI analysis (powered by your local model)

### Troubleshooting Ollama

| Issue | Solution |
|-------|----------|
| Connection failed | Ensure Ollama is running: `ollama serve` |
| Model not found | Pull the model: `ollama pull qwen2.5:3b` |
| Slow responses | Use a smaller model or ensure GPU is enabled |
| Can't connect from phone | Use computer's IP, not localhost |

---

## Features Tab

### Overview

The Features Hub provides access to additional tools and features. Tap any card to access that feature.

### Available Features

#### 1. 📊 Analytics
- View your problem-solving statistics
- Track difficulty distribution
- Monitor progress over time

#### 2. 🎯 Goals
- Set daily/weekly coding goals
- Track streak and consistency
- Receive reminders

#### 3. 🏆 Achievements
- Unlock achievements for milestones
- Track badges earned
- Celebrate progress

#### 4. 🃏 Flashcards
- Review DS&A concepts
- Spaced repetition learning
- Custom card creation

#### 5. ⏱️ Focus Mode
- Pomodoro timer for focused practice
- Distraction blocking
- Session tracking

#### 6. 🎤 AI Interview Prep
**One of the most powerful features!**

Features:
- **Mock Interviews**: Practice with AI interviewer
- **Live Coding**: Solve problems with real-time feedback
- **System Design**: Practice architecture questions
- **Behavioral**: Prepare for soft-skill questions

How to use:
1. Select interview type (Coding, System Design, Behavioral)
2. Choose difficulty level
3. Start interview session
4. Receive questions and provide answers
5. Get detailed feedback

#### 7. 🏅 Leaderboard
- Compare progress with community
- View rankings by topic
- Friendly competition

#### 8. 📴 Offline Mode
- Download problems for offline practice
- Works without internet
- Sync when back online

#### 9. 🛡️ Uninstall Protection
- Prevent accidental uninstallation
- Requires password to uninstall
- Good for commitment

#### 10. 📰 AI/ML News
Stay updated with latest developments:
- Curated AI/ML news from multiple sources
- Topic filtering (LLMs, Computer Vision, etc.)
- **Open Article**: Tap to read full article in browser
- **Refresh**: Pull down or tap FAB to refresh

Settings for AI News:
- Select preferred topics
- Choose news sources
- Set refresh frequency

#### 11. 🎲 Random Challenge
From the Features Hub, you can start a random challenge:
- Select topic (Arrays, Trees, Graphs, etc.)
- Choose difficulty (Easy, Medium, Hard)
- Opens LeetCode with filters applied
- Great for variety in practice

---

## Chatbot Tab

### Overview

The Strategic Chatbot is an advanced AI assistant for deep analysis and research. Unlike simple Q&A, it:
- Performs multi-step research
- Analyzes companies, markets, and strategies
- Provides citations and sources
- Uses advanced reasoning

### Use Cases

| Category | Example Queries |
|----------|-----------------|
| **Company Analysis** | "Analyze Google's AI strategy for 2024" |
| **Market Research** | "Compare cloud market share: AWS vs Azure vs GCP" |
| **Career Advice** | "What skills should ML engineers learn in 2026?" |
| **Tech Trends** | "Explain the impact of LLMs on software development" |
| **Interview Prep** | "Common system design questions at Netflix" |

### How to Use

1. **Start a conversation**
   - Type your question in the input field
   - Tap send (or press Enter)

2. **Wait for analysis**
   - Complex queries may take 30-60 seconds
   - The chatbot performs deep research

3. **Review response**
   - Responses include structured analysis
   - Sources and citations when available
   - Follow-up suggestions

4. **Continue conversation**
   - Ask follow-up questions
   - Request clarification
   - Dive deeper into topics

### Cost Tracking

The chatbot shows token usage and estimated costs:
- Input tokens: What you send
- Output tokens: What AI generates
- Estimated cost: Based on API pricing

### Tips for Better Results

- 📝 Be specific in your questions
- 🎯 Ask for structured output (bullet points, tables)
- 🔄 Use follow-ups to dive deeper
- 📊 Request comparisons for decision-making

---

## Profile Tab

### Overview

The Profile tab provides a unified view of your professional presence across multiple platforms. It uses **expandable dropdown sections** for organized viewing.

### Sections

#### 1. GitHub (Expanded by Default)
Shows your GitHub profile information:

- **Profile Info**: Avatar, name, bio
- **Stats**: Followers, following, repositories
- **Contribution Activity**: Commits, PRs, issues
- **Contribution Heatmap**: GitHub-style activity graph
  - Auto-scrolls to show current month
  - Color-coded by activity level
- **README.md**: Your profile README rendered in-app

#### 2. Credly
Displays your professional certifications:

- Automatically fetches badges from Credly profile
- Shows badge images, names, issuers
- Tap any badge to view on Credly website
- "View All Badges on Credly" button

#### 3. LinkedIn
Quick preview of your LinkedIn profile:

- Name, title, company
- Professional summary
- Skills tags
- "View Full Profile" button

#### 4. Medium
Your blog articles from Medium:

- Fetches from your Medium RSS feed
- Shows article titles and dates
- Tags/categories for each article
- Tap to open article in browser
- "View All on Medium" button

### Refreshing Data

- Tap the **refresh icon** (top right) to reload all sections
- Each section loads data when expanded
- Collapsing and re-expanding also refreshes

### Customization

Profile usernames are configured in `local.properties`:
- `GITHUB_OWNER`: Your GitHub username
- Credly, LinkedIn, Medium usernames are currently hardcoded

---

## Settings & Configuration

### In-App Settings

Access settings through specific feature screens:
- **AI News Settings**: Topic and source preferences
- **Focus Mode Settings**: Timer durations
- **Goal Settings**: Daily/weekly targets

### Build-Time Configuration

These are set in `local.properties` before building:

| Setting | Description |
|---------|-------------|
| `GEMINI_API_KEY` | Google Gemini API key for AI features |
| `GITHUB_TOKEN` | GitHub PAT for profile and sync |
| `GITHUB_OWNER` | Your GitHub username |
| `GITHUB_REPO` | Repository for solution sync |
| `GITHUB_BRANCH` | Branch to sync to |
| `SETTINGS_UPDATE_PASSWORD` | PIN for protected settings |
| `OLLAMA_BASE_URL` | Ollama server address |
| `OLLAMA_MODEL` | Which Ollama model to use |

### Protected Settings

Some settings require a password to change:
- Default password: `1234` (or as configured)
- Used for sensitive operations

---

## Tips & Best Practices

### For Interview Preparation

1. **Daily Practice**
   - Solve at least 1 problem daily
   - Mix difficulties (2 Easy, 1 Medium, or 1 Hard)
   - Use Focus Mode for uninterrupted sessions

2. **Topic Rotation**
   - Week 1: Arrays & Strings
   - Week 2: Linked Lists & Stacks
   - Week 3: Trees & Graphs
   - Week 4: Dynamic Programming
   - Repeat with increased difficulty

3. **Mock Interviews**
   - Use AI Interview Prep 2-3 times/week
   - Practice explaining your thought process
   - Time yourself (45 min for coding, 30 min for design)

4. **Track Progress**
   - Check Analytics regularly
   - Celebrate achievements
   - Identify weak areas

### For Maximum App Value

1. **Complete Profile Setup**
   - Link all accounts (GitHub, Credly, LinkedIn, Medium)
   - Keep profiles updated
   - Build your professional presence

2. **Use AI Wisely**
   - Don't immediately look at solutions
   - Try problems for 20-30 min first
   - Use AI for hints, not full solutions

3. **Offline Capability**
   - Download problems before traveling
   - Use Ollama for privacy/offline AI
   - Sync solutions when back online

4. **Stay Updated**
   - Check AI/ML News regularly
   - Use Strategic Chatbot for trend analysis
   - Stay informed about industry changes

---

## FAQ

### General

**Q: Is the app free?**
A: The app is open source and free. You only pay for API usage (Gemini has a generous free tier).

**Q: Does it work offline?**
A: Partially. Offline Mode allows pre-downloaded problems. Ollama works offline after setup.

**Q: Is my data secure?**
A: The app stores API keys locally in your build. No data is sent to third parties except the configured APIs.

### LeetCode Features

**Q: Can I solve any LeetCode problem?**
A: Yes, any public LeetCode problem URL will work.

**Q: Why does fetching sometimes fail?**
A: LeetCode may rate-limit requests. Wait a moment and try again.

**Q: Are solutions synced to GitHub automatically?**
A: You need to manually trigger sync. Check GitHub settings.

### AI Features

**Q: Which AI is better - Gemini or Ollama?**
A: Gemini is faster and more capable. Ollama is private and free after setup.

**Q: How much does Gemini cost?**
A: Gemini has a free tier with generous limits. Most users won't incur costs.

**Q: Does AI Interview Prep replace real interviews?**
A: No, but it's excellent practice. Combine with real mock interviews.

### Profile Features

**Q: Why doesn't Credly show my badges?**
A: Credly pages are JavaScript-rendered. If scraping fails, use the "View on Credly" button.

**Q: How do I update my profile information?**  
A: The app fetches live data from each platform. Update your actual profiles.

**Q: Can I add more platforms to Profile?**
A: Not currently, but the codebase can be extended.

### Technical Issues

**Q: App crashes on launch. What do I do?**
A: Check that all required fields in local.properties are filled correctly.

**Q: AI features return errors. Why?**
A: Verify API keys are valid and you have internet connectivity.

**Q: Ollama won't connect from my phone. Help!**
A: Use your computer's local IP (not localhost). Ensure firewall allows port 11434.

---

## Feature Summary Table

| Feature | Tab | Requires Internet | Requires API Key |
|---------|-----|-------------------|------------------|
| LeetCode Fetch | Leetcode | Yes | No |
| AI Analysis | Leetcode | Yes | Gemini |
| Solution Sync | Leetcode | Yes | GitHub |
| Local AI | Ollama | No* | No |
| Interview Prep | Features | Yes | Gemini |
| AI News | Features | Yes | Gemini |
| Analytics | Features | No | No |
| Goals/Achievements | Features | No | No |
| Flashcards | Features | No | No |
| Focus Mode | Features | No | No |
| Strategic Chatbot | Chatbot | Yes | Gemini |
| GitHub Profile | Profile | Yes | GitHub |
| Credly Badges | Profile | Yes | No |
| LinkedIn Info | Profile | Partial | No |
| Medium Articles | Profile | Yes | No |

*Requires Ollama running on local network

---

## Keyboard Shortcuts (When using external keyboard)

| Shortcut | Action |
|----------|--------|
| Enter | Send message/submit |
| Tab | Move to next field |
| Ctrl+V / Cmd+V | Paste |
| Ctrl+C / Cmd+C | Copy |

---

## Version Information

- **App Version**: 1.0
- **Min Android Version**: 7.0 (API 24)
- **Target Android Version**: 14 (API 35)
- **Last Updated**: April 2026

---

## Contact & Support

- **Repository**: [github.com/VigneshwaraChinnadurai/Google_Prep](https://github.com/VigneshwaraChinnadurai/Google_Prep)
- **Issues**: Report bugs via GitHub Issues
- **Author**: Vigneshwara Chinnadurai

---

*Happy Coding! 🚀*
