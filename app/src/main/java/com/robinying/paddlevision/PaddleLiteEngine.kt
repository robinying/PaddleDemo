package com.robinying.paddlevision

import android.graphics.Bitmap
import androidx.core.graphics.scale
import com.baidu.paddle.lite.MobileConfig
import com.baidu.paddle.lite.PaddlePredictor
import com.baidu.paddle.lite.PowerMode
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.util.ArrayDeque
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
 *
 * Predictors are cached per model path because parsing a Paddle Lite model from disk is a
 * per-run fixed cost. `PaddlePredictor` is not thread safe, so callers must serialize
 * [run] — see [InferenceGate].
 */
class PaddleLiteEngine {
    private val predictorsByPath = mutableMapOf<String, PaddlePredictor>()

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
        val predictor = predictorFor(contract.modelFile)
        val input = bitmap.toNchw(contract.inputWidth, contract.inputHeight, contract.mean, contract.standardDeviation)
        val elapsedMillis = measureTimeMillis {
            predictor.setInput(input, contract.inputWidth, contract.inputHeight)
            if (!predictor.run()) {
                throw VisionInferenceException(
                    VisionErrorCode.INFERENCE_FAILED,
                    UiText(R.string.error_inference_failed),
                )
            }
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
        requireSupportedOcrLanguage(language)
        val detectorFile = File(modelDirectory, OCR_DETECTOR_ASSET)
        val recognizerFile = File(modelDirectory, OCR_RECOGNIZER_ASSET)
        val dictionaryFile = File(modelDirectory, OCR_DICTIONARY_ASSET)
        if (!dictionaryFile.isFile) {
            throw VisionInferenceException(
                VisionErrorCode.ASSET_MISSING,
                UiText(R.string.error_ocr_dictionary_missing),
            )
        }

        val detectorSize = calculateOcrDetectorSize(bitmap.width, bitmap.height)
        val detector = predictorFor(detectorFile)
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
            if (!detector.run()) {
                throw VisionInferenceException(
                    VisionErrorCode.INFERENCE_FAILED,
                    UiText(R.string.error_ocr_detection_failed),
                )
            }
            detectorOutput = detector.getOutput(0).floatDataOrThrow()
            detectorShape = detector.getOutput(0).shape()
        }
        val regions = detectorOutput.toOcrRegions(
            shape = detectorShape,
            sourceSize = ImageSize(bitmap.width, bitmap.height),
        )
        if (regions.isEmpty()) {
            return VisionInferenceResult(
                task = VisionTask.OCR,
                imageSize = ImageSize(bitmap.width, bitmap.height),
                elapsedMillis = elapsedMillis,
            )
        }

        val dictionary = loadOcrDictionary(dictionaryFile)
        val recognizer = predictorFor(recognizerFile)
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
                if (!predictor.run()) {
                    throw VisionInferenceException(
                        VisionErrorCode.INFERENCE_FAILED,
                        UiText(R.string.error_ocr_recognition_failed),
                    )
                }
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

    /**
     * Returns a predictor for [modelFile], parsing it only the first time this engine
     * instance sees that path. The engine is not thread safe; [InferenceGate] guarantees
     * callers serialize access.
     */
    private fun predictorFor(modelFile: File): PaddlePredictor {
        if (!modelFile.isFile || modelFile.length() == 0L) {
            throw VisionInferenceException(
                VisionErrorCode.ASSET_MISSING,
                UiText(R.string.error_model_file_missing, listOf(modelFile.name)),
            )
        }
        return predictorsByPath[modelFile.absolutePath] ?: createPredictor(modelFile).also { created ->
            predictorsByPath[modelFile.absolutePath] = created
        }
    }

    private fun createPredictor(modelFile: File): PaddlePredictor {
        return try {
            val config = MobileConfig().apply {
                setModelFromFile(modelFile.absolutePath)
                setThreads(2)
                setPowerMode(PowerMode.LITE_POWER_HIGH)
            }
            PaddlePredictor.createPaddlePredictor(config)
                ?: throw VisionInferenceException(
                    VisionErrorCode.MODEL_INITIALIZATION_FAILED,
                    UiText(R.string.error_model_load_failed, listOf(UiText(R.string.error_unknown))),
                )
        } catch (exception: VisionInferenceException) {
            throw exception
        } catch (exception: Exception) {
            throw VisionInferenceException(
                VisionErrorCode.MODEL_INITIALIZATION_FAILED,
                UiText(
                    R.string.error_model_load_failed,
                    listOf(exception.message ?: UiText(R.string.error_unknown)),
                ),
                exception,
            )
        }
    }

    private companion object {
        const val OCR_RECOGNITION_HEIGHT = 32
        const val MAX_OCR_REGIONS = 20
        val OCR_MEAN = floatArrayOf(0.5f, 0.5f, 0.5f)
        val OCR_STANDARD_DEVIATION = floatArrayOf(0.5f, 0.5f, 0.5f)
    }
}

/**
 * Rejects OCR languages whose model is not bundled. The selector already hides them, so
 * this guard is defence in depth: any other caller still gets an actionable message
 * instead of a failed inference.
 */
internal fun requireSupportedOcrLanguage(language: OcrLanguage) {
    if (!language.isPackaged) {
        throw VisionInferenceException(
            VisionErrorCode.UNSUPPORTED_TASK,
            UiText(R.string.error_ocr_language_unsupported),
        )
    }
}

/**
 * Pins the input geometry and the output contract of one single-head detector. [decode] is the
 * only place that turns a raw model output into detections, so the shape contracts that fail
 * first when a model build changes live here.
 */
internal data class ModelContract(
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
                UiText(R.string.error_object_output_contract, listOf(shape.contentToString())),
            )
        }
        val selected = values.asListOfDetectionCandidates(sourceSize).nonMaximumSuppression()
        return VisionInferenceResult(
            task = task,
            imageSize = sourceSize,
            elapsedMillis = elapsedMillis,
            detections = selected.map { candidate ->
                DetectedObject(candidate.categoryId, candidate.confidence, candidate.boundingBox)
            },
        )
    }

    companion object {
        fun forTask(task: VisionTask, modelDirectory: File): ModelContract = when (task) {
            VisionTask.OBJECT -> ModelContract(
                task, File(modelDirectory, OBJECT_MODEL_ASSET),
                300, 300, floatArrayOf(0.5f, 0.5f, 0.5f), floatArrayOf(0.5f, 0.5f, 0.5f),
            )
            VisionTask.FACE -> ModelContract(
                task, File(modelDirectory, FACE_MODEL_ASSET),
                320, 240, floatArrayOf(0.498f, 0.498f, 0.498f), floatArrayOf(0.502f, 0.502f, 0.502f),
            )
            VisionTask.OCR -> error("OCR uses the dedicated multi-model pipeline")
        }
    }
}

internal data class DetectionCandidate(val categoryId: Int, val confidence: Float, val boundingBox: PixelBox)
private data class RecognizedRegion(val block: OcrTextBlock?, val elapsedMillis: Long)

private fun PaddlePredictor.setInput(values: FloatArray, width: Int, height: Int) {
    val tensor = getInput(0)
    if (!tensor.resize(longArrayOf(1L, 3L, height.toLong(), width.toLong()))) {
        throw VisionInferenceException(
            VisionErrorCode.INFERENCE_FAILED,
            UiText(R.string.error_model_input_failed),
        )
    }
    if (!tensor.setData(values)) {
        throw VisionInferenceException(
            VisionErrorCode.INFERENCE_FAILED,
            UiText(R.string.error_model_input_failed),
        )
    }
}

internal fun decodeFaceCandidates(scores: FloatArray, boxes: FloatArray, sourceSize: ImageSize): List<DetectionCandidate> {
    if (scores.size % 2 != 0 || boxes.size != scores.size * 2) {
        throw VisionInferenceException(
            VisionErrorCode.INFERENCE_FAILED,
            UiText(R.string.error_face_output_contract),
        )
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

internal fun FloatArray.asListOfDetectionCandidates(sourceSize: ImageSize): List<DetectionCandidate> =
    indices.step(6).mapNotNull { index ->
        val confidence = this[index + 1]
        if (!confidence.isFinite() || confidence < 0.5f) return@mapNotNull null
        val box = VisionGeometry.normalizedToPixels(
            NormalizedBox(this[index + 2], this[index + 3], this[index + 4], this[index + 5]), sourceSize,
        )
        if (box.width <= 0f || box.height <= 0f) null else DetectionCandidate(this[index].toInt(), confidence, box)
    }

internal fun List<DetectionCandidate>.nonMaximumSuppression(): List<DetectionCandidate> {
    val result = mutableListOf<DetectionCandidate>()
    groupBy { it.categoryId }.forEach { (_, candidates) ->
        result += VisionGeometry.nonMaximumSuppression(
            candidates.map { it.boundingBox }, candidates.map { it.confidence }, 0.45f,
        ).map(candidates::get)
    }
    return result.sortedByDescending { it.confidence }
}

internal fun calculateOcrDetectorSize(width: Int, height: Int): ImageSize {
    val scale = min(1f, 960f / max(width, height))
    return ImageSize(
        width = max(32, (ceil(width * scale / 32f) * 32).toInt()),
        height = max(32, (ceil(height * scale / 32f) * 32).toInt()),
    )
}

internal fun extractOcrRegions(
    values: FloatArray,
    mapWidth: Int,
    mapHeight: Int,
    sourceSize: ImageSize,
    threshold: Float = 0.3f,
    maxRegions: Int = 20,
): List<PixelBox> {
    val minimumComponentPixels = 2
    val minimumRegionSize = 4f
    if (mapWidth <= 0 || mapHeight <= 0 || maxRegions < 0 || values.size < mapWidth * mapHeight) {
        throw VisionInferenceException(
            VisionErrorCode.INFERENCE_FAILED,
            UiText(R.string.error_ocr_detection_output_invalid),
        )
    }
    val visited = BooleanArray(mapWidth * mapHeight)
    val regions = mutableListOf<OcrRegionCandidate>()
    for (startIndex in visited.indices) {
        if (visited[startIndex] || values[startIndex] < threshold) {
            continue
        }
        val queue = ArrayDeque<Int>()
        queue.addLast(startIndex)
        visited[startIndex] = true
        var pixelCount = 0
        var minX = mapWidth
        var minY = mapHeight
        var maxX = -1
        var maxY = -1
        while (queue.isNotEmpty()) {
            val index = queue.removeFirst()
            val x = index % mapWidth
            val y = index / mapWidth
            pixelCount++
            minX = min(minX, x)
            minY = min(minY, y)
            maxX = max(maxX, x)
            maxY = max(maxY, y)
            for (offsetY in -1..1) {
                for (offsetX in -1..1) {
                    if (offsetX == 0 && offsetY == 0) {
                        continue
                    }
                    val neighborX = x + offsetX
                    val neighborY = y + offsetY
                    if (neighborX !in 0 until mapWidth || neighborY !in 0 until mapHeight) {
                        continue
                    }
                    val neighborIndex = neighborY * mapWidth + neighborX
                    if (!visited[neighborIndex] && values[neighborIndex] >= threshold) {
                        visited[neighborIndex] = true
                        queue.addLast(neighborIndex)
                    }
                }
            }
        }
        // Every pixel in the component already scored >= threshold, so only the pixel-count
        // and minimum-size guards can reject it.
        if (pixelCount < minimumComponentPixels) {
            continue
        }
        val scaleX = sourceSize.width.toFloat() / mapWidth
        val scaleY = sourceSize.height.toFloat() / mapHeight
        val box = PixelBox(
            left = minX * scaleX,
            top = minY * scaleY,
            right = (maxX + 1) * scaleX,
            bottom = (maxY + 1) * scaleY,
        )
        if (box.width >= minimumRegionSize && box.height >= minimumRegionSize) {
            regions += OcrRegionCandidate(box)
        }
    }
    return regions
        .sortedWith(compareBy<OcrRegionCandidate> { it.boundingBox.top }.thenBy { it.boundingBox.left })
        .take(maxRegions)
        .map(OcrRegionCandidate::boundingBox)
}

private data class OcrRegionCandidate(val boundingBox: PixelBox)

private fun FloatArray.toOcrRegions(shape: LongArray, sourceSize: ImageSize): List<PixelBox> {
    if (shape.size < 4) {
        throw VisionInferenceException(
            VisionErrorCode.INFERENCE_FAILED,
            UiText(R.string.error_ocr_detection_output_invalid),
        )
    }
    val mapHeight = shape[shape.size - 2].toInt()
    val mapWidth = shape.last().toInt()
    return extractOcrRegions(this, mapWidth, mapHeight, sourceSize)
}

private fun Bitmap.crop(region: PixelBox): Bitmap {
    val left = region.left.toInt().coerceIn(0, width - 1)
    val top = region.top.toInt().coerceIn(0, height - 1)
    val right = region.right.toInt().coerceIn(left + 1, width)
    val bottom = region.bottom.toInt().coerceIn(top + 1, height)
    return Bitmap.createBitmap(this, left, top, right - left, bottom - top)
}

internal fun calculateRecognitionWidth(width: Int, height: Int): Int {
    if (height <= 0) return MIN_RECOGNITION_WIDTH
    val proportionalWidth = (width.toFloat() / height * 32f).toInt().coerceAtLeast(MIN_RECOGNITION_WIDTH)
    return proportionalWidth.coerceAtMost(MAX_RECOGNITION_WIDTH)
}

private const val MIN_RECOGNITION_WIDTH = 32
private const val MAX_RECOGNITION_WIDTH = 320

private fun Bitmap.toNchw(width: Int, height: Int, mean: FloatArray, standardDeviation: FloatArray): FloatArray {
    val scaled = if (this.width == width && this.height == height) this else scale(width, height)
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

/**
 * Greedy CTC decoding: per timestep take the argmax class, drop the blank class (index 0)
 * and collapse repeats of the class kept at the previous timestep.
 */
internal fun decodeOcrRecognition(
    values: FloatArray,
    shape: LongArray,
    dictionary: List<String>,
    boundingBox: PixelBox,
): OcrTextBlock? {
    if (shape.size < 3) {
        throw VisionInferenceException(
            VisionErrorCode.INFERENCE_FAILED,
            UiText(R.string.error_ocr_recognition_output_invalid),
        )
    }
    val steps = shape[shape.size - 2].toInt()
    val classCount = shape.last().toInt()
    if (steps <= 0 || classCount <= 1 || values.size < steps * classCount) {
        throw VisionInferenceException(
            VisionErrorCode.INFERENCE_FAILED,
            UiText(R.string.error_ocr_recognition_output_invalid),
        )
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
    ?: throw VisionInferenceException(
        VisionErrorCode.INFERENCE_FAILED,
        UiText(R.string.error_model_output_missing),
    )
