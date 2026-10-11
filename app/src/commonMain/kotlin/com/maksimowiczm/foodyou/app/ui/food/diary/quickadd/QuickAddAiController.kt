package com.maksimowiczm.foodyou.app.ui.food.diary.quickadd

import androidx.compose.ui.graphics.ImageBitmap
import com.maksimowiczm.foodyou.ai.ImagePreparation
import com.maksimowiczm.foodyou.ai.decodeJpegToImageBitmap
import com.maksimowiczm.foodyou.ai.domain.AiCalculationException
import com.maksimowiczm.foodyou.ai.domain.MAX_AI_PHOTOS
import com.maksimowiczm.foodyou.ai.domain.NutritionAiRequest
import com.maksimowiczm.foodyou.ai.domain.NutritionAiResult
import com.maksimowiczm.foodyou.ai.domain.NutritionCalculator
import com.maksimowiczm.foodyou.ai.domain.PreparedImage
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class AiPhoto(val image: PreparedImage, val thumbnail: ImageBitmap?)

internal data class QuickAddAiState(
    val description: String = "",
    val photos: List<AiPhoto> = emptyList(),
    val status: Status = Status.Idle,
    val result: NutritionAiResult? = null,
    val appliedOverlay: NutritionFacts? = null,
) {
    sealed interface Status {
        data object Idle : Status

        data object Loading : Status

        data class Error(val exception: AiCalculationException) : Status
    }

    val canCalculate: Boolean
        get() = photos.isNotEmpty() || description.isNotBlank()
}

/**
 * Drives the AI part of the quick-add screen: description and photo input, calculation status and
 * the last applied nutrition overlay used to enrich saved entries.
 */
internal class QuickAddAiController(
    private val calculator: NutritionCalculator,
    private val imagePreparation: ImagePreparation,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(QuickAddAiState())

    val state: StateFlow<QuickAddAiState> = _state.asStateFlow()

    /**
     * Identifies the current input generation. It is incremented on every input mutation and on
     * every accepted [calculate] call; a running calculation captures the value when it starts and
     * drops its response once the value no longer matches, so a response computed for older input
     * can never set [QuickAddAiState.result] or overwrite [QuickAddAiState.status].
     */
    private var generation = 0

    /**
     * Updates the description; a changed description invalidates a pending result and any
     * calculation still in flight.
     */
    fun setDescription(description: String) {
        generation++
        _state.update { it.copy(description = description, result = null) }
    }

    /**
     * Prepares up to [MAX_AI_PHOTOS] photos in total. The incoming list is truncated to the
     * remaining capacity and processed sequentially off the main thread; the capacity is checked
     * again before every append so overlapping calls can never exceed the cap. Unreadable images
     * are skipped and failures never propagate.
     */
    fun addPhotos(uris: List<String>) {
        generation++
        val remaining = MAX_AI_PHOTOS - _state.value.photos.size
        if (remaining <= 0) return

        val accepted = uris.take(remaining)
        if (accepted.isEmpty()) return

        scope.launch(Dispatchers.IO) {
            for (uri in accepted) {
                val photo =
                    try {
                        val image = imagePreparation.prepare(uri) ?: continue
                        // A thumbnail that cannot be decoded keeps the photo without one
                        // instead of dropping the photo entirely.
                        AiPhoto(
                            image,
                            runCatching { decodeJpegToImageBitmap(image.jpeg) }.getOrNull(),
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        continue
                    }

                _state.update { current ->
                    if (current.photos.size >= MAX_AI_PHOTOS) {
                        current
                    } else {
                        current.copy(photos = current.photos + photo)
                    }
                }
            }
        }
    }

    fun removePhoto(photo: AiPhoto) {
        generation++
        _state.update { it.copy(photos = it.photos - photo) }
    }

    fun clearPhotos() {
        generation++
        _state.update { it.copy(photos = emptyList()) }
    }

    /**
     * Requests a nutrition calculation for the current description and photos.
     *
     * Only one calculation runs at a time: while one is in flight
     * ([QuickAddAiState.Status.Loading]) further calls are ignored. A response whose input
     * generation changed while the request was running is dropped without touching the state.
     */
    fun calculate() {
        if (_state.value.status is QuickAddAiState.Status.Loading) return
        generation++
        _state.update { it.copy(status = QuickAddAiState.Status.Loading) }

        scope.launch {
            val token = generation
            val snapshot = _state.value
            val request =
                NutritionAiRequest(
                    description = snapshot.description.trim().takeIf { it.isNotBlank() },
                    images = snapshot.photos.map { it.image },
                )

            try {
                val result = calculator.calculate(request)
                _state.update { current ->
                    if (token == generation) {
                        current.copy(result = result, status = QuickAddAiState.Status.Idle)
                    } else {
                        current.copy(status = QuickAddAiState.Status.Idle)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.update { current ->
                    if (token == generation) {
                        current.copy(
                            status =
                                QuickAddAiState.Status.Error(
                                    e as? AiCalculationException ?: AiCalculationException.Network(e)
                                )
                        )
                    } else {
                        current.copy(status = QuickAddAiState.Status.Idle)
                    }
                }
            }
        }
    }

    /** Stores the displayed result as the overlay merged into the entry on save. */
    fun applyResult() {
        _state.update { it.copy(appliedOverlay = it.result?.nutritionFacts, result = null) }
    }

    fun dismissResult() {
        _state.update { it.copy(result = null) }
    }
}
