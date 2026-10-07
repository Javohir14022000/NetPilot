package dev.mobile.netpilot.internal.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * The single SQLite file shared by captured traffic and mock rules. Plain SQLite instead
 * of Room so a debug library never forces Room/KSP versions onto the host app.
 */
internal class NetPilotDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context, NAME, null, VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(TransactionTable.CREATE)
        db.execSQL(MockRuleTable.CREATE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < VERSION_MOCK_RULES) {
            db.execSQL("ALTER TABLE ${TransactionTable.NAME} ADD COLUMN ${TransactionTable.MOCK_RULE_NAME} TEXT")
            db.execSQL(MockRuleTable.CREATE)
        }
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Only happens when switching to an older NetPilot build; the data is disposable.
        db.execSQL("DROP TABLE IF EXISTS ${TransactionTable.NAME}")
        db.execSQL("DROP TABLE IF EXISTS ${MockRuleTable.NAME}")
        onCreate(db)
    }

    companion object {
        private const val NAME = "netpilot.db"
        private const val VERSION_MOCK_RULES = 2
        private const val VERSION = VERSION_MOCK_RULES

        @Volatile
        private var instance: NetPilotDatabase? = null

        fun get(context: Context): NetPilotDatabase =
            instance ?: synchronized(this) {
                instance ?: NetPilotDatabase(context.applicationContext).also { instance = it }
            }
    }
}

internal object TransactionTable {
    const val NAME = "transactions"
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
    const val MOCK_RULE_NAME = "mock_rule_name"

    val CREATE = """
        CREATE TABLE $NAME (
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
            $ERROR TEXT,
            $MOCK_RULE_NAME TEXT
        )
    """.trimIndent()
}

internal object MockRuleTable {
    const val NAME = "mock_rules"
    const val ID = "id"
    const val RULE_NAME = "name"
    const val METHOD = "method"
    const val URL_PATTERN = "url_pattern"
    const val IS_REGEX = "is_regex"
    const val OUTCOME = "outcome"
    const val STATUS_CODE = "status_code"
    const val CONTENT_TYPE = "content_type"
    const val HEADERS = "headers"
    const val BODY = "body"
    const val DELAY_MS = "delay_ms"
    const val PRIORITY = "priority"
    const val IS_ENABLED = "is_enabled"

    val CREATE = """
        CREATE TABLE $NAME (
            $ID INTEGER PRIMARY KEY AUTOINCREMENT,
            $RULE_NAME TEXT NOT NULL,
            $METHOD TEXT,
            $URL_PATTERN TEXT NOT NULL,
            $IS_REGEX INTEGER NOT NULL,
            $OUTCOME TEXT NOT NULL,
            $STATUS_CODE INTEGER NOT NULL,
            $CONTENT_TYPE TEXT,
            $HEADERS TEXT,
            $BODY TEXT NOT NULL,
            $DELAY_MS INTEGER NOT NULL,
            $PRIORITY INTEGER NOT NULL,
            $IS_ENABLED INTEGER NOT NULL
        )
    """.trimIndent()
}

internal fun Cursor.string(column: String): String = getString(getColumnIndexOrThrow(column))

internal fun Cursor.long(column: String): Long = getLong(getColumnIndexOrThrow(column))

internal fun Cursor.int(column: String): Int = getInt(getColumnIndexOrThrow(column))

internal fun Cursor.stringOrNull(column: String): String? =
    getColumnIndexOrThrow(column).let { if (isNull(it)) null else getString(it) }

internal fun Cursor.longOrNull(column: String): Long? =
    getColumnIndexOrThrow(column).let { if (isNull(it)) null else getLong(it) }

internal fun Cursor.intOrNull(column: String): Int? =
    getColumnIndexOrThrow(column).let { if (isNull(it)) null else getInt(it) }
