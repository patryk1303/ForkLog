package com.maksimowiczm.foodyou.common.infrastructure.system

import com.maksimowiczm.foodyou.common.system.SystemDetails
import com.maksimowiczm.foodyou.common.infrastructure.system.DateProviderImpl
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

actual fun Module.systemDetailsDefinition() {
    singleOf(::AndroidSystemDetails).bind<SystemDetails>()
}

actual val systemModule: Module = module {
    systemDetailsDefinition()
    factoryOf(::DateProviderImpl).bind<DateProvider>()
}