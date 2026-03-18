# LeetCode Checker (Android)

This app has a single button (`LeetCode`) that fetches and displays the current LeetCode Daily Challenge.

## How it works

- It calls LeetCode GraphQL endpoint: `https://leetcode.com/graphql`
- It uses `activeDailyCodingChallengeQuestion`, so the date changes automatically based on LeetCode's current daily challenge.
- It then fetches question content using `titleSlug` and shows a short preview.

## Project location

`mobile_apps/leetcode_checker`

## Open and run

1. Open Android Studio.
2. Select **Open** and choose `mobile_apps/leetcode_checker`.
3. Let Gradle sync complete.
4. Run the app on an emulator or physical device.

## Build APK From Terminal

From `mobile_apps/leetcode_checker`:

1. Debug APK:

	```powershell
	.\build_apk.ps1 -Variant debug
	```

2. Release APK:

	- Copy `keystore.properties.example` to `keystore.properties`
	- Fill in your keystore values
	- Run:

	```powershell
	.\build_apk.ps1 -Variant release
	```

APK output paths:

- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release.apk`

## Main files

- `app/src/main/java/com/vignesh/leetcodechecker/MainActivity.kt`
- `app/src/main/java/com/vignesh/leetcodechecker/LeetCodeViewModel.kt`
- `app/src/main/java/com/vignesh/leetcodechecker/data/LeetCodeRepository.kt`
- `app/src/main/java/com/vignesh/leetcodechecker/data/LeetCodeApi.kt`
- `app/src/main/AndroidManifest.xml`

## Notes

- Internet permission is included in manifest.
- The app opens the daily problem URL in browser via an "Open in Browser" button.

## Detailed Documentation

- See `DETAILED_DOCUMENTATION.md` for a full architecture and runtime-flow guide.
