package com.chemlab.ui

import androidx.compose.ui.graphics.Color

/**
 * 端口在界面上的五种视觉状态。
 *
 * 颜色即语义：绿=可以连、蓝=已选中、蓝灰=已连好、灰=闲置、灰(20%)=不可连。
 */
enum class PortVisualState(val color: Color) {
    /** 闲置：灰 */
    Idle(Color(0xFF9E9E9E)),

    /** 与已选端口兼容：绿色 + 呼吸闪烁 */
    Compatible(Color(0xFF4CAF50)),

    /** 已被选中：蓝色 + 外圈脉冲 */
    Selected(Color(0xFF2196F3)),

    /** 已经连好线：蓝灰 */
    Connected(Color(0xFF607D8B)),

    /** 与已选端口不兼容：灰色降到 20% 不透明度 */
    Invalid(Color(0xFF9E9E9E).copy(alpha = 0.20f))
}

/** 连接线的视觉状态 */
enum class LinkVisualState(val color: Color) {
    /** 正常导通 */
    Open(Color(0xFF607D8B)),

    /** 已断开（保留记录但不再传输） */
    Closed(Color(0xFF4A4F55)),

    /** 长按/选中的高亮态 */
    Highlighted(Color(0xFF2196F3))
}