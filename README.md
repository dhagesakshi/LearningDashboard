# Learning Dashboard (Android)

A compact Kotlin + Jetpack Compose assignment starter with Login, Course Dashboard, Course Details, lesson completion, Room persistence, and a unit test.

## Requirements
- Android Studio (recent stable version)
- JDK 17 (Android Studio's bundled JBR is recommended)
- Android SDK 35

## Run
1. Unzip this project.
2. Open Android Studio → **Open** → select the `LearningDashboard` folder (the folder containing `settings.gradle.kts`).
3. Allow Gradle sync to finish. If prompted, install Android SDK Platform 35 and Build Tools.
4. Create an emulator in **Device Manager** (for example, a Pixel device with API 35), or connect an Android phone with USB debugging enabled.
5. Press **Run ▶** with the `app` configuration selected.

Login is intentionally mocked: enter any valid-looking email and a password of at least 6 characters.

## What is included
- Compose UI with Login, Dashboard, and Details screens
- MVVM-style ViewModel and repository separation
- Room database stores courses and lesson completion locally
- Mock remote data source with simulated loading and error handling
- Dashboard states: loading, success, empty, error, and retry
- Lesson completion updates the course progress and persists locally
- Unit test for progress calculation

## Offline demo
This assignment uses a mock remote source rather than a real internet API. On first dashboard load, sample course data is saved to Room. The app reads the Room cache if the mock source fails. To demonstrate the fallback reliably, use the **Simulate API failure** button on the dashboard after the first successful load; cached courses should remain visible. Turning off device internet alone does not disable a mock API.

## Test
In Android Studio, open the Gradle tool window and run `app > Tasks > verification > testDebugUnitTest`, or use the terminal:
```bash
./gradlew testDebugUnitTest
```
A Gradle wrapper is not included in this ZIP; Android Studio can use its configured Gradle installation. If running from terminal, install Gradle or generate a wrapper from Android Studio first.

## Build APK
In Android Studio: **Build → Build Bundle(s) / APK(s) → Build APK(s)**. The debug APK is usually created at `app/build/outputs/apk/debug/app-debug.apk`.

## Architecture
Compose UI → ViewModel → Repository → mock remote source + Room local cache. In production, replace the mock source with Retrofit/HTTP and use secure token storage backed by Android Keystore.
