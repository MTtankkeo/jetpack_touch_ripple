package app.pubart.ui.layout.touch_ripple

import android.os.Build
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animate
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pubart.ui.Theme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.sqrt

class Point(
    private val x: Float,
    private val y: Float
) {
    fun distance(target: Point): Float {
        val xd = target.x - x
        val yd = target.y - y
        return sqrt(xd.pow(2) + yd.pow(2))
    }

    companion object {
        fun offset(offset: Offset): Point {
            return Point(offset.x, offset.y)
        }
    }
}

class TouchEffect(
    val offset: Offset,
) {
    var spreadValue by mutableFloatStateOf(0f)
    var fadeValue   by mutableFloatStateOf(0f)

    suspend fun animate(): TouchEffect {
        coroutineScope {
            launch {
                animate(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec = TweenSpec(durationMillis = 250),
                ) { value, _ -> spreadValue = value; }

                animate(
                    initialValue = 1f,
                    targetValue = 0f,
                    animationSpec = TweenSpec(durationMillis = 500)
                ) { value, _ -> fadeValue = value; }
            }
            launch {
                animate(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = TweenSpec(durationMillis = 100)
                ) { value, _ -> fadeValue = value; }
            }
        }
        return this
    }
}

class TouchRippleController {
    val effects = mutableStateListOf<TouchEffect>()

    fun attach(effect: TouchEffect) {
        effects.add(effect)
    }
    fun detach(effect: TouchEffect) {
        effects.remove(effect)
    }
}

@Composable
fun rememberTouchRippleController(): TouchRippleController {
    return remember { TouchRippleController() }
}

@Composable
fun TouchRippleGestureDetector(
    controller: TouchRippleController,
    onTap: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier.pointerInput("Touch Ripple Gestures") {
            if (onTap != null) {
                detectTapGestures {
                    scope.launch {
                        val effect = TouchEffect(it);
                        controller.attach(effect)
                        controller.detach(effect.animate())
                    }
                }
            }
        }
    ) {
        content()
    }
}

@Composable
fun TouchRipple(
    controller: TouchRippleController = rememberTouchRippleController(),
    color: Color = Theme.current.touchRipple,
    onTap: (() -> Unit)? = null,
    onTapStart: (() -> Unit)? = null,
    onTapEnd: (() -> Unit)? = null,
    blurRadius: Dp = 30.dp,
    content: @Composable () -> Unit,
) {
    TouchRippleGestureDetector(
        controller = controller,
        onTap = onTap,
    ) {
        content()
        Canvas(
            modifier = Modifier.matchParentSize().run {
                // 안드로이드 버전이 12 이하인 경우, 기본적으로 블러 효과를 지원하지 않습니다.
                if (Build.VERSION.SDK_INT >= 32) this.blur(blurRadius, blurRadius) else this
            }
        ) {
            clipRect {
                controller.effects.forEach {
                    // 해당 반지름은 원이 중심을 기준으로 컴포저블 요소를 완전히 덮을 때까지 확장할 수 있는 크기와 같습니다.
                    val centerToStartAndTop = Point.offset(Offset.Zero)
                        .distance(Point.offset(center))

                    // 해당 반지름은 이벤트가 발생한 위치가 컴포저블 요소의 중심으로부터 멀어질수록,
                    // 원이 컴포저블 요소를 완전히 덮을 수 있도록 추가적인 반지름을 계산하여 보장합니다.
                    val centerToOffset = Point.offset(it.offset).distance(Point.offset(center))
                    val circleRadius = centerToStartAndTop + centerToOffset

                    drawCircle(
                        center = it.offset,
                        color = color.copy(alpha = color.alpha * it.fadeValue),
                        radius = circleRadius * it.spreadValue,
                    )
                }
            }
        }
    }
}