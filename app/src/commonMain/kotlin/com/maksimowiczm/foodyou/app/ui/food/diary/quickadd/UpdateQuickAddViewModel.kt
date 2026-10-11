package com.maksimowiczm.foodyou.app.ui.food.diary.quickadd

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.ai.ImagePreparation
import com.maksimowiczm.foodyou.ai.domain.NutritionCalculator
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.fooddiary.domain.entity.ManualDiaryEntryId
import com.maksimowiczm.foodyou.fooddiary.domain.repository.ManualDiaryEntryRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal class UpdateQuickAddViewModel(
    id: ManualDiaryEntryId,
    private val manualDiaryEntryRepository: ManualDiaryEntryRepository,
    private val dateProvider: DateProvider,
    nutritionCalculator: NutritionCalculator,
    imagePreparation: ImagePreparation,
) : ViewModel() {

    private val eventChannel = Channel<QuickAddUiEvent>()
    val uiEvents = eventChannel.receiveAsFlow()

    val ai = QuickAddAiController(nutritionCalculator, imagePreparation, viewModelScope)

    val entry =
        manualDiaryEntryRepository
            .observe(id)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(2_000),
                initialValue = null,
            )

    fun updateEntry(
        name: String,
        energy: Double,
        proteins: Double,
        carbohydrates: Double,
        fats: Double,
    ) {
        val entry = entry.value

        if (entry == null) {
            return
        }

        viewModelScope.launch {
            val facts =
                mergeQuickAddNutritionFacts(
                    base = entry.nutritionFacts,
                    aiOverlay = ai.state.value.appliedOverlay,
                    energyKcal = energy,
                    proteins = proteins,
                    carbohydrates = carbohydrates,
                    fats = fats,
                )

            val updatedEntry =
                entry.copy(
                    name = name,
                    nutritionFacts = facts,
                    updatedAt = dateProvider.now(),
                )

            manualDiaryEntryRepository.update(updatedEntry)

            eventChannel.send(QuickAddUiEvent.Saved)
        }
    }
}
