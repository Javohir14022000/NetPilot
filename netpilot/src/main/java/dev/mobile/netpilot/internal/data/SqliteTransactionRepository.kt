package dev.mobile.netpilot.internal.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import dev.mobile.netpilot.NetPilotConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal class SqliteTransactionRepository(
    context: Context,
    private val config: NetPilotConfig,
    private val clock: () -> Long = System::currentTimeMillis,
) : TransactionRepository {

    private val database = NetPilotDatabase.get(context)
    private val changeCounter = MutableStateFlow(0L)
    override val changes: StateFlow<Long> = changeCounter.asStateFlow()

    override fun insert(transaction: HttpTransaction): Long {
        val db = database.writableDatabase
        val id = db.insertOrThrow(TransactionTable.NAME, null, transaction.toContentValues())
        prune(db)
        notifyChanged()
        return id
    }

    override fun update(transaction: HttpTransaction) {
        database.writableDatabase.update(
            TransactionTable.NAME,
            transaction.toContentValues(),
            "${TransactionTable.ID} = ?",
            arrayOf(transaction.id.toString()),
        )
        notifyChanged()
    }

    override fun getSummaries(query: String): List<TransactionSummary> {
        val search = SearchSelection.from(query)
        return database.readableDatabase
            .query(TransactionTable.NAME, SUMMARY_COLUMNS, search?.selection, search?.args, null, null, "${TransactionTable.ID} DESC")
            .use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toSummary()) } }
    }

    override fun get(id: Long): HttpTransaction? =
        database.readableDatabase
            .query(TransactionTable.NAME, null, "${TransactionTable.ID} = ?", arrayOf(id.toString()), null, null, null)
            .use { cursor -> if (cursor.moveToFirst()) cursor.toTransaction() else null }

    override fun getRecent(limit: Int): List<HttpTransaction> =
        database.readableDatabase
            .query(TransactionTable.NAME, null, null, null, null, null, "${TransactionTable.ID} DESC", limit.toString())
            .use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toTransaction()) } }

    override fun clear() {
        database.writableDatabase.delete(TransactionTable.NAME, null, null)
        notifyChanged()
    }

    private fun prune(db: SQLiteDatabase) {
        val oldestAllowed = clock() - config.retentionPeriodMillis
        // maxRecords is a validated Int, so inlining it into LIMIT is safe.
        db.delete(
            TransactionTable.NAME,
            "${TransactionTable.REQUEST_DATE} < ? OR ${TransactionTable.ID} NOT IN " +
                "(SELECT ${TransactionTable.ID} FROM ${TransactionTable.NAME} " +
                "ORDER BY ${TransactionTable.ID} DESC LIMIT ${config.maxRecords})",
            arrayOf(oldestAllowed.toString()),
        )
    }

    private fun notifyChanged() {
        changeCounter.update { it + 1 }
    }

    /** Case-insensitive substring search over URL, method and status code. */
    private class SearchSelection(val selection: String, val args: Array<String>) {
        companion object {
            fun from(query: String): SearchSelection? {
                val trimmed = query.trim()
                if (trimmed.isEmpty()) return null
                val pattern = "%" + trimmed.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
                return SearchSelection(
                    selection = "(${TransactionTable.URL} LIKE ? ESCAPE '\\' OR ${TransactionTable.METHOD} LIKE ? ESCAPE '\\' " +
                        "OR CAST(${TransactionTable.RESPONSE_CODE} AS TEXT) LIKE ? ESCAPE '\\')",
                    args = arrayOf(pattern, pattern, pattern),
                )
            }
        }
    }

    private companion object {
        val SUMMARY_COLUMNS = with(TransactionTable) {
            arrayOf(ID, REQUEST_DATE, METHOD, HOST, PATH, RESPONSE_CODE, RESPONSE_SIZE, TOOK_MS, ERROR, MOCK_RULE_NAME)
        }

        fun HttpTransaction.toContentValues() = ContentValues().apply {
            put(TransactionTable.REQUEST_DATE, requestDate)
            put(TransactionTable.METHOD, method)
            put(TransactionTable.URL, url)
            put(TransactionTable.HOST, host)
            put(TransactionTable.PATH, path)
            put(TransactionTable.SCHEME, scheme)
            put(TransactionTable.REQUEST_HEADERS, HeaderCodec.encode(requestHeaders))
            put(TransactionTable.REQUEST_CONTENT_TYPE, requestContentType)
            put(TransactionTable.REQUEST_SIZE, requestSize)
            put(TransactionTable.REQUEST_BODY, requestBody)
            put(TransactionTable.PROTOCOL, protocol)
            put(TransactionTable.RESPONSE_DATE, responseDate)
            put(TransactionTable.RESPONSE_CODE, responseCode)
            put(TransactionTable.RESPONSE_MESSAGE, responseMessage)
            put(TransactionTable.RESPONSE_HEADERS, HeaderCodec.encode(responseHeaders))
            put(TransactionTable.RESPONSE_CONTENT_TYPE, responseContentType)
            put(TransactionTable.RESPONSE_SIZE, responseSize)
            put(TransactionTable.RESPONSE_BODY, responseBody)
            put(TransactionTable.TOOK_MS, tookMs)
            put(TransactionTable.ERROR, error)
            put(TransactionTable.MOCK_RULE_NAME, mockRuleName)
        }

        fun Cursor.toSummary() = TransactionSummary(
            id = long(TransactionTable.ID),
            requestDate = long(TransactionTable.REQUEST_DATE),
            method = string(TransactionTable.METHOD),
            host = string(TransactionTable.HOST),
            path = string(TransactionTable.PATH),
            responseCode = intOrNull(TransactionTable.RESPONSE_CODE),
            responseSize = longOrNull(TransactionTable.RESPONSE_SIZE),
            tookMs = longOrNull(TransactionTable.TOOK_MS),
            error = stringOrNull(TransactionTable.ERROR),
            mockRuleName = stringOrNull(TransactionTable.MOCK_RULE_NAME),
        )

        fun Cursor.toTransaction() = HttpTransaction(
            id = long(TransactionTable.ID),
            requestDate = long(TransactionTable.REQUEST_DATE),
            method = string(TransactionTable.METHOD),
            url = string(TransactionTable.URL),
            host = string(TransactionTable.HOST),
            path = string(TransactionTable.PATH),
            scheme = string(TransactionTable.SCHEME),
            requestHeaders = HeaderCodec.decode(stringOrNull(TransactionTable.REQUEST_HEADERS)),
            requestContentType = stringOrNull(TransactionTable.REQUEST_CONTENT_TYPE),
            requestSize = long(TransactionTable.REQUEST_SIZE),
            requestBody = stringOrNull(TransactionTable.REQUEST_BODY),
            protocol = stringOrNull(TransactionTable.PROTOCOL),
            responseDate = longOrNull(TransactionTable.RESPONSE_DATE),
            responseCode = intOrNull(TransactionTable.RESPONSE_CODE),
            responseMessage = stringOrNull(TransactionTable.RESPONSE_MESSAGE),
            responseHeaders = HeaderCodec.decode(stringOrNull(TransactionTable.RESPONSE_HEADERS)),
            responseContentType = stringOrNull(TransactionTable.RESPONSE_CONTENT_TYPE),
            responseSize = longOrNull(TransactionTable.RESPONSE_SIZE),
            responseBody = stringOrNull(TransactionTable.RESPONSE_BODY),
            tookMs = longOrNull(TransactionTable.TOOK_MS),
            error = stringOrNull(TransactionTable.ERROR),
            mockRuleName = stringOrNull(TransactionTable.MOCK_RULE_NAME),
        )
    }
}
