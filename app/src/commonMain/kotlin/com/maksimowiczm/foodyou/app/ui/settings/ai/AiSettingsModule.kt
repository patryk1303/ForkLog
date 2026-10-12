package com.maksimowiczm.foodyou.app.ui.settings.ai

import com.maksimowiczm.foodyou.ai.infrastructure.AiProtocolClient
import com.maksimowiczm.foodyou.common.infrastructure.koin.userPreferencesRepository
import io.ktor.client.HttpClient
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.core.module.dsl.viewModel

internal fun Module.aiSettings() {
    viewModel {
        AiSettingsViewModel(
            preferencesRepository = userPreferencesRepository(),
            httpClient = get<HttpClient>(named("ai:http-client")),
            openAiClient = get<AiProtocolClient>(named("ai:openai-client")),
            geminiClient = get<AiProtocolClient>(named("ai:gemini-client")),
            anthropicClient = get<AiProtocolClient>(named("ai:anthropic-client")),
        )
    }
}
