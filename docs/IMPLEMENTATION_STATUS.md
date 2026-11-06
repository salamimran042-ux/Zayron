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
- GitHub Actions compiles checked-in repository source, runs JVM unit tests, builds a debug APK and uploads the artifact.
- Successful APK artifacts are configured for 90-day retention.
- Shared OkHttp transport with bounded timeouts, HTTPS-only URL resolution and off-main-thread request execution.
- Typed network outcomes distinguish successful responses, HTTP errors, authentication requirements, stream protocol errors, unconfigured backend, rejected destinations and connection failures.
- Safe API error mapping converts documented server error codes and HTTP statuses into user-facing messages without exposing raw server response bodies.
- Automated JVM unit tests cover safe API error mapping, chat history serialization compatibility, stream decoding and secure access-token expiry behavior.
- Authentication domain contract separates non-secret session profile data, stable failure categories and repository operations from the UI.
- Session credentials have an Android Keystore AES-256-GCM encryption foundation, stored as ciphertext in app-private no-backup storage with no plaintext fallback.
- A secure chat access-token provider reads the encrypted session and refuses to supply an expired access token.
- Android instrumentation tests cover Keystore-backed save/read round-trips, plaintext absence, ciphertext tamper rejection, cleanup and fresh-session recreation. The instrumentation job passed in workflow runs #33, #35 and #36; the #36 log records four tests started and a successful Gradle test task.
- Backend URL and relative API-path policy is isolated into testable helpers; JVM tests cover HTTPS-only configuration, base-path resolution and rejection of network-path references, encoded traversal examples, absolute, query, fragment and backslash paths.
- Workflow #69 (`Document local PDF extraction limits and capability`) passed project identity/source checks, JVM tests, Android debug APK packaging and Android Keystore instrumentation tests. The uploaded APK is a debug build, not a release-signed APK.

## Partial / preparation only

- Sign-in screen: visual shell only; no account is created and no credentials are submitted.
- Authentication repository: interface and typed domain models are prepared, and encrypted session storage is implemented as a client foundation. There is still no concrete remote login, refresh-token rotation, logout request or server-backed session restoration.
- AI Chat: local message history, role-aware bubbles, delivery states, cancellation when clearing an active conversation, streamed assistant-delta presentation, and safe user-facing service errors are wired to the remote chat boundary. When the backend is not configured or no valid session exists, messages remain explicitly local-only; no fake assistant response is generated.
- Chat request privacy: each request includes the current user message plus only earlier messages marked SENT by the backend. Local-only and failed messages are never uploaded implicitly. Access-token reads use the IO dispatcher and the encrypted Android Keystore session store; tokens are not exposed to Compose state.
- Chat network transport: the OkHttp SSE adapter sends authenticated requests to the proposed `/api/v1/chat/stream` route and requires a terminal `message.completed` event before reporting success. MockWebServer tests exercise event streaming, bearer-token attachment, missing-token rejection and HTTP error handling. The UI integration is client-side only: `AiServiceConfig.API_BASE_URL` remains empty and no production backend or login service is deployed.
- Image-to-Video: image selection and prompt entry only.
- Inpaint & Eraser: local image selection and an in-memory mask-preparation canvas with brush strokes, brush-size adjustment, undo and clear controls. The editor does not yet export/persist the mask or perform AI inpainting/background removal.
- Document AI: local file selection and metadata inspection for PDF, TXT/plain text and DOCX. Plain-text formats (including TXT, Markdown, CSV, JSON, XML and LOG) can be previewed locally up to 12,000 characters. DOCX body text is extracted locally from `word/document.xml`, with decompressed XML capped at 4 MiB and the visible preview capped at 12,000 characters. Selectable PDF text extraction is implemented locally with PDFBox-Android; the input copy is capped at 25 MiB, extraction at the first 30 pages, and the visible preview at 12,000 characters. The temporary PDF copy is stored in app-private cache and deleted after extraction. Extracted text can also be searched locally with case-insensitive word or phrase matching, a match count, and previous/next navigation through matching positions with a context snippet for the selected match. Search applies to the extracted preview, not content beyond its 12,000-character limit. Dedicated JVM tests cover empty queries, case-insensitive matching, multiple matches, query trimming, snippet boundaries, line-break handling and result-position navigation. Scanned-PDF OCR, document Q&A, indexing, retrieval and citations are not connected; no file is uploaded.
- App Builder: local app brief only; no code generation, preview, build worker or APK export.
- Voice Assistant: local Android microphone permission flow, SpeechRecognizer dictation, editable transcript and Android TextToSpeech read-aloud controls are implemented. Recognition availability and language support depend on the device service; this is not an AI assistant and does not send audio/text to a Zayron backend.
- Network layer: shared transport, typed result handling, safe user-facing error mapping, a typed chat-stream contract, complete-frame SSE event parsing and incremental SSE frame decoding are prepared and covered by JVM tests. The secure token provider is not yet wired into a running backend session, and no automatic refresh-and-retry interceptor exists.

## Recent local UI milestones (not yet backend-connected)\n\n- Image editing now includes a local mask-preparation canvas; mask persistence/export and image transformation remain outstanding.\n- Document AI now inspects selected file metadata, previews bounded plain-text content, extracts bounded DOCX body text and selectable PDF text locally, and searches the extracted preview on-device with previous/next match navigation. Scanned-PDF OCR and all AI/document-backend features remain outstanding.\n\n## Not yet implemented

- Production backend deployment and configured HTTPS API base URL.
- HTTPS API authentication, remote login, automatic token refresh and server-side session revocation.
- Backend-defined access/refresh token format, expiry, rotation, revocation and logout behavior.
- User registration, sign-in, password reset and account management.
- Credits, usage metering, subscriptions, billing and entitlement enforcement.
- Secure file upload, storage, deletion and job status tracking.
- Provider integrations and server-side secrets management.
- Server-side AI chat, image/video generation, inpainting, document RAG and app build workers.
- AI-backed voice conversation, server-side speech processing, and integration of spoken user requests with the Zayron assistant.
- Backend integration tests and release signing.

## Security and release notes

- The Android manifest includes the normal INTERNET permission; this alone does not initiate a network request.
- The backend URL remains empty until a real Zayron backend is deployed. The client refuses non-HTTPS backend URLs.
- Requests are restricted to the configured backend host, port and base path; absolute external destinations and path traversal are rejected.
- Network calls run on the IO dispatcher and use bounded timeouts.
- HTTP error responses are represented separately from transport failures. Response bodies are parsed carefully and are not exposed as raw user-facing error text.
- No body/header logging interceptor is installed. Never embed provider API keys or service credentials in the APK.
- The Android client has a Keystore-backed encrypted session-storage foundation. Its device-side instrumentation suite passed in workflow #53; remote login, refresh coordination, logout/revocation and backend integration remain outstanding.
- The chat preview is stored in app-private local preferences. It is not encrypted chat backup, account sync or a substitute for a secure backend.
- Debug APKs from separate GitHub-hosted runners may have different debug signing certificates. An update may therefore require a matching signing key or uninstalling the previous debug build (which can erase local app data).
- Do not label a feature as AI-powered until the complete backend path has been integrated and tested.
