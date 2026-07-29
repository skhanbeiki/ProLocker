package com.carbon.prolocker.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

@Composable
fun PatternLockView(
    isError: Boolean,
    vibrationEnabled: Boolean = true,
    hidePatternPath: Boolean = false,
    onPatternDrawn: (List<Int>) -> Unit,
    onInteractionStarted: () -> Unit
) {
    var selectedDots by remember { mutableStateOf(emptyList<Int>()) }
    var currentDragPosition by remember { mutableStateOf<Offset?>(null) }
    var showLineError by remember { mutableStateOf(false) }

    var nodePositions by remember { mutableStateOf(emptyArray<Offset>()) }
    var nodeSpacing by remember { mutableFloatStateOf(0f) }

    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // Per-node animated scale (bounce) and glow (fade) — read inside Canvas
    // draw scope so updates redraw without recomposing the whole tree.
    val nodeScale = remember { List(9) { Animatable(1f) } }
    val nodeGlow = remember { List(9) { Animatable(0f) } }

    LaunchedEffect(isError) {
        if (isError) {
            showLineError = true
            kotlinx.coroutines.delay(1000)
            showLineError = false
            selectedDots = emptyList()
            nodeScale.forEach { it.snapTo(1f) }
            nodeGlow.forEach { it.snapTo(0f) }
        }
    }

    val primaryColor = Color.White
    val errorColor = Color(0xFFFF5252)
    val outlineColor = Color.White.copy(alpha = 0.5f)
    val glowColor = Color(0xFF64B5F6)

    fun animateNodeSelected(index: Int) {
        scope.launch {
            nodeScale[index].snapTo(1f)
            nodeScale[index].animateTo(
                targetValue = 1.35f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
            nodeScale[index].animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        scope.launch {
            nodeGlow[index].snapTo(1f)
            nodeGlow[index].animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing)
            )
        }
    }

    fun resetAllNodeAnimations() {
        scope.launch { nodeScale.forEach { it.animateTo(1f, tween(180)) } }
        scope.launch { nodeGlow.forEach { it.animateTo(0f, tween(180)) } }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth(0.75f)
            .aspectRatio(1f)
            .pointerInput(Unit) {
                var lastRawPosition: Offset? = null

                detectDragGestures(
                    onDragStart = { offset ->
                        if (showLineError) {
                            showLineError = false
                            selectedDots = emptyList()
                        }
                        onInteractionStarted()

                        lastRawPosition = offset
                        val hitDot = findNodeAt(offset, nodePositions, nodeSpacing)
                        if (hitDot != null) {
                            selectedDots = listOf(hitDot)
                            animateNodeSelected(hitDot)
                            if (vibrationEnabled) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        } else {
                            selectedDots = emptyList()
                        }
                        currentDragPosition = offset
                    },
                    onDrag = { change, _ ->
                        val previous = lastRawPosition ?: change.position
                        val current = change.position
                        currentDragPosition = current

                        // Sweep the segment travelled since the last sample so
                        // fast swipes never skip over a node.
                        val newlyHit = findNodesAlongSegment(
                            from = previous,
                            to = current,
                            nodePositions = nodePositions,
                            nodeSpacing = nodeSpacing,
                            alreadySelected = selectedDots
                        )

                        for (hitDot in newlyHit) {
                            if (hitDot in selectedDots) continue

                            if (selectedDots.isNotEmpty()) {
                                val lastDot = selectedDots.last()
                                val intermediate = getInterpolatedNodes(lastDot, hitDot)
                                for (node in intermediate) {
                                    if (node !in selectedDots) {
                                        selectedDots = selectedDots + node
                                        animateNodeSelected(node)
                                        if (vibrationEnabled) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                    }
                                }
                            }

                            if (hitDot !in selectedDots) {
                                selectedDots = selectedDots + hitDot
                                animateNodeSelected(hitDot)
                                if (vibrationEnabled) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            }
                        }

                        lastRawPosition = current
                    },
                    onDragEnd = {
                        currentDragPosition = null
                        lastRawPosition = null
                        onPatternDrawn(selectedDots)
                        if (!isError && selectedDots.size >= 4) {
                            selectedDots = emptyList()
                            resetAllNodeAnimations()
                        } else if (selectedDots.size < 4 && selectedDots.isNotEmpty()) {
                            showLineError = true
                        }
                    },
                    onDragCancel = {
                        currentDragPosition = null
                        lastRawPosition = null
                        selectedDots = emptyList()
                        resetAllNodeAnimations()
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val spacing = canvasWidth / 3f
            nodeSpacing = spacing

            if (nodePositions.isEmpty()) {
                nodePositions = Array(9) { i ->
                    val row = i / 3
                    val col = i % 3
                    Offset(spacing * col + spacing / 2f, spacing * row + spacing / 2f)
                }
            }

            // Smaller dots than before, plus a faint Android-style guide ring.
            val baseRadius = spacing * 0.055f
            val selectedRadius = spacing * 0.075f
            val outlineRadius = spacing * 0.11f
            val glowRadius = spacing * 0.16f

            for (i in 0 until 9) {
                drawCircle(
                    color = outlineColor.copy(alpha = if (showLineError) 0.25f else 0.35f),
                    radius = outlineRadius,
                    center = nodePositions[i],
                    style = Stroke(width = spacing * 0.02f)
                )
            }

            // Lines under the dots so nodes always render on top.
            if (!hidePatternPath || showLineError) {
                val lineColor = if (showLineError) errorColor else primaryColor
                val strokeWidth = spacing * 0.045f

                for (i in 0 until selectedDots.size - 1) {
                    drawLine(
                        color = lineColor,
                        start = nodePositions[selectedDots[i]],
                        end = nodePositions[selectedDots[i + 1]],
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }

                if (currentDragPosition != null && selectedDots.isNotEmpty() && !showLineError) {
                    drawLine(
                        color = primaryColor,
                        start = nodePositions[selectedDots.last()],
                        end = currentDragPosition!!,
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }
            }

            for (i in 0 until 9) {
                val offset = nodePositions[i]
                val isSelected = i in selectedDots
                val scale = nodeScale[i].value
                val glow = nodeGlow[i].value

                val dotColor = when {
                    showLineError -> errorColor
                    isSelected -> primaryColor
                    else -> outlineColor
                }

                if (glow > 0f) {
                    drawCircle(
                        color = glowColor.copy(alpha = glow * 0.35f),
                        radius = glowRadius * (0.8f + glow * 0.4f),
                        center = offset
                    )
                }

                drawCircle(
                    color = dotColor,
                    radius = (if (isSelected) selectedRadius else baseRadius) * scale,
                    center = offset
                )
            }
        }
    }
}

/**
 * Simple point hit-test, used only for the very first touch of a gesture
 * (onDragStart), where there is no previous sample to sweep from yet.
 */
private fun findNodeAt(
    position: Offset,
    nodePositions: Array<Offset>,
    nodeSpacing: Float
): Int? {
    if (nodePositions.isEmpty() || nodeSpacing == 0f) return null
    val hitRadius = nodeSpacing * 0.42f
    var bestIndex: Int? = null
    var bestDistance = hitRadius

    for (i in nodePositions.indices) {
        val dist = distance(nodePositions[i], position)
        if (dist < bestDistance) {
            bestDistance = dist
            bestIndex = i
        }
    }
    return bestIndex
}

/**
 * Sweeps the segment from [from] to [to] and returns every not-yet-selected
 * node whose hit circle the segment passes through, ordered by how far
 * along the segment the intersection occurs.
 *
 * This is the core fix for both reported bugs:
 * - "line breaks mid-drag": a fast swipe can move the finger past a node
 *   between two motion events without any single sample ever landing
 *   inside its hit radius. Testing the whole travelled segment against
 *   each node's circle (a line/circle intersection test) catches it.
 * - "wrong node selected mid-drag": returning hits sorted by their
 *   position along the segment guarantees nodes are appended in the
 *   order the finger actually crossed them, not in array/index order.
 */
private fun findNodesAlongSegment(
    from: Offset,
    to: Offset,
    nodePositions: Array<Offset>,
    nodeSpacing: Float,
    alreadySelected: List<Int>
): List<Int> {
    if (nodePositions.isEmpty() || nodeSpacing == 0f) return emptyList()
    val hitRadius = nodeSpacing * 0.42f

    val dx = to.x - from.x
    val dy = to.y - from.y
    val a = dx * dx + dy * dy

    val hits = mutableListOf<Pair<Int, Float>>()

    for (i in nodePositions.indices) {
        if (i in alreadySelected) continue
        val center = nodePositions[i]

        if (a == 0f) {
            if (distance(center, to) <= hitRadius) hits.add(i to 0f)
            continue
        }

        val fx = from.x - center.x
        val fy = from.y - center.y
        val b = 2 * (fx * dx + fy * dy)
        val c = fx * fx + fy * fy - hitRadius * hitRadius

        val discriminant = b * b - 4 * a * c
        if (discriminant < 0) continue

        val sqrtDisc = sqrt(discriminant)
        val t1 = (-b - sqrtDisc) / (2 * a)
        val t2 = (-b + sqrtDisc) / (2 * a)

        val enterT = when {
            t1 in 0f..1f -> t1
            t2 in 0f..1f -> t2
            t1 < 0f && t2 > 1f -> 0f // whole segment starts inside the circle
            else -> null
        }

        if (enterT != null) hits.add(i to enterT)
    }

    return hits.sortedBy { it.second }.map { it.first }
}

/**
 * Full Android-style pattern interpolation.
 * Returns all nodes that lie on the straight line between from and to.
 */
private fun getInterpolatedNodes(from: Int, to: Int): List<Int> {
    val r1 = from / 3; val c1 = from % 3
    val r2 = to / 3; val c2 = to % 3

    val dr = r2 - r1
    val dc = c2 - c1

    if (abs(dr) <= 1 && abs(dc) <= 1) return emptyList()

    if (dr == 0 && abs(dc) == 2) {
        return listOf(r1 * 3 + (c1 + c2) / 2)
    }

    if (dc == 0 && abs(dr) == 2) {
        return listOf((r1 + r2) / 2 * 3 + c1)
    }

    if (abs(dr) == 2 && abs(dc) == 2) {
        return listOf(4)
    }

    return emptyList()
}

private fun distance(p1: Offset, p2: Offset): Float {
    val dx = p1.x - p2.x
    val dy = p1.y - p2.y
    return sqrt(dx * dx + dy * dy)
}