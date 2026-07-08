package org.midorinext.android.legacy.history

import mozilla.components.concept.storage.*
import java.io.*

// Those are still needed for deserialization when migrating from old history
data class Visit(val timestamp: Long, val type: VisitType): Serializable
data class PageObservation(val title: String?) : Serializable
