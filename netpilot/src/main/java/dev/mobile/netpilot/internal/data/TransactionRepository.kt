package dev.mobile.netpilot.internal.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** Storage for captured transactions. All calls are blocking. */
internal interface TransactionRepository {
    /** Increments on every write so observers know to re-query. */
    val changes: StateFlow<Long>

    /** Stores a new transaction and returns its generated id. */
    fun insert(transaction: HttpTransaction): Long

    fun update(transaction: HttpTransaction)

    fun getSummaries(query: String): List<TransactionSummary>

    fun get(id: Long): HttpTransaction?

    /** Newest first, with bodies; used for exports. */
    fun getRecent(limit: Int): List<HttpTransaction>

    fun clear()
}

internal fun TransactionRepository.observeSummaries(query: String): Flow<List<TransactionSummary>> =
    changes.map { getSummaries(query) }.conflate().flowOn(Dispatchers.IO)

internal fun TransactionRepository.observe(id: Long): Flow<HttpTransaction?> =
    changes.map { get(id) }.conflate().flowOn(Dispatchers.IO)
