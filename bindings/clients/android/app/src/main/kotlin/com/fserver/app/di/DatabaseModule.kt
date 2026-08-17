package com.fserver.app.di

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.fserver.app.database.FServerDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

private const val DATABASE_NAME = "fserver.db"

/**
 * Driver and database live for the whole process — no `close()`, same reason as [coreModule].
 */
internal val databaseModule = module {
    single<SqlDriver> {
        AndroidSqliteDriver(
            schema = FServerDatabase.Schema,
            context = androidContext(),
            name = DATABASE_NAME,
        )
    }

    single { FServerDatabase(get()) }
}
