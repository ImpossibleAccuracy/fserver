package com.fserver.app.presentation.screens.files.editor

import androidx.compose.runtime.getValue
import com.fserver.app.presentation.composable.ObserveEffects
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.attafitamim.krop.core.crop.CropState
import com.attafitamim.krop.core.crop.LocalCropperStyle
import com.attafitamim.krop.core.crop.RectCropShape
import com.attafitamim.krop.core.crop.cropState
import com.attafitamim.krop.core.crop.cropperStyle
import com.attafitamim.krop.core.images.ImageBitmapSrc
import com.attafitamim.krop.ui.CropperPreview
import com.fserver.app.R
import com.fserver.app.presentation.designkit.DkActionBar
import com.fserver.app.presentation.designkit.DkGhostButton
import com.fserver.app.presentation.designkit.DkIconButton
import com.fserver.app.presentation.designkit.DkInlineSpinner
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSecondaryButton
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.screens.files.editor.model.ImageEditorIntent
import com.fserver.app.presentation.screens.files.editor.model.ImageEditorState
import com.fserver.app.presentation.screens.files.editor.model.ImageEditorUiEffect
import com.fserver.app.presentation.theme.FServerTheme
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ImageEditorScreen(
    modifier: Modifier = Modifier,
    key: Destination.Files.ImageEditor,
    viewModel: ImageEditorViewModel = koinViewModel { parametersOf(key) },
    navigateUp: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveEffects(viewModel.uiEffects) { effect ->
        when (effect) {
            ImageEditorUiEffect.NavigateBack -> navigateUp()
        }
    }

    ImageEditorScreenContent(
        modifier = modifier,
        state = state,
        onIntent = viewModel::onIntent,
        navigateUp = navigateUp,
    )
}

@Composable
private fun ImageEditorScreenContent(
    modifier: Modifier = Modifier,
    state: ImageEditorState,
    onIntent: (ImageEditorIntent) -> Unit,
    navigateUp: () -> Unit,
) {
    val saving = state.status == ImageEditorState.StatusUi.Saving

    BackHandler(enabled = saving) {}

    DkScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            DkTopBar(
                title = state.fileName.ifEmpty { stringResource(R.string.image_editor_title) },
                onBack = { if (!saving) navigateUp() },
                actions = {
                    DkGhostButton(
                        text = stringResource(R.string.action_save),
                        enabled = state.canEdit && state.hasChanges,
                        onClick = { onIntent(ImageEditorIntent.SaveRequested) },
                    )
                }
            )
        },
        bottomBar = {
            if (state.crop != null) {
                EditorActionBar(state = state, onIntent = onIntent)
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            when (state.status) {
                ImageEditorState.StatusUi.Loading -> DkInlineSpinner()

                ImageEditorState.StatusUi.Failed -> LoadFailed(
                    onRetry = { onIntent(ImageEditorIntent.RetryRequested) },
                )

                ImageEditorState.StatusUi.Ready,
                ImageEditorState.StatusUi.Saving -> state.crop?.let { crop ->
                    Cropper(
                        modifier = Modifier.fillMaxSize(),
                        crop = crop,
                        saving = saving,
                    )
                }
            }
        }
    }
}

@Composable
private fun Cropper(
    modifier: Modifier = Modifier,
    crop: CropState,
    saving: Boolean,
) {
    val style = rememberEditorCropStyle()

    Box(
        modifier = modifier
            .clipToBounds()
            .background(style.backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalCropperStyle provides style) {
            CropperPreview(modifier = Modifier.fillMaxSize(), state = crop)
        }

        if (saving) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center,
            ) {
                DkInlineSpinner()
            }
        }
    }
}

@Composable
private fun rememberEditorCropStyle() = MaterialTheme.colorScheme.primary.let { accent ->
    remember(accent) {
        cropperStyle(
            backgroundColor = Color.Black,
            rectColor = accent,
            shapes = listOf(RectCropShape),
            aspects = emptyList(),
        )
    }
}

@Composable
private fun EditorActionBar(
    modifier: Modifier = Modifier,
    state: ImageEditorState,
    onIntent: (ImageEditorIntent) -> Unit,
) {
    DkActionBar(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            DkIconButton(
                onClick = { onIntent(ImageEditorIntent.RotateLeft) },
                icon = Icons.AutoMirrored.Filled.RotateLeft,
                enabled = state.canEdit,
                contentDescription = stringResource(R.string.image_editor_rotate_left),
            )
            DkIconButton(
                onClick = { onIntent(ImageEditorIntent.RotateRight) },
                icon = Icons.AutoMirrored.Filled.RotateRight,
                enabled = state.canEdit,
                contentDescription = stringResource(R.string.image_editor_rotate_right),
            )
            DkIconButton(
                onClick = { onIntent(ImageEditorIntent.FlipHorizontal) },
                icon = Icons.Default.Flip,
                enabled = state.canEdit,
                contentDescription = stringResource(R.string.image_editor_flip),
            )
            DkIconButton(
                onClick = { onIntent(ImageEditorIntent.Reset) },
                icon = Icons.Default.Restore,
                enabled = state.canEdit && state.hasChanges,
                contentDescription = stringResource(R.string.image_editor_reset),
            )
        }
    }
}

@Composable
private fun LoadFailed(
    modifier: Modifier = Modifier,
    onRetry: () -> Unit,
) {
    Column(
        modifier = modifier.padding(horizontal = DkSpacing.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DkSpacing.lg),
    ) {
        Text(
            text = stringResource(R.string.image_editor_load_failed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        DkSecondaryButton(
            text = stringResource(R.string.action_retry),
            onClick = onRetry,
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun ImageEditorScreenPreview() {
    FServerTheme {
        ImageEditorScreenContent(
            state = ImageEditorState(
                fileName = "IMG_2041.jpg",
                status = ImageEditorState.StatusUi.Ready,
                crop = cropState(ImageBitmapSrc(ImageBitmap(400, 300))),
            ),
            onIntent = {},
            navigateUp = {},
        )
    }
}

@Preview(name = "Failed", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun ImageEditorScreenFailedPreview() {
    FServerTheme {
        ImageEditorScreenContent(
            state = ImageEditorState(
                fileName = "IMG_2041.jpg",
                status = ImageEditorState.StatusUi.Failed,
            ),
            onIntent = {},
            navigateUp = {},
        )
    }
}
