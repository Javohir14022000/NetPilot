package dev.mobile.netpilot.internal.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import dev.mobile.netpilot.NetPilotConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Plain SQLite storage. Room is avoided on purpose: a debug library should not force
 * its Room/KSP versions onto the host app.
 */
internal class SqliteTransactionRepository(
    context: Context,
    private val config: NetPilotConfig,
    private val clock: () -> Long = System::currentTimeMillis,
) : TransactionRepository {

    private val helper = DbHelper(context.applicationContext)
    private val changeCounter = MutableStateFlow(0L)
    override val changes: StateFlow<Long> = changeCounter.asStateFlow()

    override fun insert(transaction: HttpTransaction): Long {
        val db = helper.writableDatabase
        val id = db.insertOrThrow(TABLE, null, transaction.toContentValues())
        prune(db)
        notifyChanged()
        return id
    }

    override fun update(transaction: HttpTransaction) {
        helper.writableDatabase.update(TABLE, transaction.toContentValues(), "$ID = ?", arrayOf(transaction.id.toString()))
        notifyChanged()
    }

    override fun getSummaries(query: String): List<TransactionSummary> {
        val search = SearchSelection.from(query)
        return helper.readableDatabase
            .query(TABLE, SUMMARY_COLUMNS, search?.selection, search?.args, null, null, "$ID DESC")
            .use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toSummary()) } }
    }

    override fun get(id: Long): HttpTransaction? =
        helper.readableDatabase
            .query(TABLE, null, "$ID = ?", arrayOf(id.toString()), null, null, null)
            .use { cursor -> if (cursor.moveToFirst()) cursor.toTransaction() else null }

    override fun clear() {
        helper.writableDatabase.delete(TABLE, null, null)
        notifyChanged()
    }

    private fun prune(db: SQLiteDatabase) {
        val oldestAllowed = clock() - config.retentionPeriodMillis
        // maxRecords is a validated Int, so inlining it into LIMIT is safe.
        db.delete(
            TABLE,
            "$REQUEST_DATE < ? OR $ID NOT IN (SELECT $ID FROM $TABLE ORDER BY $ID DESC LIMIT ${config.maxRecords})",
            arrayOf(oldestAllowed.toString()),
        )
    }

    private fun notifyChanged() {
        changeCounter.update { it + 1 }
    }

    private class DbHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $TABLE (
                    $ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $REQUEST_DATE INTEGER NOT NULL,
                    $METHOD TEXT NOT NULL,
                    $URL TEXT NOT NULL,
                    $HOST TEXT NOT NULL,
                    $PATH TEXT NOT NULL,
                    $SCHEME TEXT NOT NULL,
                    $REQUEST_HEADERS TEXT,
                    $REQUEST_CONTENT_TYPE TEXT,
                    $REQUEST_SIZE INTEGER NOT NULL,
                    $REQUEST_BODY TEXT,
                    $PROTOCOL TEXT,
                    $RESPONSE_DATE INTEGER,
                    $RESPONSE_CODE INTEGER,
                    $RESPONSE_MESSAGE TEXT,
                    $RESPONSE_HEADERS TEXT,
                    $RESPONSE_CONTENT_TYPE TEXT,
                    $RESPONSE_SIZE INTEGER,
                    $RESPONSE_BODY TEXT,
                    $TOOK_MS INTEGER,
                    $ERROR TEXT
                )
                """.trimIndent(),
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // Captured traffic is disposable debug data, so a schema change simply resets it.
            db.execSQL("DROP TABLE IF EXISTS $TABLE")
            onCreate(db)
        }
    }

    /** Case-insensitive substring search over URL, method and status code. */
    private class SearchSelection(val selection: String, val args: Array<String>) {
        companion object {
            fun from(query: String): SearchSelection? {
                val trimmed = query.trim()
                if (trimmed.isEmpty()) return null
                val pattern = "%" + trimmed.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
                return SearchSelection(
                    selection = "($URL LIKE ? ESCAPE '\\' OR $METHOD LIKE ? ESCAPE '\\' " +
                        "OR CAST($RESPONSE_CODE AS TEXT) LIKE ? ESCAPE '\\')",
                    args = arrayOf(pattern, pattern, pattern),
                )
            }
        }
    }

    private companion object {
        const val DB_NAME = "netpilot.db"
        const val DB_VERSION = 1
        const val TABLE = "transactions"

        const val ID = "id"
        const val REQUEST_DATE = "request_date"
        const val METHOD = "method"
        const val URL = "url"
        const val HOST = "host"
        const val PATH = "path"
        const val SCHEME = "scheme"
        const val REQUEST_HEADERS = "request_headers"
        const val REQUEST_CONTENT_TYPE = "request_content_type"
        const val REQUEST_SIZE = "request_size"
        const val REQUEST_BODY = "request_body"
        const val PROTOCOL = "protocol"
        const val RESPONSE_DATE = "response_date"
        const val RESPONSE_CODE = "response_code"
        const val RESPONSE_MESSAGE = "response_message"
        const val RESPONSE_HEADERS = "response_headers"
        const val RESPONSE_CONTENT_TYPE = "response_content_type"
        const val RESPONSE_SIZE = "response_size"
        const val RESPONSE_BODY = "response_body"
        const val TOOK_MS = "took_ms"
        const val ERROR = "error"

        val SUMMARY_COLUMNS = arrayOf(ID, REQUEST_DATE, METHOD, HOST, PATH, RESPONSE_CODE, RESPONSE_SIZE, TOOK_MS, ERROR)

        fun HttpTransaction.toContentValues() = ContentValues().apply {
            put(REQUEST_DATE, requestDate)
            put(METHOD, method)
            put(URL, url)
            put(HOST, host)
            put(PATH, path)
            put(SCHEME, scheme)
            put(REQUEST_HEADERS, HeaderCodec.encode(requestHeaders))
            put(REQUEST_CONTENT_TYPE, requestContentType)
            put(REQUEST_SIZE, requestSize)
            put(REQUEST_BODY, requestBody)
            put(PROTOCOL, protocol)
            put(RESPONSE_DATE, responseDate)
            put(RESPONSE_CODE, responseCode)
            put(RESPONSE_MESSAGE, responseMessage)
            put(RESPONSE_HEADERS, HeaderCodec.encode(responseHeaders))
            put(RESPONSE_CONTENT_TYPE, responseContentType)
            put(RESPONSE_SIZE, responseSize)
            put(RESPONSE_BODY, responseBody)
            put(TOOK_MS, tookMs)
            put(ERROR, error)
        }

        fun Cursor.toSummary() = TransactionSummary(
            id = long(ID),
            requestDate = long(REQUEST_DATE),
            method = string(METHOD),
            host = string(HOST),
            path = string(PATH),
            responseCode = intOrNull(RESPONSE_CODE),
            responseSize = longOrNull(RESPONSE_SIZE),
            tookMs = longOrNull(TOOK_MS),
            error = stringOrNull(ERROR),
        )

        fun Cursor.toTransaction() = HttpTransaction(
            id = long(ID),
            requestDate = long(REQUEST_DATE),
            method = string(METHOD),
            url = string(URL),
            host = string(HOST),
            path = string(PATH),
            scheme = string(SCHEME),
            requestHeaders = HeaderCodec.decode(stringOrNull(REQUEST_HEADERS)),
            requestContentType = stringOrNull(REQUEST_CONTENT_TYPE),
            requestSize = long(REQUEST_SIZE),
            requestBody = stringOrNull(REQUEST_BODY),
            protocol = stringOrNull(PROTOCOL),
            responseDate = longOrNull(RESPONSE_DATE),
            responseCode = intOrNull(RESPONSE_CODE),
            responseMessage = stringOrNull(RESPONSE_MESSAGE),
            responseHeaders = HeaderCodec.decode(stringOrNull(RESPONSE_HEADERS)),
            responseContentType = stringOrNull(RESPONSE_CONTENT_TYPE),
            responseSize = longOrNull(RESPONSE_SIZE),
            responseBody = stringOrNull(RESPONSE_BODY),
            tookMs = longOrNull(TOOK_MS),
            error = stringOrNull(ERROR),
        )

        fun Cursor.string(column: String): String = getString(getColumnIndexOrThrow(column))
        fun Cursor.long(column: String): Long = getLong(getColumnIndexOrThrow(column))

        fun Cursor.stringOrNull(column: String): String? =
            getColumnIndexOrThrow(column).let { if (isNull(it)) null else getString(it) }

        fun Cursor.longOrNull(column: String): Long? =
            getColumnIndexOrThrow(column).let { if (isNull(it)) null else getLong(it) }

        fun Cursor.intOrNull(column: String): Int? =
            getColumnIndexOrThrow(column).let { if (isNull(it)) null else getInt(it) }
    }
}
