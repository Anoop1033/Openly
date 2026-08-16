package com.openly.shared.ui.radar

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openly.shared.data.model.NearbyUser
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

private const val RING_COUNT = 3
private const val SWEEP_TRAIL_DEGREES = 50f
private val BLIP_RADIUS = 9.dp
private val HIT_RADIUS = 22.dp

@Composable
fun RadarCanvas(
    nearbyUsers: List<NearbyUser>,
    radiusMeters: Double,
    onBlipClick: (NearbyUser) -> Unit,
    modifier: Modifier = Modifier
) {
    val sweepColor = MaterialTheme.colorScheme.primary
    val ringColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    val blipColor = MaterialTheme.colorScheme.primary
    val textMeasurer = rememberTextMeasurer()

    val infiniteTransition = rememberInfiniteTransition(label = "radar-sweep")
    val sweepCompassAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep-angle"
    )

    // Compass bearings (0=north, clockwise) need a -90 shift to match Compose's drawArc
    // convention, where 0 degrees points at 3 o'clock.
    val composeSweepAngle = sweepCompassAngle - 90f

    Canvas(
        modifier = modifier.pointerInput(nearbyUsers) {
            detectTapGestures { tapOffset ->
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxRadiusPx = min(size.width, size.height) / 2f * 0.9f
                val hitRadiusPx = HIT_RADIUS.toPx()

                val closest = nearbyUsers.minByOrNull { user ->
                    val position = blipPosition(center, maxRadiusPx, user, radiusMeters)
                    hypot((position.x - tapOffset.x).toDouble(), (position.y - tapOffset.y).toDouble())
                }
                if (closest != null) {
                    val position = blipPosition(center, maxRadiusPx, closest, radiusMeters)
                    val distancePx = hypot((position.x - tapOffset.x).toDouble(), (position.y - tapOffset.y).toDouble())
                    if (distancePx <= hitRadiusPx) onBlipClick(closest)
                }
            }
        }
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = min(size.width, size.height) / 2f * 0.9f

        for (ring in 1..RING_COUNT) {
            drawCircle(
                color = ringColor,
                radius = maxRadius * ring / RING_COUNT,
                center = center,
                style = Stroke(width = 1.5f)
            )
        }
        drawLine(ringColor, Offset(center.x, center.y - maxRadius), Offset(center.x, center.y + maxRadius), 1f)
        drawLine(ringColor, Offset(center.x - maxRadius, center.y), Offset(center.x + maxRadius, center.y), 1f)

        val sweepBounds = Rect(center - Offset(maxRadius, maxRadius), Size(maxRadius * 2, maxRadius * 2))
        val trailSteps = 24
        for (step in 0 until trailSteps) {
            val t = step / trailSteps.toFloat()
            drawArc(
                color = sweepColor.copy(alpha = (1f - t) * 0.35f),
                startAngle = composeSweepAngle - t * SWEEP_TRAIL_DEGREES,
                sweepAngle = -(SWEEP_TRAIL_DEGREES / trailSteps) - 0.5f,
                useCenter = true,
                topLeft = sweepBounds.topLeft,
                size = sweepBounds.size
            )
        }
        val sweepRad = Math.toRadians(composeSweepAngle.toDouble())
        drawLine(
            color = sweepColor,
            start = center,
            end = center + Offset((maxRadius * cos(sweepRad)).toFloat(), (maxRadius * sin(sweepRad)).toFloat()),
            strokeWidth = 2f
        )

        nearbyUsers.forEach { user ->
            val position = blipPosition(center, maxRadius, user, radiusMeters)
            drawCircle(color = blipColor.copy(alpha = 0.25f), radius = BLIP_RADIUS.toPx() * 2f, center = position)
            drawCircle(color = blipColor, radius = BLIP_RADIUS.toPx(), center = position)

            val label = textMeasurer.measure(user.profile.avatarEmoji, style = TextStyle(fontSize = 16.sp))
            drawText(
                textLayoutResult = label,
                topLeft = Offset(position.x - label.size.width / 2f, position.y - BLIP_RADIUS.toPx() - label.size.height - 2.dp.toPx())
            )
        }
    }
}

private fun blipPosition(center: Offset, maxRadiusPx: Float, user: NearbyUser, radiusMeters: Double): Offset {
    val fraction = (user.distanceMeters / radiusMeters).coerceIn(0.08, 1.0)
    val r = maxRadiusPx * fraction.toFloat()
    val bearingRad = Math.toRadians(user.bearingDegrees)
    val x = center.x + r * sin(bearingRad).toFloat()
    val y = center.y - r * cos(bearingRad).toFloat()
    return Offset(x, y)
}
