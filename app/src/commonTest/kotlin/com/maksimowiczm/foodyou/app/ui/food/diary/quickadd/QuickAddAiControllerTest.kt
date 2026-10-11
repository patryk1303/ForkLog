package com.maksimowiczm.foodyou.app.ui.food.diary.quickadd

import com.maksimowiczm.foodyou.ai.ImagePreparation
import com.maksimowiczm.foodyou.ai.domain.MAX_AI_PHOTOS
import com.maksimowiczm.foodyou.ai.domain.NutritionAiRequest
import com.maksimowiczm.foodyou.ai.domain.NutritionAiResult
import com.maksimowiczm.foodyou.ai.domain.NutritionCalculator
import com.maksimowiczm.foodyou.ai.domain.PreparedImage
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield

class QuickAddAiControllerTest {

    private val staleResult = NutritionAiResult(name = "stale", nutritionFacts = NutritionFacts())
    private val freshResult = NutritionAiResult(name = "fresh", nutritionFacts = NutritionFacts())

    @Test
    fun `stale response after describe change is dropped`() = runBlocking {
        val calculator = FakeNutritionCalculator()
        val controller = QuickAddAiController(calculator, FakeImagePreparation(), this)

        controller.setDescription("banana pancakes")
        controller.calculate()
        yield()

        assertEquals(1, calculator.requests.size)
        assertEquals("banana pancakes", calculator.requests.single().description)
        assertEquals(QuickAddAiState.Status.Loading, controller.state.value.status)

        // Editing the input while the request is in flight invalidates that request.
        controller.setDescription("banana smoothie")
        calculator.completeAll(staleResult)
        yield()

        assertNull(controller.state.value.result, "stale response must not be applied")
        assertEquals(QuickAddAiState.Status.Idle, controller.state.value.status)

        // The stale response released the loading state, so a fresh calculation can run.
        controller.calculate()
        yield()
        assertEquals(2, calculator.requests.size)
        calculator.completeAll(freshResult)
        yield()
        assertEquals(freshResult, controller.state.value.result)
    }

    @Test
    fun `second calculate while loading is a no-op`() = runBlocking {
        val calculator = FakeNutritionCalculator()
        val controller = QuickAddAiController(calculator, FakeImagePreparation(), this)

        controller.setDescription("oatmeal")
        controller.calculate()
        controller.calculate()
        yield()

        assertEquals(1, calculator.requests.size, "overlapping calculations must not start")

        calculator.completeAll(freshResult)
        yield()

        assertEquals(freshResult, controller.state.value.result)
        assertEquals(QuickAddAiState.Status.Idle, controller.state.value.status)
    }

    @Test
    fun `overlapping addPhotos never exceed the photo cap`() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val imagePreparation = FakeImagePreparation(blockOn = "a1", release = release)
        val controller = QuickAddAiController(FakeNutritionCalculator(), imagePreparation, this)

        try {
            controller.addPhotos(listOf("a1", "a2", "a3", "a4"))
            withTimeout(5_000) { imagePreparation.started.await() }

            // The first batch is still blocked, so this call still sees full capacity.
            controller.addPhotos(listOf("b1", "b2", "b3", "b4"))
            release.complete(Unit)

            withTimeout(5_000) { imagePreparation.prepared.first { "a4" in it && "b4" in it } }
            val settled = awaitSettled(controller.state)
            assertTrue(
                settled.photos.size <= MAX_AI_PHOTOS,
                "expected at most $MAX_AI_PHOTOS photos, got ${settled.photos.size}",
            )
        } finally {
            release.complete(Unit)
        }
    }

    /** Waits until [state] stops changing so every photo append has landed. */
    private suspend fun awaitSettled(state: StateFlow<QuickAddAiState>): QuickAddAiState {
        var previous = state.value
        var unchanged = 0
        withTimeout(10_000) {
            while (unchanged < 5) {
                delay(20)
                val current = state.value
                if (current == previous) {
                    unchanged++
                } else {
                    unchanged = 0
                    previous = current
                }
            }
        }
        return previous
    }
}

private class FakeNutritionCalculator : NutritionCalculator {
    val requests = mutableListOf<NutritionAiRequest>()
    private val gates = mutableListOf<CompletableDeferred<NutritionAiResult>>()

    override suspend fun calculate(request: NutritionAiRequest): NutritionAiResult {
        requests += request
        val gate = CompletableDeferred<NutritionAiResult>()
        gates += gate
        return gate.await()
    }

    fun completeAll(result: NutritionAiResult) {
        gates.forEach { it.complete(result) }
    }
}

private class FakeImagePreparation(
    private val blockOn: String? = null,
    private val release: CompletableDeferred<Unit> = CompletableDeferred(),
) : ImagePreparation {
    val started = CompletableDeferred<Unit>()
    val prepared = MutableStateFlow(emptyList<String>())

    override fun prepare(uri: String): PreparedImage? {
        prepared.update { it + uri }
        if (uri == blockOn) {
            started.complete(Unit)
            runBlocking { withTimeout(20_000) { release.await() } }
        }
        return PreparedImage(jpeg = uri.encodeToByteArray(), base64 = uri)
    }
}
