package com.maksimowiczm.foodyou.app.ui.food.diary.quickadd

import com.maksimowiczm.foodyou.common.domain.food.NutrientValue.Companion.toNutrientValue
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.common.domain.food.NutrientsHelper
import kotlin.math.abs

/**
 * Merges the nutrition values edited in the quick-add form with the extended nutrients saved from
 * the last applied AI calculation.
 *
 * The core fields (energy and macros) always come from the arguments, while the extended AI
 * nutrients (saturated fats, sugars, dietary fiber and salt) are taken from [aiOverlay] whenever
 * the overlay holds a value. Any other base field is preserved untouched.
 */
internal fun mergeQuickAddNutritionFacts(
    base: NutritionFacts,
    aiOverlay: NutritionFacts?,
    energyKcal: Double,
    proteins: Double,
    carbohydrates: Double,
    fats: Double,
): NutritionFacts {
    val merged =
        base.copy(
            energy = energyKcal.toNutrientValue(),
            proteins = proteins.toNutrientValue(),
            carbohydrates = carbohydrates.toNutrientValue(),
            fats = fats.toNutrientValue(),
        )

    if (aiOverlay == null) {
        return merged
    }

    return merged.copy(
        saturatedFats = aiOverlay.saturatedFats.takeIf { it.value != null } ?: merged.saturatedFats,
        sugars = aiOverlay.sugars.takeIf { it.value != null } ?: merged.sugars,
        dietaryFiber = aiOverlay.dietaryFiber.takeIf { it.value != null } ?: merged.dietaryFiber,
        salt = aiOverlay.salt.takeIf { it.value != null } ?: merged.salt,
    )
}

/**
 * Whether the auto-calculated energy must be turned off because the AI energy diverges from the
 * energy computed with the Atwater factors by more than 1 kcal.
 *
 * Returns false whenever any of the values is missing.
 */
internal fun shouldDisableAutoCalculate(
    aiKcal: Double?,
    proteins: Double?,
    carbohydrates: Double?,
    fats: Double?,
): Boolean {
    if (aiKcal == null || proteins == null || carbohydrates == null || fats == null) {
        return false
    }

    val calculatedKcal = NutrientsHelper.calculateEnergy(proteins, carbohydrates, fats)
    return abs(aiKcal - calculatedKcal) > 1.0
}
