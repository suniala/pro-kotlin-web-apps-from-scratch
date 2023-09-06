package kotlinbook.test

import kotlinbook.createAndMigrateDataSource
import kotlinbook.createAppConfig
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
