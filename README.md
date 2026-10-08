# G002 将棋

G002 将棋は、候補手と理由を示す指導モードと、オフラインAI対局モードを備えたAndroid将棋アプリです。

## License and corresponding source

G002 is distributed under **GPL-3.0-or-later**. See [LICENSE](LICENSE) and
[NOTICE](NOTICE). The bundled ARM64 engine is YaneuraOu at fixed upstream commit
`c1b80eaa09fe13d5f12b1599d1ae4d53c224de30`; its Android source and build files
are included in `third_party/YaneuraOu/`.

The engine is started as an independent USI process. Kotlin/Compose code uses
the public USI protocol and keeps a local legal-move engine as a runtime
fallback if the native process cannot start. The included `nn.bin` is the
official Uonuma KP256 evaluation file; its provenance and SHA-256 are recorded
in [NOTICE](NOTICE).

## Build

Prerequisites: Android Studio / JDK 17, Android SDK API 36, and Android NDK r23b
only when rebuilding the bundled engine.

```powershell
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:bundleRelease
```

The standard release bundle is intentionally unsigned. Do not commit an upload
key, keystore, password, APK, or AAB. Use the existing G002 upload key locally
to sign the produced AAB before submitting it to Google Play.

## Rebuild the ARM64 engine

The checked-in source can rebuild the same engine edition using the existing
Android NDK. The helper only creates local output and never handles upload keys.

```powershell
./tools/build-yaneuraou-kp256-android.ps1 -AndroidNdkRoot 'C:\Users\Mel7Dev\AppData\Local\Android\Sdk\ndk\23.1.7779620'
```

The expected engine and model SHA-256 values are recorded in `NOTICE`.
