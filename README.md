# Paddle Vision Android

基于 **Paddle Lite v2.10-rc** 的离线 Android 静态图片视觉应用，提供 OCR、通用目标检测与**人脸检测**能力。

> 人脸功能仅输出本地图片中的人脸位置和置信度；不包含身份识别、特征向量、比对、活体检测或年龄/性别/情绪等属性推断。

## 功能

- **OCR**：中文静态图片文字识别。检测阶段按连通区域分割并以从上到下、从左到右的顺序识别；当前仍不支持旋转文本、透视矫正或复杂版面分析。
- **目标检测**：基于 SSD MobileNet V1 Pascal VOC 模型，输出类别、置信度与边界框。类别在结果中保持语言中立的类别 ID，由界面按当前系统语言渲染标签。
- **人脸检测**：输出人脸边界框和置信度，不生成或保存身份数据。
- **本地离线推理**：模型和推理均在设备端完成。
- **系统 Photo Picker**：选择单张图片，不申请宽泛媒体读取权限。
- **图片预览与状态反馈**：选图后在工作区显示本地缩略图；预览与推理解码共用同一套尺寸策略与错误分类，预览失败时在工作区给出提示而非留白；推理期间保留预览并叠加进度状态。
- **自适应 Compose 界面**：分析模式与语言选择有明确选中态，内容自动避开状态栏和系统导航栏；浅色 / 深色配色均由 `ui/theme` 的设计令牌驱动。
- **MVVM + UDF**：Compose UI 通过 `VisionIntent` 驱动 `VisionViewModel`，由 `StateFlow<VisionUiState>` 渲染；推理请求具有唯一标识，切换任务、语言或图片会取消并失效旧请求，避免过期结果覆盖当前状态。
- **单次推理串行化**：协程取消无法中断已进入 JNI 的 Paddle Lite 计算，因此原生推理经 `InferenceGate` 串行化，新请求排队等待旧请求真正返回，避免两套模型与位图同时驻留内存。
- **可本地化的失败提示**：`VisionInferenceException` 携带资源 ID 而非中文文案，界面按当前语言渲染，任何情况下都不会显示内部枚举名。

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
| `MainActivity.kt` | Compose 宿主；装配主题、ViewModel 与 Photo Picker Effect。 |
| `VisionViewModel.kt` | 状态唯一所有者；取消失效推理请求；处理用户 Intent、推理生命周期与一次性 Effect。 |
| `VisionUiState.kt` | `VisionUiState`、`UiText`、`VisionIntent`、`VisionEffect`、纯 `VisionUiReducer` 与类别标签映射。 |
| `VisionInferenceUseCase.kt` | 编排图片解码、模型准备、Paddle 推理与 Bitmap 释放；`InferenceGate` 串行化原生推理。 |
| `ImageDecoder.kt` | Content URI 解码、图片尺寸限制和分类解码错误；`decodePreview` 与推理共用同一尺寸策略。 |
| `ModelStore.kt` | 将模型从 assets 原子复制到 app 私有目录，按任务惰性做 SHA-256 校验并在进程内缓存校验结果。 |
| `PaddleLiteEngine.kt` | OCR、SSD 目标检测及人脸检测模型推理和后处理；按模型路径缓存 Predictor。 |
| `VisionGeometry.kt` | 纯 Kotlin 坐标转换、IoU 与 NMS。 |
| `ui/` | `VisionScreen`、`TaskSelector`、`LanguageSelector`、`ImageWorkspace`、`ResultPanel` 等 Compose 组件。 |
| `ui/theme/` | 设计令牌（`VisionColors`）与 `MaterialTheme` 接线，浅色 / 深色两套取值。 |

## 环境要求

- **Gradle/AGP 运行 JDK：17**（以当前 Android Gradle Plugin 的要求为准）
- Java/Kotlin 编译目标：11
- Android SDK，`compileSdk = 36`
- Android NDK `26.3.11579264`
- CMake `3.22.1`
- `arm64-v8a` Android 设备
- Android API 29 及以上

## 下载、校验和安装模型资产

模型、字典和 Paddle Lite Runtime 均不由 Gradle 在构建过程中下载。先运行：

```bash
bash scripts/fetch_paddle_assets.sh
```

脚本会下载并安装：

- Paddle Lite v2.10-rc Java/JNI runtime；
- OCR 检测、识别模型与中文词典；
- SSD MobileNet V1 Pascal VOC 模型；
- 人脸检测模型；
- `third_party/paddle-assets.sha256` 打包资产 SHA-256 清单。

官方样例图片只安装到 `app/src/androidTest/assets/samples/`，供 Instrumentation smoke test 使用，不会进入发布 APK。OpenCV 与 OCR 方向分类模型不再下载，原因见 [`third_party/paddle-lite-v2.10-rc.md`](third_party/paddle-lite-v2.10-rc.md)。

脚本完成时会生成清单，并验证该清单可校验当前生成的打包内容。任意时刻可在仓库根目录重新校验已打包的模型和 JNI 库：

```bash
shasum -a 256 -c third_party/paddle-assets.sha256
```

应用首次用到某个能力时会校验该能力所需的模型和字典（按任务惰性校验，并在同一进程内缓存结果）；私有目录中的文件缺失、为空或 hash 不匹配时，会从 APK assets 原子重装。不要混用不同 Paddle Lite 版本的 runtime、模型和转换工具。具体说明见 [`third_party/paddle-lite-v2.10-rc.md`](third_party/paddle-lite-v2.10-rc.md)。

## 构建与测试

### JVM 单元测试

```bash
./gradlew testDebugUnitTest --console=plain
```

覆盖范围包括：

- UDF reducer 状态转换；
- ViewModel 选图 Effect、推理成功状态、重复运行保护，以及运行中切换任务/图片时旧请求不会回写；
- 推理失败时界面拿到的是异常携带的可本地化文案，且兜底路径不会出现内部枚举名；
- `InferenceGate` 的串行化与「排队中被取消的请求不会执行」；
- OCR 连通域提取、阅读顺序、CTC 解码的重复折叠与 blank 处理、尺寸换算、坐标转换、IoU 与 NMS；
- 目标检测类别 ID 到标签的映射与越界兜底；
- 预览与推理共用的解码尺寸策略；
- 资源完整性：各语言目录与默认 `values/` 的资源名集合必须一致，复数必须提供 `other` 数量，翻译不得改动位置参数（lint 不覆盖 `string-array` 与复数数量，由该用例补齐）。

### Lint 门禁

```bash
./gradlew lintDebug --console=plain
```

`lintDebug` 设为必过门禁：任何 error 都会让构建失败。明确接受的风险（`ChromeOsAbiSupport`、依赖版本升级提示）在 `app/lint.xml` 中显式登记为 `informational`，其余一律保持默认严重级别。

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
- `ImageDecoder`：file URI 尺寸约束、不可访问 URI、损坏图片、超出像素预算的超大图、PNG/WebP、EXIF `Orientation=6` 的解码朝向，以及「预览与推理解码策略一致」；
- 真实 Paddle Lite Java/JNI runtime 创建 predictor 并执行目标检测 smoke test（按类别 ID 断言，与展示语言解耦）；
- OCR 与人脸检测固定样例 smoke test，以及未打包 OCR 语言在加载任何模型前被拒绝；
- `ModelStore`：hash 不匹配时的原子重装、按任务惰性校验（准备物体检测不会安装人脸/OCR 资产）、同一实例不重复哈希；
- `LocalVisionInferenceUseCase`：content URI 全链路，以及并发两次调用被串行化且都返回完整结果；
- 界面路由：Photo Picker 取消后回到选图状态、OCR 语言选择器只列出已打包语言、Activity 重建后仍保留所选能力。

这些 smoke test 证明打包模型可由实际 runtime 执行，但**不等同于准确性 Golden Sample 验证**。在将模型版本或预处理变更发布前，应在目标设备上校准并版本化以下标注：OCR 预期文本及阅读顺序、对象/人脸类别和数量、边界框 IoU 与置信度阈值，以及无结果负样本。

### 安装到设备

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell monkey -p com.robinying.paddlevision 1
```

每次涉及状态管理、模型、图片解码或 runtime 的变更后，都必须重新执行 JVM、Lint、Debug/Release 构建和目标设备 Instrumentation 测试，并手工验证 Photo Picker 的取消、运行中切换任务/语言/图片、HEIC、飞行模式冷启动、无 ANR / 无 native crash，以及日志无敏感内容。

## 权限与隐私

静态图片 MVP：

- 不申请 `INTERNET`；
- 不申请 `CAMERA`；
- 不申请 `READ_MEDIA_IMAGES`；
- 图片通过系统 Photo Picker 选择；
- 图片、OCR 文本、目标检测结果和人脸框不上传、不持久化、不写入发布日志；
- `allowBackup="false"`，并在 `res/xml/data_extraction_rules.xml` 中显式排除云备份与设备迁移，私有目录只存放可从 APK 资产重建的模型副本。

## 性能口径

结果面板展示的「端到端耗时」由 `LocalVisionInferenceUseCase` 测量，覆盖图片解码、模型准备与校验、预处理、推理和后处理全链路。`VisionInferenceResult.inferenceMillis` 另行保留模型 `run()` 自身的耗时，仅供诊断与断言使用（`elapsedMillis >= inferenceMillis`），不单独展示。

## 当前限制

- OCR 当前仅打包中文模型。语言选择器只列出已打包的语言，未打包语言（英语/法语/西班牙语）不会作为可选项出现，选择器下方给出说明；`requireSupportedOcrLanguage` 作为二次防线仍会拒绝未打包语言。
- 目标检测类别标签按系统语言渲染；Pascal VOC 的 21 个类别名随 `values-*/arrays.xml` 提供，越界类别 ID 回退到带占位符的「未知类别」文案。
- OCR 仅对检测概率图的连通区域进行轴对齐裁剪；复杂多区域版面、旋转文本、透视矫正和完整语言扩展仍有待后续实现。
- UI 会展示原图预览、任务状态和结果摘要；图片结果框（OCR 文本块、目标/人脸边界框）的覆盖层渲染仍是后续增强项。
- 首发 ABI 仅为 `arm64-v8a`。
- 依赖版本升级（Gradle / AGP / Kotlin / AndroidX）与 Paddle Lite runtime 升级解耦，另行排期；当前版本在 `app/lint.xml` 中登记为可接受风险。

## 第三方声明

- Paddle Lite：Apache-2.0。
- 具体模型的来源与许可说明见 [`third_party/NOTICE.md`](third_party/NOTICE.md)。
- 本项目**不包含** OpenCV、Clipper 或 DB 后处理实现，OCR 后处理为自有 Kotlin 实现。

发布前应审查模型、字典、样例数据及其再分发许可。
