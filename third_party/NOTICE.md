# Third-party notices

## Paddle Lite Demo and adapted deployment contracts

- Project: Paddle-Lite-Demo
- Source: https://github.com/PaddlePaddle/Paddle-Lite-Demo
- License: Apache License 2.0
- Usage: the object-detection and face-detection deployment contracts are adapted into this
  application — model input sizes, pre-processing mean/scale constants, output tensor layouts and
  the general approach to decoding detections into boxes.

  This application does **not** contain OpenCV, Clipper or a DB post-processing implementation.
  OCR post-processing here is an original Kotlin implementation: a BFS connected-component pass
  over the detector probability map, a pixel-count and minimum-size filter, and a top-to-bottom
  then left-to-right reading-order sort (`PaddleLiteEngine.kt`).

## Paddle Lite runtime

- Version: v2.10-rc
- Source: https://paddlelite-demo.bj.bcebos.com/libs/android/paddle_lite_libs_v2_10_rc.tar.gz
- License: Apache License 2.0

## Models

The app's local demo models are downloaded only by `scripts/fetch_paddle_assets.sh` from the official Paddle Lite Demo object store. Before redistributing this app, review each selected model's license, supported language/label files, and applicable data-use requirements.
