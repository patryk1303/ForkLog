package com.maksimowiczm.foodyou.ai.infrastructure

import kotlin.test.Test
import kotlin.test.assertTrue

class NutritionPromptTest {

    @Test
    fun `system contains every schema key and the language tag`() {
        val system = NutritionPrompt.system("pl-PL")

        assertTrue(system.contains("pl-PL"))
        listOf(
            "name",
            "energy_kcal",
            "proteins_g",
            "carbohydrates_g",
            "fats_g",
            "saturated_fats_g",
            "sugars_g",
            "dietary_fiber_g",
            "salt_g",
        )
            .forEach { key -> assertTrue(system.contains(key), "system prompt is missing `$key`") }
    }

    @Test
    fun `system states units and null rule`() {
        val system = NutritionPrompt.system("en-US")

        assertTrue(system.contains("kcal"))
        assertTrue(system.contains("null"))
    }

    @Test
    fun `user with description returns description`() {
        val description = "A bowl of oatmeal with banana and honey"

        val user = NutritionPrompt.user(description)

        assertTrue(user.contains(description))
    }

    @Test
    fun `user without description mentions photo`() {
        val user = NutritionPrompt.user(null)

        assertTrue(user.contains("photo"))
    }
}
