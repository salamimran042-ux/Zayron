# Zayron AI — implementation status

**Product identity:** Zayron AI  
**Tagline:** Think Beyond Limits.  
**Android application ID / namespace:** `ai.zayron.client`

A successful APK build proves compilation and packaging only; it does not prove that remote AI services work.

## Working in the current client

- Light-first Material 3 application shell and unified workspace.
- One workspace switcher for AI Chat, Image-to-Video, Image Editing, Document AI, App Builder and Voice Assistant.
- Local chat message entry, display, clear confirmation and app-private persistence across app restarts.
- Chat message models distinguish user and assistant roles and explicit local/pending/sent/failed delivery states.
- Local chat JSON codec preserves message roles and delivery state, reads legacy records safely and tolerates unknown future enum values.
- Chat bubbles are laid out according to speaker role.
- Chat UI state is separated from the persistence contract through a repository interface.
- Android system picker for selecting image and document files in the preparation screens.
- Local prompt / app-brief entry fields.
- Explicit UI notices where a remote service is not connected.
- GitHub Actions compiles checked-in repository source, runs JVM unit tests and uploads a debug APK.
- Successful APK artifacts are configured for 90-day retention.
- Shared OkHttp transport with bounded timeouts, HTTPS-only URL resolution and off-main-thread request execution.
- Typed network outcomes distinguish successful responses, HTTP errors, authentication requirements, stream protocol errors, unconfigured backend, rejected destinations and connection failures.
- Safe API error mapping converts documented server error codes and HTTP statuses into user-facing messages without exposing raw server response bodies.
- Automated JVM unit tests cover safe API error mapping, chat history serialization compatibility, stream decoding and secure access-token expiry behavior.
- Authentication domain contract separates non-secret session profile data, stable failure categories and repository operations from the UI.
- Session credentials have an Android Keystore AES-256-GCM encryption foundation, stored as ciphertext in app-private no-backup storage with no plaintext fallback.
- A secure chat access-token provider reads the encrypted session and refuses to supply an expired access token.
- Android instrumentation tests cover Keystore-backed save/read round-trips, plaintext absence, ciphertext tamper rejection, cleanup and fresh-session recreation; the CI instrumentation job passed in workflow run #33.
- Backend URL and relative API-path policy is isolated into testable helpers; new JVM tests cover HTTPS-only configuration, base-path resolution and rejection of traversal, absolute, query, fragment and backslash paths.

## Partial / preparation only

- Sign-in screen: visual shell only; no account is created and no credentials are submitted.
- Authentication repository: interface and typed domain models are prepared, and encrypted session storage is implemented as a client foundation. There is still no concrete remote login, refresh-token rotation, logout request or server-backed session restoration.
- AI Chat: local-only message history and role-aware conversation presentation; no assistant response generation, streaming, cloud sync or server delivery.
- Chat network transport: an OkHttp SSE adapter now exists behind the remote data-source interface. It requires an injected access-token provider and a deployed backend endpoint; it is not connected to the chat UI or called by the app. MockWebServer tests exercise event streaming, bearer-token attachment, missing-token rejection and HTTP error handling.
- Image-to-Video: image selection and prompt entry only.
- Inpaint & Eraser: image selection and instruction entry only; no mask editor or image transformation.
- Document AI: file selection only; no parsing, retrieval, citations or document upload.
- App Builder: local app brief only; no code generation, preview, build worker or APK export.
- Voice Assistant: interface only; no microphone capture, speech recognition or spoken AI response.
- Network layer: shared transport, typed result handling, safe user-facing error mapping, a typed chat-stream contract, complete-frame SSE event parsing and incremental SSE frame decoding are prepared and covered by JVM tests. The secure token provider is not yet wired into a running backend session, and no automatic refresh-and-retry interceptor exists.

## Not yet implemented

- Production backend deployment and configured HTTPS API base URL.
- HTTPS API authentication, remote login, automatic token refresh and server-side session revocation.
- Backend-defined access/refresh token format, expiry, rotation, revocation and logout behavior.
- User registration, sign-in, password reset and account management.
- Credits, usage metering, subscriptions, billing and entitlement enforcement.
- Secure file upload, storage, deletion and job status tracking.
- Provider integrations and server-side secrets management.
- Server-side AI chat, image/video generation, inpainting, document RAG and app build workers.
- Voice capture, speech-to-text and text-to-speech integration.
- Backend integration tests and release signing.

## Security and release notes

- The Android manifest includes the normal INTERNET permission; this alone does not initiate a network request.
- The backend URL remains empty until a real Zayron backend is deployed. The client refuses non-HTTPS backend URLs.
- Requests are restricted to the configured backend host, port and base path; absolute external destinations and path traversal are rejected.
- Network calls run on the IO dispatcher and use bounded timeouts.
- HTTP error responses are represented separately from transport failures. Response bodies are parsed carefully and are not exposed as raw user-facing error text.
- No body/header logging interceptor is installed. Never embed provider API keys or service credentials in the APK.
- The Android client has a Keystore-backed encrypted session-storage foundation. Its device-side instrumentation suite passed in CI workflow run #33; remote login, refresh coordination, logout/revocation and backend integration remain outstanding.
- The chat preview is stored in app-private local preferences. It is not encrypted chat backup, account sync or a substitute for a secure backend.
- Debug APKs from separate GitHub-hosted runners may have different debug signing certificates. An update may therefore require a matching signing key or uninstalling the previous debug build (which can erase local app data).
- Do not label a feature as AI-powered until the complete backend path has been integrated and tested.
