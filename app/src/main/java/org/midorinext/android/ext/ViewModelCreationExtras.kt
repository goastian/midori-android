package org.midorinext.android.ext

import android.os.Bundle
import androidx.lifecycle.DEFAULT_ARGS_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras

/**
 * ComponentActivity exposes the launching Intent extras as the default arguments of every
 * SavedStateHandle. Intents sent by Custom Tabs clients carry a SparseArray, which cannot be
 * written back with bundleOf when the activity saves its state and crashes onSaveInstanceState.
 * No view model reads launch extras from its SavedStateHandle, so they are not exposed.
 */
fun CreationExtras.withoutIntentDefaultArgs(): CreationExtras =
    MutableCreationExtras(this).apply { this[DEFAULT_ARGS_KEY] = Bundle() }
