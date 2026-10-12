package com.maksimowiczm.foodyou.ai.infrastructure

import com.maksimowiczm.foodyou.ai.domain.AiPreferences
import com.maksimowiczm.foodyou.ai.domain.NutritionCalculator
import com.maksimowiczm.foodyou.ai.imagePreparationDefinition
import com.maksimowiczm.foodyou.ai.infrastructure.anthropic.AnthropicClient
import com.maksimowiczm.foodyou.ai.infrastructure.gemini.GeminiClient
import com.maksimowiczm.foodyou.ai.infrastructure.openai.OpenAiCompatibleClient
import com.maksimowiczm.foodyou.common.infrastructure.koin.userPreferencesRepository
import com.maksimowiczm.foodyou.common.infrastructure.koin.userPreferencesRepositoryOf
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.dsl.onClose

private val aiHttpClientQualifier: Qualifier = named("ai:http-client")
private val openAiClientQualifier: Qualifier = named("ai:openai-client")
private val geminiClientQualifier: Qualifier = named("ai:gemini-client")
private val anthropicClientQualifier: Qualifier = named("ai:anthropic-client")

fun Module.aiModule() {
    imagePreparationDefinition()

    userPreferencesRepositoryOf(::DataStoreAiPreferencesRepository)

    single(aiHttpClientQualifier) {
            HttpClient {
                install(HttpTimeout) { requestTimeoutMillis = 60_000 }
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        }
        .onClose { it?.close() }
    factory<AiProtocolClient>(openAiClientQualifier) { OpenAiCompatibleClient(get(aiHttpClientQualifier)) }
    factory<AiProtocolClient>(geminiClientQualifier) { GeminiClient(get(aiHttpClientQualifier)) }
    factory<AiProtocolClient>(anthropicClientQualifier) { AnthropicClient(get(aiHttpClientQualifier)) }
    factory<NutritionCalculator> {
        NutritionCalculatorImpl(
            preferencesRepository = userPreferencesRepository<AiPreferences>(),
            systemDetails = get(),
            openAiClient = get(openAiClientQualifier),
            geminiClient = get(geminiClientQualifier),
            anthropicClient = get(anthropicClientQualifier),
        )
    }
}

val aiModule = module { aiModule() }
