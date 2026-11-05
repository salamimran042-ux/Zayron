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

## Current implementation

The implementation branch contains a light-first unified Android/Compose workspace with six modes:
- AI Chat
- Image-to-Video
- AI Inpaint & Eraser
- Document AI
- AI App Builder
- Voice Assistant

AI Chat currently stores preview messages in app-private local storage so they remain after the app is closed and reopened. Messages are not sent to an AI service. Tool preparation screens support local file selection or prompt entry where applicable, but do not upload or process content.

## Honest feature status

- **Implemented in client:** workspace shell, mode switcher, local chat history, local file pickers, prompt/brief entry and explicit service-status notices.
- **Partial:** sign-in presentation and all five non-chat tool preparation interfaces.
- **Not connected:** authentication, cloud chat/AI replies, image/video generation, inpainting, document parsing/RAG, app generation/build worker, voice recognition/synthesis, credits, billing and backend persistence.

See [docs/IMPLEMENTATION_STATUS.md](docs/IMPLEMENTATION_STATUS.md) for the detailed feature audit and [docs/BACKEND_API_CONTRACT.md](docs/BACKEND_API_CONTRACT.md) for the proposed secure server boundary. The backend contract is planning documentation; it does not claim those endpoints are live.

## Build

GitHub Actions runs **Build Zayron AI APK** on pushes to `main` or `zayron-ai-implementation`, and can also be started manually. It compiles checked-in source files and does not generate or overwrite Kotlin source.

On a successful run, download the `Zayron-AI-Debug-APK` artifact.

## Security

Do not put provider API keys or private credentials in the Android application. Connect the client to a secured HTTPS backend only after the backend contract and deployment are ready.


Latest engineering update: local chat history is now persisted in app-private storage, with an in-app clear confirmation. This change is awaiting its own CI build verification.
