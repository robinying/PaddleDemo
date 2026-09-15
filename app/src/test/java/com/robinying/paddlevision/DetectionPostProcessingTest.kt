package com.robinying.paddlevision

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Covers the detector output decoding that used to be reachable only from a device: the per-row
 * decode of the object and face heads, the category-scoped suppression, and the input geometry
 * and output contracts that fail first when a model build changes.
 */
class DetectionPostProcessingTest {
    private val sourceSize = ImageSize(100, 100)

    /** A 10x10 px box at (10, 10) once mapped onto [sourceSize]. */
    private val squareBox = NormalizedBox(0.1f, 0.1f, 0.2f, 0.2f)
    private val squarePixels = PixelBox(10f, 10f, 20f, 20f)

    /** Overlaps [squareBox] with an IoU of ~0.68, above the 0.45 suppression threshold. */
    private val overlappingBox = NormalizedBox(0.11f, 0.11f, 0.21f, 0.21f)

    @Test
    fun objectContractPinsTheSsdInputGeometry() {
        val contract = ModelContract.forTask(VisionTask.OBJECT, File("."))

        assertEquals(VisionTask.OBJECT, contract.task)
        assertEquals(300, contract.inputWidth)
        assertEquals(300, contract.inputHeight)
        assertEquals(listOf(0.5f, 0.5f, 0.5f), contract.mean.toList())
        assertEquals(listOf(0.5f, 0.5f, 0.5f), contract.standardDeviation.toList())
    }

    @Test
    fun faceContractPinsTheBundledFaceInputGeometry() {
        val contract = ModelContract.forTask(VisionTask.FACE, File("."))

        assertEquals(VisionTask.FACE, contract.task)
        assertEquals(320, contract.inputWidth)
        assertEquals(240, contract.inputHeight)
        assertEquals(listOf(0.498f, 0.498f, 0.498f), contract.mean.toList())
        assertEquals(listOf(0.502f, 0.502f, 0.502f), contract.standardDeviation.toList())
    }

    @Test
    fun ocrHasNoSingleDetectorContract() {
        try {
            ModelContract.forTask(VisionTask.OCR, File("."))
            fail("Expected OCR to be rejected as a single-head detector")
        } catch (exception: IllegalStateException) {
            assertTrue(
                "The failure must name the OCR pipeline as the reason",
                exception.message?.contains("OCR") == true,
            )
        }
    }

    @Test
    fun faceCandidatesKeepConfidenceAndMapTheBoxToPixels() {
        val candidates = decodeFaceCandidates(
            scores = faceScores(0.9f),
            boxes = faceBoxes(squareBox),
            sourceSize = sourceSize,
        )

        assertEquals(1, candidates.size)
        assertEquals(0.9f, candidates.single().confidence, 0f)
        assertEquals(squarePixels, candidates.single().boundingBox)
    }

    @Test
    fun faceCandidatesBelowTheConfidenceThresholdAreDropped() {
        val candidates = decodeFaceCandidates(
            scores = faceScores(0.4999f, 0.5f),
            boxes = faceBoxes(squareBox, squareBox),
            sourceSize = sourceSize,
        )

        assertEquals("The threshold is inclusive at 0.5", listOf(0.5f), candidates.map { it.confidence })
    }

    @Test
    fun faceCandidatesWithANonFiniteConfidenceAreDropped() {
        val candidates = decodeFaceCandidates(
            scores = faceScores(Float.NaN, 0.9f, Float.POSITIVE_INFINITY),
            boxes = faceBoxes(squareBox, squareBox, squareBox),
            sourceSize = sourceSize,
        )

        assertEquals(listOf(0.9f), candidates.map { it.confidence })
    }

    @Test
    fun faceCandidatesWithADegenerateBoxAreDropped() {
        val candidates = decodeFaceCandidates(
            scores = faceScores(0.9f, 0.8f),
            boxes = faceBoxes(NormalizedBox(0.5f, 0.1f, 0.5f, 0.2f), squareBox),
            sourceSize = sourceSize,
        )

        assertEquals(1, candidates.size)
        assertEquals(squarePixels, candidates.single().boundingBox)
    }

    @Test
    fun faceCandidatesReturnNothingForAnEmptyOutput() {
        val candidates = decodeFaceCandidates(
            scores = FloatArray(0),
            boxes = FloatArray(0),
            sourceSize = sourceSize,
        )

        assertTrue(candidates.isEmpty())
    }

    @Test
    fun faceCandidatesRejectAnOddScoreCount() {
        expectContractError(UiText(R.string.error_face_output_contract)) {
            decodeFaceCandidates(
                scores = floatArrayOf(0f, 0.9f, 0f),
                boxes = floatArrayOf(0f, 0f, 1f, 1f, 0f, 0f),
                sourceSize = sourceSize,
            )
        }
    }

    @Test
    fun faceCandidatesRejectABoxArrayThatDoesNotMatchTheScores() {
        expectContractError(UiText(R.string.error_face_output_contract)) {
            decodeFaceCandidates(
                scores = faceScores(0.9f),
                boxes = faceBoxes(squareBox, squareBox),
                sourceSize = sourceSize,
            )
        }
    }

    @Test
    fun detectionRowsAreDecodedAsCategoryConfidenceAndBox() {
        val candidates = rows(
            detectionRow(categoryId = 12, confidence = 0.9f, box = squareBox),
            detectionRow(categoryId = 15, confidence = 0.7f, box = NormalizedBox(0.5f, 0.5f, 0.9f, 0.9f)),
        ).asListOfDetectionCandidates(sourceSize)

        assertEquals(listOf(12, 15), candidates.map { it.categoryId })
        assertEquals(listOf(0.9f, 0.7f), candidates.map { it.confidence })
        assertEquals(
            listOf(squarePixels, PixelBox(50f, 50f, 90f, 90f)),
            candidates.map { it.boundingBox },
        )
    }

    @Test
    fun detectionRowsBelowTheConfidenceThresholdAreDropped() {
        val candidates = rows(
            detectionRow(categoryId = 12, confidence = 0.49f),
            detectionRow(categoryId = 15, confidence = 0.5f),
        ).asListOfDetectionCandidates(sourceSize)

        assertEquals("The threshold is inclusive at 0.5", listOf(15), candidates.map { it.categoryId })
    }

    @Test
    fun detectionRowsWithANonFiniteConfidenceAreDropped() {
        val candidates = rows(
            detectionRow(categoryId = 12, confidence = Float.NaN),
            detectionRow(categoryId = 15, confidence = Float.NEGATIVE_INFINITY),
            detectionRow(categoryId = 9, confidence = 0.8f),
        ).asListOfDetectionCandidates(sourceSize)

        assertEquals(listOf(9), candidates.map { it.categoryId })
    }

    @Test
    fun detectionRowsWithADegenerateBoxAreDropped() {
        val candidates = rows(
            detectionRow(categoryId = 12, confidence = 0.9f, box = NormalizedBox(0.3f, 0.1f, 0.3f, 0.4f)),
            detectionRow(categoryId = 15, confidence = 0.8f, box = squareBox),
        ).asListOfDetectionCandidates(sourceSize)

        assertEquals(listOf(15), candidates.map { it.categoryId })
    }

    @Test
    fun detectionRowTruncatesAFractionalCategoryId() {
        val candidates = floatArrayOf(12.9f, 0.9f, 0.1f, 0.1f, 0.2f, 0.2f)
            .asListOfDetectionCandidates(sourceSize)

        assertEquals(listOf(12), candidates.map { it.categoryId })
    }

    @Test
    fun overlappingBoxesOfTheSameCategoryAreSuppressed() {
        val candidates = rows(
            detectionRow(categoryId = 12, confidence = 0.9f, box = squareBox),
            detectionRow(categoryId = 12, confidence = 0.8f, box = overlappingBox),
        ).asListOfDetectionCandidates(sourceSize).nonMaximumSuppression()

        assertEquals(1, candidates.size)
        assertEquals(0.9f, candidates.single().confidence, 0f)
    }

    /**
     * Suppression is scoped to a category: two detectors firing on the same region must both
     * survive, which is the one behaviour [VisionGeometry.nonMaximumSuppression] cannot express
     * on its own because that helper only ever sees a single flat box list.
     */
    @Test
    fun overlappingBoxesOfDifferentCategoriesAreBothKept() {
        val candidates = rows(
            detectionRow(categoryId = 12, confidence = 0.9f, box = squareBox),
            detectionRow(categoryId = 15, confidence = 0.8f, box = overlappingBox),
        ).asListOfDetectionCandidates(sourceSize).nonMaximumSuppression()

        assertEquals(listOf(12, 15), candidates.map { it.categoryId })
    }

    @Test
    fun suppressionResultsAreOrderedByDescendingConfidenceAcrossCategories() {
        val candidates = rows(
            detectionRow(categoryId = 12, confidence = 0.6f, box = NormalizedBox(0.1f, 0.1f, 0.2f, 0.2f)),
            detectionRow(categoryId = 15, confidence = 0.95f, box = NormalizedBox(0.6f, 0.6f, 0.7f, 0.7f)),
            detectionRow(categoryId = 9, confidence = 0.75f, box = NormalizedBox(0.3f, 0.3f, 0.4f, 0.4f)),
        ).asListOfDetectionCandidates(sourceSize).nonMaximumSuppression()

        assertEquals(listOf(15, 9, 12), candidates.map { it.categoryId })
    }

    @Test
    fun objectDecodeBuildsDetectionsWithLanguageNeutralCategoryIds() {
        val contract = ModelContract.forTask(VisionTask.OBJECT, File("."))
        val values = rows(
            detectionRow(categoryId = 12, confidence = 0.9f, box = squareBox),
            detectionRow(categoryId = 15, confidence = 0.8f, box = NormalizedBox(0.5f, 0.5f, 0.9f, 0.9f)),
        )

        val result = contract.decode(sourceSize, longArrayOf(1, 2, 6), values, secondaryValues = null, elapsedMillis = 42)

        assertEquals(VisionTask.OBJECT, result.task)
        assertEquals(sourceSize, result.imageSize)
        assertEquals(42L, result.elapsedMillis)
        assertEquals(listOf(12, 15), result.detections.map { it.categoryId })
        assertTrue(result.faces.isEmpty())
        assertTrue(result.textBlocks.isEmpty())
    }

    @Test
    fun objectDecodeRejectsAnOutputShorterThanOneDetectionRow() {
        val contract = ModelContract.forTask(VisionTask.OBJECT, File("."))
        val shape = longArrayOf(1, 100, 3)

        expectContractError(UiText(R.string.error_object_output_contract, listOf(shape.contentToString()))) {
            contract.decode(sourceSize, shape, FloatArray(3), secondaryValues = null, elapsedMillis = 1)
        }
    }

    @Test
    fun objectDecodeRejectsAnOutputThatIsNotAWholeNumberOfDetectionRows() {
        val contract = ModelContract.forTask(VisionTask.OBJECT, File("."))
        val shape = longArrayOf(1, 100, 7)

        expectContractError(UiText(R.string.error_object_output_contract, listOf(shape.contentToString()))) {
            contract.decode(sourceSize, shape, FloatArray(7), secondaryValues = null, elapsedMillis = 1)
        }
    }

    @Test
    fun faceDecodeBuildsFacesFromTheScoreAndBoxOutputs() {
        val contract = ModelContract.forTask(VisionTask.FACE, File("."))

        val result = contract.decode(
            sourceSize = sourceSize,
            shape = longArrayOf(1, 1, 2),
            values = faceScores(0.9f),
            secondaryValues = faceBoxes(squareBox),
            elapsedMillis = 7,
        )

        assertEquals(VisionTask.FACE, result.task)
        assertEquals(listOf(DetectedFace(0.9f, squarePixels)), result.faces)
        assertTrue(result.detections.isEmpty())
        assertTrue(result.textBlocks.isEmpty())
    }

    @Test
    fun faceDecodeReturnsNoFacesForAnEmptyOutput() {
        val contract = ModelContract.forTask(VisionTask.FACE, File("."))

        val result = contract.decode(
            sourceSize = sourceSize,
            shape = longArrayOf(1, 0, 2),
            values = FloatArray(0),
            secondaryValues = FloatArray(0),
            elapsedMillis = 3,
        )

        assertTrue(result.faces.isEmpty())
    }

    @Test
    fun faceDecodeSuppressesOverlappingCandidates() {
        val contract = ModelContract.forTask(VisionTask.FACE, File("."))

        val result = contract.decode(
            sourceSize = sourceSize,
            shape = longArrayOf(1, 2, 2),
            values = faceScores(0.9f, 0.8f),
            secondaryValues = faceBoxes(squareBox, overlappingBox),
            elapsedMillis = 5,
        )

        assertEquals(1, result.faces.size)
        assertEquals(0.9f, result.faces.single().confidence, 0f)
    }

    private fun expectContractError(expected: UiText, block: () -> Unit) {
        try {
            block()
            fail("Expected the detector output contract to be enforced")
        } catch (exception: VisionInferenceException) {
            assertEquals(VisionErrorCode.INFERENCE_FAILED, exception.code)
            assertEquals(expected, exception.text)
        }
    }

    /** Scores are `[class, confidence]` pairs; the class slot is ignored by the face head. */
    private fun faceScores(vararg confidences: Float): FloatArray =
        confidences.flatMap { confidence -> listOf(0f, confidence) }.toFloatArray()

    /** Four normalized coordinates per score pair. */
    private fun faceBoxes(vararg boxes: NormalizedBox): FloatArray =
        boxes.flatMap { box -> listOf(box.left, box.top, box.right, box.bottom) }.toFloatArray()

    /** One `[categoryId, confidence, left, top, right, bottom]` row per detection. */
    private fun detectionRow(
        categoryId: Int,
        confidence: Float,
        box: NormalizedBox = squareBox,
    ): FloatArray = floatArrayOf(categoryId.toFloat(), confidence, box.left, box.top, box.right, box.bottom)

    private fun rows(vararg detectionRows: FloatArray): FloatArray =
        detectionRows.flatMap { it.toList() }.toFloatArray()
}
