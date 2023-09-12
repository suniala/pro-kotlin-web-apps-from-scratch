package kotlinbook.db

import kotliquery.Row
import kotliquery.Session
import org.slf4j.LoggerFactory

object DBSupport {
    private val log = LoggerFactory.getLogger(DBSupport.javaClass)

    fun mapFromRow(row: Row): Map<String, Any?> {
        return row.underlying.metaData
            .let { (1..it.columnCount).map(it::getColumnName) }
            .map { it to row.anyOrNull(it) }
            .toMap()
    }

    fun <A> dbSavePoint(dbSess: Session, body: () -> A): A {
        val sp = dbSess.connection.underlying.setSavepoint()
        return try {
            body().also {
                dbSess.connection.underlying.releaseSavepoint(sp)
            }
        } catch (e: Throwable) {
            log.warn("Got exception, will rollback")
            dbSess.connection.underlying.rollback(sp)
            throw e
        }
    }
}
