package dev.mobile.netpilot.internal.mock

import kotlinx.coroutines.flow.StateFlow
import okhttp3.Request

/** Called by the interceptor on every request; must be fast and thread-safe. */
internal fun interface MockRuleMatcher {
    fun findMatch(request: Request): MockRule?

    companion object {
        val NONE = MockRuleMatcher { null }
    }
}

/** Persistent mock rules plus the global on/off switch. Writes are blocking. */
internal interface MockRuleStore : MockRuleMatcher {
    /** Sorted by priority (highest first), then newest first. */
    val rules: StateFlow<List<MockRule>>
    val isMockingEnabled: StateFlow<Boolean>

    /** Inserts when [MockRule.id] is 0, otherwise updates. Returns the rule id. */
    fun save(rule: MockRule): Long

    fun delete(id: Long)

    fun setMockingEnabled(isEnabled: Boolean)
}
