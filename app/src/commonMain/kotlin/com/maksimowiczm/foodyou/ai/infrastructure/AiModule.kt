package com.maksimowiczm.foodyou.ai.infrastructure

import com.maksimowiczm.foodyou.ai.domain.AiPreferences
import com.maksimowiczm.foodyou.ai.domain.NutritionCalculator
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
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.dsl.onClose

fun Module.aiModule() {
    userPreferencesRepositoryOf(::DataStoreAiPreferencesRepository)

    single(named(NutritionCalculatorImpl::class.qualifiedName!!)) {
            HttpClient {
                install(HttpTimeout) { requestTimeoutMillis = 60_000 }
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        }
        .onClose { it?.close() }
    factory(named(OpenAiCompatibleClient::class.qualifiedName!!)) {
        OpenAiCompatibleClient(get(named(NutritionCalculatorImpl::class.qualifiedName!!)))
    }
    factory(named(GeminiClient::class.qualifiedName!!)) {
        GeminiClient(get(named(NutritionCalculatorImpl::class.qualifiedName!!)))
    }
    factory(named(AnthropicClient::class.qualifiedName!!)) {
        AnthropicClient(get(named(NutritionCalculatorImpl::class.qualifiedName!!)))
    }
    factory<NutritionCalculator> {
        NutritionCalculatorImpl(
            preferencesRepository = userPreferencesRepository<AiPreferences>(),
            systemDetails = get(),
            openAiClient = get(named(OpenAiCompatibleClient::class.qualifiedName!!)),
            geminiClient = get(named(GeminiClient::class.qualifiedName!!)),
            anthropicClient = get(named(AnthropicClient::class.qualifiedName!!)),
        )
    }
}

val aiModule = module { aiModule() }