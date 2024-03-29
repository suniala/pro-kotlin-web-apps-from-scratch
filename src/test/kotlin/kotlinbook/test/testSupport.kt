package kotlinbook.test

import kotlinbook.config.createAndMigrateDataSource
import kotlinbook.config.createAppConfig
import kotliquery.TransactionalSession
import kotliquery.sessionOf

val testAppConfig = createAppConfig("test")
val testDataSource = createAndMigrateDataSource(testAppConfig)

fun testTx(handler: (dbSess: TransactionalSession) -> Unit) {
    sessionOf(
        testDataSource,
        returnGeneratedKey = true
    ).use { dbSess ->
        dbSess.transaction { dbSessTx ->
            try {
                handler(dbSessTx)
            } finally {
                dbSessTx.connection.rollback()
            }
        }
    }
}
