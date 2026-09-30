package com.fserver.core.di

import com.fserver.core.oneshot.OneShotTransfersController
import com.fserver.core.oneshot.impl.OneShotExchange
import com.fserver.core.oneshot.impl.OneShotOutbox
import com.fserver.core.oneshot.impl.OneShotSender
import com.fserver.core.sync.server.handler.upload.oneshot.OneShotStaging
import com.fserver.core.sync.server.handler.upload.oneshot.OneShotUploadTarget
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/** One-shot transfers, outside any source. Their bytes go through sync's upload, under their own key. */
internal val oneShotModule = module {
    singleOf(::OneShotStaging)
    singleOf(::OneShotOutbox)
    singleOf(::OneShotSender)
    singleOf(::OneShotExchange)
    singleOf(::OneShotUploadTarget)
    singleOf(::OneShotTransfersController)
}
