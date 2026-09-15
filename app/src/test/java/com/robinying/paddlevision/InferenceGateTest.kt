package com.robinying.paddlevision

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
