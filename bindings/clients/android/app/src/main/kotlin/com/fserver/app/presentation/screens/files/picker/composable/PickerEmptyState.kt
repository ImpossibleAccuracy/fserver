package com.fserver.app.presentation.screens.files.picker.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.fserver.app.R
import com.fserver.app.presentation.designkit.DkCaption
import com.fserver.app.presentation.designkit.DkMonoCaption
import com.fserver.app.presentation.designkit.DkSpacing

@Composable
fun PickerEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(DkSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(DkSpacing.sm, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DkCaption(text = stringResource(R.string.picker_empty_title))
        DkMonoCaption(text = stringResource(R.string.picker_empty_hint))
    }
}