<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# Run and deploy your AI Studio app

This contains everything you need to run your app locally.

View your app in AI Studio: https://ai.studio/apps/0b61a368-019e-43b3-a0ac-df833401bf8c

## Run Locally

**Prerequisites:**  [Android Studio](https://developer.android.com/studio)


1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project.
4. Create a file named `.env` in the project directory and set `GEMINI_API_KEY` in that file to your Gemini API key (see `.env.example` for an example)
5. Remove this line from the app's `build.gradle.kts` file: `signingConfig = signingConfigs.getByName("debugConfig")`
6. Run the app on an emulator or physical device

## Gradle

This project uses Android Gradle Plugin 9.1.1 and should be built with Gradle 9.3.1.

Do not commit the Gradle distribution zip. For local command-line builds, download `gradle-9.3.1-bin.zip` from the official Gradle releases page, place it in the project root if needed, and extract it to `.gradle-local/`. These paths are ignored by Git.

```bash
mkdir -p .gradle-local
unzip gradle-9.3.1-bin.zip -d .gradle-local
GRADLE_USER_HOME=.gradle-user .gradle-local/gradle-9.3.1/bin/gradle :app:compileDebugKotlin
```

GitHub Actions downloads Gradle 9.3.1 during CI and uploads the built APK as an artifact/release asset, so the repository does not need to store Gradle or APK binaries.
