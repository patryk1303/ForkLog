package com.maksimowiczm.foodyou.app.ui.food.diary.quickadd

import com.maksimowiczm.foodyou.common.domain.food.NutrientValue
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuickAddNutritionMergeTest {

    @Test
    fun `create without overlay fills core and leaves extended null`() {
        val result =
            mergeQuickAddNutritionFacts(
                base = NutritionFacts(),
                aiOverlay = null,
                energyKcal = 250.0,
                proteins = 10.0,
                carbohydrates = 30.0,
                fats = 5.0,
            )

        assertEquals(250.0, result.energy.value)
        assertEquals(10.0, result.proteins.value)
        assertEquals(30.0, result.carbohydrates.value)
        assertEquals(5.0, result.fats.value)
        assertTrue(result.energy is NutrientValue.Complete)
        assertTrue(result.proteins is NutrientValue.Complete)
        assertTrue(result.carbohydrates is NutrientValue.Complete)
        assertTrue(result.fats is NutrientValue.Complete)
        assertNull(result.saturatedFats.value)
        assertNull(result.sugars.value)
        assertNull(result.dietaryFiber.value)
        assertNull(result.salt.value)
    }

    @Test
    fun `overlay overwrites extended ai nutrients`() {
        val overlay =
            NutritionFacts(
                saturatedFats = NutrientValue.Complete(1.0),
                sugars = NutrientValue.Complete(2.0),
                dietaryFiber = NutrientValue.Complete(3.0),
                salt = NutrientValue.Complete(0.5),
            )

        val result =
            mergeQuickAddNutritionFacts(
                base = NutritionFacts(),
                aiOverlay = overlay,
                energyKcal = 100.0,
                proteins = 5.0,
                carbohydrates = 10.0,
                fats = 2.0,
            )

        assertEquals(1.0, result.saturatedFats.value)
        assertEquals(2.0, result.sugars.value)
        assertEquals(3.0, result.dietaryFiber.value)
        assertEquals(0.5, result.salt.value)
    }

    @Test
    fun `null overlay field keeps base value`() {
        val base = NutritionFacts(saturatedFats = NutrientValue.Complete(2.0))
        val overlay = NutritionFacts(saturatedFats = NutrientValue.Incomplete(null))

        val result =
            mergeQuickAddNutritionFacts(
                base = base,
                aiOverlay = overlay,
                energyKcal = 100.0,
                proteins = 5.0,
                carbohydrates = 10.0,
                fats = 2.0,
            )

        assertEquals(2.0, result.saturatedFats.value)
        assertTrue(result.saturatedFats is NutrientValue.Complete)
    }

    @Test
    fun `core fields always come from arguments not overlay`() {
        val overlay =
            NutritionFacts(
                energy = NutrientValue.Complete(999.0),
                proteins = NutrientValue.Complete(99.0),
                carbohydrates = NutrientValue.Complete(98.0),
                fats = NutrientValue.Complete(97.0),
            )

        val result =
            mergeQuickAddNutritionFacts(
                base = NutritionFacts(),
                aiOverlay = overlay,
                energyKcal = 100.0,
                proteins = 10.0,
                carbohydrates = 20.0,
                fats = 5.0,
            )

        assertEquals(100.0, result.energy.value)
        assertEquals(10.0, result.proteins.value)
        assertEquals(20.0, result.carbohydrates.value)
        assertEquals(5.0, result.fats.value)
    }

    @Test
    fun `update preserves nutrients ai never returns`() {
        val base =
            NutritionFacts(
                vitaminC = NutrientValue.Complete(0.08),
                cholesterol = NutrientValue.Complete(0.03),
            )
        val overlay = NutritionFacts(salt = NutrientValue.Complete(0.4))

        val result =
            mergeQuickAddNutritionFacts(
                base = base,
                aiOverlay = overlay,
                energyKcal = 300.0,
                proteins = 20.0,
                carbohydrates = 40.0,
                fats = 8.0,
            )

        assertEquals(0.08, result.vitaminC.value)
        assertEquals(0.03, result.cholesterol.value)
        assertEquals(0.4, result.salt.value)
        assertEquals(300.0, result.energy.value)
    }

    @Test
    fun `negative values never occur`() {
        val base =
            NutritionFacts(
                saturatedFats = NutrientValue.Incomplete(null),
                sugars = NutrientValue.Incomplete(null),
            )
        val overlay =
            NutritionFacts(
                saturatedFats = NutrientValue.Complete(1.5),
                sugars = NutrientValue.Complete(0.0),
            )

        val result =
            mergeQuickAddNutritionFacts(
                base = base,
                aiOverlay = overlay,
                energyKcal = 0.0,
                proteins = 0.0,
                carbohydrates = 0.0,
                fats = 0.0,
            )

        listOf(
                result.energy,
                result.proteins,
                result.carbohydrates,
                result.fats,
                result.saturatedFats,
                result.sugars,
            )
            .forEach { nutrient ->
                val value = nutrient.value
                assertTrue(nutrient is NutrientValue.Complete, "expected Complete: $nutrient")
                assertTrue(value != null && value >= 0.0, "expected non-negative: $value")
            }
    }
}
