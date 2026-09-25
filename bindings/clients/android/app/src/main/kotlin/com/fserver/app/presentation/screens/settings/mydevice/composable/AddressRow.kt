package com.fserver.app.presentation.screens.settings.mydevice.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import com.fserver.app.presentation.composable.model.localizedName
import com.fserver.app.presentation.designkit.DkMonoCaption
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkTag
import com.fserver.app.presentation.designkit.DkTagStyle
import com.fserver.app.presentation.screens.settings.mydevice.model.MyDeviceState

/** One address the device answers on, with the method that owns it named next to it. */
@Composable
fun AddressRow(
    modifier: Modifier = Modifier,
    address: MyDeviceState.AddressUi,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = DkSpacing.md, vertical = DkSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DkMonoCaption(modifier = Modifier.weight(1f), text = address.address)

        address.transport?.let {
            DkTag(text = stringResource(it.localizedName), style = DkTagStyle.Outline)
        }
    }
}
