package org.midorinext.android.mozac

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class MemoryPressureMonitor(
    private val scope: CoroutineScope,
    private val underPressure: suspend () -> Boolean,
    private val onPressure: () -> Unit,
    private val awaitNextCheck: suspend () -> Unit = { delay(15_000) },
) {
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                if (underPressure()) onPressure()
                awaitNextCheck()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
