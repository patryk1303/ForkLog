package com.maksimowiczm.foodyou.app.ui.food.diary.quickadd.aicalc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maksimowiczm.foodyou.ai.domain.NutritionAiResult
import com.maksimowiczm.foodyou.app.ui.common.utility.LocalEnergyFormatter
import com.maksimowiczm.foodyou.common.compose.component.unorderedList
import com.maksimowiczm.foodyou.common.compose.utility.formatClipZeros
import com.maksimowiczm.foodyou.common.domain.food.NutrientValue
import com.maksimowiczm.foodyou.settings.domain.entity.NutrientsOrder
import com.maksimowiczm.foodyou.settings.domain.entity.NutrientsOrderProvider
import foodyou.app.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.pluralStringResource

@Composable
internal fun AiResultDialog(
    name: String,
    result: NutritionAiResult,
    onApply: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val energyFormatter = LocalEnergyFormatter.current
    val nutrientsOrderProvider = NutrientsOrderProvider()
    val nameFieldState = rememberTextFieldState(name)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    onApply(nameFieldState.value)
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
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
            ) {
                OutlinedTextField(
                    state = nameFieldState,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(Res.string.product_name)) },
                    keyboardOptions = androidx.compose.ui.text.input.KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        keyboardType = KeyboardType.Text,
                    ),
                    singleLine = true,
                )

                Spacer(Modifier.height(16.dp))

                val facts = result.nutritionFacts
                val nutrientRows = mutableListOf<Pair<String, String?>>()

                nutrientsOrderProvider.nutrientsOrder.forEach { order ->
                    when (order) {
                        NutrientsOrder.Energy -> {
                            val energy = facts.energy
                            nutrientRows.add(
                                stringResource(Res.string.unit_energy) to energy.value?.let {
                                    energyFormatter.formatEnergy(it, withSuffix = true)
                                }
                            )
                        }
                        NutrientsOrder.Proteins -> {
                            val p = facts.proteins
                            nutrientRows.add(
                                stringResource(Res.string.nutriment_proteins) to p.value?.let {
                                    "${it.formatClipZeros()} ${stringResource(Res.string.unit_gram_short)}"
                                }
                            )
                        }
                        NutrientsOrder.Fats -> {
                            val f = facts.fats
                            nutrientRows.add(
                                stringResource(Res.string.nutriment_fats) to f.value?.let {
                                    "${it.formatClipZeros()} ${stringResource(Res.string.unit_gram_short)}"
                                }
                            )
                        }
                        NutrientsOrder.Carbohydrates -> {
                            val c = facts.carbohydrates
                            nutrientRows.add(
                                stringResource(Res.string.nutriment_carbohydrates) to c.value?.let {
                                    "${it.formatClipZeros()} ${stringResource(Res.string.unit_gram_short)}"
                                }
                            )
                        }
                        NutrientsOrder.SaturatedFats -> {
                            val sf = facts.saturatedFats
                            nutrientRows.add(
                                stringResource(Res.string.nutriment_saturated_fats) to sf.value?.let {
                                    "${it.formatClipZeros()} ${stringResource(Res.string.unit_gram_short)}"
                                }
                            )
                        }
                        NutrientsOrder.Sugars -> {
                            val s = facts.sugars
                            nutrientRows.add(
                                stringResource(Res.string.nutriment_sugars) to s.value?.let {
                                    "${it.formatClipZeros()} ${stringResource(Res.string.unit_gram_short)}"
                                }
                            )
                        }
                        NutrientsOrder.DietaryFiber -> {
                            val df = facts.dietaryFiber
                            nutrientRows.add(
                                stringResource(Res.string.nutriment_fiber) to df.value?.let {
                                    "${it.formatClipZeros()} ${stringResource(Res.string.unit_gram_short)}"
                                }
                            )
                        }
                        NutrientsOrder.Salt -> {
                            val salt = facts.salt
                            nutrientRows.add(
                                stringResource(Res.string.nutriment_salt) to salt.value?.let {
                                    "${it.formatClipZeros()} ${stringResource(Res.string.unit_gram_short)}"
                                }
                            )
                        }
                        else -> Unit
                    }
                }

                Text(
                    text = unorderedList(
                        nutrientRows.map { (label, value) ->
                            "$label: ${value ?: "—"}"
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
    )
}