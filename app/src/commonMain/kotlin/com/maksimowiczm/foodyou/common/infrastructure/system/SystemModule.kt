package com.maksimowiczm.foodyou.common.infrastructure.system

import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.system.SystemDetails
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind

expect fun Module.systemDetailsDefinition()
expect val systemModule: Module