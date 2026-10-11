package com.maksimowiczm.foodyou.ai.infrastructure

internal object NutritionPrompt {

    fun system(languageTag: String): String =
        """
        You are a nutritionist. Estimate the nutrition facts of the food described by the user.

        Respond with ONLY a JSON object, no prose, no markdown fences.

        Use exactly these keys and types:
        {
          "name": "string",
          "energy_kcal": number or null,
          "proteins_g": number or null,
          "carbohydrates_g": number or null,
          "fats_g": number or null,
          "saturated_fats_g": number or null,
          "sugars_g": number or null,
          "dietary_fiber_g": number or null,
          "salt_g": number or null
        }

        Rules:
        - Report absolute totals for the whole portion as described, not per 100 g.
        - Energy is in kcal, all other values are in grams.
        - Use null when a value is unknown or cannot be estimated. Never guess.
        - Values must be non-negative numbers.
        - "name" is a short dish name written in this language: $languageTag.
        """
            .trimIndent()

    fun user(description: String?): String =
        description?.takeIf { it.isNotBlank() }
            ?: "Estimate nutrition from the attached photo(s)."
}
