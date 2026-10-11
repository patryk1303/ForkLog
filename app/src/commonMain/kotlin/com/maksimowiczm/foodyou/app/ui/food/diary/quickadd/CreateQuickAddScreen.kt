package com.maksimowiczm.foodyou.app.ui.food.diary.quickadd

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.ai.domain.AiPreferences
import com.maksimowiczm.foodyou.app.ui.common.utility.LocalEnergyFormatter
import com.maksimowiczm.foodyou.common.compose.extension.LaunchedCollectWithLifecycle
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import kotlinx.datetime.LocalDate
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named

@Composable
fun CreateQuickAddScreen(
    onBack: () -> Unit,
    onSave: () -> Unit,
    mealId: Long,
    date: LocalDate,
    modifier: Modifier = Modifier,
    onOpenAiSettings: () -> Unit = {},
) {
    val viewModel: CreateQuickAddViewModel = koinViewModel { parametersOf(date, mealId) }
    val energyFormatter = LocalEnergyFormatter.current

    val aiPreferencesRepository: UserPreferencesRepository<AiPreferences> =
        koinInject(named(AiPreferences::class.qualifiedName!!))
    val aiPreferences = aiPreferencesRepository.observe().collectAsStateWithLifecycle(null).value
    val isConfigured = aiPreferences?.isConfigured == true

    val formState = rememberQuickAddFormState()

    val latestOnSave by rememberUpdatedState(onSave)
    LaunchedCollectWithLifecycle(viewModel.uiEvents) {
        when (it) {
            QuickAddUiEvent.Saved -> latestOnSave()
        }
    }

    val aiState by viewModel.ai.state.collectAsStateWithLifecycle()
    val aiResult = aiState.result

    QuickAddScreen(
        onBack = onBack,
        onSave = {
            val name = formState.name.value
            val energy = formState.energy.value?.let(energyFormatter::toKcal) ?: 0.0
            val proteins = formState.proteins.value ?: 0.0
            val carbohydrates = formState.carbohydrates.value ?: 0.0
            val fats = formState.fats.value ?: 0.0

            viewModel.addEntry(
                name = name,
                energy = energy,
                proteins = proteins,
                carbohydrates = carbohydrates,
                fats = fats,
            )
        },
        modifier = modifier,
        state = formState,
        aiState = aiState,
        aiResult = aiResult,
        isAiConfigured = isConfigured,
        onAiDescriptionChange = { viewModel.ai.setDescription(it) },
        onAiAddPhotos = { viewModel.ai.addPhotos(it) },
        onAiRemovePhoto = { viewModel.ai.removePhoto(it) },
        onAiCalculate = { viewModel.ai.calculate() },
        onAiApply = { editedName ->
            aiResult?.let { result ->
                formState.applyAiResult(result.copy(name = editedName), energyFormatter)
                viewModel.ai.applyResult()
            }
        },
        onAiDismiss = { viewModel.ai.dismissResult() },
        onOpenAiSettings = onOpenAiSettings,
    )
}
