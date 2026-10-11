package com.maksimowiczm.foodyou.app.ui.settings.ai

import com.maksimowiczm.foodyou.common.infrastructure.koin.userPreferencesRepository
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel

internal fun Module.aiSettings() {
    viewModel { AiSettingsViewModel(preferencesRepository = userPreferencesRepository()) }
}
