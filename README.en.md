<p align="center">
  <img src="docs/logo.svg" width="128" height="128" alt="Krypton Code logo">
</p>

# Krypton Code

[Türkçe](README.md) · **English**

A code-focused, multi-AI generation tool for Android. Split a large job into small, detailed prompts, assign each prompt to a different AI account, and let the results be generated in order, stored as a zip, and optionally pushed to GitHub and verified by CI (Actions).

## Features

- **Multiple AI accounts:** Google Gemini, Groq, OpenRouter, Mistral, Cerebras, NVIDIA NIM, DeepSeek, Claude, OpenAI, Kimi, and custom OpenAI-compatible providers.
- **Model picker:** After the key is verified, suitable chat models are listed with free ones highlighted.
- **Smart account pool:** Each prompt goes to the best available account: strongest model first, equal models take turns (least recently used), recently flaky accounts drop to the back, and **backup** accounts only step in when nothing else is available. An optional **daily request limit** per account keeps free quotas from being drained, and a circuit breaker rests an account for 10 minutes after three provider/network failures. A background check (every 6 hours, no quota used) removes accounts whose key was rejected and restores ones that recovered. The **Usage and cost** screen shows per-account tokens, requests, quota fill and (when a price is set) estimated cost; values are marked "≈" when the provider did not report usage. The **AIs** screen shows each account's place in the chain, why it is resting, and when it returns.
- **Advanced prompt engine:** variables (`{{name}}`), binding one node's output into the next (`{{output:Title}}`) and per-node validation/retry rules — see [docs/PROMPT-ENGINE.md](docs/PROMPT-ENGINE.md) (Turkish).
- **Prompt sets:** Build ordered prompts, reorder them, and assign an account to each prompt automatically or manually.
- **Background runs:** Generation continues through WorkManager; interrupted jobs are recovered.
- **History:** Generated outputs and prepared zip files are stored on the device.
- **GitHub + CI loop:** Pushes output to a new branch as a single commit, waits for the Actions result, and on failure feeds a log summary back to the AI to attempt a fix.
- **Live streaming:** For API accounts, a node's output streams in as it is generated (SSE via `okhttp-sse`); the stored output takes over when the job finishes. Providers that don't support streaming fall back to a single response automatically. Web-session accounts are not streamed.
- **Notification controls and home-screen widget:** The ongoing-run notification has **Pause / Cancel** buttons (**Resume / Cancel** when paused). A Jetpack Glance widget shows the running run's status, progress and current node without opening the app.
- **Bilingual UI:** Turkish (default) and English (`values-en`); Android 13+ supports a per-app language.
- **Local crash log (ACRA):** Crash details stay on the device and nothing is sent over the network; share or delete them from Settings.
- **Quality tooling:** ktlint in CI (report-only for now), LeakCanary in debug builds, weekly dependency updates via Dependabot.
- **Theme:** System / light / dark.
- **Animations:** A state-aware logo (idle, working, waiting, success, error, intro), screen transitions, staggered reveals, press feedback and a pulse ring on running nodes. Can be turned off in Settings.
- **Battery restriction:** Settings shows the status and opens Android's "unrestricted" prompt plus OEM auto-start screens (Xiaomi, Huawei, Oppo, Vivo, Samsung, ...).
- **Security:** Keys and tokens are encrypted with AES-GCM via Android Keystore and kept on-device only; backups are disabled.

## Requirements

- Android 8.0 (API 26) or newer
- For development: JDK 17, Android SDK 35 (Android Studio Ladybug or newer)

## Install (users)

1. Download the latest `KriptonCod-vX.Y.Z.apk` from [Releases](../../releases).
2. Allow "Install unknown apps" on your phone and install the APK.
3. Verify integrity with the accompanying `.sha256` file.

## Build (developers)

```bash
./gradlew assembleDebug          # debug APK: app/build/outputs/apk/debug/
./gradlew lintDebug              # lint
./gradlew testDebugUnitTest      # unit tests
```

The Gradle wrapper (including `gradle-wrapper.jar`) is checked in; no separate Gradle install is needed.

### Google OAuth (optional)

To use Gemini with a Google account, create an Android OAuth client ID in Google Cloud Console and enter it under **Settings → Google OAuth** in the app.

## Usage

1. In the **AIs** tab, pick a provider, verify the key, choose a model, and save.
2. In the **Prompts** tab, create a set, add prompts, and assign accounts.
3. Press **Run** on the set screen. Optionally pick a target GitHub repository; otherwise only a zip is produced.
4. Progress and results appear in the **History** tab; open a run and use **Save zip** to store the zip on your phone.
5. To push to GitHub, add a token under **Settings → GitHub** (needs `repo` and `workflow` scopes). The result is committed to the `kriptoncod/run-<id>` branch as a single commit and the verify workflow runs; on failure the AI sends a fix to the same branch (attempt count is set in the GitHub settings).

## Publishing a release (automatic)

Just push a tag:

```bash
git tag v0.2.0
git push origin v0.2.0
```

The `Release` workflow runs tests, builds the APK, generates a SHA-256 checksum, and uploads everything to GitHub Releases with release notes. Tags like `v1.0.0-beta.1` are marked as pre-releases.

See [docs/RELEASING.md](docs/RELEASING.md) for details. For a signed APK, add these under **Settings → Secrets and variables → Actions**:

| Secret | Description |
| --- | --- |
| `KEYSTORE_BASE64` | Base64 of the keystore file (`base64 -w0 release.keystore`) |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias |
| `KEY_PASSWORD` | Key password |

You can generate the keystore with `./scripts/create-keystore.sh`. Without these secrets the workflow publishes a debug-signed APK (for testing only).

To group the release notes, label PRs with `feature`, `bug`, `docs`, `ci`, etc. (`.github/release.yml`).

## Project layout

```
app/src/main/java/com/kriptoncod/app/
├── data/      # providers, repositories, database (Room), GitHub, vault (Keystore)
├── domain/    # models and preferences
├── ui/        # Compose screens, components, theme, navigation
└── work/      # WorkManager workers (dispatch, recovery, CI loop)
```

## Tech stack

Kotlin 2.0, Jetpack Compose (Material 3), Room, DataStore, WorkManager, OkHttp, kotlinx.serialization.

## License

[MIT](LICENSE). See [CHANGELOG.md](CHANGELOG.md) for changes.

## Contributing

Use the Issues page for bug reports. When opening a PR, add a label matching the change; release notes are grouped by it.
