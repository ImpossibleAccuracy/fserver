package com.fserver.app.presentation.screens.transfers

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination

fun EntryProviderScope<Destination>.transfersEntry() {
    entry<Destination.Transfers> {
        TransfersScreen()
    }
}
