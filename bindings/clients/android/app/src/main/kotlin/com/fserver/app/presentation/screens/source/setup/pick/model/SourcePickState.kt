package com.fserver.app.presentation.screens.source.setup.pick.model

import com.fserver.app.presentation.screens.source.shared.model.SourceKindUi

/**
 * Screen 0 of the send flow.
 *
 * The two mass-market sources are cards; the rare and dangerous one sits behind "More", which
 * is the whole state this screen has.
 */
data class SourcePickState(
    val moreExpanded: Boolean = false,
    val unavailable: Set<SourceKindUi> = emptySet(),
) {
    val primary: List<SourceKindUi> = listOf(SourceKindUi.Media, SourceKindUi.Folder)

    // TODO: drop SourceKindUi.AppStorage before production - it is a development source.
    val behindMore: List<SourceKindUi> =
        listOf(SourceKindUi.WholeDevice, SourceKindUi.AppStorage)
}
