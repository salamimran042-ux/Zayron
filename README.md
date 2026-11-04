# Zayron AI

**Think Beyond Limits.**

Android identity:
- Application ID / namespace: `ai.zayron.client`
- Minimum Android SDK: 26
- Compile / target SDK: 34
- Java: 17
- Kotlin: 1.9.22
- Android Gradle Plugin: 8.2.2
- Gradle: 8.4

## Current implementation status

This branch contains a newly authored Android/Compose foundation:
- Light-first Material 3 theme foundation
- Dashboard with all six Zayron AI tools
- Navigation to six clearly labelled foundation screens
- Backend configuration placeholder with no embedded provider credentials
- GitHub Actions workflow that builds committed repository source

The six tool screens are foundation/placeholder screens. Real AI workflows, authentication, credits, persistence, uploads, generation jobs and backend integrations are not yet implemented or verified.

## Build

GitHub Actions runs **Build Zayron AI APK** on pushes to `main` or `zayron-ai-implementation`, and can also be started manually. It compiles checked-in source files and does not generate or overwrite Kotlin source.

On a successful run, download the `Zayron-AI-Debug-APK` artifact.

## Backend security

Do not put provider API keys or private credentials in the Android application. Connect the client to a secured HTTPS backend when the backend contract and deployment are ready.
