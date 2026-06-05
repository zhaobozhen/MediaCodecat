package com.absinthe.mediacodecat.utils

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

@Composable
fun rememberUiSensor(): UiSensorState {
    val context = LocalContext.current.applicationContext
    val uiSensor = remember(context) { UiSensorState(context) }

    DisposableEffect(uiSensor) {
        uiSensor.start()
        onDispose { uiSensor.stop() }
    }

    return uiSensor
}

@Stable
class UiSensorState internal constructor(context: Context) {

    var gravityAngle: Float by mutableFloatStateOf(DefaultAngleDegrees)
        private set

    var gravity: Offset by mutableStateOf(Offset.Zero)
        private set

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor =
        sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event == null) return
            val x = event.values.getOrNull(0) ?: return
            val y = event.values.getOrNull(1) ?: return
            val z = event.values.getOrNull(2) ?: GravityZ
            val norm = sqrt(x * x + y * y + z * z).takeIf { it > 0f } ?: return
            val targetAngle = atan2(y, x) * RadToDeg

            gravityAngle = smoothAngleDegrees(gravityAngle, targetAngle, SensorAlpha)
            gravity = gravity * (1f - SensorAlpha) + Offset(x / norm, y / norm) * SensorAlpha
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun start() {
        sensor ?: return
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        sensorManager.unregisterListener(listener)
    }
}

private const val DefaultAngleDegrees = 45f
private const val GravityZ = 9.81f
private const val SensorAlpha = 0.35f
private val RadToDeg = (180f / PI).toFloat()

private fun smoothAngleDegrees(current: Float, target: Float, alpha: Float): Float {
    val delta = ((target - current + 540f) % 360f) - 180f
    return normalizeAngleDegrees(current + delta * alpha)
}

private fun normalizeAngleDegrees(angle: Float): Float {
    return ((angle % 360f) + 360f) % 360f
}
