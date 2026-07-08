package org.midorinext.android

import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.system.exitProcess

open class MidoriActivity: AppCompatActivity() {
    private var imm: InputMethodManager? = null
    private var onKeyboardHiddenCallback: (() -> Unit)? = null
    private var rootView: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        imm = ContextCompat.getSystemService(this, InputMethodManager::class.java)
    }

    fun bindRootView(v: View) {
        rootView = v
        ViewCompat.setOnApplyWindowInsetsListener(v) { view, insets ->
            if (!insets.isVisible(WindowInsetsCompat.Type.ime())) {
                onKeyboardHiddenCallback?.invoke()
            }
            ViewCompat.onApplyWindowInsets(view, insets)
        }
    }

    fun forceHideKeyboard() {
        rootView?.let { imm?.hideSoftInputFromWindow(it.windowToken, 0) }
    }

    fun registerOnKeyboardHiddenCallback(callback: () -> Unit) {
        onKeyboardHiddenCallback = callback
    }

    fun unregisterOnKeyboardHiddenCallback() {
        onKeyboardHiddenCallback = null
    }

    fun quit() {
        finishAffinity()
        exitProcess(0)
    }
}