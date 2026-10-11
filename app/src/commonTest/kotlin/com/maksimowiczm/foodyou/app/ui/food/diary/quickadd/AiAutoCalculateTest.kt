package com.maksimowiczm.foodyou.app.ui.food.diary.quickadd

import com.maksimowiczm.foodyou.common.domain.food.NutrientsHelper
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiAutoCalculateTest {

    private val proteins = 10.0
    private val carbohydrates = 20.0
    private val fats = 5.0

    private val calculatedKcal = NutrientsHelper.calculateEnergy(proteins, carbohydrates, fats)

    @Test
    fun `within 1 kcal keeps auto calculate`() {
        val aiKcal = calculatedKcal + 0.5

        assertFalse(shouldDisableAutoCalculate(aiKcal, proteins, carbohydrates, fats))
    }

    @Test
    fun `above 1 disables`() {
        val aiKcal = calculatedKcal + 1.1

        assertTrue(shouldDisableAutoCalculate(aiKcal, proteins, carbohydrates, fats))
    }

    @Test
    fun `null ai energy → false`() {
        assertFalse(shouldDisableAutoCalculate(null, proteins, carbohydrates, fats))
    }

    @Test
    fun `null macro → false`() {
        assertFalse(shouldDisableAutoCalculate(calculatedKcal, null, carbohydrates, fats))
    }
}
