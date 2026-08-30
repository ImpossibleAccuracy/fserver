package com.fserver.app.presentation.screens.source.pick.model

import com.fserver.app.presentation.screens.source.shared.composable.SourceKindUi

/**
 * Screen 0 of the send flow.
 *
 * The two mass-market sources are cards; the rare and dangerous one sits behind "More", which
 * is the whole state this screen has.
 */
data class SourcePickState(
    val moreExpanded: Boolean = false,
) {
    val primary: List<SourceKindUi> = listOf(SourceKindUi.Media, SourceKindUi.Folder)

    val behindMore: List<SourceKindUi> = listOf(SourceKindUi.WholeDevice)
}
