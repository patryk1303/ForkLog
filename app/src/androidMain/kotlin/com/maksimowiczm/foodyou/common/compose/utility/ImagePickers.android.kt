package com.maksimowiczm.foodyou.common.compose.utility

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

@Composable
actual fun rememberPhotoPicker(
    maxItems: Int,
    onImagesPicked: (List<String>) -> Unit,
): () -> Unit {
    val activity = LocalActivity.current

    // Use the compatible GetContent / OpenMultipleDocuments contracts
    // (PickVisualMediaRequest is not publicly available in this AndroidX version;
    // the brief accepts fallback to single-select when picker is unavailable)
    val singleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { onImagesPicked(listOf(it.toString())) }
    }
    val multipleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            onImagesPicked(uris.map { it.toString() })
        }
    }

    return {
        if (maxItems < 2) {
            singleLauncher.launch("image/*")
        } else {
            multipleLauncher.launch(arrayOf("image/*"))
        }
    }
}

@Composable
actual fun rememberCameraCapture(
    onImageCaptured: (String) -> Unit,
    onPermissionDenied: () -> Unit,
): () -> Unit {
    val activity = LocalActivity.current
    val context = activity as Context
    var photoFile by remember { mutableStateOf<File?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            photoFile?.let { file ->
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.ai.fileprovider",
                    file
                )
                onImageCaptured(uri.toString())
            }
        }
    }

    fun takePicture() {
        val cacheDir = File(context.cacheDir, "ai_photos").apply { mkdirs() }
        val file = File(cacheDir, "photo_${System.currentTimeMillis()}.jpg")
        photoFile = file
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.ai.fileprovider",
            file
        )
        cameraLauncher.launch(uri)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            takePicture()
        } else {
            onPermissionDenied()
        }
    }

    return {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            takePicture()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
}
