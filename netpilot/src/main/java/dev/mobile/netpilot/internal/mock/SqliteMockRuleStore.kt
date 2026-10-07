package dev.mobile.netpilot.internal.mock

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import androidx.core.content.edit
import dev.mobile.netpilot.internal.data.HeaderCodec
import dev.mobile.netpilot.internal.data.MockRuleTable
import dev.mobile.netpilot.internal.data.NetPilotDatabase
import dev.mobile.netpilot.internal.data.int
import dev.mobile.netpilot.internal.data.long
import dev.mobile.netpilot.internal.data.string
import dev.mobile.netpilot.internal.data.stringOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.Request

/**
 * Rules live in SQLite and are mirrored in memory, so matching on the network thread
 * never touches the disk. Everything loads lazily on first use.
 */
internal class SqliteMockRuleStore(context: Context) : MockRuleStore {

    private val appContext = context.applicationContext
    private val database = NetPilotDatabase.get(appContext)
    private val preferences by lazy { appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE) }
    private val rulesState by lazy { MutableStateFlow(loadRules()) }
    private val mockingEnabledState by lazy {
        MutableStateFlow(preferences.getBoolean(KEY_MOCKING_ENABLED, true))
    }

    override val rules: StateFlow<List<MockRule>>
        get() = rulesState

    override val isMockingEnabled: StateFlow<Boolean>
        get() = mockingEnabledState

    override fun findMatch(request: Request): MockRule? =
        if (mockingEnabledState.value) rulesState.value.firstMatch(request) else null

    @Synchronized
    override fun save(rule: MockRule): Long {
        val db = database.writableDatabase
        val values = rule.toContentValues()
        val id = if (rule.id == 0L) {
            db.insertOrThrow(MockRuleTable.NAME, null, values)
        } else {
            db.update(MockRuleTable.NAME, values, "${MockRuleTable.ID} = ?", arrayOf(rule.id.toString()))
            rule.id
        }
        rulesState.value = loadRules()
        return id
    }

    @Synchronized
    override fun delete(id: Long) {
        database.writableDatabase.delete(MockRuleTable.NAME, "${MockRuleTable.ID} = ?", arrayOf(id.toString()))
        rulesState.value = loadRules()
    }

    override fun setMockingEnabled(isEnabled: Boolean) {
        preferences.edit { putBoolean(KEY_MOCKING_ENABLED, isEnabled) }
        mockingEnabledState.value = isEnabled
    }

    private fun loadRules(): List<MockRule> =
        database.readableDatabase
            .query(MockRuleTable.NAME, null, null, null, null, null, "${MockRuleTable.PRIORITY} DESC, ${MockRuleTable.ID} DESC")
            .use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toMockRule()) } }

    private companion object {
        const val PREFERENCES_NAME = "netpilot_settings"
        const val KEY_MOCKING_ENABLED = "mocking_enabled"

        fun MockRule.toContentValues() = ContentValues().apply {
            put(MockRuleTable.RULE_NAME, name)
            put(MockRuleTable.METHOD, method)
            put(MockRuleTable.URL_PATTERN, urlPattern)
            put(MockRuleTable.IS_REGEX, isRegex)
            put(MockRuleTable.OUTCOME, outcome.name)
            put(MockRuleTable.STATUS_CODE, statusCode)
            put(MockRuleTable.CONTENT_TYPE, contentType)
            put(MockRuleTable.HEADERS, HeaderCodec.encode(headers))
            put(MockRuleTable.BODY, body)
            put(MockRuleTable.DELAY_MS, delayMillis)
            put(MockRuleTable.PRIORITY, priority)
            put(MockRuleTable.IS_ENABLED, isEnabled)
        }

        fun Cursor.toMockRule(): MockRule {
            val outcomeName = string(MockRuleTable.OUTCOME)
            return MockRule(
                id = long(MockRuleTable.ID),
                name = string(MockRuleTable.RULE_NAME),
                method = stringOrNull(MockRuleTable.METHOD),
                urlPattern = string(MockRuleTable.URL_PATTERN),
                isRegex = int(MockRuleTable.IS_REGEX) != 0,
                outcome = MockOutcome.entries.find { it.name == outcomeName } ?: MockOutcome.RESPOND,
                statusCode = int(MockRuleTable.STATUS_CODE),
                contentType = stringOrNull(MockRuleTable.CONTENT_TYPE),
                headers = HeaderCodec.decode(stringOrNull(MockRuleTable.HEADERS)),
                body = string(MockRuleTable.BODY),
                delayMillis = long(MockRuleTable.DELAY_MS),
                priority = int(MockRuleTable.PRIORITY),
                isEnabled = int(MockRuleTable.IS_ENABLED) != 0,
            )
        }
    }
}
