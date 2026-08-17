package com.localcharacter.app.ui.motion

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

object AppMotion {
    const val InstantMillis = 70
    const val FastMillis = 110
    const val NormalMillis = 170
    const val ScreenMillis = 185

    val StandardEasing = CubicBezierEasing(0.20f, 0.0f, 0.0f, 1.0f)
    val FadeEasing = CubicBezierEasing(0.2f, 0.0f, 0.15f, 1.0f)

    fun <T> fastTween(): TweenSpec<T> = tween(durationMillis = FastMillis, easing = StandardEasing)
    fun <T> screenTween(): TweenSpec<T> = tween(durationMillis = ScreenMillis, easing = StandardEasing)

    fun pressSpring(): SpringSpec<Float> = spring(
        dampingRatio = 0.86f,
        stiffness = Spring.StiffnessMedium,
    )
}
