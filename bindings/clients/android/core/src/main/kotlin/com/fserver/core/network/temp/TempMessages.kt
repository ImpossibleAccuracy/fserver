package com.fserver.core.network.temp

internal sealed interface TempMessages {
    data object Hello : TempMessages
}
