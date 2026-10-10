package com.maksimowiczm.foodyou.ai.infrastructure

import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.maksimowiczm.foodyou.ai.domain.AiPreferences
import com.maksimowiczm.foodyou.ai.domain.AiProvider
import com.maksimowiczm.foodyou.ai.domain.AiProviderConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiPreferencesMappingTest {

    @Test
    fun `defaults are unconfigured`() {
        val read = preferencesOf().toAiPreferences()

        assertEquals(
            AiPreferences(
                selectedProvider = null,
                openAi = AiProviderConfig(null, "gpt-4o-mini", "https://api.openai.com/v1"),
                gemini = AiProviderConfig(null, "gemini-2.5-flash", null),
                anthropic = AiProviderConfig(null, "claude-sonnet-4-5", null),
                openRouter = AiProviderConfig(null, "openai/gpt-4o-mini", "https://openrouter.ai/api/v1"),
                custom = AiProviderConfig(null, null, null),
            ),
            read,
        )
        assertFalse(read.isConfigured)
    }

    @Test
    fun `roundtrip preserves all fields`() {
        val built = AiPreferences(
            selectedProvider = AiProvider.OpenRouter,
            openAi = AiProviderConfig("openai-key", "gpt-4o-mini", "https://api.openai.com/v1"),
            gemini = AiProviderConfig("gemini-key", "gemini-2.5-flash", null),
            anthropic = AiProviderConfig("anthropic-key", "claude-sonnet-4-5", null),
            openRouter = AiProviderConfig("key", "org/model", null),
            custom = AiProviderConfig("custom-key", "llama3.1", "http://10.0.0.2:11434/v1"),
        )

        val stored = preferencesOf().toMutablePreferences()
        stored.applyAiPreferences(built)
        val read = stored.toAiPreferences()

        // A null baseUrl round-trips as null only for providers whose default is null; for
        // OpenRouter the absent key reads back as the provider default (see toAiPreferences).
        assertEquals(
            built.copy(
                openRouter = built.openRouter.copy(baseUrl = AiProvider.OpenRouter.defaultBaseUrl),
            ),
            read,
        )
    }

    @Test
    fun `unknown selected provider string is ignored`() {
        val stored = preferencesOf().toMutablePreferences()
        stored.applyAiPreferences(AiPreferences(selectedProvider = AiProvider.OpenAi))
        stored[stringPreferencesKey("ai:selected_provider")] = "Bogus"

        assertNull(stored.toAiPreferences().selectedProvider)
    }

    @Test
    fun `missing model falls back to provider default`() {
        val stored = preferencesOf().toMutablePreferences()
        stored.applyAiPreferences(AiPreferences())

        stored.remove(stringPreferencesKey("ai:gemini_model"))

        assertEquals(AiProvider.Gemini.defaultModel, stored.toAiPreferences().gemini.model)
    }

    @Test
    fun `isConfigured requires key, model and custom base url`() {
        // No provider selected.
        assertFalse(AiPreferences().isConfigured)

        // Missing api key.
        assertFalse(
            AiPreferences(
                selectedProvider = AiProvider.OpenAi,
                openAi = AiProviderConfig(
                    apiKey = null,
                    model = "gpt-4o-mini",
                    baseUrl = "https://api.openai.com/v1",
                ),
            ).isConfigured
        )

        // Blank model.
        assertFalse(
            AiPreferences(
                selectedProvider = AiProvider.Gemini,
                gemini = AiProviderConfig(apiKey = "key", model = "  ", baseUrl = null),
            ).isConfigured
        )

        // Custom provider with key and model but no base url.
        assertFalse(
            AiPreferences(
                selectedProvider = AiProvider.Custom,
                custom = AiProviderConfig(apiKey = "key", model = "llama3.1", baseUrl = null),
            ).isConfigured
        )

        // Non-custom providers do not require a base url.
        assertTrue(
            AiPreferences(
                selectedProvider = AiProvider.OpenRouter,
                openRouter = AiProviderConfig(apiKey = "key", model = "org/model", baseUrl = null),
            ).isConfigured
        )

        // Custom provider with key, model and base url.
        assertTrue(
            AiPreferences(
                selectedProvider = AiProvider.Custom,
                custom = AiProviderConfig(
                    apiKey = "key",
                    model = "llama3.1",
                    baseUrl = "http://localhost:11434/v1",
                ),
            ).isConfigured
        )
    }
}
