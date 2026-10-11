package com.maksimowiczm.foodyou.app.ui.food.diary.quickadd.aicalc

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.maksimowiczm.foodyou.ai.domain.NutritionAiResult
import com.maksimowiczm.foodyou.app.ui.common.utility.LocalEnergyFormatter
import com.maksimowiczm.foodyou.common.compose.component.unorderedList
import com.maksimowiczm.foodyou.common.compose.utility.formatClipZeros
import foodyou.app.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AiResultDialog(
    name: String,
    result: NutritionAiResult,
    onApply: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val energyFormatter = LocalEnergyFormatter.current
    var editedName by rememberSaveable(result) { mutableStateOf(name) }

    val facts = result.nutritionFacts
    val gramUnit = stringResource(Res.string.unit_gram_short)
    val energyString =
        facts.energy.value?.let { energyFormatter.formatEnergy(it, withSuffix = true) } ?: "—"
    val proteinsString =
        facts.proteins.value?.let { "${it.formatClipZeros()} $gramUnit" } ?: "—"
    val carbohydratesString =
        facts.carbohydrates.value?.let { "${it.formatClipZeros()} $gramUnit" } ?: "—"
    val fatsString =
        facts.fats.value?.let { "${it.formatClipZeros()} $gramUnit" } ?: "—"
    val saturatedFatsString =
        facts.saturatedFats.value?.let { "${it.formatClipZeros()} $gramUnit" } ?: "—"
    val sugarsString =
        facts.sugars.value?.let { "${it.formatClipZeros()} $gramUnit" } ?: "—"
    val fiberString =
        facts.dietaryFiber.value?.let { "${it.formatClipZeros()} $gramUnit" } ?: "—"
    val saltString =
        facts.salt.value?.let { "${it.formatClipZeros()} $gramUnit" } ?: "—"

    val items =
        listOf(
            "${stringResource(Res.string.unit_energy)}: $energyString",
            "${stringResource(Res.string.nutriment_proteins)}: $proteinsString",
            "${stringResource(Res.string.nutriment_carbohydrates)}: $carbohydratesString",
            "${stringResource(Res.string.nutriment_fats)}: $fatsString",
            "${stringResource(Res.string.nutriment_saturated_fats)}: $saturatedFatsString",
            "${stringResource(Res.string.nutriment_sugars)}: $sugarsString",
            "${stringResource(Res.string.nutriment_fiber)}: $fiberString",
            "${stringResource(Res.string.nutriment_salt)}: $saltString",
        )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    onApply(editedName)
                    onDismiss()
                },
            ) {
                Text(stringResource(Res.string.action_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
        title = { Text(stringResource(Res.string.headline_ai_result)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = editedName,
                    onValueChange = { editedName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(Res.string.product_name)) },
                    keyboardOptions =
                        KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            keyboardType = KeyboardType.Text,
                        ),
                    singleLine = true,
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    text = unorderedList(items),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
    )
}