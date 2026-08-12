package com.fserver.core.net

sealed interface TempMessages {
    data object Hello : TempMessages
}
