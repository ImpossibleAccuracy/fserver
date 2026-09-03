package com.fserver.app.presentation.screens.source.upload

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator
import com.fserver.app.presentation.screens.source.shared.SourceFlowParent
import com.fserver.app.presentation.screens.source.shared.closeSourceFlow
import com.fserver.app.presentation.screens.source.shared.isAnsweredSourceFlowScreen
import com.fserver.app.presentation.screens.source.shared.popToSourcePick
import com.fserver.app.presentation.screens.source.shared.sourceFlowViewModel

fun EntryProviderScope<Destination>.sourceUploadEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Source.Upload>(metadata = SourceFlowParent) { _ ->
        val flow = sourceFlowViewModel { navigator.popToSourcePick() } ?: return@entry

        SourceUploadScreen(
            handler = flow.upload,
            navigateToDone = {
                navigator.navigate(
                    screen = Destination.Source.Done,
                    dropping = { it is Destination.Source.Upload },
                )
            },
            closeFlow = { navigator.closeSourceFlow() },
        )
    }
}
