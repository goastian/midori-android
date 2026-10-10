package org.midorinext.android.storage

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.midorinext.android.R
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MidoriClientProvider @Inject constructor(@ApplicationContext context: Context) {
    val clientState = MutableStateFlow(context.getString(R.string.app_client_string)).asStateFlow()
}
