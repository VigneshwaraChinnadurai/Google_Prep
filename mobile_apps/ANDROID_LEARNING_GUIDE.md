# Android Development Learning Guide
## A Complete Roadmap for Understanding the Apps in This Repository

This guide is tailored specifically to help you understand and take full control of the Android apps in this `mobile_apps/` folder. It covers everything from basics to the specific technologies used in each app.

---

## 📱 Apps Overview

| App | Architecture | AI Integration | Key Technologies |
|-----|-------------|----------------|------------------|
| `leetcode_checker/` | Standalone | Google Gemini | Compose, Room, MVVM |
| `leedcode_checker_ollama/` | Standalone | Ollama (Local) | Compose, Room, MVVM |
| `job_automation_agent/` | Client-Server | Python Backend | Compose, Retrofit, WebSocket |
| `job_automation_standalone/` | Standalone | Google Gemini | Compose, Room, MVVM |

---

## 🎯 Learning Path (Recommended Order)

### Phase 1: Foundations (Week 1-2)
1. Kotlin Basics
2. Android Studio Setup
3. Android Project Structure
4. Gradle Build System

### Phase 2: Modern Android UI (Week 3-4)
5. Jetpack Compose
6. Material Design 3
7. Navigation

### Phase 3: Data & Architecture (Week 5-6)
8. MVVM Architecture
9. Room Database
10. StateFlow & Coroutines

### Phase 4: Networking & AI (Week 7-8)
11. Retrofit & API Calls
12. Google Gemini SDK
13. Background Processing

---

## 1️⃣ Kotlin Basics

Kotlin is the primary language for Android development. All apps in this folder use Kotlin.

### Key Concepts to Learn

```kotlin
// Variables
val immutable = "Cannot change"    // Like final in Java
var mutable = "Can change"

// Null Safety (Kotlin's killer feature)
var nullable: String? = null       // Can be null
var nonNull: String = "Must have value"

// Data Classes (used heavily for models)
data class Job(
    val id: Long,
    val title: String,
    val company: String
)

// Lambda Functions
val items = listOf(1, 2, 3)
items.filter { it > 1 }           // [2, 3]
items.map { it * 2 }              // [2, 4, 6]

// Extension Functions
fun String.addExclamation() = "$this!"
"Hello".addExclamation()          // "Hello!"

// Coroutines (async programming)
suspend fun fetchData(): Data {
    return withContext(Dispatchers.IO) {
        // Network call
    }
}
```

### 📚 Resources
- **Official**: [Kotlin Docs](https://kotlinlang.org/docs/home.html)
- **Interactive**: [Kotlin Koans](https://play.kotlinlang.org/koans)
- **Video**: [Kotlin Course for Beginners](https://www.youtube.com/watch?v=F9UC9DY-vIU) (freeCodeCamp)

### 📁 Examples in Your Code
- `job_automation_standalone/app/src/main/java/com/vignesh/jobautomation/data/database/Database.kt` - Data classes
- `leetcode_checker/app/src/main/java/.../data/models/` - Model classes

---

## 2️⃣ Android Project Structure

Every Android project follows this structure:

```
app/
├── build.gradle.kts          # App-level dependencies & config
├── src/
│   ├── main/
│   │   ├── AndroidManifest.xml    # App permissions & components
│   │   ├── java/com/package/      # Kotlin/Java source code
│   │   │   ├── MainActivity.kt    # Entry point
│   │   │   ├── ui/                # UI screens
│   │   │   ├── data/              # Data layer
│   │   │   ├── viewmodel/         # ViewModels
│   │   │   └── ai/                # AI integration
│   │   └── res/                   # Resources
│   │       ├── values/            # strings.xml, colors.xml
│   │       ├── drawable/          # Images, icons
│   │       └── xml/               # XML configs
│   └── test/                      # Unit tests
├── proguard-rules.pro        # Code obfuscation rules
build.gradle.kts              # Project-level config
settings.gradle.kts           # Module settings
gradle.properties             # Gradle settings
local.properties              # Local SDK path & API keys
```

### Key Files Explained

| File | Purpose |
|------|---------|
| `AndroidManifest.xml` | Declares permissions, activities, services |
| `build.gradle.kts` | Dependencies, SDK versions, build config |
| `local.properties` | API keys (NEVER commit to git!) |
| `MainActivity.kt` | App entry point, sets up navigation |

### 📁 Your Apps Structure
- Check `job_automation_standalone/app/src/main/` for a clean example

---

## 3️⃣ Gradle Build System

Gradle manages dependencies, builds APKs, and configures the project.

### Key Concepts

```kotlin
// app/build.gradle.kts

plugins {
    id("com.android.application")      // Android app plugin
    id("org.jetbrains.kotlin.android") // Kotlin support
    id("com.google.devtools.ksp")      // Annotation processing
}

android {
    namespace = "com.vignesh.jobautomation"
    compileSdk = 34                    // SDK version to compile against
    
    defaultConfig {
        applicationId = "com.vignesh.jobautomation"  // Unique app ID
        minSdk = 26                    // Minimum Android version
        targetSdk = 34                 // Target Android version
        versionCode = 1                // Internal version number
        versionName = "1.0.0"          // User-visible version
    }
    
    buildFeatures {
        compose = true                 // Enable Jetpack Compose
        buildConfig = true             // Generate BuildConfig class
    }
}

dependencies {
    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.material3:material3")
    
    // Room Database
    implementation("androidx.room:room-runtime:2.6.0")
    ksp("androidx.room:room-compiler:2.6.0")  // Code generation
    
    // Gemini AI
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")
}
```

### Common Commands

```powershell
# Build debug APK
./gradlew assembleDebug

# Build and install on device
./gradlew installDebug

# Clean build
./gradlew clean

# Run tests
./gradlew test

# Check dependencies
./gradlew dependencies
```

### 📚 Resources
- **Official**: [Android Gradle Plugin](https://developer.android.com/build)
- **Video**: [Gradle for Android](https://www.youtube.com/watch?v=o0M4f5djJTQ)

---

## 4️⃣ Jetpack Compose (UI Framework)

Compose is the modern way to build Android UI. All apps in this folder use Compose.

### Key Concepts

```kotlin
// Basic Composable Function
@Composable
fun Greeting(name: String) {
    Text(text = "Hello, $name!")
}

// State Management
@Composable
fun Counter() {
    var count by remember { mutableStateOf(0) }
    
    Button(onClick = { count++ }) {
        Text("Count: $count")
    }
}

// Layouts
@Composable
fun MyScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        Text("Title", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Row {
            Text("Left")
            Spacer(modifier = Modifier.weight(1f))
            Text("Right")
        }
    }
}

// Lists
@Composable
fun JobList(jobs: List<Job>) {
    LazyColumn {
        items(jobs) { job ->
            JobCard(job)
        }
    }
}

// Cards
@Composable
fun JobCard(job: Job) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(job.title, style = MaterialTheme.typography.titleMedium)
            Text(job.company, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
```

### Compose Modifiers (Layout & Styling)

```kotlin
Modifier
    .fillMaxWidth()           // Take full width
    .fillMaxSize()            // Take full width & height
    .padding(16.dp)           // Add padding
    .background(Color.Blue)   // Background color
    .clickable { }            // Make clickable
    .weight(1f)               // Flex weight in Row/Column
    .size(48.dp)              // Fixed size
```

### 📚 Resources
- **Official**: [Jetpack Compose](https://developer.android.com/jetpack/compose)
- **Codelabs**: [Compose Basics](https://developer.android.com/codelabs/jetpack-compose-basics)
- **Video**: [Compose Course](https://www.youtube.com/watch?v=6_wK_Ud8--0) (Philipp Lackner)

### 📁 Examples in Your Code
- `job_automation_standalone/app/src/main/java/.../ui/dashboard/DashboardScreen.kt`
- `job_automation_standalone/app/src/main/java/.../ui/jobs/JobsScreen.kt`
- `leetcode_checker/app/src/main/java/.../ui/screens/`

---

## 5️⃣ Material Design 3

Material 3 is Google's latest design system. Your apps use it for consistent styling.

### Theme Setup

```kotlin
// ui/theme/Theme.kt
@Composable
fun JobAutomationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
```

### Common Components

```kotlin
// Buttons
Button(onClick = { }) { Text("Primary") }
OutlinedButton(onClick = { }) { Text("Outlined") }
TextButton(onClick = { }) { Text("Text") }
FloatingActionButton(onClick = { }) { Icon(Icons.Default.Add, "Add") }

// Text Fields
OutlinedTextField(
    value = text,
    onValueChange = { text = it },
    label = { Text("Label") }
)

// Cards
Card { /* content */ }
ElevatedCard { /* content */ }

// Dialogs
AlertDialog(
    onDismissRequest = { },
    title = { Text("Title") },
    text = { Text("Message") },
    confirmButton = { TextButton(onClick = {}) { Text("OK") } }
)

// Bottom Navigation
NavigationBar {
    NavigationBarItem(
        selected = true,
        onClick = { },
        icon = { Icon(Icons.Default.Home, "Home") },
        label = { Text("Home") }
    )
}
```

### 📚 Resources
- **Official**: [Material 3 for Compose](https://developer.android.com/jetpack/compose/designsystems/material3)
- **Components**: [Material 3 Components](https://m3.material.io/components)

---

## 6️⃣ Navigation

Navigation Compose handles screen transitions and back stack.

### Setup

```kotlin
// MainActivity.kt
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    
    NavHost(
        navController = navController,
        startDestination = "dashboard"
    ) {
        composable("dashboard") {
            DashboardScreen(
                onNavigateToJobs = { navController.navigate("jobs") }
            )
        }
        composable("jobs") {
            JobsScreen(
                onNavigateToDetail = { jobId ->
                    navController.navigate("job/$jobId")
                }
            )
        }
        composable("job/{jobId}") { backStackEntry ->
            val jobId = backStackEntry.arguments?.getString("jobId")
            JobDetailScreen(jobId = jobId)
        }
    }
}
```

### Navigation Actions

```kotlin
// Navigate forward
navController.navigate("screen")

// Navigate with arguments
navController.navigate("job/$jobId")

// Navigate and clear back stack
navController.navigate("home") {
    popUpTo("splash") { inclusive = true }
}

// Go back
navController.popBackStack()
```

### 📚 Resources
- **Official**: [Navigation Compose](https://developer.android.com/jetpack/compose/navigation)

### 📁 Examples in Your Code
- `job_automation_standalone/app/src/main/java/.../MainActivity.kt`

---

## 7️⃣ MVVM Architecture

Model-View-ViewModel separates UI from business logic.

```
┌─────────────────────────────────────────────────────────────┐
│                         UI Layer                            │
│  ┌─────────────────────────────────────────────────────┐   │
│  │              Composable Screens                      │   │
│  │  • Observes StateFlow from ViewModel                 │   │
│  │  • Calls ViewModel functions on user actions         │   │
│  └─────────────────────────────────────────────────────┘   │
│                           │                                 │
│                           ▼                                 │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                   ViewModel                          │   │
│  │  • Holds UI state (StateFlow)                        │   │
│  │  • Contains business logic                           │   │
│  │  • Survives configuration changes                    │   │
│  └─────────────────────────────────────────────────────┘   │
│                           │                                 │
│                           ▼                                 │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                   Repository                         │   │
│  │  • Single source of truth                            │   │
│  │  • Coordinates data from multiple sources            │   │
│  └─────────────────────────────────────────────────────┘   │
│                           │                                 │
│              ┌────────────┴────────────┐                   │
│              ▼                         ▼                   │
│  ┌───────────────────┐     ┌───────────────────┐          │
│  │   Room Database    │     │   Network/API     │          │
│  │   (Local Data)     │     │   (Remote Data)   │          │
│  └───────────────────┘     └───────────────────┘          │
└─────────────────────────────────────────────────────────────┘
```

### ViewModel Example

```kotlin
class JobsViewModel(
    private val repository: JobAutomationRepository
) : ViewModel() {
    
    // UI State
    private val _uiState = MutableStateFlow(JobsUiState())
    val uiState: StateFlow<JobsUiState> = _uiState.asStateFlow()
    
    // Load data
    init {
        viewModelScope.launch {
            repository.getAllJobs().collect { jobs ->
                _uiState.update { it.copy(jobs = jobs) }
            }
        }
    }
    
    // User actions
    fun addJob(job: Job) {
        viewModelScope.launch {
            repository.insertJob(job)
        }
    }
}

// UI State class
data class JobsUiState(
    val jobs: List<Job> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)
```

### Using in Composable

```kotlin
@Composable
fun JobsScreen(viewModel: JobsViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    
    LazyColumn {
        items(uiState.jobs) { job ->
            JobCard(
                job = job,
                onClick = { viewModel.selectJob(job) }
            )
        }
    }
}
```

### 📚 Resources
- **Official**: [App Architecture](https://developer.android.com/topic/architecture)
- **Video**: [MVVM Explained](https://www.youtube.com/watch?v=9dJXqNW4YdI)

### 📁 Examples in Your Code
- `job_automation_standalone/app/src/main/java/.../viewmodel/ViewModels.kt`
- `job_automation_standalone/app/src/main/java/.../data/repository/JobAutomationRepository.kt`

---

## 8️⃣ Room Database

Room is Android's local database solution (SQLite wrapper).

### Entity (Table)

```kotlin
@Entity(tableName = "jobs")
data class JobEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val company: String,
    val description: String,
    val matchScore: Int? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
```

### DAO (Data Access Object)

```kotlin
@Dao
interface JobDao {
    @Query("SELECT * FROM jobs ORDER BY created_at DESC")
    fun getAllJobs(): Flow<List<JobEntity>>  // Reactive stream
    
    @Query("SELECT * FROM jobs WHERE id = :jobId")
    suspend fun getJobById(jobId: Long): JobEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(job: JobEntity): Long
    
    @Update
    suspend fun update(job: JobEntity)
    
    @Delete
    suspend fun delete(job: JobEntity)
    
    @Query("DELETE FROM jobs WHERE id = :jobId")
    suspend fun deleteById(jobId: Long)
}
```

### Database

```kotlin
@Database(
    entities = [JobEntity::class, ProfileEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun jobDao(): JobDao
    abstract fun profileDao(): ProfileDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "job_automation.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
```

### Type Converters (for complex types)

```kotlin
class Converters {
    @TypeConverter
    fun fromStringList(value: List<String>): String = value.joinToString(",")
    
    @TypeConverter
    fun toStringList(value: String): List<String> = 
        if (value.isEmpty()) emptyList() else value.split(",")
}
```

### 📚 Resources
- **Official**: [Room Database](https://developer.android.com/training/data-storage/room)
- **Codelab**: [Room with a View](https://developer.android.com/codelabs/android-room-with-a-view-kotlin)

### 📁 Examples in Your Code
- `job_automation_standalone/app/src/main/java/.../data/database/Database.kt` (complete example)

---

## 9️⃣ Coroutines & StateFlow

Coroutines handle async operations. StateFlow provides reactive state management.

### Coroutines Basics

```kotlin
// Launch a coroutine
viewModelScope.launch {
    val result = fetchData()  // Suspend function
    _uiState.value = result
}

// Dispatchers
Dispatchers.Main      // UI thread
Dispatchers.IO        // Network/Database
Dispatchers.Default   // CPU-intensive work

// Switch context
suspend fun fetchData() = withContext(Dispatchers.IO) {
    api.getData()
}

// Parallel execution
coroutineScope {
    val deferred1 = async { api.call1() }
    val deferred2 = async { api.call2() }
    val results = awaitAll(deferred1, deferred2)
}
```

### StateFlow

```kotlin
// In ViewModel
private val _uiState = MutableStateFlow(UiState())
val uiState: StateFlow<UiState> = _uiState.asStateFlow()

// Update state
_uiState.update { currentState ->
    currentState.copy(isLoading = true)
}

// In Composable
val uiState by viewModel.uiState.collectAsState()
```

### Flow (Reactive streams)

```kotlin
// From Room DAO
@Query("SELECT * FROM jobs")
fun getAllJobs(): Flow<List<Job>>

// Collect in ViewModel
repository.getAllJobs()
    .onStart { _uiState.update { it.copy(isLoading = true) } }
    .catch { e -> _uiState.update { it.copy(error = e.message) } }
    .collect { jobs -> _uiState.update { it.copy(jobs = jobs) } }
```

### 📚 Resources
- **Official**: [Kotlin Coroutines](https://developer.android.com/kotlin/coroutines)
- **Video**: [Coroutines Deep Dive](https://www.youtube.com/watch?v=ShNhJ3wMpvQ)

---

## 🔟 Google Gemini SDK Integration

Your apps use Gemini for AI features.

### Setup

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")
}

// local.properties (NEVER commit!)
GEMINI_API_KEY=your_api_key_here

// Access in code via BuildConfig
val apiKey = BuildConfig.GEMINI_API_KEY
```

### Basic Usage

```kotlin
class GeminiClient(apiKey: String) {
    private val model = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = apiKey,
        generationConfig = generationConfig {
            temperature = 0.3f
            maxOutputTokens = 4096
        }
    )
    
    suspend fun generateContent(prompt: String): String {
        val response = model.generateContent(prompt)
        return response.text ?: ""
    }
    
    // Chat conversation
    suspend fun chat(history: List<Content>, message: String): String {
        val chat = model.startChat(history = history)
        val response = chat.sendMessage(message)
        return response.text ?: ""
    }
}
```

### Structured Output (JSON parsing)

```kotlin
suspend fun analyzeJob(jobDescription: String): JobAnalysis {
    val prompt = """
        Analyze this job description and return JSON:
        {"matchScore": 0-100, "skills": ["skill1", "skill2"]}
        
        Job: $jobDescription
    """.trimIndent()
    
    val response = model.generateContent(prompt)
    return moshi.adapter(JobAnalysis::class.java)
        .fromJson(response.text!!)!!
}
```

### 📚 Resources
- **Official**: [Gemini API](https://ai.google.dev/gemini-api/docs)
- **Get API Key**: [Google AI Studio](https://aistudio.google.com/app/apikey)

### 📁 Examples in Your Code
- `job_automation_standalone/app/src/main/java/.../ai/GeminiClient.kt` (complete implementation)
- `leetcode_checker/app/src/main/java/.../ai/`

---

## 1️⃣1️⃣ Retrofit (API Calls)

Retrofit is used for REST API communication (in client-server apps).

### Setup

```kotlin
// Define API interface
interface JobApi {
    @GET("jobs")
    suspend fun getJobs(): List<Job>
    
    @GET("jobs/{id}")
    suspend fun getJob(@Path("id") id: Long): Job
    
    @POST("jobs")
    suspend fun createJob(@Body job: Job): Job
    
    @PUT("jobs/{id}")
    suspend fun updateJob(@Path("id") id: Long, @Body job: Job): Job
    
    @DELETE("jobs/{id}")
    suspend fun deleteJob(@Path("id") id: Long)
}

// Create Retrofit instance
val retrofit = Retrofit.Builder()
    .baseUrl("https://api.example.com/")
    .addConverterFactory(MoshiConverterFactory.create())
    .client(OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build())
    .build()

val api = retrofit.create(JobApi::class.java)
```

### 📚 Resources
- **Official**: [Retrofit](https://square.github.io/retrofit/)

### 📁 Examples in Your Code
- `job_automation_agent/android/app/src/main/java/.../api/`

---

## 1️⃣2️⃣ App-Specific Deep Dives

### leetcode_checker/
- **Purpose**: Check LeetCode solutions using Gemini AI
- **Key Files**: 
  - `app/src/main/java/.../ui/screens/` - All UI screens
  - `app/src/main/java/.../ai/` - Gemini integration
- **Docs**: Read `DETAILED_DOCUMENTATION.md` in that folder

### leedcode_checker_ollama/
- **Purpose**: Same as above but uses local Ollama instead of Gemini
- **Key Difference**: Uses localhost API calls to Ollama server

### job_automation_agent/
- **Purpose**: Job search automation with Python backend
- **Architecture**: Client-Server (Android + Python FastAPI)
- **Key Files**:
  - `android/` - Android client
  - `backend/` - Python FastAPI server
- **Docs**: Read `DETAILED_DOCUMENTATION.md`

### job_automation_standalone/
- **Purpose**: Complete job automation without backend
- **Key Files**:
  - `app/src/main/java/.../MainActivity.kt` - Entry point
  - `app/src/main/java/.../ai/GeminiClient.kt` - AI integration
  - `app/src/main/java/.../data/database/Database.kt` - Room DB
  - `app/src/main/java/.../viewmodel/ViewModels.kt` - All ViewModels
  - `app/src/main/java/.../ui/` - All screens

---

## 🛠️ Development Environment Setup

### Required Tools
1. **Android Studio** - [Download](https://developer.android.com/studio)
2. **JDK 17** - Install via Android Studio or [Adoptium](https://adoptium.net/)
3. **Android SDK** - Via Android Studio SDK Manager

### Recommended Android Studio Plugins
- **Kotlin** (built-in)
- **Material Theme UI** - Better IDE theme
- **Rainbow Brackets** - Easier bracket matching
- **Key Promoter X** - Learn shortcuts

### Useful ADB Commands

```powershell
# List connected devices
adb devices

# Install APK
adb install app/build/outputs/apk/debug/app-debug.apk

# Uninstall app
adb uninstall com.vignesh.jobautomation

# View logs
adb logcat | Select-String "JobAutomation"

# Clear app data
adb shell pm clear com.vignesh.jobautomation

# Take screenshot
adb exec-out screencap -p > screenshot.png
```

---

## 📖 Recommended Learning Path

### Week 1-2: Kotlin Foundations
- [ ] Complete [Kotlin Koans](https://play.kotlinlang.org/koans)
- [ ] Read `Database.kt` - understand data classes
- [ ] Read `ViewModels.kt` - understand lambdas & higher-order functions

### Week 3-4: Compose UI
- [ ] Complete [Compose Basics Codelab](https://developer.android.com/codelabs/jetpack-compose-basics)
- [ ] Study `DashboardScreen.kt` - layouts, modifiers
- [ ] Study `JobsScreen.kt` - lists, cards
- [ ] Modify one screen to add a new feature

### Week 5-6: Architecture
- [ ] Read [Guide to App Architecture](https://developer.android.com/topic/architecture)
- [ ] Trace data flow: Database → DAO → Repository → ViewModel → UI
- [ ] Study `GeminiClient.kt` - coroutines, suspend functions

### Week 7-8: Build Something
- [ ] Add a new screen to job_automation_standalone
- [ ] Add a new AI feature using Gemini
- [ ] Fix a bug or improve an existing feature

---

## 🎥 Video Resources (Free)

| Topic | Channel | Link |
|-------|---------|------|
| Kotlin Basics | freeCodeCamp | [Full Course](https://www.youtube.com/watch?v=F9UC9DY-vIU) |
| Jetpack Compose | Philipp Lackner | [Playlist](https://www.youtube.com/playlist?list=PLQkwcJG4YTCSpJ2NLhDTHhi6XBNfk9WiC) |
| MVVM Architecture | Philipp Lackner | [Video](https://www.youtube.com/watch?v=9dJXqNW4YdI) |
| Room Database | Stevdza-San | [Video](https://www.youtube.com/watch?v=bOd3wO0uFr8) |
| Coroutines | Philipp Lackner | [Video](https://www.youtube.com/watch?v=ShNhJ3wMpvQ) |
| Full Android Course | Google | [Android Basics with Compose](https://developer.android.com/courses/android-basics-compose/course) |

---

## 📝 Quick Reference Card

```
┌────────────────────────────────────────────────────────────────┐
│                    COMPOSE CHEAT SHEET                         │
├────────────────────────────────────────────────────────────────┤
│ @Composable fun MyScreen() { }    // Define UI component       │
│ remember { mutableStateOf() }     // Remember state            │
│ by remember { }                   // Delegate state            │
│ LaunchedEffect(key) { }           // Side effect               │
│ collectAsState()                  // Collect Flow to State     │
├────────────────────────────────────────────────────────────────┤
│                    LAYOUTS                                      │
├────────────────────────────────────────────────────────────────┤
│ Column { }                        // Vertical stack            │
│ Row { }                           // Horizontal stack          │
│ Box { }                           // Stack/overlap             │
│ LazyColumn { items() }            // Scrollable list           │
│ Scaffold { }                      // App structure             │
├────────────────────────────────────────────────────────────────┤
│                    MODIFIERS                                    │
├────────────────────────────────────────────────────────────────┤
│ .fillMaxWidth()                   // Full width                │
│ .padding(16.dp)                   // Add padding               │
│ .clickable { }                    // Make clickable            │
│ .weight(1f)                       // Flex weight               │
│ .background(color)                // Background color          │
├────────────────────────────────────────────────────────────────┤
│                    COROUTINES                                   │
├────────────────────────────────────────────────────────────────┤
│ viewModelScope.launch { }         // Launch in ViewModel       │
│ withContext(Dispatchers.IO) { }   // Switch to IO thread       │
│ flow.collect { }                  // Collect Flow values       │
│ async { } / await()               // Parallel execution        │
└────────────────────────────────────────────────────────────────┘
```

---

## 🤔 Common Issues & Solutions

### Build Fails with JDK Error
**Problem**: jlink.exe error with Android Studio's JBR
**Solution**: Set `org.gradle.java.home` in `gradle.properties` to Temurin JDK 17

### App Crashes on Start
**Check**: Logcat for stack trace
**Common causes**: Missing API key, database migration needed, null pointer

### Compose Preview Not Working
**Solution**: Build project first (`Ctrl+F9`), ensure `@Preview` annotation exists

### Gradle Sync Fails
**Try**: File → Invalidate Caches → Restart, or delete `.gradle` folder

---

## 🚀 Next Steps

1. **Read the code** - Start with `MainActivity.kt` and trace the flow
2. **Make small changes** - Change a button text, add a log statement
3. **Debug** - Use breakpoints in Android Studio
4. **Build features** - Add a new feature to understand the architecture
5. **Ask questions** - Use this guide as reference

Good luck with your Android development journey! 🎉
