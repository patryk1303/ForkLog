package com.maksimowiczm.foodyou.ai.infrastructure

import com.maksimowiczm.foodyou.common.infrastructure.koin.userPreferencesRepositoryOf
import org.koin.core.module.Module
import org.koin.dsl.module

fun Module.aiModule() {
    userPreferencesRepositoryOf(::DataStoreAiPreferencesRepository)
}

val aiModule = module { aiModule() }
