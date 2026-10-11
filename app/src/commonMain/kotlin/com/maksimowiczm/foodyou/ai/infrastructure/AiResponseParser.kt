package com.maksimowiczm.foodyou.ai.infrastructure

import com.maksimowiczm.foodyou.ai.domain.AiCalculationException
import com.maksimowiczm.foodyou.ai.domain.NutritionAiResult
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.common.domain.food.NutrientValue.Companion.toNutrientValue
import com.maksimowiczm.foodyou.common.domain.food.NutrientsHelper
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

internal object AiResponseParser {

    fun parse(raw: String): NutritionAiResult {
        val response = decode(extractJson(raw))
        val name = response.name?.trim()
        if (name.isNullOrEmpty()) throw AiCalculationException.InvalidResponse

        val proteins = response.proteinsG.nonNegative()
        val carbohydrates = response.carbohydratesG.nonNegative()
        val fats = response.fatsG.nonNegative()
        val energy =
            response.energyKcal.nonNegative()
                ?: if (proteins != null && carbohydrates != null && fats != null) {
                    NutrientsHelper.calculateEnergy(proteins, carbohydrates, fats)
                } else {
                    null
                }

        return NutritionAiResult(
            name = name,
            nutritionFacts =
                NutritionFacts(
                    energy = energy.toNutrientValue(),
                    proteins = proteins.toNutrientValue(),
                    carbohydrates = carbohydrates.toNutrientValue(),
                    fats = fats.toNutrientValue(),
                    saturatedFats = response.saturatedFatsG.nonNegative().toNutrientValue(),
                    sugars = response.sugarsG.nonNegative().toNutrientValue(),
                    dietaryFiber = response.dietaryFiberG.nonNegative().toNutrientValue(),
                    salt = response.saltG.nonNegative().toNutrientValue(),
                ),
        )
    }

    private fun extractJson(raw: String): String {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start == -1 || end == -1 || end < start) {
            throw AiCalculationException.InvalidResponse
        }
        return raw.substring(start, end + 1)
    }

    private fun decode(jsonText: String): NutritionResponse =
        try {
            json.decodeFromString(NutritionResponse.serializer(), jsonText)
        } catch (exception: SerializationException) {
            throw AiCalculationException.InvalidResponse
        }

    private fun Double?.nonNegative(): Double? = this?.takeIf { it >= 0 }

    @Serializable
    internal data class NutritionResponse(
        val name: String? = null,
        @SerialName("energy_kcal") val energyKcal: Double? = null,
        @SerialName("proteins_g") val proteinsG: Double? = null,
        @SerialName("carbohydrates_g") val carbohydratesG: Double? = null,
        @SerialName("fats_g") val fatsG: Double? = null,
        @SerialName("saturated_fats_g") val saturatedFatsG: Double? = null,
        @SerialName("sugars_g") val sugarsG: Double? = null,
        @SerialName("dietary_fiber_g") val dietaryFiberG: Double? = null,
        @SerialName("salt_g") val saltG: Double? = null,
    )

    private val json = Json { ignoreUnknownKeys = true }
}
