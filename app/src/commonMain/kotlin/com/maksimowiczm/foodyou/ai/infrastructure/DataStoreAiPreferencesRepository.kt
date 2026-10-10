package com.maksimowiczm.foodyou.ai.infrastructure

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.maksimowiczm.foodyou.ai.domain.AiPreferences
import com.maksimowiczm.foodyou.ai.domain.AiProvider
import com.maksimowiczm.foodyou.ai.domain.AiProviderConfig
import com.maksimowiczm.foodyou.common.infrastructure.datastore.AbstractDataStoreUserPreferencesRepository
import com.maksimowiczm.foodyou.common.infrastructure.datastore.set

internal class DataStoreAiPreferencesRepository(dataStore: DataStore<Preferences>) :
    AbstractDataStoreUserPreferencesRepository<AiPreferences>(dataStore) {
    override fun Preferences.toUserPreferences(): AiPreferences = toAiPreferences()

    override fun MutablePreferences.applyUserPreferences(updated: AiPreferences) {
        applyAiPreferences(updated)
    }
}

internal fun Preferences.toAiPreferences(): AiPreferences =
    AiPreferences(
        selectedProvider =
            this[AiPreferencesKeys.SelectedProvider]?.let { stored ->
                AiProvider.entries.firstOrNull { it.name == stored }
            },
        openAi =
            AiProviderConfig(
                apiKey = this[AiPreferencesKeys.OpenAiApiKey],
                model = this[AiPreferencesKeys.OpenAiModel] ?: AiProvider.OpenAi.defaultModel,
                baseUrl = this[AiPreferencesKeys.OpenAiBaseUrl] ?: AiProvider.OpenAi.defaultBaseUrl,
            ),
        gemini =
            AiProviderConfig(
                apiKey = this[AiPreferencesKeys.GeminiApiKey],
                model = this[AiPreferencesKeys.GeminiModel] ?: AiProvider.Gemini.defaultModel,
                baseUrl = this[AiPreferencesKeys.GeminiBaseUrl] ?: AiProvider.Gemini.defaultBaseUrl,
            ),
        anthropic =
            AiProviderConfig(
                apiKey = this[AiPreferencesKeys.AnthropicApiKey],
                model = this[AiPreferencesKeys.AnthropicModel]
                    ?: AiProvider.Anthropic.defaultModel,
                baseUrl = this[AiPreferencesKeys.AnthropicBaseUrl]
                    ?: AiProvider.Anthropic.defaultBaseUrl,
            ),
        openRouter =
            AiProviderConfig(
                apiKey = this[AiPreferencesKeys.OpenRouterApiKey],
                model = this[AiPreferencesKeys.OpenRouterModel]
                    ?: AiProvider.OpenRouter.defaultModel,
                baseUrl = this[AiPreferencesKeys.OpenRouterBaseUrl]
                    ?: AiProvider.OpenRouter.defaultBaseUrl,
            ),
        custom =
            AiProviderConfig(
                apiKey = this[AiPreferencesKeys.CustomApiKey],
                model = this[AiPreferencesKeys.CustomModel] ?: AiProvider.Custom.defaultModel,
                baseUrl = this[AiPreferencesKeys.CustomBaseUrl] ?: AiProvider.Custom.defaultBaseUrl,
            ),
    )

internal fun MutablePreferences.applyAiPreferences(updated: AiPreferences) {
    this[AiPreferencesKeys.SelectedProvider] = updated.selectedProvider?.name
    this[AiPreferencesKeys.OpenAiApiKey] = updated.openAi.apiKey
    this[AiPreferencesKeys.OpenAiModel] = updated.openAi.model
    this[AiPreferencesKeys.OpenAiBaseUrl] = updated.openAi.baseUrl
    this[AiPreferencesKeys.GeminiApiKey] = updated.gemini.apiKey
    this[AiPreferencesKeys.GeminiModel] = updated.gemini.model
    this[AiPreferencesKeys.GeminiBaseUrl] = updated.gemini.baseUrl
    this[AiPreferencesKeys.AnthropicApiKey] = updated.anthropic.apiKey
    this[AiPreferencesKeys.AnthropicModel] = updated.anthropic.model
    this[AiPreferencesKeys.AnthropicBaseUrl] = updated.anthropic.baseUrl
    this[AiPreferencesKeys.OpenRouterApiKey] = updated.openRouter.apiKey
    this[AiPreferencesKeys.OpenRouterModel] = updated.openRouter.model
    this[AiPreferencesKeys.OpenRouterBaseUrl] = updated.openRouter.baseUrl
    this[AiPreferencesKeys.CustomApiKey] = updated.custom.apiKey
    this[AiPreferencesKeys.CustomModel] = updated.custom.model
    this[AiPreferencesKeys.CustomBaseUrl] = updated.custom.baseUrl
}

private object AiPreferencesKeys {
    val SelectedProvider = stringPreferencesKey("ai:selected_provider")
    val OpenAiApiKey = stringPreferencesKey("ai:openai_api_key")
    val OpenAiModel = stringPreferencesKey("ai:openai_model")
    val OpenAiBaseUrl = stringPreferencesKey("ai:openai_base_url")
    val GeminiApiKey = stringPreferencesKey("ai:gemini_api_key")
    val GeminiModel = stringPreferencesKey("ai:gemini_model")
    val GeminiBaseUrl = stringPreferencesKey("ai:gemini_base_url")
    val AnthropicApiKey = stringPreferencesKey("ai:anthropic_api_key")
    val AnthropicModel = stringPreferencesKey("ai:anthropic_model")
    val AnthropicBaseUrl = stringPreferencesKey("ai:anthropic_base_url")
    val OpenRouterApiKey = stringPreferencesKey("ai:openrouter_api_key")
    val OpenRouterModel = stringPreferencesKey("ai:openrouter_model")
    val OpenRouterBaseUrl = stringPreferencesKey("ai:openrouter_base_url")
    val CustomApiKey = stringPreferencesKey("ai:custom_api_key")
    val CustomModel = stringPreferencesKey("ai:custom_model")
    val CustomBaseUrl = stringPreferencesKey("ai:custom_base_url")
}
