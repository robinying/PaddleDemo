# Paddle Lite Android dependencies — v2.10-rc

This project packages Paddle Lite **v2.10-rc** for `arm64-v8a`.

The Java/JNI runtime, `libc++_shared.so`, OpenCV library, and all optimized `.nb` models must be obtained together by running:

```bash
./scripts/fetch_paddle_assets.sh
```

The script generates `third_party/paddle-assets.sha256` using repository-relative paths and validates the generated manifest before returning. Revalidate the packaged dependency set from the repository root with:

```bash
shasum -a 256 -c third_party/paddle-assets.sha256
```

Do not replace individual models or native libraries manually. The application verifies required model and dictionary hashes before it reuses private installed copies.

## Source artifacts

- Paddle Lite Android runtime: `https://paddlelite-demo.bj.bcebos.com/libs/android/paddle_lite_libs_v2_10_rc.tar.gz`
- OpenCV Android SDK 4.2.0: `https://paddlelite-demo.bj.bcebos.com/libs/android/opencv-4.2.0-android-sdk.tar.gz`
- OCR, SSD MobileNet V1 and face-detection model archives: URLs pinned in `scripts/fetch_paddle_assets.sh`.

The v2.14-rc runtime metadata previously placed in this repository must not be mixed with these v2.10-rc models. A model/runtime upgrade requires replacing the complete dependency set and rerunning native and device acceptance tests.

## Required post-fetch layout

```text
app/src/main/jniLibs/arm64-v8a/
  libc++_shared.so
  libopencv_java4.so
  libpaddle_lite_jni.so
app/src/main/assets/models/
  ocr/*.nb
  object/ssd_mobilenet_v1_pascalvoc_for_cpu/model.nb
  face/model.nb
third_party/paddle-assets.sha256
```

The application must surface a clear asset-not-installed error if this set is incomplete. It must never claim that inference ran without loading matching assets.
