package com.maksimowiczm.foodyou.app.ui.food.diary.quickadd.aicalc

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.clip
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuAnchor
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maksimowiczm.foodyou.ai.domain.AiCalculationException
import com.maksimowiczm.foodyou.ai.domain.MAX_AI_PHOTOS
import com.maksimowiczm.foodyou.app.ui.food.diary.quickadd.AiPhoto
import com.maksimowiczm.foodyou.app.ui.food.diary.quickadd.QuickAddAiState
import com.maksimowiczm.foodyou.app.ui.food.diary.quickadd.QuickAddAiState.Status
import com.maksimowiczm.foodyou.app.ui.common.component.Chip
import com.maksimowiczm.foodyou.common.compose.component.unorderedList
import com.maksimowiczm.foodyou.common.compose.utility.rememberCameraCapture
import com.maksimowiczm.foodyou.common.compose.utility.rememberPhotoPicker
import foodyou.app.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.pluralStringResource

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
internal fun QuickAddAiSection(
    state: QuickAddAiState,
    isConfigured: Boolean,
    onDescriptionChange: (String) -> Unit,
    onAddPhotos: (List<String>) -> Unit,
    onRemovePhoto: (AiPhoto) -> Unit,
    onCalculate: () -> Unit,
    onOpenAiSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var permissionDenied by remember { mutableStateOf(false) }
    val descriptionFieldState = rememberTextFieldState(state.description)

    val photoPickerLauncher = rememberPhotoPicker(
        maxItems = MAX_AI_PHOTOS,
        onImagesPicked = onAddPhotos,
    )
    val cameraCaptureLauncher = rememberCameraCapture(
        onImageCaptured = { uri: String -> onAddPhotos(listOf(uri)) },
        onPermissionDenied = { permissionDenied = true },
    )

    val canShowAddTile = state.photos.size < MAX_AI_PHOTOS
    val canCalculate = state.canCalculate && state.status !is QuickAddAiState.Status.Loading
    val showNotConfiguredError = (state.status is QuickAddAiState.Status.Error) && (state.status as QuickAddAiState.Status.Error).exception is AiCalculationException.NotConfigured
    val errorException = if (state.status is QuickAddAiState.Status.Error) (state.status as QuickAddAiState.Status.Error).exception else null

    Column(modifier = modifier) {
        OutlinedTextField(
            state = descriptionFieldState,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(Res.string.description_ai)) },
            supportingText = { Text(stringResource(Res.string.description_ai_calculation), style = MaterialTheme.typography.bodySmall) },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                keyboardType = KeyboardType.Text,
            ),
            singleLine = false,
            maxLines = 4,
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.photos.forEach { photo ->
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp)),
                ) {
                    photo.thumbnail?.let { bitmap ->
                        Image(
                            bitmap = bitmap,
                            contentScale = ContentScale.Crop,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    IconButton(
                        onClick = { onRemovePhoto(photo) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(Res.string.action_remove_a_photo),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            if (canShowAddTile) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton(
                        onClick = photoPickerLauncher,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Image,
                            contentDescription = null,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.action_choose_photos))
                    }
                    FilledTonalButton(
                        onClick = cameraCaptureLauncher,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PhotoCamera,
                            contentDescription = null,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.action_take_photo))
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (!canShowAddTile) {
            Text(
                text = stringResource(Res.string.error_ai_max_photos),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (permissionDenied) {
            Text(
                text = stringResource(Res.string.error_ai_camera_permission),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(
                onClick = {
                    if (!isConfigured) {
                        onOpenAiSettings()
                    } else {
                        onCalculate()
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                if (state.status is QuickAddAiState.Status.Loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Calculate,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.action_ai_calculate))
                }
            }

            if (showNotConfiguredError) {
                TextButton(
                    onClick = onOpenAiSettings,
                    modifier = Modifier.align(Alignment.CenterVertically),
                ) {
                    Text(stringResource(Res.string.action_ai_open_settings))
                }
            }
        }

        when (errorException) {
            is AiCalculationException.NotConfigured -> {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.error_ai_not_configured),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = onOpenAiSettings) {
                        Text(stringResource(Res.string.action_ai_open_settings))
                    }
                }
            }
            is AiCalculationException.Unauthorized -> {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(Res.string.error_ai_unauthorized),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            is AiCalculationException.RateLimited -> {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(Res.string.error_ai_rate_limited),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            is AiCalculationException.ServerError -> {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(Res.string.error_ai_server),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            is AiCalculationException.Network -> {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(Res.string.error_ai_network),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            is AiCalculationException.InvalidResponse -> {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(Res.string.error_ai_invalid_response),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            else -> Unit
        }

        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
        ) {
            Text(
                text = stringResource(Res.string.description_ai_privacy),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}