# Glassline Launcher

Traditional Android Views/Kotlin launcher baseline for Android 8.0+.

## Build in GitHub Actions
1. Push this project to a GitHub repository.
2. Open **Actions** and run **Build Glassline Launcher APK**, or push a change to `app/**`.
3. Download the `glassline-debug-apk` artifact from the completed workflow run.
4. Install `app-debug.apk` on your device and select Glassline as the Home app.

## Build locally
Requires JDK 17, Android SDK Platform 36, Build Tools 36.0.0, and Gradle 8.13:
```bash
gradle assembleDebug
```

This is a functional starter, not a verified production launcher. Test on-device before using as the default Home app. It does not request unrestricted package visibility, calendar access, or media-session access. Sustained 120 FPS cannot be guaranteed by application code.
