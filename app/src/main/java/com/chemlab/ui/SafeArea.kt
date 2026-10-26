package com.chemlab.ui

import android.os.Build
import android.view.RoundedCorner
import android.view.View
import android.view.WindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import kotlin.math.max

/** 系统圆角半径 × 该系数后作为内缩量（与旧版 GameView 一致） */
private const val SAFE_CORNER_SCALE = 0.45f

/** 系统无 insets 数据时的兜底内缩比例（与旧版 GameView 一致） */
private const val SAFE_FALLBACK_FRACTION = 0.06f

/**
 * 安全显示区内缩，算法与旧版 [com.chemlab.game.GameView.updateSafeInsets] 完全一致：
 * 取「刘海 safeInset」与「系统圆角半径 × 0.45」的较大值；两者都拿不到时按屏宽 6% 兜底。
 *
 * 手表多为圆角或圆形屏，不内缩会让四角的按钮与文字被切掉。
 * 返回值为四边内缩量，直接用于根布局的 padding：背景铺满全屏，内容留在安全区内。
 */
@Composable
fun rememberSafeAreaPadding(): PaddingValues {
    val view = LocalView.current
    val density = LocalDensity.current.density
    var pad by remember { mutableStateOf(PaddingValues(0.dp)) }

    fun apply(insets: WindowInsets?, wPx: Float) {
        var l = 0f
        var t = 0f
        var r = 0f
        var b = 0f
        if (insets != null) {
            // 刘海屏 safeInset（API 28+）
            if (Build.VERSION.SDK_INT >= 28) {
                insets.displayCutout?.let { cutout ->
                    l = max(l, cutout.safeInsetLeft.toFloat())
                    t = max(t, cutout.safeInsetTop.toFloat())
                    r = max(r, cutout.safeInsetRight.toFloat())
                    b = max(b, cutout.safeInsetBottom.toFloat())
                }
            }
            // 系统圆角半径（API 30+）
            if (Build.VERSION.SDK_INT >= 30) {
                fun corner(which: Int): Float =
                    insets.getRoundedCorner(which)?.radius?.toFloat() ?: 0f

                val tl = corner(RoundedCorner.POSITION_TOP_LEFT) * SAFE_CORNER_SCALE
                val tr = corner(RoundedCorner.POSITION_TOP_RIGHT) * SAFE_CORNER_SCALE
                val bl = corner(RoundedCorner.POSITION_BOTTOM_LEFT) * SAFE_CORNER_SCALE
                val br = corner(RoundedCorner.POSITION_BOTTOM_RIGHT) * SAFE_CORNER_SCALE
                l = max(l, max(tl, bl))
                r = max(r, max(tr, br))
                t = max(t, max(tl, tr))
                b = max(b, max(bl, br))
            }
        }
        // 兜底：拿不到系统值时按屏幕比例估算（圆角/圆形屏）
        if (l <= 0f && t <= 0f && r <= 0f && b <= 0f) {
            val f = wPx * SAFE_FALLBACK_FRACTION
            l = f; t = f; r = f; b = f
        }
        pad = PaddingValues(
            (l / density).dp,
            (t / density).dp,
            (r / density).dp,
            (b / density).dp
        )
    }

    DisposableEffect(view) {
        val dm = view.resources.displayMetrics
        fun widthOf(v: View): Float =
            if (v.width > 0) v.width.toFloat() else dm.widthPixels.toFloat()

        apply(view.rootWindowInsets, widthOf(view))

        val listener = View.OnApplyWindowInsetsListener { v, insets ->
            apply(insets, widthOf(v))
            insets
        }
        view.setOnApplyWindowInsetsListener(listener)
        view.requestApplyInsets()
        onDispose { view.setOnApplyWindowInsetsListener(null) }
    }

    return pad
}