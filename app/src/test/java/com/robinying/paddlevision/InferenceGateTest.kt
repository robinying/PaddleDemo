package com.robinying.paddlevision

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InferenceGateTest {
    @Test
    fun aSecondCallerWaitsUntilTheFirstOneHasReturned() = runTest {
        val gate = InferenceGate()
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        var active = 0
        var peakActive = 0

        val first = launch {
            gate.runExclusive {
                active++
                peakActive = maxOf(peakActive, active)
                firstEntered.complete(Unit)
                releaseFirst.await()
                active--
            }
        }
        firstEntered.await()

        val second = launch {
            gate.runExclusive {
                active++
                peakActive = maxOf(peakActive, active)
                active--
            }
        }
        advanceUntilIdle()

        assertEquals("The second caller must not overlap the first", 1, peakActive)
        assertEquals(1, active)

        releaseFirst.complete(Unit)
        first.join()
        second.join()

        assertEquals(1, peakActive)
        assertEquals(0, active)
    }

    @Test
    fun aQueuedCallerThatIsCancelledNeverRuns() = runTest {
        val gate = InferenceGate()
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        var queuedWorkRan = false

        val first = launch {
            gate.runExclusive {
                firstEntered.complete(Unit)
                releaseFirst.await()
            }
        }
        firstEntered.await()

        val second = launch {
            gate.runExclusive { queuedWorkRan = true }
        }
        advanceUntilIdle()
        second.cancel()

        releaseFirst.complete(Unit)
        advanceUntilIdle()
        first.join()

        assertFalse("An invalidated request must not start once it is cancelled", queuedWorkRan)
    }

    /**
     * `withLock` releases on the failure path too. Without that, one failed inference would wedge
     * the gate and every later request would wait forever behind a lock nobody holds — the app would
     * go silent after a single model error rather than reporting it.
     *
     * The timeout runs on the test scheduler's virtual clock, so a gate that is never released
     * fails this test immediately instead of hanging the suite.
     */
    @Test
    fun aThrowingBlockStillReleasesTheGate() = runTest {
        val gate = InferenceGate()

        try {
            gate.runExclusive { throw IllegalStateException("boom") }
            fail("Expected the block's failure to propagate to the caller")
        } catch (exception: IllegalStateException) {
            assertEquals("boom", exception.message)
        }

        var secondRan = false
        withTimeoutOrNull(1_000) { gate.runExclusive { secondRan = true } }

        assertTrue("A failed run must not leave the gate locked", secondRan)
    }
}
