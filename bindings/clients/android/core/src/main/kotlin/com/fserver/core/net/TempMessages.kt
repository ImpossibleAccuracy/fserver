package com.fserver.core.net

internal sealed interface TempMessages {
    data object Hello : TempMessages
}
