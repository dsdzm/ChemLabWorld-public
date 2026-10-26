package com.chemlab.game

import android.content.SharedPreferences

/**
 * 成就系统：成就定义、解锁判定与本地持久化（SharedPreferences）。
 * 同时负责"物质图鉴"的收集记录，供成就/图鉴合并界面使用。
 */
class AchievementManager(private val prefs: SharedPreferences) {

    data class Def(val id: String, val name: String, val desc: String)

    val defs: List<Def> = listOf(
        // ---- 入门操作 ----
        Def("first_add", "初次实验", "第一次向容器中加入物质"),
        Def("first_heat", "小小加热员", "第一次点燃酒精灯"),
        Def("first_dissolve", "溶解大师", "第一次搅拌溶解固体"),
        Def("first_gas", "气体制造者", "第一次生成气体"),
        Def("first_precipitate", "沉淀观察员", "第一次生成沉淀"),
        Def("first_water", "第一滴水", "第一次向容器中加入水"),
        Def("clean_lab", "洁净如新", "清洗一次容器"),
        Def("swap_gear", "换换装备", "更换一次实验器材"),
        Def("evaporate_crystal", "蒸发结晶", "蒸发溶液得到晶体"),
        Def("vent_gas", "排气操作", "排出容器内的气体"),
        // ---- 反应类型 ----
        Def("neutralization", "中和反应", "完成一次酸碱中和"),
        Def("combination_done", "化合能手", "完成一次化合反应"),
        Def("decomposition_done", "分解达人", "完成一次分解反应"),
        Def("displacement_done", "置换先锋", "完成一次置换反应"),
        Def("double_done", "复分解专家", "完成一次复分解反应"),
        Def("redox_done", "氧化还原王者", "完成一次氧化还原反应"),
        Def("four_basic", "四大基本反应", "集齐化合、分解、置换、复分解"),
        Def("redox_10", "氧化还原达人", "完成 10 次氧化还原反应"),
        Def("neutral_5", "中和达人", "完成 5 次酸碱中和"),
        Def("metal_displace", "金属置换", "用金属置换出另一种金属"),
        // ---- 条件与现象 ----
        Def("combustion", "燃烧吧", "以点燃方式完成一次燃烧"),
        Def("electrolysis", "通电分解", "电解水生成氢气和氧气"),
        Def("electro_3", "电化学家", "完成 3 次电解反应"),
        Def("catalyst", "催化剂的作用", "使用催化剂完成一次反应"),
        Def("catalyst_5", "催化之道", "借助催化剂完成 5 次反应"),
        Def("hot_300", "高温实验", "把容器加热到 300℃ 以上"),
        Def("hot_500", "烈焰熔炉", "把容器加热到 500℃ 以上"),
        Def("cool_down", "冷静一下", "把加热后的容器冷却回室温"),
        Def("exothermic", "放热反应", "完成一次放热反应"),
        Def("endothermic", "吸热反应", "完成一次吸热反应"),
        Def("light_10", "光芒四射", "累计完成 10 次发光反应"),
        Def("gas_10", "制气专家", "累计生成 10 次气体"),
        Def("precip_10", "沉淀猎人", "累计生成 10 次沉淀"),
        Def("color_10", "变色龙", "累计观察到 10 次颜色变化"),
        // ---- 物质收集（图鉴） ----
        Def("catalog_10", "初识物质", "收集 10 种不同物质"),
        Def("catalog_30", "物质图鉴", "收集 30 种不同物质"),
        Def("catalog_60", "收藏家", "收集 60 种不同物质"),
        Def("catalog_100", "百科全书", "收集 100 种不同物质"),
        Def("cat_element", "单质控", "使用过单质参与反应"),
        Def("cat_oxide", "氧化物行家", "使用过氧化物参与反应"),
        Def("cat_acid", "酸缸子", "使用过酸参与反应"),
        Def("cat_base", "碱大师", "使用过碱参与反应"),
        Def("cat_salt", "盐之有理", "使用过盐参与反应"),
        Def("all_categories", "五类齐全", "单质、氧化物、酸、碱、盐都参与过反应"),
        // ---- 累计里程碑 ----
        Def("reaction_master", "实验室老手", "累计完成 20 次反应"),
        Def("reaction_50", "实验达人", "累计完成 50 次反应"),
        Def("reaction_100", "百炼成钢", "累计完成 100 次反应"),
        Def("reaction_300", "反应宗师", "累计完成 300 次反应"),
        Def("add_30", "投料不停", "累计加入 30 次物质"),
        Def("combo_3", "连锁反应", "一次操作触发 3 个以上连锁反应"),
        Def("ach_30", "成就猎人", "解锁 30 个成就"),
    )

    /** 有成就被解锁时回调（用于显示提示） */
    var onUnlock: ((Def) -> Unit)? = null

    /** 图鉴：已收集物质集合（持久化） */
    private val catalog: MutableSet<String> =
        prefs.getStringSet("catalog", emptySet())!!.toMutableSet()

    /** 已参与过反应的物质大类（持久化） */
    private val seenCategories: MutableSet<String> =
        prefs.getStringSet("cats_seen", emptySet())!!.toMutableSet()

    // ================= 计数器 =================
    private fun count(key: String): Int = prefs.getInt(key, 0)

    private fun bump(key: String, delta: Int = 1): Int {
        val v = count(key) + delta
        prefs.edit().putInt(key, v).apply()
        return v
    }

    fun isUnlocked(id: String): Boolean = prefs.getBoolean("ach_$id", false)

    fun unlockedCount(): Int = defs.count { isUnlocked(it.id) }

    private fun unlock(id: String): Boolean {
        if (isUnlocked(id)) return false
        prefs.edit().putBoolean("ach_$id", true).apply()
        defs.firstOrNull { it.id == id }?.let { onUnlock?.invoke(it) }
        return true
    }

    private fun unlockAll(vararg ids: String) = ids.forEach { unlock(it) }

    /** 反应发生时的成就判定 */
    fun onReaction(r: Reaction) {
        val rc = bump("reaction_count")

        when (r.type) {
            ReactionType.COMBINATION -> unlock("combination_done")
            ReactionType.DECOMPOSITION -> unlock("decomposition_done")
            ReactionType.DISPLACEMENT -> {
                unlock("displacement_done")
                // 金属单质置换出另一种金属单质
                val hasMetal = r.reactants.any { Substances.get(it.id).category == Category.ELEMENT }
                val madeMetal = r.products.any { Substances.get(it.id).category == Category.ELEMENT }
                if (hasMetal && madeMetal) unlock("metal_displace")
            }
            ReactionType.DOUBLE_DISPLACEMENT -> unlock("double_done")
            ReactionType.REDOX -> {}
        }
        if (r.isRedox) {
            unlock("redox_done")
            if (bump("redox_count") >= 10) unlock("redox_10")
        }
        if (r.effect.gasId != null) {
            unlock("first_gas")
            if (bump("gas_count") >= 10) unlock("gas_10")
        }
        if (r.effect.precipitateId != null) {
            unlock("first_precipitate")
            if (bump("precip_count") >= 10) unlock("precip_10")
        }
        if (r.effect.colorChange != null) {
            if (bump("color_count") >= 10) unlock("color_10")
        }
        if (r.effect.light) {
            if (bump("light_count") >= 10) unlock("light_10")
        }
        if (r.effect.heatKj > 0) unlock("exothermic")
        if (r.effect.heatKj < 0) unlock("endothermic")
        if (r.condition.needsIgnition) unlock("combustion")
        if (r.condition.needsElectrolysis) {
            unlock("electrolysis")
            if (bump("electro_count") >= 3) unlock("electro_3")
        }
        if (r.condition.catalystId != null) {
            unlock("catalyst")
            if (bump("catalyst_count") >= 5) unlock("catalyst_5")
        }
        if (isNeutralization(r)) {
            unlock("neutralization")
            if (bump("neutral_count") >= 5) unlock("neutral_5")
        }

        // 参与反应的大类统计
        var changed = false
        r.reactants.forEach {
            val c = Substances.get(it.id).category.name
            if (seenCategories.add(c)) changed = true
        }
        if (changed) prefs.edit().putStringSet("cats_seen", seenCategories).apply()
        if (seenCategories.contains(Category.ELEMENT.name)) unlock("cat_element")
        if (seenCategories.contains(Category.OXIDE.name)) unlock("cat_oxide")
        if (seenCategories.contains(Category.ACID.name)) unlock("cat_acid")
        if (seenCategories.contains(Category.BASE.name)) unlock("cat_base")
        if (seenCategories.contains(Category.SALT.name)) unlock("cat_salt")
        if (seenCategories.size >= 5) unlock("all_categories")

        // 里程碑
        if (rc >= 20) unlock("reaction_master")
        if (rc >= 50) unlock("reaction_50")
        if (rc >= 100) unlock("reaction_100")
        if (rc >= 300) unlock("reaction_300")

        // 四大基本反应集齐
        if (isUnlocked("combination_done") && isUnlocked("decomposition_done") &&
            isUnlocked("displacement_done") && isUnlocked("double_done")
        ) unlock("four_basic")

        if (unlockedCount() >= 30) unlock("ach_30")
    }

    /** 操作事件成就判定，tag 形如 "add:id" / "heat" / "chain:3" / "temp300" */
    fun onAction(tag: String) {
        when {
            tag.startsWith("add:") -> {
                unlock("first_add")
                if (bump("add_count") >= 30) unlock("add_30")
                val id = tag.removePrefix("add:")
                if (id == "h2o") unlock("first_water")
                if (catalog.add(id)) {
                    prefs.edit().putStringSet("catalog", catalog).apply()
                    if (catalog.size >= 10) unlock("catalog_10")
                    if (catalog.size >= 30) unlock("catalog_30")
                    if (catalog.size >= 60) unlock("catalog_60")
                    if (catalog.size >= 100) unlock("catalog_100")
                }
            }
            tag.startsWith("chain:") -> {
                val n = tag.removePrefix("chain:").toIntOrNull() ?: 0
                if (n >= 3) unlock("combo_3")
            }
            tag == "heat" -> unlock("first_heat")
            tag == "stir" -> unlock("first_dissolve")
            tag == "temp300" -> unlock("hot_300")
            tag == "temp500" -> unlock("hot_500")
            tag == "cool" -> unlock("cool_down")
            tag == "evaporate" -> unlock("evaporate_crystal")
            tag == "vent" -> unlock("vent_gas")
            tag == "clean" -> unlock("clean_lab")
            tag == "switch" -> unlock("swap_gear")
        }
    }

    /** 清空全部进度：成就、图鉴与所有计数器 */
    fun reset() {
        prefs.edit().clear().apply()
        catalog.clear()
        seenCategories.clear()
    }

    // ================= 图鉴查询（供合并界面使用） =================
    /** 图鉴已收集物质列表（按名称排序） */
    fun catalogSubstances(): List<Substance> =
        catalog.mapNotNull { runCatching { Substances.get(it) }.getOrNull() }.sortedBy { it.cn }

    /** 是否已收集某物质 */
    fun isCollected(id: String): Boolean = catalog.contains(id)

    /** 图鉴收集进度 */
    fun collectedCount(): Int = catalog.size

    /** 图鉴总物质数 */
    fun catalogTotal(): Int = Substances.all().size

    private fun isNeutralization(r: Reaction): Boolean {
        if (r.type != ReactionType.DOUBLE_DISPLACEMENT) return false
        val hasAcid = r.reactants.any { Substances.get(it.id).category == Category.ACID }
        val hasBase = r.reactants.any { Substances.get(it.id).category == Category.BASE }
        val water = r.products.any { it.id == "h2o" }
        return hasAcid && hasBase && water
    }
}