param(
    [ValidateSet("debug", "release")]
    [string]$Variant = "debug"
)

$ErrorActionPreference = "Stop"
Push-Location $PSScriptRoot

try {
    if (-not (Test-Path "./gradlew.bat")) {
        throw "gradlew.bat not found."
    }

    if (-not $env:JAVA_HOME) {
        $studioJbr = "C:/Program Files/Android/Android Studio/jbr"
        if (Test-Path "$studioJbr/bin/java.exe") {
            $env:JAVA_HOME = $studioJbr
        }
    }

    if ($Variant -eq "release" -and -not (Test-Path "./keystore.properties")) {
        throw "keystore.properties not found. Copy keystore.properties.example to keystore.properties and fill in signing values."
    }

    if ($Variant -eq "debug") {
        ./gradlew.bat assembleDebug
        if ($LASTEXITCODE -ne 0) { throw "Gradle build failed with exit code $LASTEXITCODE." }
        Write-Host "Debug APK:" (Resolve-Path "./app/build/outputs/apk/debug/app-debug.apk")
    } else {
        ./gradlew.bat assembleRelease
        if ($LASTEXITCODE -ne 0) { throw "Gradle build failed with exit code $LASTEXITCODE." }
        Write-Host "Release APK:" (Resolve-Path "./app/build/outputs/apk/release/app-release.apk")
    }
}
finally {
    Pop-Location
}
