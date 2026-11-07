# Zayron AI release signing

## Permanent Android identity

- Application ID / package: `ai.zayron.client`
- App label: `Zayron AI`
- First intended install version: `0.1.0` (version code `1`)

The application ID and release signing certificate must remain unchanged for normal in-place APK updates. Every later update must use the same release keystore and a higher `versionCode`.

## Key custody

The release keystore is the long-term publishing identity. Keep at least two secure backups under the project owner's control. Do not commit the keystore, passwords, or base64-encoded keystore to Git. Do not include them in public source archives or ordinary installation bundles. Anyone who obtains the keystore and its passwords can sign an app update as Zayron AI.

## GitHub Actions secrets required

The manual workflow `.github/workflows/release-apk.yml` expects these repository Actions secrets:

| Secret | Value |
|---|---|
| `ZAYRON_RELEASE_KEYSTORE_BASE64` | Base64-encoded bytes of the permanent JKS/PKCS12 keystore |
| `ZAYRON_RELEASE_STORE_PASSWORD` | Keystore password |
| `ZAYRON_RELEASE_KEY_ALIAS` | Alias of the release key |
| `ZAYRON_RELEASE_KEY_PASSWORD` | Private-key password |

The workflow decodes the keystore into the temporary GitHub runner directory, builds the release APK, verifies its signature and package ID, then uploads the APK as a 90-day artifact. It does not print passwords or upload the keystore.

## Before first installation

1. Create the permanent release keystore once and back it up securely.
2. Add all four Actions secrets above to the repository.
3. Ensure the release workflow is available from the repository's default branch before dispatching it.
4. Build the release APK and verify its signing-certificate SHA-256 fingerprint.
5. Record the fingerprint in a safe project record. Compare it with every future release.
6. Install only the verified signed release APK. Do not use the debug APK as the update track.

## Update rule

Never regenerate or replace the release keystore after the first installation. Increase `versionCode` for every update. Keep the package ID exactly `ai.zayron.client`. A changed signing certificate will normally prevent Android from installing an update over the existing app.
