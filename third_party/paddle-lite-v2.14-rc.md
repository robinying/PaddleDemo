# Deprecated Paddle Lite v2.14-rc metadata

This file is retained solely to explain the earlier scaffold state. The application now packages the verified **v2.10-rc** runtime/model set described in [`paddle-lite-v2.10-rc.md`](paddle-lite-v2.10-rc.md).

Do **not** mix a v2.14 runtime, headers, converter, or model with the v2.10-rc asset bundle. Any upgrade must replace every native runtime and `.nb` model together, regenerate `third_party/paddle-assets.sha256`, and repeat JVM, Android instrumentation, and physical-device acceptance.
