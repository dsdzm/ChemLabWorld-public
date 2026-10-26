package com.chemlab.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.chemlab.game.AchievementManager
import com.chemlab.game.WorldState
import kotlinx.coroutines.delay

/**
 * 「器材联动实验」页面入口。
 *
 * 与主页面（GameView，纯 Canvas 自绘）分开做成独立 Activity：
 *  · 主页面完全不改渲染方式，新模式的 Compose 依赖不会渗进原有代码；
 *  · 返回键天然退回主页面，不需要在 GameView 里维护新的页面栈分支。
 *
 * 主循环：每 [TICK_MS]（100ms）驱动一次 [WorldState.tick]。
 * 关键节能点：**tick 返回 false 时不推进 UI 帧** —— 世界静止（无连接、无加热/电解、
 * 无气体、已回到室温）时既不跑模拟也不重绘，低配手表上可长时间停留而不发热掉电。
 */
class ExperimentActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val achievements = AchievementManager(getSharedPreferences("chemlab", MODE_PRIVATE))
        val worldState = WorldState()
        // 复用既有成就与图鉴系统：联动实验里发生的反应同样计入
        worldState.onReaction = { _, r -> achievements.onReaction(r) }
        worldState.onAction = { tag -> achievements.onAction(tag) }

        setContent {
            // 帧计数用 State 对象向下传递，而不是 Int 参数：
            // 这样每 tick 自增只会让「绘制阶段」失效（Canvas 重绘），
            // 不会触发 ExperimentScreen 的整屏重组 —— 低配手表上这是最大的性能差别。
            val tick = remember { mutableStateOf(0) }
            val lifecycleOwner = LocalLifecycleOwner.current

            // 仅在 RESUMED 时推进主循环：退到后台立即停算，避免无谓耗电
            LaunchedEffect(lifecycleOwner) {
                lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    while (true) {
                        delay(TICK_MS)
                        // 静止时 tick() 返回 false → 不推进帧 → 不触发重绘，界面完全静止
                        if (worldState.tick()) tick.value++
                    }
                }
            }

            ExperimentScreen(
                state = worldState,
                tick = tick,
                onExit = { finish() }
            )
        }
    }
}