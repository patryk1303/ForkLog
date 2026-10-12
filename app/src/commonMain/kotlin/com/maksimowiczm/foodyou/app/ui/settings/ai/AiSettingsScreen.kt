package com.maksimowiczm.foodyou.app.ui.settings.ai

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.ai.domain.AiPreferences
import com.maksimowiczm.foodyou.ai.domain.AiProvider
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import com.maksimowiczm.foodyou.common.compose.extension.add
import foodyou.app.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AiSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: AiSettingsViewModel = koinViewModel()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val isFetchingModels by viewModel.isFetchingModels.collectAsStateWithLifecycle()
    val fetchModelsError by viewModel.fetchModelsError.collectAsStateWithLifecycle()
    val isTestingConnection by viewModel.isTestingConnection.collectAsStateWithLifecycle()
    val testConnectionSuccess by viewModel.testConnectionSuccess.collectAsStateWithLifecycle()
    val testConnectionError by viewModel.testConnectionError.collectAsStateWithLifecycle()

    AiSettingsScreen(
        onBack = onBack,
        preferences = preferences,
        models = models,
        isFetchingModels = isFetchingModels,
        fetchModelsError = fetchModelsError,
        isTestingConnection = isTestingConnection,
        testConnectionSuccess = testConnectionSuccess,
        testConnectionError = testConnectionError,
        onProviderSelect = viewModel::selectProvider,
        onFetchModels = viewModel::fetchModels,
        onTestConnection = viewModel::testConnection,
        onSave = viewModel::saveConfig,
        modifier = modifier,
    )
}

@Composable
private fun AiSettingsScreen(
    onBack: () -> Unit,
    preferences: AiPreferences?,
    models: List<String>,
    isFetchingModels: Boolean,
    fetchModelsError: String?,
    isTestingConnection: Boolean,
    testConnectionSuccess: Boolean,
    testConnectionError: String?,
    onProviderSelect: (AiProvider) -> Unit,
    onFetchModels: (provider: AiProvider, apiKey: String, baseUrl: String?) -> Unit,
    onTestConnection: (provider: AiProvider, apiKey: String, model: String, baseUrl: String?) -> Unit,
    onSave: (provider: AiProvider, apiKey: String?, model: String?, baseUrl: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val selectedProvider = preferences?.selectedProvider
    val config = selectedProvider?.let { preferences?.configOf(it) }

    var apiKey by rememberSaveable(selectedProvider) { mutableStateOf(config?.apiKey.orEmpty()) }
    var model by rememberSaveable(selectedProvider) { mutableStateOf(config?.model.orEmpty()) }
    var baseUrl by rememberSaveable(selectedProvider) { mutableStateOf(config?.baseUrl.orEmpty()) }

    val isBaseUrlValid = baseUrl.startsWith("http://") || baseUrl.startsWith("https://")
    val canSave =
        selectedProvider != null && (selectedProvider != AiProvider.Custom || isBaseUrlValid)

    val canFetchModels =
        selectedProvider != null &&
            apiKey.isNotBlank() &&
            (selectedProvider == AiProvider.OpenAi ||
                selectedProvider == AiProvider.OpenRouter ||
                (selectedProvider == AiProvider.Custom && isBaseUrlValid))

    val canTestConnection =
        selectedProvider != null &&
            apiKey.isNotBlank() &&
            model.isNotBlank() &&
            (selectedProvider != AiProvider.Custom || isBaseUrlValid)

    Scaffold(
        modifier = modifier,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(Res.string.headline_ai_settings)) },
                navigationIcon = { ArrowBackIconButton(onBack) },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier =
                Modifier.fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = paddingValues.add(vertical = 8.dp),
        ) {
            item {
                Text(
                    text = stringResource(Res.string.headline_ai_provider),
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            item {
                Column {
                    AiProvider.entries.forEach { provider ->
                        ProviderOption(
                            provider = provider,
                            selected = provider == selectedProvider,
                            onClick = { onProviderSelect(provider) },
                        )
                    }
                }
            }

            if (selectedProvider != null) {
                item {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text(stringResource(Res.string.headline_api_key)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                item {
                    var modelMenuExpanded by remember { mutableStateOf(false) }
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        placeholder = selectedProvider.defaultModel?.let { { Text(it) } },
                        singleLine = true,
                        trailingIcon = {
                            if (models.isNotEmpty()) {
                                Box {
                                    IconButton(onClick = { modelMenuExpanded = true }) {
                                        Icon(
                                            imageVector = Icons.Outlined.ArrowDropDown,
                                            contentDescription = null,
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = modelMenuExpanded,
                                        onDismissRequest = { modelMenuExpanded = false },
                                    ) {
                                        models.forEach { fetchedModel ->
                                            DropdownMenuItem(
                                                text = { Text(fetchedModel) },
                                                onClick = {
                                                    model = fetchedModel
                                                    modelMenuExpanded = false
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (selectedProvider == AiProvider.Custom) {
                    item {
                        OutlinedTextField(
                            value = baseUrl,
                            onValueChange = { baseUrl = it },
                            label = { Text(stringResource(Res.string.headline_base_url)) },
                            isError = !isBaseUrlValid,
                            supportingText = {
                                if (!isBaseUrlValid) {
                                    Text(stringResource(Res.string.error_invalid_base_url))
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                if (canFetchModels || canTestConnection) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (canFetchModels) {
                                        FilledTonalButton(
                                            onClick = { onFetchModels(selectedProvider, apiKey, baseUrl) },
                                            enabled = !isFetchingModels,
                                        ) {
                                            if (isFetchingModels) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(18.dp),
                                                    strokeWidth = 2.dp,
                                                )
                                            } else {
                                                Text(stringResource(Res.string.action_fetch_models))
                                            }
                                        }
                                    }
                                    if (canTestConnection) {
                                        OutlinedButton(
                                            onClick = {
                                                onTestConnection(selectedProvider, apiKey, model, baseUrl)
                                            },
                                            enabled = !isTestingConnection,
                                        ) {
                                            if (isTestingConnection) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(18.dp),
                                                    strokeWidth = 2.dp,
                                                )
                                            } else {
                                                Text(stringResource(Res.string.action_test_connection))
                                            }
                                        }
                                    }
                                }
                            }
                            if (fetchModelsError != null) {
                                Text(
                                    text = fetchModelsError,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            if (testConnectionSuccess) {
                                Text(
                                    text = stringResource(Res.string.message_test_connection_success),
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            if (testConnectionError != null) {
                                Text(
                                    text =
                                        stringResource(
                                            Res.string.error_test_connection_failed,
                                            testConnectionError,
                                        ),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }

                item {
                    Card(
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                    ) {
                        Text(
                            text = stringResource(Res.string.description_ai_privacy),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }

                item {
                    FilledTonalButton(
                        onClick = { onSave(selectedProvider, apiKey, model, baseUrl) },
                        enabled = canSave,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(Res.string.action_save))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderOption(
    provider: AiProvider,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClick = onClick,
                )
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = null, interactionSource = interactionSource)
        Text(
            text = stringResource(provider.labelStringResource()),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun AiProvider.labelStringResource(): StringResource =
    when (this) {
        AiProvider.OpenAi -> Res.string.provider_openai
        AiProvider.Gemini -> Res.string.provider_google_gemini
        AiProvider.Anthropic -> Res.string.provider_anthropic
        AiProvider.OpenRouter -> Res.string.provider_openrouter
        AiProvider.Custom -> Res.string.provider_custom
    }
