# Android CI — debug APK

`.github/workflows/android-debug.yml` builds a signed debug APK on every push to `main`, every pull
request, and on manual dispatch.

## What the workflow does

1. JDK 21 (Temurin) and Gradle 9.3.1 via the committed wrapper.
2. Installs Android SDK platform 36 / 36.1 and build-tools 36.0.0 / 36.1.0.
3. Restores (or generates) the debug keystore.
4. Runs `./gradlew :app:assembleDebug`.
5. Uploads `app/build/outputs/apk/debug/*.apk` as `cityflow-debug-apk-<sha>` and writes the size and
   SHA-256 to the job summary.

Download the APK from the workflow run's **Artifacts** section.

## Keystores

Signing material is never committed. `.gitignore` excludes `debug.keystore`, `*.keystore`, `*.jks`,
`keystore.properties` and `*.p12`.

### Default behaviour

If no keystore secret is configured, CI generates a throwaway debug keystore:

```bash
keytool -genkeypair -keystore debug.keystore \
  -storepass android -alias androiddebugkey -keypass android \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=Android Debug,O=Android,C=US"
```

This is enough to build and install. Because the key is new on every run, a new APK cannot update an
already-installed build in place — Android rejects it with a signature mismatch. Uninstall first, or
use a stable keystore below.

### Stable debug keystore (recommended)

Create one keystore once and keep it out of git:

```bash
keytool -genkeypair -keystore debug.keystore \
  -storepass android -alias androiddebugkey -keypass android \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=Android Debug,O=Android,C=US"

# Linux
base64 -w0 debug.keystore
# macOS
base64 -i debug.keystore
```

Add the output as a repository secret named `DEBUG_KEYSTORE_BASE64`
(Settings → Secrets and variables → Actions → New repository secret).

If the keystore uses non-default credentials, also add:

| Secret | Default if unset |
| --- | --- |
| `DEBUG_STORE_PASSWORD` | `android` |
| `DEBUG_KEY_ALIAS` | `androiddebugkey` |
| `DEBUG_KEY_PASSWORD` | `android` |

`app/build.gradle.kts` reads `DEBUG_KEYSTORE_PATH`, `DEBUG_STORE_PASSWORD`, `DEBUG_KEY_ALIAS` and
`DEBUG_KEY_PASSWORD`; the workflow sets all four from secrets, falling back to the standard debug
values. Secrets are only referenced by the signing step and are never echoed.

### Release signing

The release build type already reads `KEYSTORE_PATH`, `STORE_PASSWORD` and `KEY_PASSWORD` and uses the
`upload` alias. CI does not currently build a release; when adding it, decode the release keystore
from a repository secret into a workspace path, export those three variables, and run
`:app:assembleRelease`. Do not reuse the debug key for release builds.

## Reproduce locally

```bash
keytool -genkeypair -keystore debug.keystore -storepass android \
  -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 \
  -validity 10000 -dname "CN=Android Debug,O=Android,C=US"

# Point Gradle at an installed SDK (or set ANDROID_HOME)
echo "sdk.dir=$HOME/android-sdk" > local.properties

./gradlew :app:assembleDebug
```

The verified toolchain for this workflow is JDK 21, Gradle 9.3.1, Android platform 36.1 and
build-tools 36.1.0.

## Server tests

`.github/workflows/server-tests.yml` runs `gradle test` inside `server/` on JDK 21 for pushes and pull
requests that touch the backend. It needs no emulator, PocketBase, or OSRM: the simulation tests use
a deterministic in-memory route provider. The failed-test report is uploaded even when the job fails.

The Android workflow builds the client only. It does not start PocketBase/OSRM; the app connects to
the backend URL configured in Settings (default `http://10.0.2.2:8090` on an emulator).
