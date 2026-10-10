package com.maksimowiczm.foodyou.ai.domain

import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts

const val MAX_AI_PHOTOS = 4

data class PreparedImage(val jpeg: ByteArray, val base64: String)

data class NutritionAiRequest(val description: String?, val images: List<PreparedImage>)

data class NutritionAiResult(val name: String, val nutritionFacts: NutritionFacts)

interface NutritionCalculator {
    suspend fun calculate(request: NutritionAiRequest): NutritionAiResult
}

sealed class AiCalculationException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {
    object NotConfigured : AiCalculationException("AI provider is not configured")

    object Unauthorized : AiCalculationException("AI request unauthorized")

    object RateLimited : AiCalculationException("AI request rate limited")

    object ServerError : AiCalculationException("AI server error")

    class Network(cause: Throwable) : AiCalculationException("AI network error", cause)

    object InvalidResponse : AiCalculationException("AI response is invalid")
}
