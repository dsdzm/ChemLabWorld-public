package com.chemlab.game

import android.app.Activity
import android.os.Bundle

/**
 * 应用入口：承载 GameView，并串联成就持久化。
 * 纯 Framework + Kotlin，无 AndroidX，契合低功耗手表。
 */
class MainActivity : Activity() {

    private var gameView: GameView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val achievements = AchievementManager(getSharedPreferences("chemlab", MODE_PRIVATE))
        val view = GameView(this, achievements)
        gameView = view
        setContentView(view)
    }

    override fun onResume() {
        super.onResume()
        gameView?.start()
    }

    override fun onPause() {
        super.onPause()
        gameView?.stop()
    }

    /** 系统返回键：交给 GameView 逐级返回页面；在首页返回交还系统退出 */
    override fun onBackPressed() {
        if (!(gameView?.back() ?: false)) super.onBackPressed()
    }

    override fun onDestroy() {
        gameView?.stop()
        gameView = null
        super.onDestroy()
    }
}