package com.maksimowiczm.foodyou.app.ui.food.diary.quickadd

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.ai.ImagePreparation
import com.maksimowiczm.foodyou.ai.domain.NutritionCalculator
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.fooddiary.domain.repository.ManualDiaryEntryRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

internal class CreateQuickAddViewModel(
    private val mealId: Long,
    private val date: LocalDate,
    private val manualDiaryEntryRepository: ManualDiaryEntryRepository,
    private val dateProvider: DateProvider,
    nutritionCalculator: NutritionCalculator,
    imagePreparation: ImagePreparation,
) : ViewModel() {

    private val eventChannel = Channel<QuickAddUiEvent>()
    val uiEvents = eventChannel.receiveAsFlow()

    val ai = QuickAddAiController(nutritionCalculator, imagePreparation, viewModelScope)

    fun addEntry(
        name: String,
        energy: Double,
        proteins: Double,
        carbohydrates: Double,
        fats: Double,
    ) {
        viewModelScope.launch {
            val facts =
                mergeQuickAddNutritionFacts(
                    base = NutritionFacts(),
                    aiOverlay = ai.state.value.appliedOverlay,
                    energyKcal = energy,
                    proteins = proteins,
                    carbohydrates = carbohydrates,
                    fats = fats,
                )

            manualDiaryEntryRepository.insert(
                name = name,
                mealId = mealId,
                date = date,
                nutritionFacts = facts,
                createdAt = dateProvider.now(),
            )

            eventChannel.send(QuickAddUiEvent.Saved)
        }
    }
}
