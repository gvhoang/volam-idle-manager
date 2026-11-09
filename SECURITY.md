# Security review: v3.0.1

## Changes in this release

- Removed the cluster selector. Tabs keep using the last selected v3 `Isolated_Cluster_N_Tab_N` profile, preserving sessions for existing installs.
- Migrated account settings to the canonical per-tab keys and remove obsolete cluster-specific and duplicate keys during migration.
- Reject TLS certificate errors, disable mixed HTTP content and cleartext traffic, and disable Android backup for app data.
- Restrict top-level WebView navigation and automatic credential injection to `https://volamidle.pages.dev`.
- Reject custom URLs outside that exact HTTPS origin and limit automatic page-load retries to three attempts.
- Incremented the Android version to 3.0.1 / versionCode 2.

## Remaining findings

| Severity | Finding | Impact |
|---|---|---|
| High | Account passwords remain stored in app-private SharedPreferences in plaintext. | A rooted device, app backup made by an older build, or local forensic access may expose credentials. This release removes duplicate legacy copies, but does not encrypt the canonical copy. |
| Medium | `AndroidBridge` is still registered with `addJavascriptInterface`, which exposes the object to frames in the trusted page. | A compromised trusted origin or injected third-party frame may invoke bridge callbacks. The callback that can trigger auto-login now checks the top-level URL and tab index, but a stronger origin-bound message channel is still preferable. |
| Low | APK signing credentials are not included in the repository. | A signed release APK requires the established release keystore and its credentials; the debug keystore is not suitable for public distribution or future upgrade continuity. |

The debug APK builds successfully. Release sources compile, but `assembleRelease` stops at packaging because the configured `my-upload-key.jks` is absent. A signed release APK requires the existing release keystore; this is not a penetration test of the live game service or Android devices.
