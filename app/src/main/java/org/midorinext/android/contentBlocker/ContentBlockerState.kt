package org.midorinext.android.contentBlocker

import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Job
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

open class ContentBlockerState {
    enum class Status {
        ALLOWED, BLOCKED, CHECKING
    }

    enum class BlockReason {
        NONE, INVALID, ERROR, IP, DOMAIN, URL, SEARCH_ENGINE
    }

    open var status by mutableStateOf(Status.ALLOWED)
    open var blockReason by mutableStateOf(BlockReason.NONE)

    open fun getStatusForTab(tabId: String) = Status.ALLOWED
    open fun getBlockReasonForTab(tabId: String) = BlockReason.NONE

    open fun check(url: String): Job? = null
    open fun onMidori(): Job? = null
    open fun cancel() = Unit
}

