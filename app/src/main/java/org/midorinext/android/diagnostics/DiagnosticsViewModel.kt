package org.midorinext.android.diagnostics

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(private val diagnostics: DiagnosticsController) : ViewModel() {
    val consent = diagnostics.consent
    fun setEnabled(enabled: Boolean) { diagnostics.setEnabled(enabled) }
}
