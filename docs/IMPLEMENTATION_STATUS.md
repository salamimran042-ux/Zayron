# Zayron AI — implementation status

**Product identity:** Zayron AI  
**Tagline:** Think Beyond Limits.  
**Android application ID / namespace:** `ai.zayron.client`

This document separates what is present in the Android client from services that still need implementation. A successful APK build proves compilation and packaging only; it does not prove that remote AI services work.

## Working in the current client

- Light-first Material 3 application shell and unified workspace.
- One workspace switcher for AI Chat, Image-to-Video, Image Editing, Document AI, App Builder and Voice Assistant.
- Local chat message entry, display, clear confirmation and app-private persistence across app restarts.
- Android system picker for selecting image and document files in the preparation screens.
- Local prompt / app-brief entry fields.
- Explicit UI notices where a remote service is not connected.
- GitHub Actions compiles checked-in repository source and uploads a debug APK.

## Partial / preparation only

- Sign-in screen: visual shell only; no account is created and no credentials are submitted.
- AI Chat: local-only message history; no assistant response, streaming, cloud sync or server delivery.
- Image-to-Video: image selection and prompt entry only.
- Inpaint & Eraser: image selection and instruction entry only; no mask editor or image transformation.
- Document AI: file selection only; no parsing, retrieval, citations or document upload.
- App Builder: local app brief only; no code generation, preview, build worker or APK export.
- Voice Assistant: interface only; no microphone capture, speech recognition or spoken AI response.

## Not yet implemented

- Production backend, HTTPS API authentication and token refresh.
- User registration, sign-in, password reset and account management.
- Credits, usage metering, subscriptions, billing and entitlement enforcement.
- Secure file upload, storage, deletion and job status tracking.
- Provider integrations and server-side secrets management.
- Server-side AI chat, image/video generation, inpainting, document RAG and app build workers.
- Voice capture, speech-to-text and text-to-speech integration.
- Automated Android UI tests, backend integration tests and release signing.

## Security and release notes

- Never embed provider API keys or service credentials in the Android APK.
- The chat preview is stored in app-private local preferences. It is not encrypted chat backup, account sync or a substitute for a secure backend.
- Do not use production credentials in debug builds.
- Debug APKs from separate GitHub-hosted runners may have different debug signing certificates. An update may therefore require a matching signing key or uninstalling the previous debug build (which can erase local app data).
- Do not label a feature as AI-powered until the complete backend path has been integrated and tested.
