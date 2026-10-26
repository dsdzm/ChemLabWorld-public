package com.chemlab.game

/**
 * 实验器材（作为反应容器/工作台）
 */
data class Equipment(
    val id: String,
    val cn: String,
    val formula: String,   // 用于示意图的简写
    val note: String
) {
    companion object {
        const val BEAKER = "beaker"
        const val TEST_TUBE = "testtube"
        const val FLASK = "flask"
        const val GAS_BOTTLE = "gasbottle"
        const val EVAP_DISH = "evapdish"

        // —— 「器材联动实验」模式新增器材 ——
        const val ALCOHOL_LAMP = "alcohollamp"
        const val GAS_COLLECTOR = "gascollector"
        const val FUNNEL = "funnel"
        const val BATTERY = "battery"
    }
}

object Equipments {
    val BEAKER = Equipment(Equipment.BEAKER, "烧杯", "B", "盛放液体、溶解固体，垫石棉网加热")
    val TEST_TUBE = Equipment(Equipment.TEST_TUBE, "试管", "T", "少量试剂反应，可直接加热")
    val FLASK = Equipment(Equipment.FLASK, "锥形瓶", "F", "振荡不易溅出，常用于滴定/反应")
    val GAS_BOTTLE = Equipment(Equipment.GAS_BOTTLE, "集气瓶", "G", "收集气体，一般不能加热")
    val EVAP_DISH = Equipment(Equipment.EVAP_DISH, "蒸发皿", "E", "蒸发浓缩溶液、结晶")

    val ALL = listOf(BEAKER, TEST_TUBE, FLASK, GAS_BOTTLE, EVAP_DISH)

    /**
     * 联动实验专用器材。
     * 刻意不放入 [ALL]：原有实验台的「换容器」循环与成就判定均基于 ALL，
     * 保持 ALL 不变即可确保新增器材不影响既有玩法。
     */
    val ALCOHOL_LAMP = Equipment(Equipment.ALCOHOL_LAMP, "酒精灯", "L", "热源：与容器连接后固定到其下方供热，可随时分离")
    val GAS_COLLECTOR = Equipment(Equipment.GAS_COLLECTOR, "集气瓶", "GC", "集气：gas_in 进气 / water_out 排水")
    val FUNNEL = Equipment(Equipment.FUNNEL, "漏斗", "N", "加液：liquid_in 进 → liquid_out 出")
    val BATTERY = Equipment(Equipment.BATTERY, "电源", "V", "电解电源：接线端直接接入盛有液体的反应容器")

    fun byId(id: String): Equipment = ALL.first { it.id == id }
}