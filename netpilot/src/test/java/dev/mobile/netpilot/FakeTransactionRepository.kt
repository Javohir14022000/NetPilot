package dev.mobile.netpilot

import dev.mobile.netpilot.internal.data.HttpTransaction
import dev.mobile.netpilot.internal.data.TransactionRepository
import dev.mobile.netpilot.internal.data.TransactionSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** In-memory repository for interceptor tests. */
internal class FakeTransactionRepository : TransactionRepository {
    private val stored = linkedMapOf<Long, HttpTransaction>()
    private var nextId = 1L
    override val changes: StateFlow<Long> = MutableStateFlow(0L)

    override fun insert(transaction: HttpTransaction): Long {
        val id = nextId++
        stored[id] = transaction.copy(id = id)
        return id
    }

    override fun update(transaction: HttpTransaction) {
        stored[transaction.id] = transaction
    }

    override fun getSummaries(query: String): List<TransactionSummary> = emptyList()

    override fun get(id: Long): HttpTransaction? = stored[id]

    override fun clear() = stored.clear()

    fun single(): HttpTransaction = stored.values.single()
}
