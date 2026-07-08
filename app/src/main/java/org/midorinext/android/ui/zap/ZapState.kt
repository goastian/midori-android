package org.midorinext.android.ui.zap

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.midorinext.android.cookies.MidoriCookieState
import org.midorinext.android.usecases.ClearDataUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// TODO zap state could old clear data config (and clean use case) ?
class ZapState(
    private val clearDataUseCase: ClearDataUseCase,
    private val coroutineScope: CoroutineScope = MainScope(),
    private val cookieState: MidoriCookieState
) {
    internal enum class RequestStatus { Zapping, Confirm, Waiting, Error }
    internal enum class AnimationStatus { Idle, In, Wait, Out }

    internal var requestStatus: RequestStatus by mutableStateOf(RequestStatus.Waiting)
        private set

    internal var animationStatus by mutableStateOf(AnimationStatus.Idle)
        private set

    private var endCallback: ((Boolean) -> Unit)? = null

    internal fun updateAnimationState(s: AnimationStatus) {
        animationStatus = s
    }

    fun zap(skipConfirmation: Boolean = false, then: (Boolean) -> Unit = {}) {
        if (requestStatus == RequestStatus.Waiting) {
            endCallback = then
            if (skipConfirmation) {
                consumeZapRequest(true)
            } else {
                requestStatus = RequestStatus.Confirm
            }
        }
    }

    private fun waitAnimationAndFinish(success: Boolean) =
        coroutineScope.launch {
            while (animationStatus != AnimationStatus.Wait) {
                delay(50)
            }
            requestStatus = if (success) RequestStatus.Waiting else RequestStatus.Error
            animationStatus = AnimationStatus.Out
            endCallback?.invoke(success)
        }

    internal fun consumeZapRequest(doIt: Boolean) {
        if (doIt) {
            requestStatus = RequestStatus.Zapping
            animationStatus = AnimationStatus.In

            coroutineScope.launch {
                delay(300) // Wait for animation to cover the whole screen
                clearDataUseCase { success ->
                    coroutineScope.launch {
                        cookieState.restoreCookies {
                            waitAnimationAndFinish(success)
                        }
                    }
                }
            }
        } else {
            requestStatus = RequestStatus.Waiting
            endCallback?.invoke(false)
        }
    }

    internal fun consumeZapError() {
        if (requestStatus == RequestStatus.Error)
            requestStatus = RequestStatus.Waiting
    }
}