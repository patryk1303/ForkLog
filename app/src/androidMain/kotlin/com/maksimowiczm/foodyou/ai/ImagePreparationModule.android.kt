package com.maksimowiczm.foodyou.ai

import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module

actual fun Module.imagePreparationDefinition() {
    factory<ImagePreparation> { AndroidImagePreparation(androidContext()) }
}
