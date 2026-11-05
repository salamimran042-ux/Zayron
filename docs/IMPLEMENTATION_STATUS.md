# Zayron AI — implementation status

**Product identity:** Zayron AI  
**Tagline:** Think Beyond Limits.  
**Android application ID / namespace:** `ai.zayron.client`

A successful APK build proves compilation and packaging only; it does not prove that remote AI services work.

## Working in the current client

- Light-first Material 3 application shell and unified workspace.
- One workspace switcher for AI Chat, Image-to-Video, Image Editing, Document AI, App Builder and Voice Assistant.
- Local chat message entry, display, clear confirmation and app-private persistence across app restarts.
- Chat UI state is separated from the persistence contract through a repository interface.
- Android system picker for selecting image and document files in the preparation screens.
- Local prompt / app-brief entry fields.
- Explicit UI notices where a remote service is not connected.
- GitHub Actions compiles checked-in repository source and uploads a debug APK.
- Shared OkHttp transport with bounded timeouts, HTTPS-only URL resolution and off-main-thread request execution.
- Typed network outcomes distinguish successful responses, HTTP errors, unconfigured backend, rejected destinations and connection failures.

## Partial / preparation only

- Sign-in screen: visual shell only; no account is created and no credentials are submitted.
- AI Chat: local-only message history; no assistant response, streaming, cloud sync or server delivery.
- Image-to-Video: image selection and prompt entry only.
- Inpaint & Eraser: image selection and instruction entry only; no mask editor or image transformation.
- Document AI: file selection only; no parsing, retrieval, citations or document upload.
- App Builder: local app brief only; no code generation, preview, build worker or APK export.
- Voice Assistant: interface only; no microphone capture, speech recognition or spoken AI response.
- Network layer: shared transport and result handling are prepared, but no backend endpoint is called and no authentication/session interceptor exists.
- Chat repository abstraction currently uses a local SharedPreferences implementation only; no remote repository is connected.

## Not yet implemented

- Production backend deployment and configured HTTPS API base URL.
- HTTPS API authentication, secure session storage and token refresh.
- User registration, sign-in, password reset and account management.
- Credits, usage metering, subscriptions, billing and entitlement enforcement.
- Secure file upload, storage, deletion and job status tracking.
- Provider integrations and server-side secrets management.
- Server-side AI chat, image/video generation, inpainting, document RAG and app build workers.
- Voice capture, speech-to-text and text-to-speech integration.
- Automated Android UI tests, backend integration tests and release signing.

## Security and release notes

- The Android manifest includes the normal INTERNET permission; this alone does not initiate a network request.
- The backend URL remains empty until a real Zayron backend is deployed. The client refuses non-HTTPS backend URLs.
- Requests are restricted to the configured backend host, port and base path; absolute external destinations and path traversal are rejected.
- Network calls run on the IO dispatcher and use bounded timeouts.
- HTTP error responses are represented separately from transport failures. Response bodies should be parsed carefully and must not be exposed as raw user-facing error text.
- No body/header logging interceptor is installed. Never embed provider API keys or service credentials in the APK.
- Authentication must be added only after the backend session contract is defined, using secure session storage and server-side authorization.
- The chat preview is stored in app-private local preferences. It is not encrypted chat backup, account sync or a substitute for a secure backend.
- Debug APKs from separate GitHub-hosted runners may have different debug signing certificates. An update may therefore require a matching signing key or uninstalling the previous debug build (which can erase local app data).
- Do not label a feature as AI-powered until the complete backend path has been integrated and tested.
