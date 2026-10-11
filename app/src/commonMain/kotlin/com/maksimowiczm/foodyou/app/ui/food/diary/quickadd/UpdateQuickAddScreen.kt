package com.maksimowiczm.foodyou.app.ui.food.diary.quickadd

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.ai.domain.AiPreferences
import com.maksimowiczm.foodyou.app.ui.common.utility.LocalEnergyFormatter
import com.maksimowiczm.foodyou.common.compose.extension.LaunchedCollectWithLifecycle
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.fooddiary.domain.entity.ManualDiaryEntryId
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named

@Composable
fun UpdateQuickAddScreen(
    onBack: () -> Unit,
    onSave: () -> Unit,
    id: Long,
    modifier: Modifier = Modifier,
    onOpenAiSettings: () -> Unit = {},
) {
    val viewModel: UpdateQuickAddViewModel = koinViewModel { parametersOf(ManualDiaryEntryId(id)) }
    val energyFormatter = LocalEnergyFormatter.current

    val aiPreferencesRepository: UserPreferencesRepository<AiPreferences> =
        koinInject(named(AiPreferences::class.qualifiedName!!))
    val aiPreferences = aiPreferencesRepository.observe().collectAsStateWithLifecycle(null).value
    val isConfigured = aiPreferences?.isConfigured == true

    val latestOnSave by rememberUpdatedState(onSave)
    LaunchedCollectWithLifecycle(viewModel.uiEvents) {
        when (it) {
            QuickAddUiEvent.Saved -> latestOnSave()
        }
    }

    val entry = viewModel.entry.collectAsStateWithLifecycle().value

    if (entry == null) {
        // TODO loading state
        return
    }

    val formState =
        rememberQuickAddFormState(
            name = entry.name,
            energy = entry.nutritionFacts.energy.value,
            proteins = entry.nutritionFacts.proteins.value,
            carbohydrates = entry.nutritionFacts.carbohydrates.value,
            fats = entry.nutritionFacts.fats.value,
        )

    var showAiResultDialog by rememberSaveable { mutableStateOf(false) }
    val aiState = viewModel.ai.state
    val aiResult = aiState.value.result

    if (showAiResultDialog && aiResult != null) {
        com.maksimowiczm.foodyou.app.ui.food.diary.quickadd.aicalc.AiResultDialog(
            name = aiResult.name,
            result = aiResult,
            onApply = { editedName ->
                formState.applyAiResult(aiResult.copy(name = editedName), energyFormatter)
                viewModel.ai.applyResult()
                showAiResultDialog = false
            },
            onDismiss = { showAiResultDialog = false },
        )
    }

QuickAddScreen(
        onBack = onBack,
        onSave = {
            val name = formState.name.value
            val energy = formState.energy.value?.let(energyFormatter::toKcal) ?: 0.0
            val proteins = formState.proteins.value ?: 0.0
            val carbohydrates = formState.carbohydrates.value ?: 0.0
            val fats = formState.fats.value ?: 0.0

            viewModel.updateEntry(
                name = name,
                energy = energy,
                proteins = proteins,
                carbohydrates = carbohydrates,
                fats = fats,
            )
        },
        modifier = modifier,
        state = formState,
        aiState = viewModel.ai.state,
        aiResult = aiResult,
        onAiDescriptionChange = { viewModel.ai.setDescription(it) },
        onAiAddPhotos = { viewModel.ai.addPhotos(it) },
        onAiRemovePhoto = { viewModel.ai.removePhoto(it) },
        onAiCalculate = { viewModel.ai.calculate() },
        onAiApply = { showAiResultDialog = true },
        onOpenAiSettings = onOpenAiSettings,
        onOpenAiSettings = onOpenAiSettings,
    )
}
