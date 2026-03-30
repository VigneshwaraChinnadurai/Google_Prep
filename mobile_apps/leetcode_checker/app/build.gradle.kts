plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

import java.util.Properties

android {
    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val keystoreProperties = Properties()
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
    }

    val localPropertiesFile = rootProject.file("local.properties")
    val localProperties = Properties()
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { localProperties.load(it) }
    }

    // Load chatbot's separate API key from usecase_4 .env file
    val chatbotEnvFile = file("../../../agentic_ai/usecase_4_strategic_chatbot/.env")
    val chatbotGeminiKey = if (chatbotEnvFile.exists()) {
        chatbotEnvFile.readLines()
            .filter { it.trim().startsWith("GEMINI_API_KEY") && !it.trim().startsWith("#") }
            .map { it.substringAfter("=").trim().trim('"', '\'') }
            .firstOrNull() ?: ""
    } else {
        localProperties.getProperty("CHATBOT_GEMINI_API_KEY", "")
    }.replace("\\\\", "\\\\\\\\").replace("\"", "\\\"")

    val geminiApiKey = localProperties.getProperty("GEMINI_API_KEY", "")
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
    val githubToken = localProperties.getProperty("GITHUB_TOKEN", "")
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
    val githubOwner = localProperties.getProperty("GITHUB_OWNER", "VigneshwaraChinnadurai")
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
    val githubRepo = localProperties.getProperty("GITHUB_REPO", "Google_Prep")
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
    val githubBranch = localProperties.getProperty("GITHUB_BRANCH", "main")
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
    val settingsUpdatePassword = localProperties.getProperty("SETTINGS_UPDATE_PASSWORD", "1234")
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")

    // Ollama configuration (defaults to local loopback)
    val ollamaBaseUrl = localProperties.getProperty("OLLAMA_BASE_URL", "http://127.0.0.1:11434")
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
    val ollamaModel = localProperties.getProperty("OLLAMA_MODEL", "qwen2.5:3b")
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")

    namespace = "com.vignesh.leetcodechecker"
    compileSdk = 35
    ndkVersion = "26.1.10909125"

    defaultConfig {
        applicationId = "com.vignesh.leetcodechecker"
        minSdk = 24
        targetSdk = 35
        
        // NDK configuration for llama.cpp (64-bit only)
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
        
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17 -fexceptions -frtti -O3"
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DGGML_NATIVE=OFF",
                    "-DGGML_OPENMP=OFF"
                )
            }
        }
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
        buildConfigField("String", "CHATBOT_GEMINI_API_KEY", "\"$chatbotGeminiKey\"")
        buildConfigField("String", "GITHUB_TOKEN", "\"$githubToken\"")
        buildConfigField("String", "GITHUB_OWNER", "\"$githubOwner\"")
        buildConfigField("String", "GITHUB_REPO", "\"$githubRepo\"")
        buildConfigField("String", "GITHUB_BRANCH", "\"$githubBranch\"")
        buildConfigField("String", "SETTINGS_UPDATE_PASSWORD", "\"$settingsUpdatePassword\"")
        buildConfigField("String", "OLLAMA_BASE_URL", "\"$ollamaBaseUrl\"")
        buildConfigField("String", "OLLAMA_MODEL", "\"$ollamaModel\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                val storePath = keystoreProperties.getProperty("storeFile")
                if (!storePath.isNullOrBlank()) {
                    storeFile = file(storePath)
                }
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")

    implementation(platform("androidx.compose:compose-bom:2024.09.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
