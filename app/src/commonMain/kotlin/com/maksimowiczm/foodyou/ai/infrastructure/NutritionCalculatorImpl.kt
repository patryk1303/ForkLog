package com.maksimowiczm.foodyou.ai.infrastructure

import com.maksimowiczm.foodyou.ai.domain.AiCalculationException
import com.maksimowiczm.foodyou.ai.domain.AiPreferences
import com.maksimowiczm.foodyou.ai.domain.AiProvider
import com.maksimowiczm.foodyou.ai.domain.AiProviderConfig
import com.maksimowiczm.foodyou.ai.domain.NutritionAiRequest
import com.maksimowiczm.foodyou.ai.domain.NutritionAiResult
import com.maksimowiczm.foodyou.ai.domain.NutritionCalculator
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.common.system.SystemDetails
import kotlinx.coroutines.flow.first

internal class NutritionCalculatorImpl(
    private val preferencesRepository: UserPreferencesRepository<AiPreferences>,
    private val systemDetails: SystemDetails,
    private val openAiClient: AiProtocolClient,
    private val geminiClient: AiProtocolClient,
    private val anthropicClient: AiProtocolClient,
) : NutritionCalculator {

    override suspend fun calculate(request: NutritionAiRequest): NutritionAiResult {
        val preferences = preferencesRepository.observe().first()
        val provider =
            preferences.selectedProvider ?: throw AiCalculationException.NotConfigured
        if (!preferences.isConfigured) throw AiCalculationException.NotConfigured
        val config = preferences.configOf(provider).withDefaultBaseUrl(provider)
        val languageTag = systemDetails.languageTag.first()
        val client =
            when (provider) {
                AiProvider.OpenAi,
                AiProvider.OpenRouter,
                AiProvider.Custom -> openAiClient
                AiProvider.Gemini -> geminiClient
                AiProvider.Anthropic -> anthropicClient
            }

        val text =
            client.complete(
                system = NutritionPrompt.system(languageTag),
                user = NutritionPrompt.user(request.description),
                images = request.images,
                config = config,
            )
        return AiResponseParser.parse(text)
    }

    private fun AiProviderConfig.withDefaultBaseUrl(provider: AiProvider): AiProviderConfig =
        if (baseUrl == null) copy(baseUrl = provider.defaultBaseUrl) else this
}
