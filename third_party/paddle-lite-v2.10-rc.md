# Paddle Lite Android dependencies — v2.10-rc

This project packages Paddle Lite **v2.10-rc** for `arm64-v8a`.

The Java/JNI runtime, `libc++_shared.so`, and all optimized `.nb` models must be obtained together by running:

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
- OCR, SSD MobileNet V1 and face-detection model archives: URLs pinned in `scripts/fetch_paddle_assets.sh`.

The v2.14-rc runtime metadata previously placed in this repository must not be mixed with these v2.10-rc models. A model/runtime upgrade requires replacing the complete dependency set and rerunning native and device acceptance tests.

## Required post-fetch layout

```text
app/src/main/jniLibs/arm64-v8a/
  libc++_shared.so
  libpaddle_lite_jni.so
app/src/main/assets/models/
  ocr/ch_ppocr_mobile_v2.0_det_slim_opt.nb
  ocr/ch_ppocr_mobile_v2.0_rec_slim_opt.nb
  object/ssd_mobilenet_v1_pascalvoc_for_cpu/model.nb
  face/model.nb
app/src/main/assets/dictionaries/ppocr_keys_v1.txt
app/src/androidTest/assets/samples/          # smoke-test fixtures, not shipped in the APK
third_party/paddle-assets.sha256
```

The application must surface a clear asset-not-installed error if this set is incomplete. It must never claim that inference ran without loading matching assets.

## Deliberately not packaged

- **OpenCV Android SDK.** The app implements OCR post-processing in Kotlin (BFS connected
  components plus a reading-order sort) and never links OpenCV, so the 18 MB library was removed
  from `jniLibs` and from the fetch script.
- **OCR direction-classification model** (`ch_ppocr_mobile_v2.0_cls_slim_opt.nb`). No direction
  classifier is implemented, so the model was fetched and hash-checked on every run without ever
  being loaded. Reintroducing it means implementing the classifier and its tests first.
- **`ppocr_keys_ocrv5.txt`.** The packaged recognizer is the v2.0 slim model, decoded with
  `ppocr_keys_v1.txt` only. The fetcher still receives both dictionaries in the upstream labels
  archive and deletes the unused one rather than shipping 74 KB of unreferenced data.
