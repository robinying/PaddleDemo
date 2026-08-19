package com.robinying.paddlevision

import android.graphics.Bitmap
import android.graphics.Rect
import com.baidu.paddle.lite.MobileConfig
import com.baidu.paddle.lite.PaddlePredictor
import com.baidu.paddle.lite.PowerMode
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.system.measureTimeMillis

/**
 * Runs selected Paddle Lite v2.10-rc models locally through the matching Java/JNI runtime.
 *
 * The official v2.10 C++ runtime has malformed local ELF symbols that modern NDK linkers
 * reject. The bundled Java API uses the same versioned native runtime without that linker
 * integration, while Kotlin owns model contracts and all privacy-sensitive result handling.
 */
class PaddleLiteEngine {
    fun run(
        task: VisionTask,
        bitmap: Bitmap,
        modelDirectory: File,
        ocrLanguage: OcrLanguage = OcrLanguage.CHINESE,
    ): VisionInferenceResult {
        return if (task == VisionTask.OCR) {
            runOcr(bitmap, modelDirectory, ocrLanguage)
        } else {
            runSingleDetector(task, bitmap, modelDirectory)
        }
    }

    private fun runSingleDetector(
        task: VisionTask,
        bitmap: Bitmap,
        modelDirectory: File,
    ): VisionInferenceResult {
        val contract = ModelContract.forTask(task, modelDirectory)
        val predictor = createPredictor(contract.modelFile)
        val input = bitmap.toNchw(contract.inputWidth, contract.inputHeight, contract.mean, contract.standardDeviation)
        val elapsedMillis = measureTimeMillis {
            predictor.setInput(input, contract.inputWidth, contract.inputHeight)
            check(predictor.run()) { "Paddle Lite 推理失败" }
        }
        val firstOutput = predictor.getOutput(0).floatDataOrThrow()
        val firstShape = predictor.getOutput(0).shape()
        val secondaryOutput = if (task == VisionTask.FACE) predictor.getOutput(1).floatDataOrThrow() else null
        return contract.decode(
            sourceSize = ImageSize(bitmap.width, bitmap.height),
            shape = firstShape,
            values = firstOutput,
            secondaryValues = secondaryOutput,
            elapsedMillis = elapsedMillis,
        )
    }

    private fun runOcr(
        bitmap: Bitmap,
        modelDirectory: File,
        language: OcrLanguage,
    ): VisionInferenceResult {
        if (language != OcrLanguage.CHINESE) {
            throw VisionInferenceException(
                VisionErrorCode.UNSUPPORTED_TASK,
                "当前打包的 OCR 模型只支持中文，请选择中文 OCR",
            )
        }
        val detectorFile = File(modelDirectory, OCR_DETECTOR_MODEL)
        val recognizerFile = File(modelDirectory, OCR_RECOGNIZER_MODEL)
        val dictionaryFile = File(modelDirectory, OCR_DICTIONARY)
        if (!dictionaryFile.isFile) {
            throw VisionInferenceException(VisionErrorCode.ASSET_MISSING, "OCR 字典未安装")
        }

        val detectorSize = calculateOcrDetectorSize(bitmap.width, bitmap.height)
        val detector = createPredictor(detectorFile)
        val detectorInput = bitmap.toNchw(
            detectorSize.width,
            detectorSize.height,
            OCR_MEAN,
            OCR_STANDARD_DEVIATION,
        )
        var detectorOutput: FloatArray
        var detectorShape: LongArray
        var elapsedMillis = measureTimeMillis {
            detector.setInput(detectorInput, detectorSize.width, detectorSize.height)
            check(detector.run()) { "OCR 文本检测推理失败" }
            detectorOutput = detector.getOutput(0).floatDataOrThrow()
            detectorShape = detector.getOutput(0).shape()
        }
        val regions = detectorOutput.toOcrRegions(detectorShape, bitmap, detectorSize)
        if (regions.isEmpty()) {
            return VisionInferenceResult(
                task = VisionTask.OCR,
                imageSize = ImageSize(bitmap.width, bitmap.height),
                elapsedMillis = elapsedMillis,
            )
        }

        val dictionary = loadOcrDictionary(dictionaryFile)
        val recognizer = createPredictor(recognizerFile)
        val blocks = regions.take(MAX_OCR_REGIONS).mapNotNull { region ->
            val recognition = recognizeRegion(bitmap, region, recognizer, dictionary)
            elapsedMillis += recognition.elapsedMillis
            recognition.block
        }
        return VisionInferenceResult(
            task = VisionTask.OCR,
            imageSize = ImageSize(bitmap.width, bitmap.height),
            elapsedMillis = elapsedMillis,
            textBlocks = blocks,
        )
    }

    private fun recognizeRegion(
        source: Bitmap,
        region: PixelBox,
        predictor: PaddlePredictor,
        dictionary: List<String>,
    ): RecognizedRegion {
        val crop = source.crop(region)
        try {
            val inputWidth = calculateRecognitionWidth(crop.width, crop.height)
            val input = crop.toNchw(inputWidth, OCR_RECOGNITION_HEIGHT, OCR_MEAN, OCR_STANDARD_DEVIATION)
            var values: FloatArray
            var shape: LongArray
            val elapsedMillis = measureTimeMillis {
                predictor.setInput(input, inputWidth, OCR_RECOGNITION_HEIGHT)
                check(predictor.run()) { "OCR 文本识别推理失败" }
                values = predictor.getOutput(0).floatDataOrThrow()
                shape = predictor.getOutput(0).shape()
            }
            return RecognizedRegion(
                block = decodeOcrRecognition(values, shape, dictionary, region),
                elapsedMillis = elapsedMillis,
            )
        } finally {
            crop.recycle()
        }
    }

    private fun createPredictor(modelFile: File): PaddlePredictor {
        if (!modelFile.isFile || modelFile.length() == 0L) {
            throw VisionInferenceException(VisionErrorCode.ASSET_MISSING, "模型文件未安装：${modelFile.name}")
        }
        return try {
            val config = MobileConfig().apply {
                setModelFromFile(modelFile.absolutePath)
                setThreads(2)
                setPowerMode(PowerMode.LITE_POWER_HIGH)
            }
            PaddlePredictor.createPaddlePredictor(config)
                ?: throw VisionInferenceException(VisionErrorCode.MODEL_INITIALIZATION_FAILED, "无法加载模型")
        } catch (exception: VisionInferenceException) {
            throw exception
        } catch (exception: Exception) {
            throw VisionInferenceException(
                VisionErrorCode.MODEL_INITIALIZATION_FAILED,
                "模型加载失败：${exception.message ?: "未知错误"}",
            )
        }
    }

    private companion object {
        const val OCR_DETECTOR_MODEL = "models/ocr/ch_ppocr_mobile_v2.0_det_slim_opt.nb"
        const val OCR_RECOGNIZER_MODEL = "models/ocr/ch_ppocr_mobile_v2.0_rec_slim_opt.nb"
        const val OCR_DICTIONARY = "dictionaries/ppocr_keys_v1.txt"
        const val OCR_RECOGNITION_HEIGHT = 32
        const val MAX_OCR_REGIONS = 20
        val OCR_MEAN = floatArrayOf(0.5f, 0.5f, 0.5f)
        val OCR_STANDARD_DEVIATION = floatArrayOf(0.5f, 0.5f, 0.5f)
    }
}

private data class ModelContract(
    val task: VisionTask,
    val modelFile: File,
    val inputWidth: Int,
    val inputHeight: Int,
    val mean: FloatArray,
    val standardDeviation: FloatArray,
) {
    fun decode(
        sourceSize: ImageSize,
        shape: LongArray,
        values: FloatArray,
        secondaryValues: FloatArray?,
        elapsedMillis: Long,
    ): VisionInferenceResult {
        if (task == VisionTask.FACE) {
            return VisionInferenceResult(
                task = task,
                imageSize = sourceSize,
                elapsedMillis = elapsedMillis,
                faces = decodeFaceCandidates(values, requireNotNull(secondaryValues), sourceSize)
                    .nonMaximumSuppression()
                    .map { candidate -> DetectedFace(candidate.confidence, candidate.boundingBox) },
            )
        }
        if (values.size < 6 || values.size % 6 != 0) {
            throw VisionInferenceException(
                VisionErrorCode.INFERENCE_FAILED,
                "模型输出格式不符合检测结果契约：${shape.contentToString()}",
            )
        }
        val selected = values.asListOfDetectionCandidates(sourceSize).nonMaximumSuppression()
        return VisionInferenceResult(
            task = task,
            imageSize = sourceSize,
            elapsedMillis = elapsedMillis,
            detections = selected.map { candidate ->
                DetectedObject(PascalVocLabels.nameFor(candidate.categoryId), candidate.confidence, candidate.boundingBox)
            },
        )
    }

    companion object {
        fun forTask(task: VisionTask, modelDirectory: File): ModelContract = when (task) {
            VisionTask.OBJECT -> ModelContract(
                task, File(modelDirectory, "models/object/ssd_mobilenet_v1_pascalvoc_for_cpu/model.nb"),
                300, 300, floatArrayOf(0.5f, 0.5f, 0.5f), floatArrayOf(0.5f, 0.5f, 0.5f),
            )
            VisionTask.FACE -> ModelContract(
                task, File(modelDirectory, "models/face/model.nb"),
                320, 240, floatArrayOf(0.498f, 0.498f, 0.498f), floatArrayOf(0.502f, 0.502f, 0.502f),
            )
            VisionTask.OCR -> error("OCR uses the dedicated multi-model pipeline")
        }
    }
}

private data class DetectionCandidate(val categoryId: Int, val confidence: Float, val boundingBox: PixelBox)
private data class RecognizedRegion(val block: OcrTextBlock?, val elapsedMillis: Long)

private fun PaddlePredictor.setInput(values: FloatArray, width: Int, height: Int) {
    val tensor = getInput(0)
    check(tensor.resize(longArrayOf(1L, 3L, height.toLong(), width.toLong()))) { "无法设置模型输入尺寸" }
    check(tensor.setData(values)) { "无法写入模型输入" }
}

private fun decodeFaceCandidates(scores: FloatArray, boxes: FloatArray, sourceSize: ImageSize): List<DetectionCandidate> {
    if (scores.size % 2 != 0 || boxes.size != scores.size * 2) {
        throw VisionInferenceException(VisionErrorCode.INFERENCE_FAILED, "人脸模型输出格式不符合 scores/boxes 契约")
    }
    return scores.indices.step(2).mapNotNull { scoreIndex ->
        val confidence = scores[scoreIndex + 1]
        if (!confidence.isFinite() || confidence < 0.5f) return@mapNotNull null
        val boxIndex = scoreIndex * 2
        val box = VisionGeometry.normalizedToPixels(
            NormalizedBox(boxes[boxIndex], boxes[boxIndex + 1], boxes[boxIndex + 2], boxes[boxIndex + 3]), sourceSize,
        )
        if (box.width <= 0f || box.height <= 0f) null else DetectionCandidate(0, confidence, box)
    }
}

private fun FloatArray.asListOfDetectionCandidates(sourceSize: ImageSize): List<DetectionCandidate> =
    indices.step(6).mapNotNull { index ->
        val confidence = this[index + 1]
        if (!confidence.isFinite() || confidence < 0.5f) return@mapNotNull null
        val box = VisionGeometry.normalizedToPixels(
            NormalizedBox(this[index + 2], this[index + 3], this[index + 4], this[index + 5]), sourceSize,
        )
        if (box.width <= 0f || box.height <= 0f) null else DetectionCandidate(this[index].toInt(), confidence, box)
    }

private fun List<DetectionCandidate>.nonMaximumSuppression(): List<DetectionCandidate> {
    val result = mutableListOf<DetectionCandidate>()
    groupBy { it.categoryId }.forEach { (_, candidates) ->
        result += VisionGeometry.nonMaximumSuppression(
            candidates.map { it.boundingBox }, candidates.map { it.confidence }, 0.45f,
        ).map(candidates::get)
    }
    return result.sortedByDescending { it.confidence }
}

private fun calculateOcrDetectorSize(width: Int, height: Int): ImageSize {
    val scale = min(1f, 960f / max(width, height))
    return ImageSize(
        width = max(32, (ceil(width * scale / 32f) * 32).toInt()),
        height = max(32, (ceil(height * scale / 32f) * 32).toInt()),
    )
}

private fun FloatArray.toOcrRegions(
    shape: LongArray,
    source: Bitmap,
    detectorSize: ImageSize,
): List<PixelBox> {
    if (shape.size < 4) {
        throw VisionInferenceException(VisionErrorCode.INFERENCE_FAILED, "OCR 检测输出 shape 无效")
    }
    val mapHeight = shape[shape.size - 2].toInt()
    val mapWidth = shape.last().toInt()
    if (mapHeight <= 0 || mapWidth <= 0 || size < mapWidth * mapHeight) {
        throw VisionInferenceException(VisionErrorCode.INFERENCE_FAILED, "OCR 检测输出数据无效")
    }
    var minX = mapWidth
    var minY = mapHeight
    var maxX = -1
    var maxY = -1
    for (y in 0 until mapHeight) {
        for (x in 0 until mapWidth) {
            if (this[y * mapWidth + x] >= 0.3f) {
                minX = min(minX, x)
                minY = min(minY, y)
                maxX = max(maxX, x)
                maxY = max(maxY, y)
            }
        }
    }
    if (maxX < minX || maxY < minY) return emptyList()
    val scaleX = source.width.toFloat() / mapWidth
    val scaleY = source.height.toFloat() / mapHeight
    val box = PixelBox(minX * scaleX, minY * scaleY, (maxX + 1) * scaleX, (maxY + 1) * scaleY)
    return if (box.width < 4f || box.height < 4f) emptyList() else listOf(box)
}

private fun Bitmap.crop(region: PixelBox): Bitmap {
    val left = region.left.toInt().coerceIn(0, width - 1)
    val top = region.top.toInt().coerceIn(0, height - 1)
    val right = region.right.toInt().coerceIn(left + 1, width)
    val bottom = region.bottom.toInt().coerceIn(top + 1, height)
    return Bitmap.createBitmap(this, left, top, right - left, bottom - top)
}

private fun calculateRecognitionWidth(width: Int, height: Int): Int {
    val proportionalWidth = (width.toFloat() / height * 32f).toInt().coerceAtLeast(32)
    return proportionalWidth.coerceAtMost(320)
}

private fun Bitmap.toNchw(width: Int, height: Int, mean: FloatArray, standardDeviation: FloatArray): FloatArray {
    val scaled = if (this.width == width && this.height == height) this else Bitmap.createScaledBitmap(this, width, height, true)
    try {
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        val planeSize = width * height
        return FloatArray(planeSize * 3).also { result ->
            pixels.forEachIndexed { index, pixel ->
                result[index] = (((pixel shr 16) and 0xFF) / 255f - mean[0]) / standardDeviation[0]
                result[planeSize + index] = (((pixel shr 8) and 0xFF) / 255f - mean[1]) / standardDeviation[1]
                result[planeSize * 2 + index] = ((pixel and 0xFF) / 255f - mean[2]) / standardDeviation[2]
            }
        }
    } finally {
        if (scaled !== this) scaled.recycle()
    }
}

private fun loadOcrDictionary(file: File): List<String> = BufferedReader(FileReader(file)).use { reader ->
    buildList {
        reader.lineSequence().forEach(::add)
    }
}

private fun decodeOcrRecognition(
    values: FloatArray,
    shape: LongArray,
    dictionary: List<String>,
    boundingBox: PixelBox,
): OcrTextBlock? {
    if (shape.size < 3) {
        throw VisionInferenceException(VisionErrorCode.INFERENCE_FAILED, "OCR 识别输出 shape 无效")
    }
    val steps = shape[shape.size - 2].toInt()
    val classCount = shape.last().toInt()
    if (steps <= 0 || classCount <= 1 || values.size < steps * classCount) {
        throw VisionInferenceException(VisionErrorCode.INFERENCE_FAILED, "OCR 识别输出数据无效")
    }
    val text = StringBuilder()
    var confidenceSum = 0f
    var acceptedCount = 0
    var previousIndex = 0
    for (step in 0 until steps) {
        val start = step * classCount
        var bestIndex = 0
        var bestScore = values[start]
        for (index in 1 until classCount) {
            if (values[start + index] > bestScore) {
                bestIndex = index
                bestScore = values[start + index]
            }
        }
        if (bestIndex != 0 && bestIndex != previousIndex && bestIndex - 1 < dictionary.size) {
            text.append(dictionary[bestIndex - 1])
            confidenceSum += bestScore
            acceptedCount++
        }
        previousIndex = bestIndex
    }
    if (text.isBlank() || acceptedCount == 0) return null
    return OcrTextBlock(text.toString(), confidenceSum / acceptedCount, boundingBox)
}

private fun com.baidu.paddle.lite.Tensor.floatDataOrThrow(): FloatArray = getFloatData()
    ?: throw VisionInferenceException(VisionErrorCode.INFERENCE_FAILED, "模型未返回 float 输出")

private object PascalVocLabels {
    private val labels = listOf(
        "背景", "飞机", "自行车", "鸟", "船", "瓶子", "公共汽车", "汽车", "猫", "椅子",
        "牛", "餐桌", "狗", "马", "摩托车", "人", "盆栽", "羊", "沙发", "火车", "电视",
    )

    fun nameFor(categoryId: Int): String = labels.getOrElse(categoryId) { "未知类别($categoryId)" }
}
