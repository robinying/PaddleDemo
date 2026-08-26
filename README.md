# Paddle Vision Android

基于 **Paddle Lite v2.10-rc** 的离线 Android 静态图片视觉应用，提供 OCR、通用目标检测与**人脸检测**能力。

> 人脸功能仅输出本地图片中的人脸位置和置信度；不包含身份识别、特征向量、比对、活体检测或年龄/性别/情绪等属性推断。

## 功能

- **OCR**：中文静态图片文字识别。检测阶段按连通区域分割并以从上到下、从左到右的顺序识别；当前仍不支持旋转文本、透视矫正或复杂版面分析。
- **目标检测**：基于 SSD MobileNet V1 Pascal VOC 模型，输出类别、置信度与边界框。
- **人脸检测**：输出人脸边界框和置信度，不生成或保存身份数据。
- **本地离线推理**：模型和推理均在设备端完成。
- **系统 Photo Picker**：选择单张图片，不申请宽泛媒体读取权限。
- **MVVM + UDF**：Compose UI 通过 `VisionIntent` 驱动 `VisionViewModel`，由 `StateFlow<VisionUiState>` 渲染；推理请求具有唯一标识，切换任务、语言或图片会取消并失效旧请求，避免过期结果覆盖当前状态。

## 架构

```text
Compose UI
  → VisionIntent
  → VisionViewModel
  → StateFlow<VisionUiState>
  → Compose UI

VisionViewModel
  → VisionInferenceUseCase
  → ImageDecoder + ModelStore + PaddleLiteEngine
  → Paddle Lite Java/JNI Runtime
```

### 主要组件

| 位置 | 职责 |
| --- | --- |
| `MainActivity.kt` | Compose 宿主；渲染 State；分发 Intent；响应 Photo Picker Effect。 |
| `VisionViewModel.kt` | 状态唯一所有者；取消失效推理请求；处理用户 Intent、推理生命周期与一次性 Effect。 |
| `VisionUiState.kt` | `VisionUiState`、`VisionIntent`、`VisionEffect` 与纯 `VisionUiReducer`。 |
| `VisionInferenceUseCase.kt` | 编排图片解码、模型准备、Paddle 推理与 Bitmap 释放。 |
| `ImageDecoder.kt` | Content URI 解码、图片尺寸限制和分类解码错误。 |
| `ModelStore.kt` | 将模型从 assets 原子复制到 app 私有目录，并对 APK assets 与已安装副本进行 SHA-256 校验。 |
| `PaddleLiteEngine.kt` | OCR、SSD 目标检测及人脸检测模型推理和后处理。 |
| `VisionGeometry.kt` | 纯 Kotlin 坐标转换、IoU 与 NMS。 |

## 环境要求

- **Gradle/AGP 运行 JDK：17**（以当前 Android Gradle Plugin 的要求为准）
- Java/Kotlin 编译目标：11
- Android SDK，`compileSdk = 36`
- Android NDK `26.3.11579264`
- CMake `3.22.1`
- `arm64-v8a` Android 设备
- Android API 29 及以上

## 下载、校验和安装模型资产

模型、字典、Paddle Lite Runtime 和 OpenCV 均不由 Gradle 在构建过程中下载。先运行：

```bash
bash scripts/fetch_paddle_assets.sh
```

脚本会下载并安装：

- Paddle Lite v2.10-rc Java/JNI runtime；
- OCR 检测、识别、方向分类模型与中文词典；
- SSD MobileNet V1 Pascal VOC 模型；
- 人脸检测模型；
- 官方样例图片；
- `third_party/paddle-assets.sha256` 打包资产 SHA-256 清单。

脚本完成时会生成清单，并验证该清单可校验当前生成的打包内容。任意时刻可在仓库根目录重新校验已打包的模型和 JNI 库：

```bash
shasum -a 256 -c third_party/paddle-assets.sha256
```

应用首次使用时会再次校验需要的模型和字典；私有目录中的文件缺失、为空或 hash 不匹配时，会从 APK assets 原子重装。不要混用不同 Paddle Lite 版本的 runtime、模型和转换工具。具体说明见 [`third_party/paddle-lite-v2.10-rc.md`](third_party/paddle-lite-v2.10-rc.md)。

## 构建与测试

### JVM 单元测试

```bash
./gradlew testDebugUnitTest --console=plain
```

覆盖范围包括：

- UDF reducer 状态转换；
- ViewModel 选图 Effect、推理成功状态、重复运行保护，以及运行中切换任务/图片时旧请求不会回写；
- OCR 连通域提取、阅读顺序、坐标转换、IoU 与 NMS；
- 推理结果摘要及人脸数据边界。

### Debug / Release 构建

```bash
./gradlew assembleDebug assembleRelease --console=plain
```

APK 输出：

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release-unsigned.apk
```

### 真机 Instrumentation 测试

连接 `arm64-v8a` 设备后执行：

```bash
adb devices -l
./gradlew connectedDebugAndroidTest --console=plain
```

Instrumentation 覆盖：

- 项目 Native bridge 加载；
- `ImageDecoder` 的 file URI 解码边界与不可访问 URI 错误；
- 真实 Paddle Lite Java/JNI runtime 创建 predictor 并执行目标检测 smoke test；
- OCR 与人脸检测固定样例 smoke test。

这些 smoke test 证明打包模型可由实际 runtime 执行，但**不等同于准确性 Golden Sample 验证**。在将模型版本或预处理变更发布前，应在目标设备上校准并版本化以下标注：OCR 预期文本及阅读顺序、对象/人脸类别和数量、边界框 IoU 与置信度阈值，以及无结果负样本。

### 安装到设备

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell monkey -p com.robinying.paddlevision 1
```

每次涉及状态管理、模型、图片解码或 runtime 的变更后，都必须重新执行 JVM、Debug/Release 构建和目标设备 Instrumentation 测试，并手工验证 Photo Picker 的取消、运行中切换任务/语言/图片、JPEG/PNG/HEIC/WebP、损坏图片和超大图片。

## 权限与隐私

静态图片 MVP：

- 不申请 `INTERNET`；
- 不申请 `CAMERA`；
- 不申请 `READ_MEDIA_IMAGES`；
- 图片通过系统 Photo Picker 选择；
- 图片、OCR 文本、目标检测结果和人脸框不上传、不持久化、不写入发布日志。

## 当前限制

- OCR 当前仅打包中文模型；选择其他语言时会给出明确错误提示。
- OCR 仅对检测概率图的连通区域进行轴对齐裁剪；复杂多区域版面、旋转文本、透视矫正和完整语言扩展仍有待后续实现。
- UI 当前展示任务状态和结果摘要；图片结果框的覆盖层渲染是后续增强项。
- 首发 ABI 仅为 `arm64-v8a`。

## 第三方声明

- Paddle Lite：Apache-2.0。
- OpenCV Android SDK：详见上游许可。
- 具体模型的来源与许可说明见 [`third_party/NOTICE.md`](third_party/NOTICE.md)。

发布前应审查模型、字典、样例数据及其再分发许可。
