package com.maksimowiczm.foodyou.ai.domain

import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferences

data class AiProviderConfig(val apiKey: String?, val model: String?, val baseUrl: String?)

data class AiPreferences(
    val selectedProvider: AiProvider? = null,
    val openAi: AiProviderConfig =
        AiProviderConfig(
            apiKey = null,
            model = AiProvider.OpenAi.defaultModel,
            baseUrl = AiProvider.OpenAi.defaultBaseUrl,
        ),
    val gemini: AiProviderConfig =
        AiProviderConfig(
            apiKey = null,
            model = AiProvider.Gemini.defaultModel,
            baseUrl = AiProvider.Gemini.defaultBaseUrl,
        ),
    val anthropic: AiProviderConfig =
        AiProviderConfig(
            apiKey = null,
            model = AiProvider.Anthropic.defaultModel,
            baseUrl = AiProvider.Anthropic.defaultBaseUrl,
        ),
    val openRouter: AiProviderConfig =
        AiProviderConfig(
            apiKey = null,
            model = AiProvider.OpenRouter.defaultModel,
            baseUrl = AiProvider.OpenRouter.defaultBaseUrl,
        ),
    val custom: AiProviderConfig =
        AiProviderConfig(
            apiKey = null,
            model = AiProvider.Custom.defaultModel,
            baseUrl = AiProvider.Custom.defaultBaseUrl,
        ),
) : UserPreferences {
    fun configOf(provider: AiProvider): AiProviderConfig =
        when (provider) {
            AiProvider.OpenAi -> openAi
            AiProvider.Gemini -> gemini
            AiProvider.Anthropic -> anthropic
            AiProvider.OpenRouter -> openRouter
            AiProvider.Custom -> custom
        }

    val isConfigured: Boolean
        get() {
            val provider = selectedProvider ?: return false
            val config = configOf(provider)
            return !config.apiKey.isNullOrBlank() &&
                !config.model.isNullOrBlank() &&
                (provider != AiProvider.Custom || !config.baseUrl.isNullOrBlank())
        }
}
