package com.maksimowiczm.foodyou.ai.infrastructure

import com.maksimowiczm.foodyou.ai.domain.AiCalculationException
import com.maksimowiczm.foodyou.ai.domain.AiPreferences
import com.maksimowiczm.foodyou.ai.domain.AiProvider
import com.maksimowiczm.foodyou.ai.domain.AiProviderConfig
import com.maksimowiczm.foodyou.ai.domain.NutritionAiRequest
import com.maksimowiczm.foodyou.ai.domain.PreparedImage
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.common.system.SystemDetails
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NutritionCalculatorImplTest {

    @Test
    fun `no selected provider throws NotConfigured`() = runBlocking {
        val calculator = calculatorOf(AiPreferences(selectedProvider = null))

        assertNotConfigured { calculator.calculate(NutritionAiRequest("oatmeal", emptyList())) }
    }

    @Test
    fun `selected provider without key throws NotConfigured`() = runBlocking {
        val calculator =
            calculatorOf(
                AiPreferences(
                    selectedProvider = AiProvider.Gemini,
                    gemini =
                        AiProviderConfig(
                            apiKey = null,
                            model = AiProvider.Gemini.defaultModel,
                            baseUrl = null,
                        ),
                )
            )

        assertNotConfigured { calculator.calculate(NutritionAiRequest("oatmeal", emptyList())) }
    }

    @Test
    fun `dispatches to open ai client for OpenRouter and custom`() = runBlocking {
        val openAi = RecordingClient()
        val gemini = RecordingClient()
        val anthropic = RecordingClient()

        calculatorOf(
                AiPreferences(
                    selectedProvider = AiProvider.OpenRouter,
                    openRouter =
                        AiProviderConfig(
                            apiKey = "key",
                            model = "org/model",
                            baseUrl = "https://openrouter.ai/api/v1",
                        ),
                ),
                openAi = openAi,
                gemini = gemini,
                anthropic = anthropic,
            )
            .calculate(NutritionAiRequest("two slices of bread", emptyList()))

        calculatorOf(
                AiPreferences(
                    selectedProvider = AiProvider.Custom,
                    custom =
                        AiProviderConfig(
                            apiKey = "key",
                            model = "llama3",
                            baseUrl = "http://localhost:11434/v1",
                        ),
                ),
                openAi = openAi,
                gemini = gemini,
                anthropic = anthropic,
            )
            .calculate(NutritionAiRequest("two slices of bread", emptyList()))

        assertEquals(2, openAi.calls.size)
        assertTrue(openAi.calls[0].system.contains("en-US"))
        assertEquals(0, gemini.calls.size)
        assertEquals(0, anthropic.calls.size)
    }

    @Test
    fun `dispatches to gemini for Gemini`() = runBlocking {
        val openAi = RecordingClient()
        val gemini = RecordingClient()
        val anthropic = RecordingClient()

        calculatorOf(
                AiPreferences(
                    selectedProvider = AiProvider.Gemini,
                    gemini =
                        AiProviderConfig(
                            apiKey = "key",
                            model = AiProvider.Gemini.defaultModel,
                            baseUrl = null,
                        ),
                ),
                openAi = openAi,
                gemini = gemini,
                anthropic = anthropic,
            )
            .calculate(NutritionAiRequest("a bowl of oatmeal", emptyList()))

        assertEquals(1, gemini.calls.size)
        assertEquals(0, openAi.calls.size)
        assertEquals(0, anthropic.calls.size)
    }

    @Test
    fun `dispatches to anthropic for Anthropic`() = runBlocking {
        val openAi = RecordingClient()
        val gemini = RecordingClient()
        val anthropic = RecordingClient()

        calculatorOf(
                AiPreferences(
                    selectedProvider = AiProvider.Anthropic,
                    anthropic =
                        AiProviderConfig(
                            apiKey = "key",
                            model = AiProvider.Anthropic.defaultModel,
                            baseUrl = null,
                        ),
                ),
                openAi = openAi,
                gemini = gemini,
                anthropic = anthropic,
            )
            .calculate(NutritionAiRequest("a bowl of oatmeal", emptyList()))

        assertEquals(1, anthropic.calls.size)
        assertEquals(0, openAi.calls.size)
        assertEquals(0, gemini.calls.size)
    }

    @Test
    fun `parses client text into result`() = runBlocking {
        val openAi =
            RecordingClient(
                response =
                    """{"name":"Oatmeal","energy_kcal":350,"proteins_g":12,"carbohydrates_g":60,"fats_g":7}"""
            )

        val result =
            calculatorOf(
                    AiPreferences(
                        selectedProvider = AiProvider.OpenAi,
                        openAi =
                            AiProviderConfig(
                                apiKey = "key",
                                model = AiProvider.OpenAi.defaultModel,
                                baseUrl = AiProvider.OpenAi.defaultBaseUrl,
                            ),
                    ),
                    openAi = openAi,
                )
                .calculate(NutritionAiRequest("a bowl of oatmeal", listOf(image)))

        assertEquals("Oatmeal", result.name)
        assertEquals(350.0, result.nutritionFacts.energy.value)
        assertEquals(listOf(image), openAi.calls.single().images)
    }

    private suspend fun assertNotConfigured(block: suspend () -> Unit) {
        try {
            block()
        } catch (exception: AiCalculationException) {
            assertEquals(AiCalculationException.NotConfigured, exception)
            return
        }
        throw AssertionError("Expected AiCalculationException.NotConfigured")
    }

    private fun calculatorOf(
        preferences: AiPreferences,
        openAi: AiProtocolClient = RecordingClient(),
        gemini: AiProtocolClient = RecordingClient(),
        anthropic: AiProtocolClient = RecordingClient(),
    ) =
        NutritionCalculatorImpl(
            preferencesRepository = FakePreferencesRepository(preferences),
            systemDetails = FakeSystemDetails(),
            openAiClient = openAi,
            geminiClient = gemini,
            anthropicClient = anthropic,
        )

    private class FakePreferencesRepository(preferences: AiPreferences) :
        UserPreferencesRepository<AiPreferences> {
        private val state = MutableStateFlow(preferences)

        override fun observe(): Flow<AiPreferences> = state

        override suspend fun update(transform: AiPreferences.() -> AiPreferences) {
            state.value = state.value.transform()
        }
    }

    private class FakeSystemDetails : SystemDetails {
        override val languageTag: Flow<String> = flowOf("en-US")

        override fun setLanguage(tag: String) {}

        override fun setSystemLanguage() {}
    }

    private class RecordingClient(private val response: String = CANNED_RESPONSE) :
        AiProtocolClient {
        val calls = mutableListOf<Call>()

        override suspend fun complete(
            system: String,
            user: String,
            images: List<PreparedImage>,
            config: AiProviderConfig,
        ): String {
            calls += Call(system = system, user = user, images = images, config = config)
            return response
        }

        data class Call(
            val system: String,
            val user: String,
            val images: List<PreparedImage>,
            val config: AiProviderConfig,
        )
    }

    private companion object {
        val image = PreparedImage(jpeg = byteArrayOf(), base64 = "Zmlyc3Q=")
        const val CANNED_RESPONSE = """{"name":"Oatmeal","energy_kcal":350}"""
    }
}