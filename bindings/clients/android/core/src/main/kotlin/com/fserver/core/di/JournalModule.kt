package com.fserver.core.di

import com.fserver.core.journal.ActivityJournal
import com.fserver.core.journal.impl.JournalWriter
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/** The activity journal: the engine's writer, and what the host reads. */
internal val journalModule = module {
    singleOf(::JournalWriter)
    singleOf(::ActivityJournal)
}
