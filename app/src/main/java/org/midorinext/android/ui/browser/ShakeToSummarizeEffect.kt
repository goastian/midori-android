package org.midorinext.android.ui.browser

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlin.math.sqrt

/**
 * Listens only while the browser is visible. A cooldown prevents a single shake
 * from opening several summaries as the accelerometer settles.
 */
@Composable
fun ShakeToSummarizeEffect(enabled: Boolean, onShake: () -> Unit) {
    val context = LocalContext.current
    val latestOnShake = rememberUpdatedState(onShake)

    DisposableEffect(context, enabled) {
        if (!enabled) {
            onDispose { }
        } else {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            if (accelerometer == null) {
                onDispose { }
            } else {
                val listener = object : SensorEventListener {
                    private var lastShakeAt = 0L

                    override fun onSensorChanged(event: SensorEvent) {
                        val gravity = SensorManager.GRAVITY_EARTH
                        val acceleration = sqrt(
                            event.values[0] * event.values[0] +
                                event.values[1] * event.values[1] +
                                event.values[2] * event.values[2]
                        ) / gravity
                        val now = System.currentTimeMillis()
                        if (acceleration >= SHAKE_THRESHOLD_G && now - lastShakeAt >= COOLDOWN_MS) {
                            lastShakeAt = now
                            latestOnShake.value()
                        }
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                }
                sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
                onDispose { sensorManager.unregisterListener(listener) }
            }
        }
    }
}

private const val SHAKE_THRESHOLD_G = 2.7f
private const val COOLDOWN_MS = 1_200L
