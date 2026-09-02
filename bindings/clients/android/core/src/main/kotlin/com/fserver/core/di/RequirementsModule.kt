package com.fserver.core.di

import com.fserver.core.requirement.RequirementsChecker
import com.fserver.core.requirement.impl.RequirementsCheckerImpl
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

/** What the OS still demands before an operation can run. */
internal val requirementsModule = module {
    singleOf(::RequirementsCheckerImpl) bind RequirementsChecker::class
}
