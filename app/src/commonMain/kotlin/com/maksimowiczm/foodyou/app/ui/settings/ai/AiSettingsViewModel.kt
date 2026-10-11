package com.maksimowiczm.foodyou.app.ui.settings.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.ai.domain.AiPreferences
import com.maksimowiczm.foodyou.ai.domain.AiProvider
import com.maksimowiczm.foodyou.ai.domain.AiProviderConfig
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal class AiSettingsViewModel(
    private val preferencesRepository: UserPreferencesRepository<AiPreferences>
) : ViewModel() {

    val preferences =
        preferencesRepository
            .observe()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null,
            )

    fun selectProvider(provider: AiProvider) {
        viewModelScope.launch {
            preferencesRepository.update { copy(selectedProvider = provider) }
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
}
