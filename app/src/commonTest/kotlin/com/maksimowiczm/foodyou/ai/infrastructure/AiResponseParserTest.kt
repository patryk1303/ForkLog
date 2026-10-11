package com.maksimowiczm.foodyou.ai.infrastructure

import com.maksimowiczm.foodyou.ai.domain.AiCalculationException
import com.maksimowiczm.foodyou.common.domain.food.NutrientValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiResponseParserTest {

    @Test
    fun `parses plain json`() {
        val raw =
            """{"name":"Oatmeal","energy_kcal":350,"proteins_g":12,"carbohydrates_g":60,"fats_g":7,"saturated_fats_g":1.2,"sugars_g":3,"dietary_fiber_g":5,"salt_g":0.4}"""

        val result = AiResponseParser.parse(raw)

        assertEquals("Oatmeal", result.name)
        val facts = result.nutritionFacts
        listOf(
            facts.energy to 350.0,
            facts.proteins to 12.0,
            facts.carbohydrates to 60.0,
            facts.fats to 7.0,
            facts.saturatedFats to 1.2,
            facts.sugars to 3.0,
            facts.dietaryFiber to 5.0,
            facts.salt to 0.4,
        )
            .forEach { (nutrient, expected) ->
                assertEquals(expected, nutrient.value)
                assertTrue(nutrient is NutrientValue.Complete)
            }
    }

    @Test
    fun `parses json wrapped in markdown fences`() {
        val raw =
            """
            ```json
            {"name":"Oatmeal","energy_kcal":350,"proteins_g":12,"carbohydrates_g":60,"fats_g":7,"saturated_fats_g":1.2,"sugars_g":3,"dietary_fiber_g":5,"salt_g":0.4}
            ```
            """
                .trimIndent()

        val result = AiResponseParser.parse(raw)

        assertEquals("Oatmeal", result.name)
        assertEquals(350.0, result.nutritionFacts.energy.value)
    }

    @Test
    fun `parses json with surrounding prose`() {
        val raw = """Here is your estimate: {"name":"Oatmeal","energy_kcal":350} hope that helps"""

        val result = AiResponseParser.parse(raw)

        assertEquals("Oatmeal", result.name)
        assertEquals(350.0, result.nutritionFacts.energy.value)
    }

    @Test
    fun `missing nutrients become incomplete null`() {
        val result = AiResponseParser.parse("""{"name":"X","proteins_g":10}""")

        val facts = result.nutritionFacts
        assertEquals(NutrientValue.Complete(10.0), facts.proteins)
        assertEquals(NutrientValue.Incomplete(null), facts.fats)
        assertEquals(NutrientValue.Incomplete(null), facts.saturatedFats)
        assertEquals(NutrientValue.Incomplete(null), facts.sugars)
        assertEquals(NutrientValue.Incomplete(null), facts.dietaryFiber)
        assertEquals(NutrientValue.Incomplete(null), facts.salt)
        // The Atwater fallback needs all three macros, so energy stays unknown here.
        assertNull(facts.energy.value)
    }

    @Test
    fun `missing energy falls back to atwater`() {
        val result =
            AiResponseParser.parse(
                """{"name":"X","proteins_g":10,"carbohydrates_g":20,"fats_g":5}"""
            )

        val energy = result.nutritionFacts.energy
        // Atwater: 4 * 10 + 4 * 20 + 9 * 5
        assertEquals(165.0, energy.value)
        assertTrue(energy is NutrientValue.Complete)
    }

    @Test
    fun `missing energy with null macro stays null`() {
        val result = AiResponseParser.parse("""{"name":"X","proteins_g":10,"fats_g":null}""")

        assertNull(result.nutritionFacts.energy.value)
    }

    @Test
    fun `negative values become null`() {
        val result = AiResponseParser.parse("""{"name":"X","carbohydrates_g":-3}""")

        val carbohydrates = result.nutritionFacts.carbohydrates
        assertEquals(NutrientValue.Incomplete(null), carbohydrates)
        assertNull(carbohydrates.value)
    }

    @Test
    fun `blank name throws invalid response`() {
        assertFailsWith<AiCalculationException.InvalidResponse> {
            AiResponseParser.parse("""{"name":"  "}""")
        }
    }

    @Test
    fun `garbage without json throws invalid response`() {
        assertFailsWith<AiCalculationException.InvalidResponse> {
            AiResponseParser.parse("I cannot estimate that.")
        }
    }

    @Test
    fun `unknown keys are ignored`() {
        val result = AiResponseParser.parse("""{"name":"X","proteins_g":10,"notes":"extra"}""")

        assertEquals("X", result.name)
        assertEquals(NutrientValue.Complete(10.0), result.nutritionFacts.proteins)
    }
}
