package com.fserver.app.domain.oneshot

import com.fserver.core.oneshot.model.OneShotTransfer

/** An incoming offer nobody on this side has answered yet. */
val OneShotTransfer.isAwaitingAnswer: Boolean
    get() = direction is OneShotTransfer.Direction.Incoming && status == OneShotTransfer.Status.Pending
