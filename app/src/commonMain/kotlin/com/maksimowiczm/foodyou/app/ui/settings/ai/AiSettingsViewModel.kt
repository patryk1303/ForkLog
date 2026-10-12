package com.maksimowiczm.foodyou.app.ui.settings.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.ai.domain.AiPreferences
import com.maksimowiczm.foodyou.ai.domain.AiProvider
import com.maksimowiczm.foodyou.ai.domain.AiProviderConfig
import com.maksimowiczm.foodyou.ai.infrastructure.AiProtocolClient
import com.maksimowiczm.foodyou.ai.infrastructure.ensureSuccess
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

internal class AiSettingsViewModel(
    private val preferencesRepository: UserPreferencesRepository<AiPreferences>,
    private val httpClient: HttpClient,
    private val openAiClient: AiProtocolClient,
    private val geminiClient: AiProtocolClient,
    private val anthropicClient: AiProtocolClient,
) : ViewModel() {

    val preferences =
        preferencesRepository
            .observe()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null,
            )

    val models = MutableStateFlow<List<String>>(emptyList())
    val isFetchingModels = MutableStateFlow(false)
    val fetchModelsError = MutableStateFlow<String?>(null)

    val isTestingConnection = MutableStateFlow(false)
    val testConnectionSuccess = MutableStateFlow(false)
    val testConnectionError = MutableStateFlow<String?>(null)

    fun selectProvider(provider: AiProvider) {
        viewModelScope.launch {
            preferencesRepository.update { copy(selectedProvider = provider) }
            models.value = emptyList()
            fetchModelsError.value = null
            testConnectionSuccess.value = false
            testConnectionError.value = null
        }
    }

    fun fetchModels(provider: AiProvider, apiKey: String, baseUrl: String?) {
        viewModelScope.launch {
            isFetchingModels.value = true
            fetchModelsError.value = null
            try {
                val resolvedBaseUrl =
                    when (provider) {
                        AiProvider.OpenAi -> AiProvider.OpenAi.defaultBaseUrl
                        AiProvider.OpenRouter -> AiProvider.OpenRouter.defaultBaseUrl
                        AiProvider.Custom -> baseUrl?.takeIf { it.isNotBlank() }
                        else -> null
                    } ?: throw IllegalArgumentException("Invalid base URL")

                val response =
                    httpClient.get("${resolvedBaseUrl.trimEnd('/')}/models") {
                        header(HttpHeaders.Authorization, "Bearer $apiKey")
                        contentType(ContentType.Application.Json)
                    }
                response.ensureSuccess()

                val body = response.body<ModelsResponse>()
                models.value = body.data.map { it.id }.sorted()
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                fetchModelsError.value = e.message ?: "Failed to fetch models"
            } finally {
                isFetchingModels.value = false
            }
        }
    }

    fun testConnection(provider: AiProvider, apiKey: String, model: String, baseUrl: String?) {
        viewModelScope.launch {
            isTestingConnection.value = true
            testConnectionSuccess.value = false
            testConnectionError.value = null
            try {
                val client =
                    when (provider) {
                        AiProvider.OpenAi,
                        AiProvider.OpenRouter,
                        AiProvider.Custom -> openAiClient
                        AiProvider.Gemini -> geminiClient
                        AiProvider.Anthropic -> anthropicClient
                    }
                val config =
                    AiProviderConfig(
                        apiKey = apiKey.takeIf { it.isNotBlank() },
                        model = model.takeIf { it.isNotBlank() },
                        baseUrl =
                            if (provider == AiProvider.Custom) {
                                baseUrl?.takeIf { it.isNotBlank() }
                            } else {
                                provider.defaultBaseUrl
                            },
                    )
                client.complete(
                    system = "You are a helpful assistant. Reply with 'OK'.",
                    user = "Test connection",
                    images = emptyList(),
                    config = config,
                )
                testConnectionSuccess.value = true
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                testConnectionError.value = e.message ?: "Unknown error"
            } finally {
                isTestingConnection.value = false
            }
        }
    }

    fun saveConfig(provider: AiProvider, apiKey: String?, model: String?, baseUrl: String?) {
        viewModelScope.launch {
            preferencesRepository.update {
                val config =
                    AiProviderConfig(
                        apiKey = apiKey?.takeIf { it.isNotBlank() },
                        model = model?.takeIf { it.isNotBlank() },
                        baseUrl =
                            if (provider == AiProvider.Custom) {
                                baseUrl?.takeIf { it.isNotBlank() }
                            } else {
                                configOf(provider).baseUrl
                            },
                    )

                when (provider) {
                    AiProvider.OpenAi -> copy(openAi = config)
                    AiProvider.Gemini -> copy(gemini = config)
                    AiProvider.Anthropic -> copy(anthropic = config)
                    AiProvider.OpenRouter -> copy(openRouter = config)
                    AiProvider.Custom -> copy(custom = config)
                }
            }
        }
    }

    @Serializable
    private data class ModelsResponse(val data: List<ModelItem> = emptyList()) {
        @Serializable
        data class ModelItem(val id: String)
    }
}
