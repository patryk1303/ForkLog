package com.maksimowiczm.foodyou.common.compose.utility

import androidx.compose.runtime.Composable

@Composable
expect fun rememberPhotoPicker(
    maxItems: Int,
    onImagesPicked: (List<String>) -> Unit,
): () -> Unit

@Composable
expect fun rememberCameraCapture(
    onImageCaptured: (String) -> Unit,
    onPermissionDenied: () -> Unit,
): () -> Unit
