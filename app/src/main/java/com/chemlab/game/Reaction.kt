package com.chemlab.game

/**
 * 反应类型：四大基本反应 + 氧化还原（纯氧化还原，超出四大基本范畴的用 REDOX）
 */
enum class ReactionType(val label: String) {
    COMBINATION("化合反应"),
    DECOMPOSITION("分解反应"),
    DISPLACEMENT("置换反应"),
    DOUBLE_DISPLACEMENT("复分解反应"),
    REDOX("氧化还原反应")
}

/** 一个物质的计量系数：id + 系数 */
data class Spec(val id: String, val coef: Int)

/** 反应条件 */
data class Condition(
    val minTemp: Double = 25.0,        // 需要达到的温度（℃）
    val needsIgnition: Boolean = false, // 点燃
    val needsElectrolysis: Boolean = false, // 通电
    val catalystId: String? = null      // 需要该物质存在作催化剂（不消耗）
)

/** 反应现象 / 驱动因素 */
data class Effect(
    val gasId: String? = null,          // 主要生成气体
    val precipitateId: String? = null,  // 主要生成沉淀
    val colorChange: String? = null,    // 颜色/现象描述
    val light: Boolean = false,         // 是否发光（燃烧）
    val heatKj: Double = 0.0            // >0 放热，<0 吸热
)

/**
 * 一条化学反应
 * 系数直接用作物质的量（mol），引擎按比例校验与消耗。
 */
data class Reaction(
    val id: String,
    val type: ReactionType,
    val isRedox: Boolean,
    val reactants: List<Spec>,
    val products: List<Spec>,
    val condition: Condition = Condition(),
    val effect: Effect = Effect()
) {
    fun equation(): String {
        val r = reactants.joinToString(" + ") { format(it, productSide = false) }
        val p = products.joinToString(" + ") { format(it, productSide = true) }
        return "$r ${conditionText()} $p"
    }

    private fun format(sp: Spec, productSide: Boolean): String {
        val s = Substances.get(sp.id)
        val coef = if (sp.coef > 1) sp.coef.toString() else ""
        val suffix = if (productSide) {
            when {
                s.isGas -> "↑"
                sp.id == effect.precipitateId -> "↓"
                else -> ""
            }
        } else ""
        return coef + s.formula + suffix
    }

    private fun conditionText(): String = when {
        condition.needsElectrolysis -> "—通电→"
        condition.needsIgnition -> "—点燃→"
        condition.catalystId != null && condition.minTemp > 30 -> "—催化剂,△→"
        condition.catalystId != null -> "—催化剂→"
        condition.minTemp > 30 -> "—△→"
        else -> "→"
    }
}

/** 反应数据库 */
object Reactions {

    private fun rxn(
        id: String, type: ReactionType, redox: Boolean,
        reactants: List<Spec>, products: List<Spec>,
        cond: Condition = Condition(), eff: Effect = Effect()
    ) = Reaction(id, type, redox, reactants, products, cond, eff)

    private fun sp(id: String, coef: Int) = Spec(id, coef)

    // ============ 紧凑构造器：用于批量扩充反应库（id 自动生成，避免与手写条目冲突） ============
    // 参数按「id, 系数」成对给出；起止段用不同的构造器名区分反应物/产物数量。

    private fun comb(a: String, ac: Int, b: String, bc: Int, c: String, cc: Int,
                     eff: Effect = Effect(), cond: Condition = Condition(), redox: Boolean = false) =
        rxn("x_comb_${a}_$b", ReactionType.COMBINATION, redox,
            listOf(sp(a, ac), sp(b, bc)), listOf(sp(c, cc)), cond, eff)

    private fun comb2(a: String, ac: Int, b: String, bc: Int, c: String, cc: Int, d: String, dc: Int,
                      eff: Effect = Effect(), cond: Condition = Condition(), redox: Boolean = false) =
        rxn("x_comb2_${a}_$b", ReactionType.COMBINATION, redox,
            listOf(sp(a, ac), sp(b, bc)), listOf(sp(c, cc), sp(d, dc)), cond, eff)

    private fun dd(a: String, ac: Int, b: String, bc: Int, c: String, cc: Int, d: String, dc: Int,
                   eff: Effect = Effect(), cond: Condition = Condition(), redox: Boolean = false) =
        rxn("x_dd_${a}_$b", ReactionType.DOUBLE_DISPLACEMENT, redox,
            listOf(sp(a, ac), sp(b, bc)), listOf(sp(c, cc), sp(d, dc)), cond, eff)

    private fun dd3(a: String, ac: Int, b: String, bc: Int, c: String, cc: Int, d: String, dc: Int, e: String, ec: Int,
                    eff: Effect = Effect(), cond: Condition = Condition(), redox: Boolean = false) =
        rxn("x_dd3_${a}_$b", ReactionType.DOUBLE_DISPLACEMENT, redox,
            listOf(sp(a, ac), sp(b, bc)), listOf(sp(c, cc), sp(d, dc), sp(e, ec)), cond, eff)

    private fun dd4(a: String, ac: Int, b: String, bc: Int, c: String, cc: Int, d: String, dc: Int, e: String, ec: Int, f: String, fc: Int,
                    eff: Effect = Effect(), cond: Condition = Condition(), redox: Boolean = false) =
        rxn("x_dd4_${a}_$b", ReactionType.DOUBLE_DISPLACEMENT, redox,
            listOf(sp(a, ac), sp(b, bc)), listOf(sp(c, cc), sp(d, dc), sp(e, ec), sp(f, fc)), cond, eff)

    private fun dec(a: String, ac: Int, c: String, cc: Int, d: String, dc: Int,
                    eff: Effect = Effect(), cond: Condition = Condition(), redox: Boolean = false) =
        rxn("x_dec_$a", ReactionType.DECOMPOSITION, redox,
            listOf(sp(a, ac)), listOf(sp(c, cc), sp(d, dc)), cond, eff)

    private fun dec1(a: String, ac: Int, c: String, cc: Int,
                     eff: Effect = Effect(), cond: Condition = Condition(), redox: Boolean = false) =
        rxn("x_dec1_$a", ReactionType.DECOMPOSITION, redox,
            listOf(sp(a, ac)), listOf(sp(c, cc)), cond, eff)

    private fun dec3(a: String, ac: Int, c: String, cc: Int, d: String, dc: Int, e: String, ec: Int,
                     eff: Effect = Effect(), cond: Condition = Condition(), redox: Boolean = false) =
        rxn("x_dec3_$a", ReactionType.DECOMPOSITION, redox,
            listOf(sp(a, ac)), listOf(sp(c, cc), sp(d, dc), sp(e, ec)), cond, eff)

    private fun displ(a: String, ac: Int, b: String, bc: Int, c: String, cc: Int, d: String, dc: Int,
                      eff: Effect = Effect(), cond: Condition = Condition()) =
        rxn("x_displ_${a}_$b", ReactionType.DISPLACEMENT, true,
            listOf(sp(a, ac), sp(b, bc)), listOf(sp(c, cc), sp(d, dc)), cond, eff)

    private fun rdx1(a: String, ac: Int, b: String, bc: Int, c: String, cc: Int,
                     eff: Effect = Effect(), cond: Condition = Condition()) =
        rxn("x_rdx1_${a}_$b", ReactionType.REDOX, true,
            listOf(sp(a, ac), sp(b, bc)), listOf(sp(c, cc)), cond, eff)

    private fun rdx(a: String, ac: Int, b: String, bc: Int, c: String, cc: Int, d: String, dc: Int,
                    eff: Effect = Effect(), cond: Condition = Condition()) =
        rxn("x_rdx_${a}_$b", ReactionType.REDOX, true,
            listOf(sp(a, ac), sp(b, bc)), listOf(sp(c, cc), sp(d, dc)), cond, eff)

    private fun rdx3(a: String, ac: Int, b: String, bc: Int, c: String, cc: Int, d: String, dc: Int, e: String, ec: Int,
                     eff: Effect = Effect(), cond: Condition = Condition()) =
        rxn("x_rdx3_${a}_$b", ReactionType.REDOX, true,
            listOf(sp(a, ac), sp(b, bc)), listOf(sp(c, cc), sp(d, dc), sp(e, ec)), cond, eff)

    val ALL: List<Reaction> = listOf(
        // ================= 化合反应 =================
        rxn("c_o2", ReactionType.COMBINATION, true,
            listOf(sp("c", 1), sp("o2", 1)), listOf(sp("co2", 1)),
            Condition(needsIgnition = true), Effect(gasId = "co2", light = true, heatKj = 393.0, colorChange = "发白光、放热，生成无色气体")),
        rxn("s_o2", ReactionType.COMBINATION, true,
            listOf(sp("s", 1), sp("o2", 1)), listOf(sp("so2", 1)),
            Condition(needsIgnition = true), Effect(gasId = "so2", light = true, heatKj = 297.0, colorChange = "蓝紫色火焰，生成刺激性气体")),
        rxn("p_o2", ReactionType.COMBINATION, true,
            listOf(sp("p", 4), sp("o2", 5)), listOf(sp("p2o5", 2)),
            Condition(needsIgnition = true), Effect(precipitateId = "p2o5", light = true, heatKj = 0.0, colorChange = "产生白烟，生成白色固体")),
        rxn("h2_o2", ReactionType.COMBINATION, true,
            listOf(sp("h2", 2), sp("o2", 1)), listOf(sp("h2o", 2)),
            Condition(needsIgnition = true), Effect(light = true, heatKj = 571.0, colorChange = "淡蓝色火焰，生成水")),
        rxn("mg_o2", ReactionType.COMBINATION, true,
            listOf(sp("mg", 2), sp("o2", 1)), listOf(sp("mgo", 2)),
            Condition(needsIgnition = true), Effect(light = true, heatKj = 1203.0, colorChange = "耀眼白光，生成白色固体")),
        rxn("fe_o2", ReactionType.COMBINATION, true,
            listOf(sp("fe", 3), sp("o2", 2)), listOf(sp("fe3o4", 1)),
            Condition(needsIgnition = true), Effect(light = true, heatKj = 1117.0, colorChange = "火星四射，生成黑色固体")),
        rxn("al_o2", ReactionType.COMBINATION, true,
            listOf(sp("al", 4), sp("o2", 3)), listOf(sp("al2o3", 2)),
            Condition(needsIgnition = true), Effect(light = true, heatKj = 3351.0, colorChange = "剧烈燃烧，生成白色固体")),
        rxn("cu_o2", ReactionType.COMBINATION, true,
            listOf(sp("cu", 2), sp("o2", 1)), listOf(sp("cuo", 2)),
            Condition(minTemp = 400.0), Effect(colorChange = "红色固体变黑，生成黑色氧化铜")),
        rxn("co_o2", ReactionType.COMBINATION, true,
            listOf(sp("co", 2), sp("o2", 1)), listOf(sp("co2", 2)),
            Condition(needsIgnition = true), Effect(gasId = "co2", light = true, heatKj = 566.0, colorChange = "蓝色火焰")),
        rxn("cao_h2o", ReactionType.COMBINATION, false,
            listOf(sp("cao", 1), sp("h2o", 1)), listOf(sp("caoh2", 1)),
            Condition(), Effect(heatKj = 63.0, colorChange = "放热，块状固体松散（生石灰变熟石灰）")),

        // ================= 分解反应 =================
        rxn("h2o2_decomp", ReactionType.DECOMPOSITION, true,
            listOf(sp("h2o2", 2)), listOf(sp("h2o", 2), sp("o2", 1)),
            Condition(catalystId = "mno2"), Effect(gasId = "o2", colorChange = "迅速产生气泡，生成氧气")),
        rxn("kclo3_decomp", ReactionType.DECOMPOSITION, true,
            listOf(sp("kclo3", 2)), listOf(sp("kcl", 2), sp("o2", 3)),
            Condition(minTemp = 200.0, catalystId = "mno2"), Effect(gasId = "o2", colorChange = "加热并加MnO₂，生成氧气")),
        rxn("kmno4_decomp", ReactionType.DECOMPOSITION, true,
            listOf(sp("kmno4", 2)), listOf(sp("k2mno4", 1), sp("mno2", 1), sp("o2", 1)),
            Condition(minTemp = 200.0), Effect(gasId = "o2", colorChange = "紫黑色固体分解，生成氧气")),
        rxn("caco3_decomp", ReactionType.DECOMPOSITION, false,
            listOf(sp("caco3", 1)), listOf(sp("cao", 1), sp("co2", 1)),
            Condition(minTemp = 700.0), Effect(gasId = "co2", heatKj = -178.0, colorChange = "高温煅烧，生成生石灰与二氧化碳")),
        rxn("h2o_electrolysis", ReactionType.DECOMPOSITION, true,
            listOf(sp("h2o", 2)), listOf(sp("h2", 2), sp("o2", 1)),
            Condition(needsElectrolysis = true), Effect(gasId = "h2", colorChange = "两极产生气泡，生成氢气和氧气")),
        rxn("h2co3_decomp", ReactionType.DECOMPOSITION, false,
            listOf(sp("h2co3", 1)), listOf(sp("h2o", 1), sp("co2", 1)),
            Condition(), Effect(gasId = "co2", colorChange = "碳酸不稳定，自动分解")),

        // ================= 置换反应 =================
        rxn("zn_hcl", ReactionType.DISPLACEMENT, true,
            listOf(sp("zn", 1), sp("hcl", 2)), listOf(sp("zncl2", 1), sp("h2", 1)),
            Condition(), Effect(gasId = "h2", colorChange = "产生气泡，生成氢气")),
        rxn("zn_h2so4", ReactionType.DISPLACEMENT, true,
            listOf(sp("zn", 1), sp("h2so4", 1)), listOf(sp("znso4", 1), sp("h2", 1)),
            Condition(), Effect(gasId = "h2", colorChange = "产生气泡，生成氢气")),
        rxn("fe_hcl", ReactionType.DISPLACEMENT, true,
            listOf(sp("fe", 1), sp("hcl", 2)), listOf(sp("fecl2", 1), sp("h2", 1)),
            Condition(), Effect(gasId = "h2", colorChange = "产生气泡，溶液变浅绿色")),
        rxn("fe_h2so4", ReactionType.DISPLACEMENT, true,
            listOf(sp("fe", 1), sp("h2so4", 1)), listOf(sp("feso4", 1), sp("h2", 1)),
            Condition(), Effect(gasId = "h2", colorChange = "产生气泡，溶液变浅绿色")),
        rxn("mg_hcl", ReactionType.DISPLACEMENT, true,
            listOf(sp("mg", 1), sp("hcl", 2)), listOf(sp("mgcl2", 1), sp("h2", 1)),
            Condition(), Effect(gasId = "h2", heatKj = 462.0, colorChange = "剧烈反应放热，产生气泡")),
        rxn("mg_h2so4", ReactionType.DISPLACEMENT, true,
            listOf(sp("mg", 1), sp("h2so4", 1)), listOf(sp("mgso4", 1), sp("h2", 1)),
            Condition(), Effect(gasId = "h2", heatKj = 469.0, colorChange = "剧烈反应放热，产生气泡")),
        rxn("al_hcl", ReactionType.DISPLACEMENT, true,
            listOf(sp("al", 2), sp("hcl", 6)), listOf(sp("alcl3", 2), sp("h2", 3)),
            Condition(), Effect(gasId = "h2", colorChange = "产生气泡，生成氢气")),
        rxn("al_h2so4", ReactionType.DISPLACEMENT, true,
            listOf(sp("al", 2), sp("h2so4", 3)), listOf(sp("al2so43", 1), sp("h2", 3)),
            Condition(), Effect(gasId = "h2", colorChange = "产生气泡，生成氢气")),
        rxn("fe_cuso4", ReactionType.DISPLACEMENT, true,
            listOf(sp("fe", 1), sp("cuso4", 1)), listOf(sp("feso4", 1), sp("cu", 1)),
            Condition(), Effect(precipitateId = "cu", colorChange = "铁表面析出红色铜，溶液由蓝变浅绿")),
        rxn("zn_cuso4", ReactionType.DISPLACEMENT, true,
            listOf(sp("zn", 1), sp("cuso4", 1)), listOf(sp("znso4", 1), sp("cu", 1)),
            Condition(), Effect(precipitateId = "cu", colorChange = "锌表面析出红色铜，溶液蓝色变浅")),
        rxn("cu_agno3", ReactionType.DISPLACEMENT, true,
            listOf(sp("cu", 1), sp("agno3", 2)), listOf(sp("cuno32", 1), sp("ag", 2)),
            Condition(), Effect(precipitateId = "ag", colorChange = "铜表面析出银白色金属，溶液变蓝")),
        rxn("na_h2o", ReactionType.DISPLACEMENT, true,
            listOf(sp("na", 2), sp("h2o", 2)), listOf(sp("naoh", 2), sp("h2", 1)),
            Condition(), Effect(gasId = "h2", heatKj = 368.0, colorChange = "钠浮于水面剧烈游动，产生气体并放热")),

        // ================= 复分解反应 =================
        // 中和
        rxn("naoh_hcl", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("naoh", 1), sp("hcl", 1)), listOf(sp("nacl", 1), sp("h2o", 1)),
            Condition(), Effect(heatKj = 57.0, colorChange = "酸碱中和，放热")),
        rxn("koh_hcl", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("koh", 1), sp("hcl", 1)), listOf(sp("kcl", 1), sp("h2o", 1)),
            Condition(), Effect(heatKj = 57.0, colorChange = "酸碱中和，放热")),
        rxn("naoh_h2so4", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("naoh", 2), sp("h2so4", 1)), listOf(sp("na2so4", 1), sp("h2o", 2)),
            Condition(), Effect(heatKj = 114.0, colorChange = "酸碱中和，放热")),
        rxn("koh_h2so4", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("koh", 2), sp("h2so4", 1)), listOf(sp("k2so4", 1), sp("h2o", 2)),
            Condition(), Effect(heatKj = 114.0, colorChange = "酸碱中和，放热")),
        rxn("caoh2_hcl", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("caoh2", 1), sp("hcl", 2)), listOf(sp("cacl2", 1), sp("h2o", 2)),
            Condition(), Effect(colorChange = "酸碱中和")),
        rxn("naoh_hno3", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("naoh", 1), sp("hno3", 1)), listOf(sp("nano3", 1), sp("h2o", 1)),
            Condition(), Effect(colorChange = "酸碱中和")),
        rxn("baoh2_h2so4", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("baoh2", 1), sp("h2so4", 1)), listOf(sp("baso4", 1), sp("h2o", 2)),
            Condition(), Effect(precipitateId = "baso4", colorChange = "生成白色沉淀（硫酸钡）")),
        rxn("nh3h2o_hcl", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("nh3h2o", 1), sp("hcl", 1)), listOf(sp("nh4cl", 1), sp("h2o", 1)),
            Condition(), Effect(colorChange = "酸碱中和")),
        // 沉淀
        rxn("cuso4_naoh", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("cuso4", 1), sp("naoh", 2)), listOf(sp("cuoh2", 1), sp("na2so4", 1)),
            Condition(), Effect(precipitateId = "cuoh2", colorChange = "生成蓝色沉淀（氢氧化铜）")),
        rxn("fecl3_naoh", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("fecl3", 1), sp("naoh", 3)), listOf(sp("feoh3", 1), sp("nacl", 3)),
            Condition(), Effect(precipitateId = "feoh3", colorChange = "生成红褐色沉淀（氢氧化铁）")),
        rxn("feso4_naoh", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("feso4", 1), sp("naoh", 2)), listOf(sp("feoh2", 1), sp("na2so4", 1)),
            Condition(), Effect(precipitateId = "feoh2", colorChange = "生成白色沉淀（氢氧化亚铁）")),
        rxn("mgcl2_naoh", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("mgcl2", 1), sp("naoh", 2)), listOf(sp("mgoh2", 1), sp("nacl", 2)),
            Condition(), Effect(precipitateId = "mgoh2", colorChange = "生成白色沉淀（氢氧化镁）")),
        rxn("alcl3_naoh", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("alcl3", 1), sp("naoh", 3)), listOf(sp("aloh3", 1), sp("nacl", 3)),
            Condition(), Effect(precipitateId = "aloh3", colorChange = "生成白色胶状沉淀（氢氧化铝）")),
        rxn("znso4_naoh", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("znso4", 1), sp("naoh", 2)), listOf(sp("znoh2", 1), sp("na2so4", 1)),
            Condition(), Effect(precipitateId = "znoh2", colorChange = "生成白色沉淀（氢氧化锌）")),
        rxn("agno3_nacl", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("agno3", 1), sp("nacl", 1)), listOf(sp("agcl", 1), sp("nano3", 1)),
            Condition(), Effect(precipitateId = "agcl", colorChange = "生成白色沉淀（氯化银）")),
        rxn("agno3_hcl", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("agno3", 1), sp("hcl", 1)), listOf(sp("agcl", 1), sp("hno3", 1)),
            Condition(), Effect(precipitateId = "agcl", colorChange = "生成白色沉淀（氯化银）")),
        rxn("bacl2_h2so4", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("bacl2", 1), sp("h2so4", 1)), listOf(sp("baso4", 1), sp("hcl", 2)),
            Condition(), Effect(precipitateId = "baso4", colorChange = "生成不溶于酸的白色沉淀（硫酸钡）")),
        rxn("bacl2_na2so4", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("bacl2", 1), sp("na2so4", 1)), listOf(sp("baso4", 1), sp("nacl", 2)),
            Condition(), Effect(precipitateId = "baso4", colorChange = "生成白色沉淀（硫酸钡）")),
        rxn("bano32_h2so4", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("bano32", 1), sp("h2so4", 1)), listOf(sp("baso4", 1), sp("hno3", 2)),
            Condition(), Effect(precipitateId = "baso4", colorChange = "生成白色沉淀（硫酸钡）")),
        // 碳酸盐 + 酸 → 气体
        rxn("na2co3_hcl", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("na2co3", 1), sp("hcl", 2)), listOf(sp("nacl", 2), sp("h2o", 1), sp("co2", 1)),
            Condition(), Effect(gasId = "co2", colorChange = "大量气泡，生成二氧化碳")),
        rxn("nahco3_hcl", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("nahco3", 1), sp("hcl", 1)), listOf(sp("nacl", 1), sp("h2o", 1), sp("co2", 1)),
            Condition(), Effect(gasId = "co2", colorChange = "产生气泡，生成二氧化碳")),
        rxn("caco3_hcl", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("caco3", 1), sp("hcl", 2)), listOf(sp("cacl2", 1), sp("h2o", 1), sp("co2", 1)),
            Condition(), Effect(gasId = "co2", colorChange = "固体溶解并产生气泡，生成二氧化碳")),
        rxn("na2co3_h2so4", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("na2co3", 1), sp("h2so4", 1)), listOf(sp("na2so4", 1), sp("h2o", 1), sp("co2", 1)),
            Condition(), Effect(gasId = "co2", colorChange = "产生气泡，生成二氧化碳")),
        // 酸 + 金属氧化物
        rxn("fe2o3_hcl", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("fe2o3", 1), sp("hcl", 6)), listOf(sp("fecl3", 2), sp("h2o", 3)),
            Condition(minTemp = 60.0), Effect(colorChange = "铁锈溶解，溶液变黄色")),
        rxn("cuo_h2so4", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("cuo", 1), sp("h2so4", 1)), listOf(sp("cuso4", 1), sp("h2o", 1)),
            Condition(minTemp = 60.0), Effect(colorChange = "黑色固体溶解，溶液变蓝色")),
        rxn("cuo_hcl", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("cuo", 1), sp("hcl", 2)), listOf(sp("cucl2", 1), sp("h2o", 1)),
            Condition(minTemp = 60.0), Effect(colorChange = "黑色固体溶解，溶液变蓝绿色")),
        rxn("cao_hcl", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("cao", 1), sp("hcl", 2)), listOf(sp("cacl2", 1), sp("h2o", 1)),
            Condition(), Effect(heatKj = 190.0, colorChange = "放热，固体溶解")),
        // 碱 + 非金属氧化物
        rxn("co2_naoh", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("co2", 1), sp("naoh", 2)), listOf(sp("na2co3", 1), sp("h2o", 1)),
            Condition(), Effect(colorChange = "碱液吸收二氧化碳")),
        rxn("co2_caoh2", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("co2", 1), sp("caoh2", 1)), listOf(sp("caco3", 1), sp("h2o", 1)),
            Condition(), Effect(precipitateId = "caco3", colorChange = "澄清石灰水变浑浊（生成碳酸钙）")),
        rxn("so2_naoh", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("so2", 1), sp("naoh", 2)), listOf(sp("na2so3", 1), sp("h2o", 1)),
            Condition(), Effect(colorChange = "碱液吸收二氧化硫")),
        rxn("co2_koh", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("co2", 1), sp("koh", 2)), listOf(sp("k2co3", 1), sp("h2o", 1)),
            Condition(), Effect(colorChange = "碱液吸收二氧化碳，生成碳酸钾")),

        // ================= 氧化还原（超出四大基本） =================
        rxn("co_cuo", ReactionType.REDOX, true,
            listOf(sp("co", 1), sp("cuo", 1)), listOf(sp("cu", 1), sp("co2", 1)),
            Condition(minTemp = 200.0), Effect(precipitateId = "cu", gasId = "co2", colorChange = "黑色固体变红，生成使石灰水变浑浊的气体")),
        rxn("h2_cuo", ReactionType.REDOX, true,
            listOf(sp("h2", 1), sp("cuo", 1)), listOf(sp("cu", 1), sp("h2o", 1)),
            Condition(minTemp = 200.0), Effect(precipitateId = "cu", colorChange = "黑色固体变红，生成水")),
        rxn("c_cuo", ReactionType.REDOX, true,
            listOf(sp("c", 1), sp("cuo", 2)), listOf(sp("cu", 2), sp("co2", 1)),
            Condition(minTemp = 600.0), Effect(precipitateId = "cu", gasId = "co2", colorChange = "黑色固体变红，生成气体")),
        rxn("co_fe2o3", ReactionType.REDOX, true,
            listOf(sp("co", 3), sp("fe2o3", 1)), listOf(sp("fe", 2), sp("co2", 3)),
            Condition(minTemp = 600.0), Effect(precipitateId = "fe", gasId = "co2", colorChange = "红棕色粉末变灰黑，生成二氧化碳（炼铁原理）")),
        rxn("na_cl2", ReactionType.REDOX, true,
            listOf(sp("na", 2), sp("cl2", 1)), listOf(sp("nacl", 2)),
            Condition(needsIgnition = true), Effect(light = true, heatKj = 821.0, colorChange = "剧烈燃烧，生成白色固体")),

        // ================= 补充反应 =================
        rxn("fe_s", ReactionType.COMBINATION, true,
            listOf(sp("fe", 1), sp("s", 1)), listOf(sp("fes", 1)),
            Condition(minTemp = 150.0), Effect(colorChange = "红热反应，生成黑色硫化亚铁")),
        rxn("c_co2", ReactionType.COMBINATION, true,
            listOf(sp("c", 1), sp("co2", 1)), listOf(sp("co", 2)),
            Condition(minTemp = 700.0), Effect(gasId = "co", colorChange = "碳还原二氧化碳，生成一氧化碳")),
        rxn("so2_h2o", ReactionType.COMBINATION, false,
            listOf(sp("so2", 1), sp("h2o", 1)), listOf(sp("h2so3", 1)),
            Condition(), Effect(colorChange = "二氧化硫溶于水，生成亚硫酸")),
        rxn("p2o5_h2o", ReactionType.COMBINATION, false,
            listOf(sp("p2o5", 1), sp("h2o", 3)), listOf(sp("h3po4", 2)),
            Condition(), Effect(heatKj = 130.0, colorChange = "五氧化二磷溶于水并放热，生成磷酸")),

        rxn("nahco3_decomp", ReactionType.DECOMPOSITION, false,
            listOf(sp("nahco3", 2)), listOf(sp("na2co3", 1), sp("h2o", 1), sp("co2", 1)),
            Condition(minTemp = 150.0), Effect(gasId = "co2", colorChange = "小苏打受热分解，生成二氧化碳")),
        rxn("cuoh2_decomp", ReactionType.DECOMPOSITION, false,
            listOf(sp("cuoh2", 1)), listOf(sp("cuo", 1), sp("h2o", 1)),
            Condition(minTemp = 200.0), Effect(colorChange = "蓝色固体变黑，生成氧化铜")),
        rxn("feoh3_decomp", ReactionType.DECOMPOSITION, false,
            listOf(sp("feoh3", 2)), listOf(sp("fe2o3", 1), sp("h2o", 3)),
            Condition(minTemp = 200.0), Effect(colorChange = "红褐色固体变为红棕色，生成氧化铁")),
        rxn("ag2o_decomp", ReactionType.DECOMPOSITION, true,
            listOf(sp("ag2o", 2)), listOf(sp("ag", 4), sp("o2", 1)),
            Condition(minTemp = 300.0), Effect(gasId = "o2", colorChange = "棕黑色固体分解，生成银白色金属")),

        rxn("al_cuso4", ReactionType.DISPLACEMENT, true,
            listOf(sp("al", 2), sp("cuso4", 3)), listOf(sp("al2so43", 1), sp("cu", 3)),
            Condition(), Effect(precipitateId = "cu", colorChange = "铝表面析出红色铜，溶液蓝色变浅")),
        rxn("mg_h2o", ReactionType.DISPLACEMENT, true,
            listOf(sp("mg", 1), sp("h2o", 2)), listOf(sp("mgoh2", 1), sp("h2", 1)),
            Condition(minTemp = 60.0), Effect(gasId = "h2", colorChange = "镁与热水反应，产生气泡")),
        rxn("fe_cucl2", ReactionType.DISPLACEMENT, true,
            listOf(sp("fe", 1), sp("cucl2", 1)), listOf(sp("fecl2", 1), sp("cu", 1)),
            Condition(), Effect(precipitateId = "cu", colorChange = "铁表面析出红色铜，溶液变浅绿")),
        rxn("zn_agno3", ReactionType.DISPLACEMENT, true,
            listOf(sp("zn", 1), sp("agno3", 2)), listOf(sp("znno32", 1), sp("ag", 2)),
            Condition(), Effect(precipitateId = "ag", colorChange = "锌表面析出银白色金属")),
        rxn("fe_agno3", ReactionType.DISPLACEMENT, true,
            listOf(sp("fe", 1), sp("agno3", 2)), listOf(sp("feno32", 1), sp("ag", 2)),
            Condition(), Effect(precipitateId = "ag", colorChange = "铁表面析出银白色金属，溶液变浅绿")),

        rxn("fecl2_naoh", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("fecl2", 1), sp("naoh", 2)), listOf(sp("feoh2", 1), sp("nacl", 2)),
            Condition(), Effect(precipitateId = "feoh2", colorChange = "生成白色沉淀（氢氧化亚铁）")),
        rxn("caoh2_na2co3", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("caoh2", 1), sp("na2co3", 1)), listOf(sp("caco3", 1), sp("naoh", 2)),
            Condition(), Effect(precipitateId = "caco3", colorChange = "生成白色沉淀（纯碱制烧碱）")),
        rxn("cuso4_bacl2", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("cuso4", 1), sp("bacl2", 1)), listOf(sp("baso4", 1), sp("cucl2", 1)),
            Condition(), Effect(precipitateId = "baso4", colorChange = "生成白色沉淀（硫酸钡），溶液变蓝绿")),
        rxn("na2co3_cacl2", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("na2co3", 1), sp("cacl2", 1)), listOf(sp("caco3", 1), sp("nacl", 2)),
            Condition(), Effect(precipitateId = "caco3", colorChange = "生成白色沉淀（碳酸钙）")),
        rxn("naoh_nh4cl", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("naoh", 1), sp("nh4cl", 1)), listOf(sp("nacl", 1), sp("h2o", 1), sp("nh3", 1)),
            Condition(minTemp = 60.0), Effect(gasId = "nh3", colorChange = "加热产生刺激气味气体（氨气）")),

        rxn("mg_co2", ReactionType.REDOX, true,
            listOf(sp("mg", 2), sp("co2", 1)), listOf(sp("mgo", 2), sp("c", 1)),
            Condition(needsIgnition = true), Effect(light = true, colorChange = "镁在二氧化碳中剧烈燃烧，生成白黑固体")),
        rxn("c_fe2o3", ReactionType.REDOX, true,
            listOf(sp("c", 3), sp("fe2o3", 2)), listOf(sp("fe", 4), sp("co2", 3)),
            Condition(minTemp = 700.0), Effect(precipitateId = "fe", gasId = "co2", colorChange = "高温下碳还原氧化铁（炼铁原理）")),

        // ================= 第三批补充反应 =================
        rxn("al_fe2o3", ReactionType.REDOX, true,
            listOf(sp("al", 2), sp("fe2o3", 1)), listOf(sp("fe", 2), sp("al2o3", 1)),
            Condition(needsIgnition = true), Effect(light = true, heatKj = 851.0, colorChange = "铝热反应：剧烈发光放热，生成铁（焊接钢轨）")),
        rxn("cl2_h2", ReactionType.COMBINATION, true,
            listOf(sp("cl2", 1), sp("h2", 1)), listOf(sp("hcl", 2)),
            Condition(needsIgnition = true), Effect(light = true, colorChange = "氢气在氯气中燃烧，苍白色火焰")),
        rxn("nh3_hcl", ReactionType.COMBINATION, false,
            listOf(sp("nh3", 1), sp("hcl", 1)), listOf(sp("nh4cl", 1)),
            Condition(), Effect(colorChange = "产生大量白烟（氯化铵固体小颗粒）")),
        rxn("cucl2_naoh", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("cucl2", 1), sp("naoh", 2)), listOf(sp("cuoh2", 1), sp("nacl", 2)),
            Condition(), Effect(precipitateId = "cuoh2", colorChange = "生成蓝色沉淀（氢氧化铜）")),
        rxn("caco3_h2so4", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("caco3", 1), sp("h2so4", 1)), listOf(sp("caso4", 1), sp("h2o", 1), sp("co2", 1)),
            Condition(), Effect(gasId = "co2", precipitateId = "caso4", colorChange = "产生气泡，同时生成微溶的硫酸钙")),
        rxn("mgo_hcl", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("mgo", 1), sp("hcl", 2)), listOf(sp("mgcl2", 1), sp("h2o", 1)),
            Condition(), Effect(colorChange = "白色固体溶解，与盐酸反应")),
        rxn("al2o3_hcl", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("al2o3", 1), sp("hcl", 6)), listOf(sp("alcl3", 2), sp("h2o", 3)),
            Condition(minTemp = 60.0), Effect(colorChange = "氧化铝溶解，与盐酸反应")),
        rxn("fe2o3_h2so4", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("fe2o3", 1), sp("h2so4", 3)), listOf(sp("fe2so43", 1), sp("h2o", 3)),
            Condition(minTemp = 60.0), Effect(colorChange = "铁锈溶解，溶液变黄色")),
        rxn("cuo_hno3", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("cuo", 1), sp("hno3", 2)), listOf(sp("cuno32", 1), sp("h2o", 1)),
            Condition(minTemp = 60.0), Effect(colorChange = "黑色固体溶解，溶液变蓝色")),
        rxn("cu_hno3", ReactionType.REDOX, true,
            listOf(sp("cu", 3), sp("hno3", 8)), listOf(sp("cuno32", 3), sp("no2", 2), sp("h2o", 4)),
            Condition(), Effect(gasId = "no2", colorChange = "铜溶解产生红棕色气体（二氧化氮）")),
        rxn("h2so3_decomp", ReactionType.DECOMPOSITION, true,
            listOf(sp("h2so3", 1)), listOf(sp("h2o", 1), sp("so2", 1)),
            Condition(minTemp = 45.0), Effect(gasId = "so2", colorChange = "亚硫酸受热分解，放出二氧化硫")),
        rxn("h2so3_naoh", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("h2so3", 1), sp("naoh", 2)), listOf(sp("na2so3", 1), sp("h2o", 2)),
            Condition(), Effect(colorChange = "酸碱中和，生成亚硫酸钠")),
        rxn("h3po4_naoh", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("h3po4", 1), sp("naoh", 3)), listOf(sp("na3po4", 1), sp("h2o", 3)),
            Condition(), Effect(colorChange = "酸碱中和，生成磷酸钠")),
        rxn("fes_hcl", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("fes", 1), sp("hcl", 2)), listOf(sp("fecl2", 1), sp("h2s", 1)),
            Condition(), Effect(gasId = "h2s", colorChange = "产生臭鸡蛋气味的气体（硫化氢）")),
        rxn("na2so3_hcl", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("na2so3", 1), sp("hcl", 2)), listOf(sp("nacl", 2), sp("h2o", 1), sp("so2", 1)),
            Condition(), Effect(gasId = "so2", colorChange = "产生刺激气味气体（二氧化硫）")),
        rxn("baoh2_co2", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("baoh2", 1), sp("co2", 1)), listOf(sp("baco3", 1), sp("h2o", 1)),
            Condition(), Effect(precipitateId = "baco3", colorChange = "生成白色沉淀（碳酸钡）")),

        // ================= 第四批补充反应 =================
        // 碱性氧化物 + 水
        rxn("na2o_h2o", ReactionType.COMBINATION, false,
            listOf(sp("na2o", 1), sp("h2o", 1)), listOf(sp("naoh", 2)),
            Condition(), Effect(heatKj = 86.0, colorChange = "氧化钠溶于水并放热，生成烧碱")),
        rxn("k2o_h2o", ReactionType.COMBINATION, false,
            listOf(sp("k2o", 1), sp("h2o", 1)), listOf(sp("koh", 2)),
            Condition(), Effect(heatKj = 91.0, colorChange = "氧化钾溶于水并放热，生成氢氧化钾")),
        // 卤素置换（活泼卤素置换较不活泼者）
        rxn("br2_ki", ReactionType.DISPLACEMENT, true,
            listOf(sp("br2", 1), sp("ki", 2)), listOf(sp("kbr", 2), sp("i2", 1)),
            Condition(), Effect(precipitateId = "i2", colorChange = "溶液出现紫色（碘单质）")),
        rxn("cl2_ki", ReactionType.DISPLACEMENT, true,
            listOf(sp("cl2", 1), sp("ki", 2)), listOf(sp("kcl", 2), sp("i2", 1)),
            Condition(), Effect(precipitateId = "i2", colorChange = "溶液变棕色/紫色（碘单质）")),
        rxn("cl2_kbr", ReactionType.DISPLACEMENT, true,
            listOf(sp("cl2", 1), sp("kbr", 2)), listOf(sp("kcl", 2), sp("br2", 1)),
            Condition(), Effect(colorChange = "溶液变为橙棕色（溴单质）")),
        // 两性氧化物 + 酸
        rxn("zno_hcl", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("zno", 1), sp("hcl", 2)), listOf(sp("zncl2", 1), sp("h2o", 1)),
            Condition(), Effect(colorChange = "白色固体溶解，生成氯化锌")),
        rxn("zno_h2so4", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("zno", 1), sp("h2so4", 1)), listOf(sp("znso4", 1), sp("h2o", 1)),
            Condition(), Effect(colorChange = "白色固体溶解，生成硫酸锌")),
        // 氯气与碱歧化（工业制漂白液）
        rxn("cl2_naoh", ReactionType.REDOX, true,
            listOf(sp("cl2", 1), sp("naoh", 2)), listOf(sp("nacl", 1), sp("naclo", 1), sp("h2o", 1)),
            Condition(), Effect(colorChange = "黄绿色气体被碱吸收，生成漂白液（歧化反应）")),
        // 次氯酸钠分解
        rxn("naclo_decomp", ReactionType.DECOMPOSITION, true,
            listOf(sp("naclo", 2)), listOf(sp("nacl", 2), sp("o2", 1)),
            Condition(minTemp = 80.0), Effect(gasId = "o2", colorChange = "次氯酸钠受热分解放出氧气")),
        // 电解饱和食盐水（工业制烧碱）
        rxn("nacl_electrolysis", ReactionType.REDOX, true,
            listOf(sp("nacl", 2), sp("h2o", 2)), listOf(sp("naoh", 2), sp("h2", 1), sp("cl2", 1)),
            Condition(needsElectrolysis = true), Effect(gasId = "h2", colorChange = "两极产生气泡，生成烧碱、氢气和氯气")),
        // 硝酸钾热分解
        rxn("kno3_decomp", ReactionType.DECOMPOSITION, true,
            listOf(sp("kno3", 2)), listOf(sp("kno2", 2), sp("o2", 1)),
            Condition(minTemp = 400.0), Effect(gasId = "o2", colorChange = "硝酸钾受热放出氧气")),

        // ================= 第五批补充反应 =================
        // ---------- 化合反应 ----------
        rxn("com_si_o2", ReactionType.COMBINATION, true,
            listOf(sp("si", 1), sp("o2", 1)), listOf(sp("sio2", 1)),
            Condition(minTemp = 400.0), Effect(colorChange = "硅粉燃烧生成白色二氧化硅")),
        rxn("com_ti_o2", ReactionType.COMBINATION, true,
            listOf(sp("ti", 1), sp("o2", 1)), listOf(sp("tio2", 1)),
            Condition(minTemp = 600.0), Effect(colorChange = "钛剧烈燃烧，生成白色二氧化钛")),
        rxn("com_cr_o2", ReactionType.COMBINATION, true,
            listOf(sp("cr", 4), sp("o2", 3)), listOf(sp("cr2o3", 2)),
            Condition(minTemp = 500.0, needsIgnition = true), Effect(light = true, colorChange = "铬燃烧生成绿色三氧化二铬")),
        rxn("com_cu2o_o2", ReactionType.COMBINATION, true,
            listOf(sp("cu2o", 2), sp("o2", 1)), listOf(sp("cuo", 4)),
            Condition(minTemp = 300.0), Effect(colorChange = "砖红色固体被氧化变黑（氧化铜）")),
        rxn("com_so3_h2o", ReactionType.COMBINATION, false,
            listOf(sp("so3", 1), sp("h2o", 1)), listOf(sp("h2so4", 1)),
            Condition(), Effect(heatKj = 79.0, colorChange = "三氧化硫溶于水放热，生成硫酸")),
        rxn("com_no_o2", ReactionType.COMBINATION, true,
            listOf(sp("no", 2), sp("o2", 1)), listOf(sp("no2", 2)),
            Condition(), Effect(gasId = "no2", colorChange = "无色一氧化氮遇氧气变红棕色二氧化氮")),
        rxn("com_pb_o2", ReactionType.COMBINATION, true,
            listOf(sp("pb", 2), sp("o2", 1)), listOf(sp("pbo", 2)),
            Condition(minTemp = 300.0), Effect(colorChange = "铅在高温下氧化生成黄色氧化铅")),
        rxn("com_hg_o2", ReactionType.COMBINATION, true,
            listOf(sp("hg", 2), sp("o2", 1)), listOf(sp("hgo", 2)),
            Condition(minTemp = 350.0), Effect(colorChange = "汞受热缓慢氧化生成红色氧化汞")),
        rxn("com_na_o2", ReactionType.COMBINATION, true,
            listOf(sp("na", 4), sp("o2", 1)), listOf(sp("na2o", 2)),
            Condition(needsIgnition = true), Effect(light = true, colorChange = "钠剧烈燃烧，生成白色氧化钠")),
        rxn("com_k_o2", ReactionType.COMBINATION, true,
            listOf(sp("k", 4), sp("o2", 1)), listOf(sp("k2o", 2)),
            Condition(needsIgnition = true), Effect(light = true, colorChange = "钾剧烈燃烧，生成白色氧化钾")),
        rxn("com_zn_o2", ReactionType.COMBINATION, true,
            listOf(sp("zn", 2), sp("o2", 1)), listOf(sp("zno", 2)),
            Condition(needsIgnition = true), Effect(light = true, colorChange = "锌剧烈燃烧，生成白色氧化锌")),
        rxn("com_cu_cl2", ReactionType.COMBINATION, true,
            listOf(sp("cu", 1), sp("cl2", 1)), listOf(sp("cucl2", 1)),
            Condition(needsIgnition = true), Effect(colorChange = "铜在氯气中燃烧，生成棕黄色氯化铜")),
        rxn("com_fe_cl2", ReactionType.COMBINATION, true,
            listOf(sp("fe", 2), sp("cl2", 3)), listOf(sp("fecl3", 2)),
            Condition(needsIgnition = true), Effect(colorChange = "铁在氯气中燃烧，生成黄色氯化铁")),
        rxn("com_cu_s", ReactionType.COMBINATION, true,
            listOf(sp("cu", 1), sp("s", 1)), listOf(sp("cus", 1)),
            Condition(minTemp = 200.0), Effect(colorChange = "铜与硫共热，生成黑色硫化铜")),
        rxn("com_na2o_co2", ReactionType.COMBINATION, false,
            listOf(sp("na2o", 1), sp("co2", 1)), listOf(sp("na2co3", 1)),
            Condition(), Effect(colorChange = "氧化钠吸收二氧化碳，生成碳酸钠")),
        rxn("com_cao_co2", ReactionType.COMBINATION, false,
            listOf(sp("cao", 1), sp("co2", 1)), listOf(sp("caco3", 1)),
            Condition(), Effect(colorChange = "生石灰吸收二氧化碳，生成碳酸钙")),
        rxn("com_cao_so3", ReactionType.COMBINATION, false,
            listOf(sp("cao", 1), sp("so3", 1)), listOf(sp("caso4", 1)),
            Condition(), Effect(colorChange = "氧化钙与三氧化硫化合，生成硫酸钙")),
        rxn("com_na2o_so2", ReactionType.COMBINATION, false,
            listOf(sp("na2o", 1), sp("so2", 1)), listOf(sp("na2so3", 1)),
            Condition(), Effect(colorChange = "氧化钠吸收二氧化硫，生成亚硫酸钠")),
        rxn("com_na2o_so3", ReactionType.COMBINATION, false,
            listOf(sp("na2o", 1), sp("so3", 1)), listOf(sp("na2so4", 1)),
            Condition(), Effect(colorChange = "氧化钠与三氧化硫化合，生成硫酸钠")),
        rxn("com_mg_cl2", ReactionType.COMBINATION, true,
            listOf(sp("mg", 1), sp("cl2", 1)), listOf(sp("mgcl2", 1)),
            Condition(needsIgnition = true), Effect(light = true, colorChange = "镁在氯气中剧烈燃烧，生成氯化镁")),
        rxn("com_s_h2", ReactionType.COMBINATION, true,
            listOf(sp("s", 1), sp("h2", 1)), listOf(sp("h2s", 1)),
            Condition(minTemp = 200.0), Effect(gasId = "h2s", colorChange = "硫与氢气化合，生成臭鸡蛋味硫化氢")),

        // ---------- 分解反应 ----------
        rxn("hgo_decomp", ReactionType.DECOMPOSITION, true,
            listOf(sp("hgo", 2)), listOf(sp("hg", 2), sp("o2", 1)),
            Condition(minTemp = 300.0), Effect(gasId = "o2", colorChange = "红色氧化汞受热分解，生成液态汞并放出氧气")),
        rxn("nh4no3_decomp", ReactionType.DECOMPOSITION, true,
            listOf(sp("nh4no3", 1)), listOf(sp("n2o", 1), sp("h2o", 2)),
            Condition(minTemp = 200.0), Effect(gasId = "n2o", colorChange = "硝酸铵受热分解，放出无色笑气（一氧化二氮）")),
        rxn("hclo_decomp", ReactionType.DECOMPOSITION, true,
            listOf(sp("hclo", 2)), listOf(sp("hcl", 2), sp("o2", 1)),
            Condition(), Effect(gasId = "o2", colorChange = "次氯酸受热/见光分解，放出氧气")),
        rxn("al2s3_h2o", ReactionType.DECOMPOSITION, false,
            listOf(sp("al2s3", 1), sp("h2o", 6)), listOf(sp("aloh3", 2), sp("h2s", 3)),
            Condition(), Effect(precipitateId = "aloh3", gasId = "h2s", colorChange = "硫化铝遇水双水解，生成白色氢氧化铝并放出臭鸡蛋气体")),

        // ---------- 置换反应 ----------
        rxn("sn_hcl", ReactionType.DISPLACEMENT, true,
            listOf(sp("sn", 1), sp("hcl", 2)), listOf(sp("sncl2", 1), sp("h2", 1)),
            Condition(), Effect(gasId = "h2", colorChange = "锡与盐酸反应，产生气泡生成氢气")),
        rxn("sn_cucl2", ReactionType.DISPLACEMENT, true,
            listOf(sp("sn", 1), sp("cucl2", 1)), listOf(sp("sncl2", 1), sp("cu", 1)),
            Condition(), Effect(precipitateId = "cu", colorChange = "锡置换出红色铜，溶液蓝色变浅")),
        rxn("pb_agno3", ReactionType.DISPLACEMENT, true,
            listOf(sp("pb", 1), sp("agno3", 2)), listOf(sp("pbno32", 1), sp("ag", 2)),
            Condition(), Effect(precipitateId = "ag", colorChange = "铅表面析出银白色金属")),
        rxn("mg_znso4", ReactionType.DISPLACEMENT, true,
            listOf(sp("mg", 1), sp("znso4", 1)), listOf(sp("mgso4", 1), sp("zn", 1)),
            Condition(), Effect(precipitateId = "zn", colorChange = "镁置换出锌，溶液澄清")),
        rxn("zn_feso4", ReactionType.DISPLACEMENT, true,
            listOf(sp("zn", 1), sp("feso4", 1)), listOf(sp("znso4", 1), sp("fe", 1)),
            Condition(), Effect(precipitateId = "fe", colorChange = "锌置换出铁，溶液浅绿色变浅")),
        rxn("al_feso4", ReactionType.DISPLACEMENT, true,
            listOf(sp("al", 2), sp("feso4", 3)), listOf(sp("al2so43", 1), sp("fe", 3)),
            Condition(), Effect(precipitateId = "fe", colorChange = "铝置换出铁，溶液浅绿色变浅")),
        rxn("mg_cuso4", ReactionType.DISPLACEMENT, true,
            listOf(sp("mg", 1), sp("cuso4", 1)), listOf(sp("mgso4", 1), sp("cu", 1)),
            Condition(), Effect(precipitateId = "cu", colorChange = "镁置换出红色铜，溶液蓝色变浅")),
        rxn("zn_cucl2", ReactionType.DISPLACEMENT, true,
            listOf(sp("zn", 1), sp("cucl2", 1)), listOf(sp("zncl2", 1), sp("cu", 1)),
            Condition(), Effect(precipitateId = "cu", colorChange = "锌表面析出红色铜")),
        rxn("fe_cuno32", ReactionType.DISPLACEMENT, true,
            listOf(sp("fe", 1), sp("cuno32", 1)), listOf(sp("feno32", 1), sp("cu", 1)),
            Condition(), Effect(precipitateId = "cu", colorChange = "铁置换出红色铜，溶液变浅绿")),
        rxn("mg_feso4", ReactionType.DISPLACEMENT, true,
            listOf(sp("mg", 1), sp("feso4", 1)), listOf(sp("mgso4", 1), sp("fe", 1)),
            Condition(), Effect(precipitateId = "fe", colorChange = "镁置换出铁，溶液浅绿色变浅")),
        rxn("al_znso4", ReactionType.DISPLACEMENT, true,
            listOf(sp("al", 2), sp("znso4", 3)), listOf(sp("al2so43", 1), sp("zn", 3)),
            Condition(), Effect(precipitateId = "zn", colorChange = "铝置换出锌")),
        rxn("al_cucl2", ReactionType.DISPLACEMENT, true,
            listOf(sp("al", 2), sp("cucl2", 3)), listOf(sp("alcl3", 2), sp("cu", 3)),
            Condition(), Effect(precipitateId = "cu", colorChange = "铝置换出红色铜，溶液蓝色变浅")),

        // ---------- 复分解反应 ----------
        // 中和反应
        rxn("hf_naoh", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("hf", 1), sp("naoh", 1)), listOf(sp("naf", 1), sp("h2o", 1)),
            Condition(), Effect(colorChange = "酸碱中和，生成氟化钠")),
        rxn("koh_hno3", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("koh", 1), sp("hno3", 1)), listOf(sp("kno3", 1), sp("h2o", 1)),
            Condition(), Effect(colorChange = "酸碱中和，放热")),
        rxn("baoh2_hno3", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("baoh2", 1), sp("hno3", 2)), listOf(sp("bano32", 1), sp("h2o", 2)),
            Condition(), Effect(colorChange = "酸碱中和")),
        rxn("caoh2_h2so4", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("caoh2", 1), sp("h2so4", 1)), listOf(sp("caso4", 1), sp("h2o", 2)),
            Condition(), Effect(precipitateId = "caso4", colorChange = "中和生成微溶的硫酸钙（浑浊）")),
        rxn("fecl3_nh3h2o", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("fecl3", 1), sp("nh3h2o", 3)), listOf(sp("feoh3", 1), sp("nh4cl", 3)),
            Condition(), Effect(precipitateId = "feoh3", colorChange = "生成红褐色氢氧化铁沉淀")),
        rxn("alcl3_nh3h2o", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("alcl3", 1), sp("nh3h2o", 3)), listOf(sp("aloh3", 1), sp("nh4cl", 3)),
            Condition(), Effect(precipitateId = "aloh3", colorChange = "生成白色胶状氢氧化铝沉淀")),
        rxn("naoh_nh4no3", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("naoh", 1), sp("nh4no3", 1)), listOf(sp("nano3", 1), sp("nh3h2o", 1)),
            Condition(), Effect(colorChange = "碱与铵盐反应生成弱碱氨水")),
        // 沉淀反应（新增）
        rxn("pbno32_ki", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("pbno32", 1), sp("ki", 2)), listOf(sp("pbi2", 1), sp("kno3", 2)),
            Condition(), Effect(precipitateId = "pbi2", colorChange = "生成黄色碘化铅沉淀")),
        rxn("agno3_kbr", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("agno3", 1), sp("kbr", 1)), listOf(sp("agbr", 1), sp("kno3", 1)),
            Condition(), Effect(precipitateId = "agbr", colorChange = "生成浅黄色溴化银沉淀")),
        rxn("agno3_ki", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("agno3", 1), sp("ki", 1)), listOf(sp("agi", 1), sp("kno3", 1)),
            Condition(), Effect(precipitateId = "agi", colorChange = "生成黄色碘化银沉淀")),
        rxn("agno3_nabr", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("agno3", 1), sp("nabr", 1)), listOf(sp("agbr", 1), sp("nano3", 1)),
            Condition(), Effect(precipitateId = "agbr", colorChange = "生成浅黄色溴化银沉淀")),
        rxn("cuso4_h2s", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("cuso4", 1), sp("h2s", 1)), listOf(sp("cus", 1), sp("h2so4", 1)),
            Condition(), Effect(precipitateId = "cus", colorChange = "生成黑色硫化铜沉淀")),
        rxn("agno3_h2s", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("agno3", 2), sp("h2s", 1)), listOf(sp("ag2s", 1), sp("hno3", 2)),
            Condition(), Effect(precipitateId = "ag2s", colorChange = "生成黑色硫化银沉淀")),
        rxn("naf_cacl2", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("naf", 2), sp("cacl2", 1)), listOf(sp("caf2", 1), sp("nacl", 2)),
            Condition(), Effect(precipitateId = "caf2", colorChange = "生成白色氟化钙沉淀")),
        rxn("pbno32_na2so4", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("pbno32", 1), sp("na2so4", 1)), listOf(sp("pbso4", 1), sp("nano3", 2)),
            Condition(), Effect(precipitateId = "pbso4", colorChange = "生成白色硫酸铅沉淀")),
        rxn("fecl3_kscn", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("fecl3", 1), sp("kscn", 3)), listOf(sp("fescn", 1), sp("kcl", 3)),
            Condition(), Effect(colorChange = "溶液变为血红色（检验Fe³⁺）")),
        rxn("na2s2o3_hcl", ReactionType.DOUBLE_DISPLACEMENT, true,
            listOf(sp("na2s2o3", 1), sp("hcl", 2)), listOf(sp("nacl", 2), sp("so2", 1), sp("s", 1), sp("h2o", 1)),
            Condition(), Effect(precipitateId = "s", gasId = "so2", colorChange = "溶液变浑浊并放出刺激性气体（硫代硫酸钠遇酸）")),
        rxn("mgso4_bacl2", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("mgso4", 1), sp("bacl2", 1)), listOf(sp("baso4", 1), sp("mgcl2", 1)),
            Condition(), Effect(precipitateId = "baso4", colorChange = "生成白色硫酸钡沉淀")),
        rxn("na2so4_cacl2", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("na2so4", 1), sp("cacl2", 1)), listOf(sp("caso4", 1), sp("nacl", 2)),
            Condition(), Effect(precipitateId = "caso4", colorChange = "生成微溶硫酸钙（浑浊）")),
        rxn("al2so43_baoh2", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("al2so43", 1), sp("baoh2", 3)), listOf(sp("aloh3", 2), sp("baso4", 3)),
            Condition(), Effect(precipitateId = "baso4", colorChange = "生成白色氢氧化铝和硫酸钡沉淀")),
        rxn("baco3_h2so4", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("baco3", 1), sp("h2so4", 1)), listOf(sp("baso4", 1), sp("h2o", 1), sp("co2", 1)),
            Condition(), Effect(gasId = "co2", precipitateId = "baso4", colorChange = "产生气泡并生成白色沉淀")),
        rxn("baco3_hno3", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("baco3", 1), sp("hno3", 2)), listOf(sp("bano32", 1), sp("h2o", 1), sp("co2", 1)),
            Condition(), Effect(gasId = "co2", colorChange = "碳酸钡溶解并放出二氧化碳")),
        rxn("cuno32_baoh2", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("cuno32", 1), sp("baoh2", 1)), listOf(sp("cuoh2", 1), sp("bano32", 1)),
            Condition(), Effect(precipitateId = "cuoh2", colorChange = "生成蓝色氢氧化铜沉淀")),
        rxn("mgo_h2so4", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("mgo", 1), sp("h2so4", 1)), listOf(sp("mgso4", 1), sp("h2o", 1)),
            Condition(), Effect(colorChange = "白色固体溶解，生成硫酸镁")),
        rxn("znso4_baoh2", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("znso4", 1), sp("baoh2", 1)), listOf(sp("znoh2", 1), sp("baso4", 1)),
            Condition(), Effect(precipitateId = "znoh2", colorChange = "生成白色氢氧化锌和硫酸钡沉淀")),
        rxn("cacl2_naoh", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("cacl2", 1), sp("naoh", 2)), listOf(sp("caoh2", 1), sp("nacl", 2)),
            Condition(), Effect(precipitateId = "caoh2", colorChange = "生成微溶氢氧化钙（浑浊）")),
        rxn("cucl2_agno3", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("cucl2", 1), sp("agno3", 2)), listOf(sp("agcl", 2), sp("cuno32", 1)),
            Condition(), Effect(precipitateId = "agcl", colorChange = "生成白色氯化银沉淀，溶液变蓝")),
        rxn("mgso4_naoh", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("mgso4", 1), sp("naoh", 2)), listOf(sp("mgoh2", 1), sp("na2so4", 1)),
            Condition(), Effect(precipitateId = "mgoh2", colorChange = "生成白色氢氧化镁沉淀")),
        rxn("feo_hcl", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("feo", 1), sp("hcl", 2)), listOf(sp("fecl2", 1), sp("h2o", 1)),
            Condition(), Effect(colorChange = "黑色氧化亚铁溶解，溶液变浅绿")),
        rxn("feo_h2so4", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("feo", 1), sp("h2so4", 1)), listOf(sp("feso4", 1), sp("h2o", 1)),
            Condition(), Effect(colorChange = "黑色氧化亚铁溶解，溶液变浅绿")),
        rxn("nahco3_h2so4", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("nahco3", 2), sp("h2so4", 1)), listOf(sp("na2so4", 1), sp("h2o", 2), sp("co2", 2)),
            Condition(), Effect(gasId = "co2", colorChange = "产生气泡，生成二氧化碳")),
        rxn("na2co3_hno3", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("na2co3", 1), sp("hno3", 2)), listOf(sp("nano3", 2), sp("h2o", 1), sp("co2", 1)),
            Condition(), Effect(gasId = "co2", colorChange = "产生气泡，生成二氧化碳")),
        rxn("kcl_agno3", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("kcl", 1), sp("agno3", 1)), listOf(sp("agcl", 1), sp("kno3", 1)),
            Condition(), Effect(precipitateId = "agcl", colorChange = "生成白色氯化银沉淀")),
        rxn("caso4_na2co3", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("caso4", 1), sp("na2co3", 1)), listOf(sp("caco3", 1), sp("na2so4", 1)),
            Condition(), Effect(precipitateId = "caco3", colorChange = "微溶硫酸钙转化为碳酸钙沉淀")),

        // ---------- 氧化还原反应 ----------
        // 铝热反应全套
        rxn("al_fe3o4", ReactionType.REDOX, true,
            listOf(sp("al", 8), sp("fe3o4", 3)), listOf(sp("al2o3", 4), sp("fe", 9)),
            Condition(needsIgnition = true), Effect(light = true, heatKj = 3323.0, colorChange = "铝热反应：八铝三铁四氧化三铁，剧烈发光生成铁")),
        rxn("al_cr2o3", ReactionType.REDOX, true,
            listOf(sp("al", 2), sp("cr2o3", 1)), listOf(sp("al2o3", 1), sp("cr", 2)),
            Condition(needsIgnition = true), Effect(light = true, heatKj = 569.0, colorChange = "铝铬热反应：剧烈发光，生成铬（镁条引燃）")),
        rxn("al_cuo", ReactionType.REDOX, true,
            listOf(sp("al", 2), sp("cuo", 3)), listOf(sp("al2o3", 1), sp("cu", 3)),
            Condition(needsIgnition = true), Effect(light = true, colorChange = "铝置换氧化铜中的铜，发光并析出红色铜")),
        rxn("al_pbo", ReactionType.REDOX, true,
            listOf(sp("al", 2), sp("pbo", 3)), listOf(sp("al2o3", 1), sp("pb", 3)),
            Condition(needsIgnition = true), Effect(light = true, colorChange = "铝置换氧化铅中铅，发光生成铅")),
        rxn("al_mno2", ReactionType.REDOX, true,
            listOf(sp("al", 2), sp("mno2", 3)), listOf(sp("al2o3", 1), sp("mn", 3)),
            Condition(needsIgnition = true), Effect(light = true, colorChange = "铝热反应：剧烈发光，析出锰")),
        // 其它氧化还原
        rxn("na2o2_h2o", ReactionType.REDOX, true,
            listOf(sp("na2o2", 2), sp("h2o", 2)), listOf(sp("naoh", 4), sp("o2", 1)),
            Condition(), Effect(gasId = "o2", colorChange = "过氧化钠遇水放出氧气（歧化反应）")),
        rxn("na2o2_co2", ReactionType.REDOX, true,
            listOf(sp("na2o2", 2), sp("co2", 2)), listOf(sp("na2co3", 2), sp("o2", 1)),
            Condition(), Effect(gasId = "o2", colorChange = "过氧化钠吸收二氧化碳并放出氧气（呼吸面具供氧）")),
        rxn("so2_o2_v2o5", ReactionType.REDOX, true,
            listOf(sp("so2", 2), sp("o2", 1)), listOf(sp("so3", 2)),
            Condition(minTemp = 450.0, catalystId = "v2o5"), Effect(colorChange = "二氧化硫在V₂O₅催化下氧化生成三氧化硫（接触法制硫酸）")),
        rxn("cl2_h2o", ReactionType.REDOX, true,
            listOf(sp("cl2", 1), sp("h2o", 1)), listOf(sp("hcl", 1), sp("hclo", 1)),
            Condition(), Effect(colorChange = "氯气溶于水发生歧化，生成盐酸和次氯酸")),
        rxn("cl2_nabr", ReactionType.DISPLACEMENT, true,
            listOf(sp("cl2", 1), sp("nabr", 2)), listOf(sp("nacl", 2), sp("br2", 1)),
            Condition(), Effect(colorChange = "氯气置换出溴，溶液变橙棕色")),
        rxn("c_sio2", ReactionType.REDOX, true,
            listOf(sp("c", 2), sp("sio2", 1)), listOf(sp("si", 1), sp("co", 2)),
            Condition(minTemp = 2000.0), Effect(gasId = "co", colorChange = "碳在高温下还原二氧化硅制粗硅")),
        rxn("c_pbo", ReactionType.REDOX, true,
            listOf(sp("c", 1), sp("pbo", 2)), listOf(sp("pb", 2), sp("co2", 1)),
            Condition(minTemp = 700.0), Effect(gasId = "co2", precipitateId = "pb", colorChange = "碳还原氧化铅，析出金属铅")),
        rxn("co_pbo", ReactionType.REDOX, true,
            listOf(sp("co", 1), sp("pbo", 1)), listOf(sp("pb", 1), sp("co2", 1)),
            Condition(minTemp = 400.0), Effect(gasId = "co2", precipitateId = "pb", colorChange = "一氧化碳还原氧化铅")),
        rxn("h2_pbo", ReactionType.REDOX, true,
            listOf(sp("h2", 1), sp("pbo", 1)), listOf(sp("pb", 1), sp("h2o", 1)),
            Condition(minTemp = 300.0), Effect(colorChange = "氢气还原氧化铅")),
        rxn("h2_tio2", ReactionType.REDOX, true,
            listOf(sp("h2", 2), sp("tio2", 1)), listOf(sp("ti", 1), sp("h2o", 2)),
            Condition(minTemp = 800.0), Effect(colorChange = "氢气高温还原二氧化钛制钛")),
        rxn("feo_c", ReactionType.REDOX, true,
            listOf(sp("feo", 1), sp("c", 1)), listOf(sp("fe", 1), sp("co", 1)),
            Condition(minTemp = 900.0), Effect(gasId = "co", colorChange = "碳还原氧化亚铁，生成铁和一氧化碳")),
        rxn("feo_h2", ReactionType.REDOX, true,
            listOf(sp("feo", 1), sp("h2", 1)), listOf(sp("fe", 1), sp("h2o", 1)),
            Condition(minTemp = 700.0), Effect(colorChange = "氢气还原氧化亚铁")),
        rxn("feo_co", ReactionType.REDOX, true,
            listOf(sp("feo", 1), sp("co", 1)), listOf(sp("fe", 1), sp("co2", 1)),
            Condition(minTemp = 700.0), Effect(gasId = "co2", colorChange = "一氧化碳还原氧化亚铁")),
        rxn("kr_cl2", ReactionType.REDOX, true,
            listOf(sp("k", 2), sp("cl2", 1)), listOf(sp("kcl", 2)),
            Condition(needsIgnition = true), Effect(light = true, colorChange = "钾在氯气中燃烧，生成氯化钾")),
        rxn("ca_cl2", ReactionType.REDOX, true,
            listOf(sp("ca", 1), sp("cl2", 1)), listOf(sp("cacl2", 1)),
            Condition(needsIgnition = true), Effect(light = true, colorChange = "钙在氯气中燃烧")),
        rxn("hg_s", ReactionType.REDOX, true,
            listOf(sp("hg", 1), sp("s", 1)), listOf(sp("hgs", 1)),
            Condition(), Effect(precipitateId = "hgs", colorChange = "汞与硫研磨生成黑色硫化汞（处理洒落汞）")),
        rxn("ag2s_decomp", ReactionType.DECOMPOSITION, true,
            listOf(sp("ag2s", 1)), listOf(sp("ag2o", 1), sp("s", 1)),
            Condition(minTemp = 500.0), Effect(colorChange = "高温下硫化银分解")),
        rxn("fes_o2", ReactionType.REDOX, true,
            listOf(sp("fes", 4), sp("o2", 7)), listOf(sp("fe2o3", 2), sp("so2", 4)),
            Condition(needsIgnition = true), Effect(gasId = "so2", colorChange = "硫化亚铁燃烧，生成氧化铁和二氧化硫")),
        rxn("fes2_o2", ReactionType.REDOX, true,
            listOf(sp("fes2", 4), sp("o2", 11)), listOf(sp("fe2o3", 2), sp("so2", 8)),
            Condition(needsIgnition = true), Effect(gasId = "so2", colorChange = "黄铁矿燃烧生成氧化铁和二氧化硫（工业制硫酸）")),
        rxn("nm_h2o2", ReactionType.REDOX, true,
            listOf(sp("kmno4", 2), sp("h2so4", 3), sp("h2o2", 5)), listOf(sp("k2so4", 1), sp("mnso4", 2), sp("o2", 5), sp("h2o", 8)),
            Condition(), Effect(gasId = "o2", colorChange = "酸性高锰酸钾氧化过氧化氢，放出氧气（紫色褪去）")),
        rxn("cu_hno3_dilute", ReactionType.REDOX, true,
            listOf(sp("cu", 3), sp("hno3", 8)), listOf(sp("cuno32", 3), sp("no", 2), sp("h2o", 4)),
            Condition(), Effect(gasId = "no", colorChange = "铜与稀硝酸反应，生成无色一氧化氮（遇空气变红棕）")),

        // ================= 第六批补充反应 =================
        // 硅及其化合物（工业制水玻璃）
        rxn("si_naoh", ReactionType.REDOX, true,
            listOf(sp("si", 1), sp("naoh", 2), sp("h2o", 1)), listOf(sp("na2sio3", 1), sp("h2", 2)),
            Condition(), Effect(gasId = "h2", colorChange = "硅与强碱溶液反应，放出氢气")),
        rxn("sio2_naoh", ReactionType.DOUBLE_DISPLACEMENT, false,
            listOf(sp("sio2", 1), sp("naoh", 2)), listOf(sp("na2sio3", 1), sp("h2o", 1)),
            Condition(minTemp = 60.0), Effect(colorChange = "二氧化硅与烧碱溶液共热（工业制水玻璃）")),

        // 浓硫酸的强氧化性
        rxn("cu_h2so4_conc", ReactionType.REDOX, true,
            listOf(sp("cu", 1), sp("h2so4", 2)), listOf(sp("cuso4", 1), sp("so2", 1), sp("h2o", 2)),
            Condition(minTemp = 200.0), Effect(gasId = "so2", colorChange = "铜与浓硫酸共热，生成刺激性气体")),
        rxn("c_h2so4_conc", ReactionType.REDOX, true,
            listOf(sp("c", 1), sp("h2so4", 2)), listOf(sp("co2", 1), sp("so2", 2), sp("h2o", 2)),
            Condition(minTemp = 200.0), Effect(gasId = "co2", colorChange = "碳被浓硫酸氧化，放出无色与刺激性气体")),

        // 铁与水蒸气高温反应
        rxn("fe_h2o_steam", ReactionType.REDOX, true,
            listOf(sp("fe", 3), sp("h2o", 4)), listOf(sp("fe3o4", 1), sp("h2", 4)),
            Condition(minTemp = 500.0), Effect(gasId = "h2", colorChange = "铁粉与水蒸气高温反应，放出氢气")),

        // 镁置换氯化铜中的铜
        rxn("mg_cucl2", ReactionType.DISPLACEMENT, true,
            listOf(sp("mg", 1), sp("cucl2", 1)), listOf(sp("mgcl2", 1), sp("cu", 1)),
            Condition(), Effect(precipitateId = "cu", colorChange = "镁置换出红色铜，溶液蓝色变浅")),

        // 铝在氯气中燃烧
        rxn("al_cl2", ReactionType.COMBINATION, true,
            listOf(sp("al", 2), sp("cl2", 3)), listOf(sp("alcl3", 2)),
            Condition(needsIgnition = true), Effect(light = true, colorChange = "铝在氯气中剧烈燃烧，生成氯化铝")),

        // 氯化银受热分解
        rxn("agcl_decomp", ReactionType.DECOMPOSITION, true,
            listOf(sp("agcl", 2)), listOf(sp("ag", 2), sp("cl2", 1)),
            Condition(minTemp = 600.0), Effect(gasId = "cl2", colorChange = "氯化银受热分解，生成银和氯气")),

        // ================= 第七批扩充：补齐至 500 条 =================

        // —— 化合 / 燃烧 ——
        comb("ca", 2, "o2", 1, "cao", 2, Effect(light = true, heatKj = 1270.0, colorChange = "钙在氧气中燃烧，发出白光生成氧化钙"), Condition(needsIgnition = true), true),
        comb("mn", 1, "o2", 1, "mno2", 1, Effect(light = true, colorChange = "锰在氧气中燃烧，生成黑色二氧化锰"), Condition(needsIgnition = true), true),
        comb("cu", 4, "o2", 1, "cu2o", 2, Effect(colorChange = "铜在氧气不足时氧化，生成砖红色氧化亚铜"), Condition(minTemp = 400.0), true),
        comb("fe", 2, "o2", 3, "fe2o3", 2, Effect(colorChange = "铁缓慢氧化生锈，生成红棕色氧化铁"), Condition(minTemp = 300.0), true),
        comb("zn", 1, "cl2", 1, "zncl2", 1, Effect(light = true, colorChange = "锌在氯气中燃烧，生成白色氯化锌"), Condition(needsIgnition = true), true),
        comb("al", 2, "s", 3, "al2s3", 1, Effect(light = true, colorChange = "铝与硫加热化合，生成灰色硫化铝"), Condition(needsIgnition = true), true),
        comb("ag", 2, "s", 1, "ag2s", 1, Effect(colorChange = "银在含硫环境中变黑，生成硫化银"), Condition(), true),
        comb("na", 2, "o2", 1, "na2o2", 1, Effect(light = true, colorChange = "钠在充足的氧气中燃烧，生成淡黄色过氧化钠"), Condition(needsIgnition = true), true),
        comb("n2", 1, "o2", 1, "no", 2, Effect(gasId = "no", colorChange = "高温下氮气与氧气化合，生成无色一氧化氮"), Condition(minTemp = 800.0), true),
        comb("n2", 1, "h2", 3, "nh3", 2, Effect(gasId = "nh3", colorChange = "高温高压并有催化剂时合成氨"), Condition(minTemp = 500.0, catalystId = "fe"), true),
        comb2("k", 2, "h2o", 2, "koh", 2, "h2", 1, Effect(gasId = "h2", light = true, colorChange = "钾与水剧烈反应，熔成小球并燃烧"), Condition(), true),
        comb2("ca", 1, "h2o", 2, "caoh2", 1, "h2", 1, Effect(gasId = "h2", colorChange = "钙与水反应放出氢气，溶液变浑浊"), Condition(), true),
        comb("mgo", 1, "h2o", 1, "mgoh2", 1, Effect(colorChange = "氧化镁与水缓慢化合，生成氢氧化镁")),
        comb("co2", 1, "h2o", 1, "h2co3", 1, Effect(colorChange = "二氧化碳溶于水生成碳酸")),
        comb("mgo", 1, "so3", 1, "mgso4", 1, Effect(colorChange = "氧化镁吸收三氧化硫，生成硫酸镁")),
        comb("cuo", 1, "so3", 1, "cuso4", 1, Effect(colorChange = "氧化铜吸收三氧化硫，生成硫酸铜")),
        comb("zno", 1, "so3", 1, "znso4", 1, Effect(colorChange = "氧化锌吸收三氧化硫，生成硫酸锌")),
        comb("k2o", 1, "so3", 1, "k2so4", 1, Effect(colorChange = "氧化钾吸收三氧化硫，生成硫酸钾")),
        comb("nh3", 1, "hno3", 1, "nh4no3", 1, Effect(colorChange = "氨气与硝酸蒸气化合，产生硝酸铵白烟")),
        comb("nh3", 1, "h2o", 1, "nh3h2o", 1, Effect(colorChange = "氨气极易溶于水，形成氨水")),
        comb2("no2", 3, "h2o", 1, "hno3", 2, "no", 1, Effect(gasId = "no", colorChange = "二氧化氮溶于水生成硝酸并放出一氧化氮"), Condition(), true),
        comb2("h2o2", 1, "h2s", 1, "s", 1, "h2o", 2, Effect(precipitateId = "s", colorChange = "过氧化氢氧化硫化氢，析出淡黄色硫"), Condition(), true),
        comb2("h2o2", 1, "na2so3", 1, "na2so4", 1, "h2o", 1, Effect(colorChange = "过氧化氢氧化亚硫酸钠为硫酸钠"), Condition(), true),
        comb2("h2o2", 1, "cl2", 1, "hcl", 2, "o2", 1, Effect(gasId = "o2", colorChange = "过氧化氢与氯气反应并放出氧气"), Condition(), true),
        comb("h2o2", 1, "so2", 1, "h2so4", 1, Effect(colorChange = "过氧化氢氧化二氧化硫，生成硫酸"), Condition(), true),
        rdx1("na2o2", 1, "so2", 1, "na2so4", 1, Effect(colorChange = "过氧化钠吸收二氧化硫，生成硫酸钠")),
        rdx1("fecl2", 2, "cl2", 1, "fecl3", 2, Effect(colorChange = "氯气将氯化亚铁氧化为氯化铁，溶液由浅绿变黄")),
        rdx("k2mno4", 2, "cl2", 1, "kmno4", 2, "kcl", 2, Effect(colorChange = "氯气将锰酸钾氧化为高锰酸钾")),

        // —— 酸碱中和 / 氢氧化物与酸 ——
        dd("naoh", 1, "hclo", 1, "naclo", 1, "h2o", 1, Effect(colorChange = "氢氧化钠与次氯酸中和，生成次氯酸钠")),
        dd("naoh", 2, "h2co3", 1, "na2co3", 1, "h2o", 2, Effect(colorChange = "氢氧化钠与碳酸中和，生成碳酸钠")),
        dd("koh", 2, "h2co3", 1, "k2co3", 1, "h2o", 2, Effect(colorChange = "氢氧化钾与碳酸中和，生成碳酸钾")),
        dd("caoh2", 1, "hf", 2, "caf2", 1, "h2o", 2, Effect(precipitateId = "caf2", colorChange = "氢氧化钙与氢氟酸反应生成氟化钙沉淀")),
        dd("caoh2", 1, "h2co3", 1, "caco3", 1, "h2o", 2, Effect(precipitateId = "caco3", colorChange = "生成白色碳酸钙沉淀")),
        dd("baoh2", 1, "hcl", 2, "bacl2", 1, "h2o", 2, Effect(colorChange = "氢氧化钡与盐酸中和")),
        dd("baoh2", 1, "h2co3", 1, "baco3", 1, "h2o", 2, Effect(precipitateId = "baco3", colorChange = "生成白色碳酸钡沉淀")),
        dd("mgoh2", 1, "hcl", 2, "mgcl2", 1, "h2o", 2, Effect(colorChange = "氢氧化镁沉淀溶解，生成氯化镁")),
        dd("mgoh2", 1, "h2so4", 1, "mgso4", 1, "h2o", 2, Effect(colorChange = "氢氧化镁沉淀溶解，生成硫酸镁")),
        dd("cuoh2", 1, "hcl", 2, "cucl2", 1, "h2o", 2, Effect(colorChange = "蓝色沉淀溶解，溶液显蓝色")),
        dd("cuoh2", 1, "h2so4", 1, "cuso4", 1, "h2o", 2, Effect(colorChange = "蓝色沉淀溶解，生成硫酸铜")),
        dd("cuoh2", 1, "hno3", 2, "cuno32", 1, "h2o", 2, Effect(colorChange = "蓝色沉淀溶解，生成硝酸铜")),
        dd("feoh2", 1, "hcl", 2, "fecl2", 1, "h2o", 2, Effect(colorChange = "白色沉淀溶解，溶液显浅绿色")),
        dd("feoh2", 1, "h2so4", 1, "feso4", 1, "h2o", 2, Effect(colorChange = "白色沉淀溶解，生成硫酸亚铁")),
        dd("feoh3", 1, "hcl", 3, "fecl3", 1, "h2o", 3, Effect(colorChange = "红褐色沉淀溶解，溶液显黄色")),
        dd("feoh3", 2, "h2so4", 3, "fe2so43", 1, "h2o", 6, Effect(colorChange = "红褐色沉淀溶解，生成硫酸铁")),
        dd("aloh3", 1, "hcl", 3, "alcl3", 1, "h2o", 3, Effect(colorChange = "白色沉淀溶解，生成氯化铝")),
        dd("aloh3", 2, "h2so4", 3, "al2so43", 1, "h2o", 6, Effect(colorChange = "白色沉淀溶解，生成硫酸铝")),
        dd("znoh2", 1, "hcl", 2, "zncl2", 1, "h2o", 2, Effect(colorChange = "白色沉淀溶解，生成氯化锌")),
        dd("znoh2", 1, "h2so4", 1, "znso4", 1, "h2o", 2, Effect(colorChange = "白色沉淀溶解，生成硫酸锌")),
        // ================= 第八批扩充：物质间更多反应 =================
        dd("na2o", 1, "hcl", 2, "nacl", 2, "h2o", 1, Effect(colorChange = "生成氯化钠"), Condition(), false),
        dd("k2o", 1, "hcl", 2, "kcl", 2, "h2o", 1, Effect(colorChange = "生成氯化钾"), Condition(), false),
        dd("ag2o", 1, "hcl", 2, "agcl", 2, "h2o", 1, Effect(colorChange = "氧化银溶解，生成白色氯化银沉淀"), Condition(), false),
        dd3("fe3o4", 1, "hcl", 8, "fecl2", 1, "fecl3", 2, "h2o", 4, Effect(colorChange = "四氧化三铁溶于盐酸，生成亚铁与铁盐"), Condition(), false),
        dd("na2o2", 1, "hcl", 2, "nacl", 2, "h2o2", 1, Effect(colorChange = "过氧化钠与盐酸反应，生成过氧化氢"), Condition(), false),
        dd("na2o", 1, "h2so4", 1, "na2so4", 1, "h2o", 1, Effect(colorChange = "生成硫酸钠"), Condition(), false),
        dd("k2o", 1, "h2so4", 1, "k2so4", 1, "h2o", 1, Effect(colorChange = "生成硫酸钾"), Condition(), false),
        dd("cao", 1, "h2so4", 1, "caso4", 1, "h2o", 1, Effect(colorChange = "生成硫酸钙"), Condition(), false),
        dd3("fe3o4", 1, "h2so4", 4, "feso4", 1, "fe2so43", 1, "h2o", 4, Effect(colorChange = "四氧化三铁溶于硫酸"), Condition(), false),
        dd("al2o3", 1, "h2so4", 3, "al2so43", 1, "h2o", 3, Effect(colorChange = "生成硫酸铝"), Condition(), false),
        dd("na2o", 1, "hno3", 2, "nano3", 2, "h2o", 1, Effect(colorChange = "生成硝酸钠"), Condition(), false),
        dd("k2o", 1, "hno3", 2, "kno3", 2, "h2o", 1, Effect(colorChange = "生成硝酸钾"), Condition(), false),
        dd("zno", 1, "hno3", 2, "znno32", 1, "h2o", 1, Effect(colorChange = "生成硝酸锌"), Condition(), false),
        dd("feo", 1, "hno3", 2, "feno32", 1, "h2o", 1, Effect(colorChange = "生成硝酸亚铁"), Condition(), false),
        dd("ag2o", 1, "hno3", 2, "agno3", 2, "h2o", 1, Effect(colorChange = "生成硝酸银"), Condition(), false),
        dd("pbo", 1, "hno3", 2, "pbno32", 1, "h2o", 1, Effect(colorChange = "生成硝酸铅"), Condition(), false),
        dd("cao", 1, "hf", 2, "caf2", 1, "h2o", 1, Effect(precipitateId = "caf2", colorChange = "氧化钙与氢氟酸反应生成氟化钙沉淀"), Condition(), false),
        dd("na2o", 1, "hf", 2, "naf", 2, "h2o", 1, Effect(colorChange = "生成氟化钠"), Condition(), false),
        dd("na2o", 3, "h3po4", 2, "na3po4", 2, "h2o", 3, Effect(colorChange = "生成磷酸钠"), Condition(), false),
        dd("nh3h2o", 1, "hno3", 1, "nh4no3", 1, "h2o", 1, Effect(colorChange = "生成硝酸铵"), Condition(), false),
        dd("znoh2", 1, "hno3", 2, "znno32", 1, "h2o", 2, Effect(colorChange = "生成硝酸锌"), Condition(), false),
        dd("feoh2", 1, "hno3", 2, "feno32", 1, "h2o", 2, Effect(colorChange = "生成硝酸亚铁"), Condition(), false),
        dd3("nahco3", 1, "hno3", 1, "nano3", 1, "h2o", 1, "co2", 1, Effect(gasId = "co2", colorChange = "小苏打与硝酸反应放出二氧化碳"), Condition(), false),
        dd3("baco3", 1, "hcl", 2, "bacl2", 1, "h2o", 1, "co2", 1, Effect(gasId = "co2", colorChange = "碳酸钡溶于盐酸并放出二氧化碳"), Condition(), false),
        dd3("na2co3", 3, "h3po4", 2, "na3po4", 2, "h2o", 3, "co2", 3, Effect(gasId = "co2", colorChange = "碳酸钠与磷酸反应放出二氧化碳"), Condition(), false),
        dd3("na2so3", 1, "h2so4", 1, "na2so4", 1, "h2o", 1, "so2", 1, Effect(gasId = "so2", colorChange = "亚硫酸钠与硫酸反应放出二氧化硫"), Condition(), false),
        dd("fes", 1, "h2so4", 1, "feso4", 1, "h2s", 1, Effect(gasId = "h2s", colorChange = "硫化亚铁与稀硫酸反应放出臭鸡蛋气味气体"), Condition(), false),
        dd("pbno32", 1, "h2so4", 1, "pbso4", 1, "hno3", 2, Effect(precipitateId = "pbso4", colorChange = "生成白色硫酸铅沉淀"), Condition(), false),
        dd("cacl2", 1, "h2so4", 1, "caso4", 1, "hcl", 2, Effect(precipitateId = "caso4", colorChange = "生成微溶硫酸钙（浑浊）"), Condition(), false),
        dd("mgcl2", 1, "koh", 2, "mgoh2", 1, "kcl", 2, Effect(precipitateId = "mgoh2", colorChange = "生成氢氧化镁沉淀"), Condition(), false),
        dd("mgso4", 1, "koh", 2, "mgoh2", 1, "k2so4", 1, Effect(precipitateId = "mgoh2", colorChange = "生成氢氧化镁沉淀"), Condition(), false),
        dd("mgcl2", 1, "baoh2", 1, "mgoh2", 1, "bacl2", 1, Effect(precipitateId = "mgoh2", colorChange = "生成氢氧化镁沉淀"), Condition(), false),
        dd("mgso4", 1, "baoh2", 1, "mgoh2", 1, "baso4", 1, Effect(precipitateId = "mgoh2", colorChange = "生成氢氧化镁沉淀"), Condition(), false),
        dd("mgcl2", 1, "caoh2", 1, "mgoh2", 1, "cacl2", 1, Effect(precipitateId = "mgoh2", colorChange = "生成氢氧化镁沉淀"), Condition(), false),
        dd("mgso4", 1, "caoh2", 1, "mgoh2", 1, "caso4", 1, Effect(precipitateId = "mgoh2", colorChange = "生成氢氧化镁沉淀"), Condition(), false),
        dd("mgcl2", 1, "nh3h2o", 2, "mgoh2", 1, "nh4cl", 2, Effect(precipitateId = "mgoh2", colorChange = "生成氢氧化镁沉淀"), Condition(), false),
        dd("alcl3", 1, "koh", 3, "aloh3", 1, "kcl", 3, Effect(precipitateId = "aloh3", colorChange = "生成氢氧化铝沉淀"), Condition(), false),
        dd("al2so43", 1, "koh", 6, "aloh3", 2, "k2so4", 3, Effect(precipitateId = "aloh3", colorChange = "生成氢氧化铝沉淀"), Condition(), false),
        dd("alcl3", 2, "baoh2", 3, "aloh3", 2, "bacl2", 3, Effect(precipitateId = "aloh3", colorChange = "生成氢氧化铝沉淀"), Condition(), false),
        dd("alcl3", 2, "caoh2", 3, "aloh3", 2, "cacl2", 3, Effect(precipitateId = "aloh3", colorChange = "生成氢氧化铝沉淀"), Condition(), false),
        dd("al2so43", 1, "caoh2", 3, "aloh3", 2, "caso4", 3, Effect(precipitateId = "aloh3", colorChange = "生成氢氧化铝沉淀"), Condition(), false),
        dd("zncl2", 1, "koh", 2, "znoh2", 1, "kcl", 2, Effect(precipitateId = "znoh2", colorChange = "生成氢氧化锌沉淀"), Condition(), false),
        dd("znso4", 1, "koh", 2, "znoh2", 1, "k2so4", 1, Effect(precipitateId = "znoh2", colorChange = "生成氢氧化锌沉淀"), Condition(), false),
        dd("znno32", 1, "koh", 2, "znoh2", 1, "kno3", 2, Effect(precipitateId = "znoh2", colorChange = "生成氢氧化锌沉淀"), Condition(), false),
        dd("zncl2", 1, "baoh2", 1, "znoh2", 1, "bacl2", 1, Effect(precipitateId = "znoh2", colorChange = "生成氢氧化锌沉淀"), Condition(), false),
        dd("znno32", 1, "baoh2", 1, "znoh2", 1, "bano32", 1, Effect(precipitateId = "znoh2", colorChange = "生成氢氧化锌沉淀"), Condition(), false),
        dd("zncl2", 1, "caoh2", 1, "znoh2", 1, "cacl2", 1, Effect(precipitateId = "znoh2", colorChange = "生成氢氧化锌沉淀"), Condition(), false),
        dd("znso4", 1, "caoh2", 1, "znoh2", 1, "caso4", 1, Effect(precipitateId = "znoh2", colorChange = "生成氢氧化锌沉淀"), Condition(), false),
        dd("zncl2", 1, "nh3h2o", 2, "znoh2", 1, "nh4cl", 2, Effect(precipitateId = "znoh2", colorChange = "生成氢氧化锌沉淀"), Condition(), false),
        dd("cucl2", 1, "koh", 2, "cuoh2", 1, "kcl", 2, Effect(precipitateId = "cuoh2", colorChange = "生成氢氧化铜沉淀"), Condition(), false),
        dd("cuso4", 1, "koh", 2, "cuoh2", 1, "k2so4", 1, Effect(precipitateId = "cuoh2", colorChange = "生成氢氧化铜沉淀"), Condition(), false),
        dd("cuno32", 1, "koh", 2, "cuoh2", 1, "kno3", 2, Effect(precipitateId = "cuoh2", colorChange = "生成氢氧化铜沉淀"), Condition(), false),
        dd("cucl2", 1, "baoh2", 1, "cuoh2", 1, "bacl2", 1, Effect(precipitateId = "cuoh2", colorChange = "生成氢氧化铜沉淀"), Condition(), false),
        dd("cuso4", 1, "baoh2", 1, "cuoh2", 1, "baso4", 1, Effect(precipitateId = "cuoh2", colorChange = "生成氢氧化铜沉淀"), Condition(), false),
        dd("cucl2", 1, "caoh2", 1, "cuoh2", 1, "cacl2", 1, Effect(precipitateId = "cuoh2", colorChange = "生成氢氧化铜沉淀"), Condition(), false),
        dd("cuso4", 1, "caoh2", 1, "cuoh2", 1, "caso4", 1, Effect(precipitateId = "cuoh2", colorChange = "生成氢氧化铜沉淀"), Condition(), false),
        dd("cucl2", 1, "nh3h2o", 2, "cuoh2", 1, "nh4cl", 2, Effect(precipitateId = "cuoh2", colorChange = "生成氢氧化铜沉淀"), Condition(), false),
        dd("fecl2", 1, "koh", 2, "feoh2", 1, "kcl", 2, Effect(precipitateId = "feoh2", colorChange = "生成氢氧化亚铁沉淀"), Condition(), false),
        dd("feso4", 1, "koh", 2, "feoh2", 1, "k2so4", 1, Effect(precipitateId = "feoh2", colorChange = "生成氢氧化亚铁沉淀"), Condition(), false),
        dd("feno32", 1, "koh", 2, "feoh2", 1, "kno3", 2, Effect(precipitateId = "feoh2", colorChange = "生成氢氧化亚铁沉淀"), Condition(), false),
        dd("fecl2", 1, "baoh2", 1, "feoh2", 1, "bacl2", 1, Effect(precipitateId = "feoh2", colorChange = "生成氢氧化亚铁沉淀"), Condition(), false),
        dd("feso4", 1, "baoh2", 1, "feoh2", 1, "baso4", 1, Effect(precipitateId = "feoh2", colorChange = "生成氢氧化亚铁沉淀"), Condition(), false),
        dd("feno32", 1, "baoh2", 1, "feoh2", 1, "bano32", 1, Effect(precipitateId = "feoh2", colorChange = "生成氢氧化亚铁沉淀"), Condition(), false),
        dd("fecl2", 1, "caoh2", 1, "feoh2", 1, "cacl2", 1, Effect(precipitateId = "feoh2", colorChange = "生成氢氧化亚铁沉淀"), Condition(), false),
        dd("feso4", 1, "caoh2", 1, "feoh2", 1, "caso4", 1, Effect(precipitateId = "feoh2", colorChange = "生成氢氧化亚铁沉淀"), Condition(), false),
        dd("fecl2", 1, "nh3h2o", 2, "feoh2", 1, "nh4cl", 2, Effect(precipitateId = "feoh2", colorChange = "生成氢氧化亚铁沉淀"), Condition(), false),
        dd("fecl3", 1, "koh", 3, "feoh3", 1, "kcl", 3, Effect(precipitateId = "feoh3", colorChange = "生成氢氧化铁沉淀"), Condition(), false),
        dd("fe2so43", 1, "koh", 6, "feoh3", 2, "k2so4", 3, Effect(precipitateId = "feoh3", colorChange = "生成氢氧化铁沉淀"), Condition(), false),
        dd("fecl3", 2, "baoh2", 3, "feoh3", 2, "bacl2", 3, Effect(precipitateId = "feoh3", colorChange = "生成氢氧化铁沉淀"), Condition(), false),
        dd("fe2so43", 1, "baoh2", 3, "feoh3", 2, "baso4", 3, Effect(precipitateId = "feoh3", colorChange = "生成氢氧化铁沉淀"), Condition(), false),
        dd("fecl3", 2, "caoh2", 3, "feoh3", 2, "cacl2", 3, Effect(precipitateId = "feoh3", colorChange = "生成氢氧化铁沉淀"), Condition(), false),
        dd("fe2so43", 1, "caoh2", 3, "feoh3", 2, "caso4", 3, Effect(precipitateId = "feoh3", colorChange = "生成氢氧化铁沉淀"), Condition(), false),
        dd("fe2so43", 1, "naoh", 6, "feoh3", 2, "na2so4", 3, Effect(precipitateId = "feoh3", colorChange = "生成氢氧化铁沉淀"), Condition(), false),
        dd("al2so43", 1, "naoh", 6, "aloh3", 2, "na2so4", 3, Effect(precipitateId = "aloh3", colorChange = "生成氢氧化铝沉淀"), Condition(), false),
        dd("znno32", 1, "naoh", 2, "znoh2", 1, "nano3", 2, Effect(precipitateId = "znoh2", colorChange = "生成氢氧化锌沉淀"), Condition(), false),
        dd("feno32", 1, "naoh", 2, "feoh2", 1, "nano3", 2, Effect(precipitateId = "feoh2", colorChange = "生成氢氧化亚铁沉淀"), Condition(), false),
        dd("cuno32", 1, "naoh", 2, "cuoh2", 1, "nano3", 2, Effect(precipitateId = "cuoh2", colorChange = "生成氢氧化铜沉淀"), Condition(), false),
        dd("zncl2", 1, "naoh", 2, "znoh2", 1, "nacl", 2, Effect(precipitateId = "znoh2", colorChange = "生成氢氧化锌沉淀"), Condition(), false),
        dd("agno3", 2, "bacl2", 1, "agcl", 2, "bano32", 1, Effect(precipitateId = "agcl", colorChange = "生成白色氯化银沉淀"), Condition(), false),
        dd("agno3", 1, "nh4cl", 1, "agcl", 1, "nh4no3", 1, Effect(precipitateId = "agcl", colorChange = "生成白色氯化银沉淀"), Condition(), false),
        dd("agno3", 2, "zncl2", 1, "agcl", 2, "znno32", 1, Effect(precipitateId = "agcl", colorChange = "生成白色氯化银沉淀"), Condition(), false),
        dd("agno3", 2, "fecl2", 1, "agcl", 2, "feno32", 1, Effect(precipitateId = "agcl", colorChange = "生成白色氯化银沉淀"), Condition(), false),
        dd("bacl2", 1, "k2so4", 1, "baso4", 1, "kcl", 2, Effect(precipitateId = "baso4", colorChange = "生成白色硫酸钡沉淀"), Condition(), false),
        dd("bacl2", 1, "znso4", 1, "baso4", 1, "zncl2", 1, Effect(precipitateId = "baso4", colorChange = "生成白色硫酸钡沉淀"), Condition(), false),
        dd("bacl2", 1, "feso4", 1, "baso4", 1, "fecl2", 1, Effect(precipitateId = "baso4", colorChange = "生成白色硫酸钡沉淀"), Condition(), false),
        dd("bacl2", 3, "fe2so43", 1, "baso4", 3, "fecl3", 2, Effect(precipitateId = "baso4", colorChange = "生成白色硫酸钡沉淀"), Condition(), false),
        dd("bacl2", 3, "al2so43", 1, "baso4", 3, "alcl3", 2, Effect(precipitateId = "baso4", colorChange = "生成白色硫酸钡沉淀"), Condition(), false),
        dd("bano32", 1, "na2so4", 1, "baso4", 1, "nano3", 2, Effect(precipitateId = "baso4", colorChange = "生成白色硫酸钡沉淀"), Condition(), false),
        dd("bano32", 1, "k2so4", 1, "baso4", 1, "kno3", 2, Effect(precipitateId = "baso4", colorChange = "生成白色硫酸钡沉淀"), Condition(), false),
        dd("bano32", 1, "znso4", 1, "baso4", 1, "znno32", 1, Effect(precipitateId = "baso4", colorChange = "生成白色硫酸钡沉淀"), Condition(), false),
        dd("bano32", 1, "feso4", 1, "baso4", 1, "feno32", 1, Effect(precipitateId = "baso4", colorChange = "生成白色硫酸钡沉淀"), Condition(), false),
        dd("bano32", 1, "cuso4", 1, "baso4", 1, "cuno32", 1, Effect(precipitateId = "baso4", colorChange = "生成白色硫酸钡沉淀"), Condition(), false),
        dd("baoh2", 1, "na2so4", 1, "baso4", 1, "naoh", 2, Effect(precipitateId = "baso4", colorChange = "生成白色硫酸钡沉淀"), Condition(), false),
        dd("baoh2", 1, "k2so4", 1, "baso4", 1, "koh", 2, Effect(precipitateId = "baso4", colorChange = "生成白色硫酸钡沉淀"), Condition(), false),
        dd("pbno32", 1, "k2so4", 1, "pbso4", 1, "kno3", 2, Effect(precipitateId = "pbso4", colorChange = "生成白色硫酸铅沉淀"), Condition(), false),
        dd("pbno32", 1, "znso4", 1, "pbso4", 1, "znno32", 1, Effect(precipitateId = "pbso4", colorChange = "生成白色硫酸铅沉淀"), Condition(), false),
        dd("pbno32", 1, "feso4", 1, "pbso4", 1, "feno32", 1, Effect(precipitateId = "pbso4", colorChange = "生成白色硫酸铅沉淀"), Condition(), false),
        dd("pbno32", 1, "cuso4", 1, "pbso4", 1, "cuno32", 1, Effect(precipitateId = "pbso4", colorChange = "生成白色硫酸铅沉淀"), Condition(), false),
        dd("bacl2", 1, "na2co3", 1, "baco3", 1, "nacl", 2, Effect(precipitateId = "baco3", colorChange = "生成白色碳酸钡沉淀"), Condition(), false),
        dd("bano32", 1, "na2co3", 1, "baco3", 1, "nano3", 2, Effect(precipitateId = "baco3", colorChange = "生成白色碳酸钡沉淀"), Condition(), false),
        dd("baoh2", 1, "na2co3", 1, "baco3", 1, "naoh", 2, Effect(precipitateId = "baco3", colorChange = "生成白色碳酸钡沉淀"), Condition(), false),
        dd("naf", 2, "caoh2", 1, "caf2", 1, "naoh", 2, Effect(precipitateId = "caf2", colorChange = "生成白色氟化钙沉淀"), Condition(), false),
        dd("so3", 1, "naoh", 2, "na2so4", 1, "h2o", 1, Effect(colorChange = "生成硫酸钠"), Condition(), false),
        dd("so3", 1, "koh", 2, "k2so4", 1, "h2o", 1, Effect(colorChange = "生成硫酸钾"), Condition(), false),
        dd("so3", 1, "baoh2", 1, "baso4", 1, "h2o", 1, Effect(precipitateId = "baso4", colorChange = "生成白色硫酸钡沉淀"), Condition(), false),
        dd("so3", 1, "caoh2", 1, "caso4", 1, "h2o", 1, Effect(precipitateId = "caso4", colorChange = "生成微溶硫酸钙"), Condition(), false),
        displ("k", 2, "hcl", 2, "kcl", 2, "h2", 1, Effect(gasId = "h2", colorChange = "钾与盐酸剧烈反应，放出氢气"), Condition()),
        displ("ca", 1, "hcl", 2, "cacl2", 1, "h2", 1, Effect(gasId = "h2", colorChange = "钙与盐酸反应，放出氢气"), Condition()),
        displ("na", 2, "hcl", 2, "nacl", 2, "h2", 1, Effect(gasId = "h2", colorChange = "钠与盐酸剧烈反应，放出氢气"), Condition()),
        displ("k", 2, "h2so4", 1, "k2so4", 1, "h2", 1, Effect(gasId = "h2", colorChange = "钾与稀硫酸反应，放出氢气"), Condition()),
        displ("ca", 1, "h2so4", 1, "caso4", 1, "h2", 1, Effect(gasId = "h2", precipitateId = "caso4", colorChange = "钙与硫酸反应生成微溶硫酸钙并放出氢气"), Condition()),
        displ("na", 2, "h2so4", 1, "na2so4", 1, "h2", 1, Effect(gasId = "h2", colorChange = "钠与稀硫酸剧烈反应，放出氢气"), Condition()),
        displ("pb", 1, "h2so4", 1, "pbso4", 1, "h2", 1, Effect(gasId = "h2", precipitateId = "pbso4", colorChange = "铅与硫酸生成难溶硫酸铅并放出少量氢气"), Condition()),
        displ("mn", 1, "h2so4", 1, "mnso4", 1, "h2", 1, Effect(gasId = "h2", colorChange = "锰与稀硫酸反应，放出氢气"), Condition()),
        displ("mg", 1, "sncl2", 1, "mgcl2", 1, "sn", 1, Effect(precipitateId = "sn", colorChange = "镁置换出锡"), Condition()),
        displ("al", 2, "sncl2", 3, "alcl3", 2, "sn", 3, Effect(precipitateId = "sn", colorChange = "铝置换出锡"), Condition()),
        displ("zn", 1, "cuno32", 1, "znno32", 1, "cu", 1, Effect(precipitateId = "cu", colorChange = "锌置换出红色铜"), Condition()),
        displ("zn", 1, "feno32", 1, "znno32", 1, "fe", 1, Effect(precipitateId = "fe", colorChange = "锌置换出铁"), Condition()),
        displ("zn", 1, "pbno32", 1, "znno32", 1, "pb", 1, Effect(precipitateId = "pb", colorChange = "锌置换出铅"), Condition()),
        displ("zn", 1, "sncl2", 1, "zncl2", 1, "sn", 1, Effect(precipitateId = "sn", colorChange = "锌置换出锡"), Condition()),
        displ("zn", 1, "mnso4", 1, "znso4", 1, "mn", 1, Effect(precipitateId = "mn", colorChange = "锌置换出锰"), Condition()),
        displ("fe", 1, "pbno32", 1, "feno32", 1, "pb", 1, Effect(precipitateId = "pb", colorChange = "铁置换出铅"), Condition()),
        displ("fe", 1, "sncl2", 1, "fecl2", 1, "sn", 1, Effect(precipitateId = "sn", colorChange = "铁置换出锡"), Condition()),
        displ("pb", 1, "cuso4", 1, "pbso4", 1, "cu", 1, Effect(precipitateId = "cu", colorChange = "铅置换出铜，同时生成硫酸铅沉淀"), Condition()),
        displ("pb", 1, "cuno32", 1, "pbno32", 1, "cu", 1, Effect(precipitateId = "cu", colorChange = "铅置换出铜"), Condition()),
        displ("mn", 1, "cuso4", 1, "mnso4", 1, "cu", 1, Effect(precipitateId = "cu", colorChange = "锰置换出铜"), Condition()),
        displ("mn", 1, "znso4", 1, "mnso4", 1, "zn", 1, Effect(precipitateId = "zn", colorChange = "锰置换出锌"), Condition()),
        displ("mn", 1, "feso4", 1, "mnso4", 1, "fe", 1, Effect(precipitateId = "fe", colorChange = "锰置换出铁"), Condition()),
        rdx3("cuo", 3, "nh3", 2, "cu", 3, "n2", 1, "h2o", 3, Effect(colorChange = "氨气还原氧化铜，生成红色铜"), Condition(minTemp = 200.0)),
        rdx("mg", 1, "cuo", 1, "mgo", 1, "cu", 1, Effect(precipitateId = "cu", colorChange = "镁还原氧化铜，生成红色铜"), Condition(minTemp = 200.0)),
        rdx("zn", 1, "cuo", 1, "zno", 1, "cu", 1, Effect(precipitateId = "cu", colorChange = "锌还原氧化铜，生成红色铜"), Condition(minTemp = 300.0)),
        rdx("fe", 2, "cuo", 3, "fe2o3", 1, "cu", 3, Effect(precipitateId = "cu", colorChange = "铁还原氧化铜，生成红色铜"), Condition(minTemp = 500.0)),
        rdx("mg", 3, "fe2o3", 1, "mgo", 3, "fe", 2, Effect(precipitateId = "fe", colorChange = "镁还原氧化铁，生成铁"), Condition(minTemp = 400.0)),
        rdx("zn", 3, "fe2o3", 1, "zno", 3, "fe", 2, Effect(precipitateId = "fe", colorChange = "锌还原氧化铁，生成铁"), Condition(minTemp = 500.0)),
        rdx("fe3o4", 1, "h2", 4, "fe", 3, "h2o", 4, Effect(colorChange = "氢气还原四氧化三铁，生成铁"), Condition(minTemp = 600.0)),
        rdx("fe3o4", 1, "co", 4, "fe", 3, "co2", 4, Effect(gasId = "co2", colorChange = "一氧化碳还原四氧化三铁，生成铁"), Condition(minTemp = 600.0)),
        rdx("c", 2, "fe3o4", 1, "fe", 3, "co2", 2, Effect(gasId = "co2", colorChange = "碳还原四氧化三铁，生成铁"), Condition(minTemp = 800.0)),
        rdx("mg", 1, "pbo", 1, "mgo", 1, "pb", 1, Effect(precipitateId = "pb", colorChange = "镁还原氧化铅，生成铅"), Condition(minTemp = 400.0)),
        rdx("zn", 1, "pbo", 1, "zno", 1, "pb", 1, Effect(precipitateId = "pb", colorChange = "锌还原氧化铅，生成铅"), Condition(minTemp = 500.0)),
        rdx("mno2", 1, "h2", 2, "mn", 1, "h2o", 2, Effect(colorChange = "氢气还原二氧化锰，生成锰"), Condition(minTemp = 500.0)),
        rdx("mno2", 1, "co", 2, "mn", 1, "co2", 2, Effect(gasId = "co2", colorChange = "一氧化碳还原二氧化锰，生成锰"), Condition(minTemp = 500.0)),
        rdx("mno2", 1, "c", 1, "mn", 1, "co2", 1, Effect(gasId = "co2", colorChange = "碳还原二氧化锰，生成锰"), Condition(minTemp = 800.0)),
        rdx("zno", 1, "h2", 1, "zn", 1, "h2o", 1, Effect(colorChange = "氢气还原氧化锌，生成锌"), Condition(minTemp = 500.0)),
        rdx("zno", 1, "co", 1, "zn", 1, "co2", 1, Effect(gasId = "co2", colorChange = "一氧化碳还原氧化锌，生成锌"), Condition(minTemp = 600.0)),
        rdx("zno", 1, "c", 1, "zn", 1, "co", 1, Effect(gasId = "co", colorChange = "碳还原氧化锌，生成锌"), Condition(minTemp = 900.0)),
        rdx("cr2o3", 1, "h2", 3, "cr", 2, "h2o", 3, Effect(colorChange = "氢气还原三氧化二铬，生成铬"), Condition(minTemp = 600.0)),
        rdx("cr2o3", 1, "co", 3, "cr", 2, "co2", 3, Effect(gasId = "co2", colorChange = "一氧化碳还原三氧化二铬"), Condition(minTemp = 700.0)),
        rdx("cr2o3", 2, "c", 3, "cr", 4, "co2", 3, Effect(gasId = "co2", colorChange = "碳还原三氧化二铬，生成铬"), Condition(minTemp = 1000.0)),
        rdx("cu2o", 1, "h2", 1, "cu", 2, "h2o", 1, Effect(colorChange = "氢气还原氧化亚铜，生成铜"), Condition(minTemp = 300.0)),
        rdx("cu2o", 1, "co", 1, "cu", 2, "co2", 1, Effect(gasId = "co2", colorChange = "一氧化碳还原氧化亚铜"), Condition(minTemp = 400.0)),
        rdx("cu2o", 2, "c", 1, "cu", 4, "co2", 1, Effect(gasId = "co2", colorChange = "碳还原氧化亚铜"), Condition(minTemp = 700.0)),
        rdx("ag2o", 1, "h2", 1, "ag", 2, "h2o", 1, Effect(colorChange = "氢气还原氧化银，生成银"), Condition(minTemp = 200.0)),
        rdx("ag2o", 1, "co", 1, "ag", 2, "co2", 1, Effect(gasId = "co2", colorChange = "一氧化碳还原氧化银"), Condition(minTemp = 200.0)),
        rdx("ag2o", 2, "c", 1, "ag", 4, "co2", 1, Effect(gasId = "co2", colorChange = "碳还原氧化银"), Condition(minTemp = 500.0)),
        rdx("hgo", 1, "h2", 1, "hg", 1, "h2o", 1, Effect(colorChange = "氢气还原氧化汞，生成汞"), Condition(minTemp = 300.0)),
        rdx("hgo", 1, "co", 1, "hg", 1, "co2", 1, Effect(gasId = "co2", colorChange = "一氧化碳还原氧化汞"), Condition(minTemp = 300.0)),
        rdx("hgo", 1, "c", 1, "hg", 1, "co", 1, Effect(gasId = "co", colorChange = "碳还原氧化汞"), Condition(minTemp = 600.0)),
        rdx("tio2", 2, "h2", 1, "ti", 1, "h2o", 2, Effect(colorChange = "氢气高温还原二氧化钛，生成钛"), Condition(minTemp = 900.0)),
        rdx("tio2", 1, "c", 1, "ti", 1, "co2", 1, Effect(gasId = "co2", colorChange = "碳高温还原二氧化钛，生成钛"), Condition(minTemp = 1200.0)),
        rdx("feo", 1, "mg", 1, "fe", 1, "mgo", 1, Effect(precipitateId = "fe", colorChange = "镁还原氧化亚铁，生成铁"), Condition(minTemp = 500.0)),
        rdx("feo", 3, "al", 2, "fe", 3, "al2o3", 1, Effect(precipitateId = "fe", colorChange = "铝还原氧化亚铁，生成铁"), Condition(minTemp = 600.0)),
        rdx("al", 2, "zno", 3, "al2o3", 1, "zn", 3, Effect(precipitateId = "zn", colorChange = "铝热还原氧化锌，生成锌"), Condition(minTemp = 500.0)),
        rdx("al", 2, "cu2o", 3, "al2o3", 1, "cu", 6, Effect(precipitateId = "cu", colorChange = "铝热还原氧化亚铜，生成铜"), Condition(minTemp = 500.0)),
        rdx("al", 2, "ag2o", 3, "al2o3", 1, "ag", 6, Effect(precipitateId = "ag", colorChange = "铝还原氧化银，生成银"), Condition(minTemp = 400.0)),
        rdx("mg", 2, "cu2o", 1, "mgo", 2, "cu", 2, Effect(precipitateId = "cu", colorChange = "镁还原氧化亚铜，生成铜"), Condition(minTemp = 400.0)),
        rdx("mg", 3, "cr2o3", 1, "mgo", 3, "cr", 2, Effect(colorChange = "镁还原三氧化二铬，生成铬"), Condition(minTemp = 500.0)),
        rdx("mg", 1, "mno2", 1, "mgo", 1, "mn", 1, Effect(colorChange = "镁还原二氧化锰，生成锰"), Condition(minTemp = 500.0)),
        rdx("mg", 1, "zno", 1, "mgo", 1, "zn", 1, Effect(colorChange = "镁还原氧化锌，生成锌"), Condition(minTemp = 500.0)),
        rdx("mg", 2, "tio2", 1, "mgo", 2, "ti", 1, Effect(colorChange = "镁高温还原二氧化钛，生成钛"), Condition(minTemp = 800.0)),
        rdx("mg", 1, "ag2o", 1, "mgo", 1, "ag", 2, Effect(colorChange = "镁还原氧化银，生成银"), Condition(minTemp = 300.0)),
        rdx("mg", 2, "sio2", 1, "mgo", 2, "si", 1, Effect(colorChange = "镁高温还原二氧化硅，生成硅"), Condition(minTemp = 800.0)),
        rdx("al", 4, "sio2", 3, "al2o3", 2, "si", 3, Effect(colorChange = "铝热还原二氧化硅，生成硅"), Condition(minTemp = 800.0)),
        rdx("h2s", 2, "so2", 1, "s", 3, "h2o", 2, Effect(precipitateId = "s", colorChange = "硫化氢与二氧化硫反应析出淡黄色硫"), Condition()),
        rdx("h2s", 2, "o2", 3, "so2", 2, "h2o", 2, Effect(gasId = "so2", colorChange = "硫化氢燃烧，发出淡蓝色火焰生成二氧化硫", light = true), Condition(needsIgnition = true)),
        rdx("nh3", 4, "o2", 5, "no", 4, "h2o", 6, Effect(gasId = "no", colorChange = "氨的催化氧化，生成一氧化氮（工业制硝酸）"), Condition(minTemp = 800.0, catalystId = "fe")),
        rdx("nh3", 4, "o2", 3, "n2", 2, "h2o", 6, Effect(colorChange = "氨气在氧气中燃烧，生成氮气与水"), Condition(needsIgnition = true)),
        rdx("h2s", 1, "cl2", 1, "s", 1, "hcl", 2, Effect(precipitateId = "s", colorChange = "氯气氧化硫化氢，析出淡黄色硫"), Condition()),
        rdx("h2s", 2, "o2", 1, "s", 2, "h2o", 2, Effect(precipitateId = "s", colorChange = "硫化氢不完全燃烧，析出硫"), Condition()),
        rdx("fecl3", 2, "cu", 1, "fecl2", 2, "cucl2", 1, Effect(colorChange = "铜将三价铁还原为亚铁，溶液由黄变蓝绿"), Condition()),
        rdx1("fecl3", 2, "fe", 1, "fecl2", 3, Effect(colorChange = "铁将三价铁还原为亚铁"), Condition()),
        rdx3("fecl3", 2, "h2s", 1, "fecl2", 2, "s", 1, "hcl", 2, Effect(precipitateId = "s", colorChange = "三价铁氧化硫化氢，析出硫"), Condition()),
        rdx3("fecl3", 2, "ki", 2, "fecl2", 2, "i2", 1, "kcl", 2, Effect(precipitateId = "i2", colorChange = "三价铁氧化碘离子，生成碘单质"), Condition()),
        rdx("fecl3", 2, "zn", 1, "fecl2", 2, "zncl2", 1, Effect(colorChange = "锌将三价铁还原为亚铁"), Condition()),
        rdx1("n2", 1, "o2", 2, "no2", 2, Effect(gasId = "no2", colorChange = "高温下氮气与氧气化合生成二氧化氮"), Condition(minTemp = 1200.0)),
        rdx("s", 1, "h2so4", 2, "so2", 3, "h2o", 2, Effect(gasId = "so2", colorChange = "硫被浓硫酸氧化，放出二氧化硫"), Condition(minTemp = 200.0)),
        rdx3("s", 1, "hno3", 6, "h2so4", 1, "no2", 6, "h2o", 2, Effect(gasId = "no2", colorChange = "硫被浓硝酸氧化，放出红棕色二氧化氮"), Condition(minTemp = 60.0)),
        rdx3("p", 1, "hno3", 5, "h3po4", 1, "no2", 5, "h2o", 1, Effect(gasId = "no2", colorChange = "磷被浓硝酸氧化为磷酸，放出红棕色气体"), Condition(minTemp = 60.0)),
        rdx3("c", 1, "hno3", 4, "co2", 1, "no2", 4, "h2o", 2, Effect(gasId = "co2", colorChange = "碳被浓硝酸氧化，放出二氧化碳与红棕色气体"), Condition(minTemp = 60.0)),
        rdx("zn", 1, "h2so4", 2, "znso4", 1, "so2", 1, Effect(gasId = "so2", colorChange = "锌与浓硫酸共热，放出刺激性气体"), Condition(minTemp = 200.0)),
        rdx3("ag", 3, "hno3", 4, "agno3", 3, "no", 1, "h2o", 2, Effect(gasId = "no", colorChange = "银与稀硝酸反应，生成无色一氧化氮"), Condition()),
        rdx("ag", 1, "hno3", 2, "agno3", 1, "no2", 1, Effect(gasId = "no2", colorChange = "银与浓硝酸反应，生成红棕色二氧化氮"), Condition(minTemp = 60.0)),
        rdx3("pb", 3, "hno3", 8, "pbno32", 3, "no", 2, "h2o", 4, Effect(gasId = "no", colorChange = "铅与稀硝酸反应，生成无色一氧化氮"), Condition()),
        rdx("cu2o", 1, "h2so4", 1, "cuso4", 1, "cu", 1, Effect(colorChange = "硫酸使氧化亚铜歧化，生成铜与硫酸铜"), Condition(minTemp = 60.0)),
        dec("mgoh2", 1, "mgo", 1, "h2o", 1, Effect(colorChange = "氢氧化镁受热分解，生成氧化镁"), Condition(minTemp = 350.0), false),
        dec("znoh2", 1, "zno", 1, "h2o", 1, Effect(colorChange = "氢氧化锌受热分解，生成氧化锌"), Condition(minTemp = 350.0), false),
        dec("aloh3", 2, "al2o3", 1, "h2o", 3, Effect(colorChange = "氢氧化铝受热分解，生成氧化铝"), Condition(minTemp = 450.0), false),
        dec("feoh2", 1, "feo", 1, "h2o", 1, Effect(colorChange = "氢氧化亚铁受热分解，生成氧化亚铁"), Condition(minTemp = 300.0), false),
        dec("caoh2", 1, "cao", 1, "h2o", 1, Effect(colorChange = "氢氧化钙高温分解，生成生石灰"), Condition(minTemp = 580.0), false),
        dec("nh4cl", 1, "nh3", 1, "hcl", 1, Effect(gasId = "nh3", colorChange = "氯化铵受热分解，放出氨气与氯化氢"), Condition(minTemp = 340.0), false),
        dec("cuso4", 1, "cuo", 1, "so3", 1, Effect(colorChange = "硫酸铜高温分解，生成氧化铜与三氧化硫"), Condition(minTemp = 650.0), false),
        dec("mgso4", 1, "mgo", 1, "so3", 1, Effect(colorChange = "硫酸镁高温分解，生成氧化镁与三氧化硫"), Condition(minTemp = 1000.0), false),
        dec("caso4", 1, "cao", 1, "so3", 1, Effect(colorChange = "硫酸钙高温分解，生成生石灰与三氧化硫"), Condition(minTemp = 1200.0), false),
        dec("n2o", 2, "n2", 1, "o2", 1, Effect(gasId = "o2", colorChange = "一氧化二氮受热分解为氮气和氧气"), Condition(minTemp = 600.0), false),
        dec("no2", 2, "no", 2, "o2", 1, Effect(gasId = "o2", colorChange = "二氧化氮受热分解为一氧化氮和氧气"), Condition(minTemp = 150.0), false),
        dec3("agno3", 2, "ag", 2, "no2", 2, "o2", 1, Effect(gasId = "o2", colorChange = "硝酸银见光受热分解，生成银与红棕色气体"), Condition(minTemp = 440.0), false),
        dec("agbr", 2, "ag", 2, "br2", 1, Effect(colorChange = "溴化银见光分解，生成银与溴"), Condition(minTemp = 500.0), false),
        dec("agi", 2, "ag", 2, "i2", 1, Effect(colorChange = "碘化银受热分解，生成银与碘"), Condition(minTemp = 550.0), false),
        dec3("cuno32", 2, "cuo", 2, "no2", 4, "o2", 1, Effect(gasId = "o2", colorChange = "硝酸铜受热分解，生成黑色氧化铜与红棕色气体"), Condition(minTemp = 170.0), false),
        dec3("pbno32", 2, "pbo", 2, "no2", 4, "o2", 1, Effect(gasId = "o2", colorChange = "硝酸铅受热分解，生成氧化铅与红棕色气体"), Condition(minTemp = 470.0), false),
        dec("al2o3", 2, "al", 4, "o2", 3, Effect(gasId = "o2", colorChange = "电解熔融氧化铝，阴极析出铝、阳极放出氧气"), Condition(needsElectrolysis = true), false),
        dec("hcl", 2, "h2", 1, "cl2", 1, Effect(gasId = "h2", colorChange = "电解盐酸，两极分别放出氢气与氯气"), Condition(needsElectrolysis = true), false),
        comb("na2o", 3, "p2o5", 1, "na3po4", 2, Effect(colorChange = "氧化钠与五氧化二磷化合，生成磷酸钠"), Condition(), false),
        comb("ag", 2, "cl2", 1, "agcl", 2, Effect(colorChange = "银在氯气中燃烧，生成白色氯化银", light = true), Condition(needsIgnition = true), true),
        dd3("koh", 1, "nh4cl", 1, "kcl", 1, "h2o", 1, "nh3", 1, Effect(gasId = "nh3", colorChange = "加热产生刺激气味气体（氨气）"), Condition(minTemp = 60.0), false),
        dd3("caoh2", 1, "nh4cl", 2, "cacl2", 1, "h2o", 2, "nh3", 2, Effect(gasId = "nh3", colorChange = "碱石灰与铵盐共热，放出氨气"), Condition(minTemp = 60.0), false),
        dd("so2", 1, "koh", 2, "k2so3", 1, "h2o", 1, Effect(colorChange = "二氧化硫被氢氧化钾溶液吸收")),
        dd("so2", 1, "caoh2", 1, "caso3", 1, "h2o", 1, Effect(precipitateId = "caso3", colorChange = "生成白色亚硫酸钙沉淀")),
        dd("hcl", 2, "na2sio3", 1, "h2sio3", 1, "nacl", 2, Effect(precipitateId = "h2sio3", colorChange = "生成白色胶状硅酸沉淀")),
        dd("h2s", 1, "cuno32", 1, "cus", 1, "hno3", 2, Effect(precipitateId = "cus", colorChange = "生成黑色硫化铜沉淀")),
        dd("pbcl2", 1, "agno3", 2, "agcl", 2, "pbno32", 1, Effect(precipitateId = "agcl", colorChange = "生成白色氯化银沉淀")),
        // ================= 第九批扩充：补足至 500 条 =================
        // —— 置换反应（金属与盐溶液） ——
        // —— 复分解：亚硫酸盐 / 硅酸盐 / 特征显色 ——
        // —— 化合 / 分解补充 ——
        // —— 氧化还原：碳 / 一氧化碳还原 ——
        displ("mg", 1, "pbno32", 1, "mgno32", 1, "pb", 1, Effect(precipitateId = "pb", colorChange = "镁置换出铅"), Condition()),
        displ("al", 2, "pbno32", 3, "alno33", 2, "pb", 3, Effect(precipitateId = "pb", colorChange = "铝置换出铅"), Condition()),
        displ("mg", 1, "cuno32", 1, "mgno32", 1, "cu", 1, Effect(precipitateId = "cu", colorChange = "镁置换出红色铜"), Condition()),
        displ("al", 2, "cuno32", 3, "alno33", 2, "cu", 3, Effect(precipitateId = "cu", colorChange = "铝置换出红色铜"), Condition()),
        displ("mg", 1, "mnso4", 1, "mgso4", 1, "mn", 1, Effect(precipitateId = "mn", colorChange = "镁置换出锰"), Condition()),
        dd3("k2so3", 1, "hcl", 2, "kcl", 2, "so2", 1, "h2o", 1, Effect(gasId = "so2", colorChange = "亚硫酸钾与盐酸反应，放出刺激性气体"), Condition(), false),
        dd3("caso3", 1, "h2so4", 1, "caso4", 1, "so2", 1, "h2o", 1, Effect(gasId = "so2", precipitateId = "caso4", colorChange = "亚硫酸钙与硫酸反应，放出刺激性气体"), Condition(), false),
        dd4("na2s2o3", 2, "hcl", 2, "nacl", 2, "s", 1, "so2", 1, "h2o", 1, Effect(gasId = "so2", precipitateId = "s", colorChange = "硫代硫酸钠与盐酸反应，溶液变浑浊并放出刺激性气体"), Condition(), false),
        dd("bacl2", 1, "na2so3", 1, "baso3", 1, "nacl", 2, Effect(precipitateId = "baso3", colorChange = "生成白色亚硫酸钡沉淀")),
        dd("bacl2", 1, "k2so3", 1, "baso3", 1, "kcl", 2, Effect(precipitateId = "baso3", colorChange = "生成白色亚硫酸钡沉淀")),
        dd("caoh2", 1, "na2so3", 1, "caso3", 1, "naoh", 2, Effect(precipitateId = "caso3", colorChange = "生成白色亚硫酸钙沉淀")),
        dd("cacl2", 1, "na2so3", 1, "caso3", 1, "nacl", 2, Effect(precipitateId = "caso3", colorChange = "生成白色亚硫酸钙沉淀")),
        dec("nh3h2o", 1, "nh3", 1, "h2o", 1, Effect(gasId = "nh3", colorChange = "氨水受热分解放出氨气"), Condition(minTemp = 80.0), false),
        rdx("c", 1, "cuo", 1, "cu", 1, "co", 1, Effect(gasId = "co", colorChange = "碳还原氧化铜，生成红色铜与一氧化碳"), Condition(minTemp = 800.0)),
        rdx("c", 1, "pbo", 1, "pb", 1, "co", 1, Effect(gasId = "co", colorChange = "碳还原氧化铅，生成铅"), Condition(minTemp = 800.0)),
        rdx("fe2o3", 1, "h2", 3, "fe", 2, "h2o", 3, Effect(precipitateId = "fe", colorChange = "氢气还原氧化铁，生成铁"), Condition(minTemp = 500.0)),
        rdx("cr2o3", 2, "al", 2, "al2o3", 1, "cr", 2, Effect(precipitateId = "cr", colorChange = "铝还原三氧化二铬，生成铬"), Condition(needsIgnition = true)),
        rdx("cu", 2, "h2so4", 2, "cuso4", 1, "so2", 1, Effect(gasId = "so2", colorChange = "铜与浓硫酸共热，生成二氧化硫"), Condition(minTemp = 200.0)),
        )
}

/** 反应引擎：判断反应能否发生并执行，_resolve 返回本轮实际发生的反应 */
object Engine {

    /** 水的物质 id：既是溶剂，也是部分反应的生成物，多处需要单独识别 */
    private const val WATER = "h2o"

    private const val ML_PER_MOL_WATER = 18.0

    /** 单轮结算的反应步数上限（循环保护之外的最后一道保险） */
    private const val MAX_STEPS = 60

    /** 物质总量（mol），水按体积折算 */
    fun Container.moles(id: String): Double = if (id == WATER) {
        waterMl / ML_PER_MOL_WATER
    } else {
        (solids[id] ?: 0.0) + (solutes[id] ?: 0.0) + (gases[id] ?: 0.0)
    }

    /** 消耗指定物质指定物质的量 */
    fun Container.consume(id: String, mol: Double) {
        if (id == "h2o") {
            waterMl = (waterMl - mol * ML_PER_MOL_WATER).coerceAtLeast(0.0)
            return
        }
        var left = mol
        for (m in arrayOf(solutes, solids, gases)) {
            val cur = m[id] ?: 0.0
            if (cur <= 0.0) continue
            val take = minOf(cur, left)
            val rest = cur - take
            if (rest <= 1e-9) m.remove(id) else m[id] = rest
            left -= take
            if (left <= 1e-9) break
        }
    }

    /** 加入产物，按物质性质自动归类 */
    fun Container.produce(id: String, mol: Double) {
        if (mol <= 0) return
        val s = Substances.get(id)
        if (s.isGas) {
            gases[id] = (gases[id] ?: 0.0) + mol
        } else if (id == "h2o") {
            waterMl += mol * ML_PER_MOL_WATER
        } else if (!s.isSoluble || waterMl <= 0.0) {
            solids[id] = (solids[id] ?: 0.0) + mol
        } else {
            solutes[id] = (solutes[id] ?: 0.0) + mol
        }
    }

    fun canApply(r: Reaction, c: Container): Boolean {
        // 1) 反应物均需存在（按计量比按需反应，不做绝对量的硬性要求）
        for (sp in r.reactants) if (c.moles(sp.id) < 1e-6) return false
        // 2) 相态/存在位置约束
        when (r.type) {
            ReactionType.DISPLACEMENT -> {
                val metal = r.reactants[0]
                if ((c.solids[metal.id] ?: 0.0) < 1e-6) return false
                val other = r.reactants[1]
                if (other.id != "h2o" && (c.solutes[other.id] ?: 0.0) < 1e-6) return false
            }
            ReactionType.DOUBLE_DISPLACEMENT -> {
                // 至少需有一种反应物处于溶液（水溶液环境）
                val anySolute = r.reactants.any { (c.solutes[it.id] ?: 0.0) > 1e-6 }
                if (!anySolute) return false
            }
            else -> {}
        }
        // 3) 条件
        val cond = r.condition
        if (cond.needsIgnition && !c.ignitionArmed) return false
        if (cond.needsElectrolysis && !c.isElectrolyzing) return false
        if (cond.catalystId != null && c.moles(cond.catalystId!!) <= 1e-9) return false
        if (c.temperature + 1e-6 < cond.minTemp) return false
        return true
    }

    private fun apply(r: Reaction, c: Container) {
        // 反应规模 = 各反应物（按其系数归一化）的最小可用量
        var scale = Double.MAX_VALUE
        for (sp in r.reactants) {
            val available = c.moles(sp.id)
            val s = available / sp.coef
            if (s < scale) scale = s
        }
        if (scale <= 0.0) return
        for (sp in r.reactants) c.consume(sp.id, sp.coef * scale)
        for (sp in r.products) {
            c.produce(sp.id, sp.coef * scale)
            // 生成物视为"最新出现"，便于后续链式反应继续推进
            c.touch(sp.id)
        }
    }

    /**
     * 给一条候选反应打分：分值越高越优先执行，用于解决"谁先反应"的问题。
     *
     * 打分策略（从强到弱）：
     *  1. focus（本次操作刚加入的物质）参与的反应最优先 —— 保证"我想做的反应"先发生；
     *  2. 反应物加入次序之和越大越优先 —— 越"新鲜"的材料越先反应，避免被旧残留抢走；
     *  3. 反应条件越苛刻（点燃/通电/催化剂/高温）越优先 —— 这类反应需要用户主动操作才会触发；
     *  4. 反应物种类越多越优先 —— 更特异的组合优先于泛泛的反应。
     * 分值相同的候选保持数据库原始顺序（Kotlin 的 maxByOrNull 取首个最大值）。
     *
     * 水（溶剂）在第 1、2 条里都被排除：它恒大量存在，且每次作为产物生成时都会被
     * touch 成最新，若让它参与竞争，「CO₂ + H₂O → H₂CO₃」这类与溶剂自身的反应就会
     * 抢先于「CO₂ + 2NaOH → Na₂CO₃ + H₂O」这类真正的溶液反应，把刚通入的气体吃光，
     * 用户想做的反应因此无法发生。
     */
    private fun score(r: Reaction, c: Container, focus: String?): Long {
        var s = 0L
        if (focus != null && focus != WATER && r.reactants.any { it.id == focus }) s += 1_000_000L
        s += r.reactants.sumOf { if (it.id == WATER) 0L else (c.order[it.id] ?: 0).toLong() } * 100L
        val cond = r.condition
        if (cond.needsIgnition) s += 30
        if (cond.needsElectrolysis) s += 30
        if (cond.catalystId != null) s += 20
        if (cond.minTemp > 30.0) s += 10
        s += r.reactants.size * 5L
        return s
    }

    /**
     * 互逆反应伙伴表：反应 id -> 与它互为可逆反应（产物=对方反应物，反之亦然）的 id 集合。
     * 例如 H₂CO₃ → H₂O + CO₂ 与 CO₂ + H₂O → H₂CO₃。
     */
    private val reversePartners: Map<String, Set<String>> by lazy {
        val reactantIds = Reactions.ALL.associate { it.id to it.reactants.map { sp -> sp.id }.toSet() }
        val productIds = Reactions.ALL.associate { it.id to it.products.map { sp -> sp.id }.toSet() }
        val map = HashMap<String, MutableSet<String>>()
        for (a in Reactions.ALL) {
            for (b in Reactions.ALL) {
                if (a.id == b.id) continue
                if (reactantIds[a.id] == productIds[b.id] && productIds[a.id] == reactantIds[b.id]) {
                    map.getOrPut(a.id) { HashSet() }.add(b.id)
                }
            }
        }
        map
    }

    /**
     * 反复尝试应用可发生的反应，直到没有新的反应发生（或达到上限）。
     *
     * @param focus 本次操作刚加入的物质 id；其参与的反应会被优先触发，
     *              从而避免刚加入的材料被容器里残留的其它物质抢先消耗。
     * 返回本轮实际发生的反应列表（用于记录与成就）。
     */
    fun resolve(c: Container, focus: String? = null): List<Reaction> {
        val happened = ArrayList<Reaction>()
        var guard = 0
        while (guard++ < MAX_STEPS) {
            var next: Reaction? = null
            var best = Long.MIN_VALUE
            for (r in Reactions.ALL) {
                if (!canApply(r, c)) continue
                val sc = score(r, c, focus)
                if (sc > best) { best = sc; next = r }
            }
            val chosen = next ?: break
            // 循环保护：本轮已发生过的反应不再重复。apply 会耗尽某个反应物，
            // 因此同一条反应被再次选中，只可能是它已被后续反应重新生成 —— 即死循环。
            // 例如「H₂CO₃ → H₂O + CO₂」后 CO₂ 又被「CO₂ + H₂O → H₂CO₃」吸收回去。
            // 同时把该反应的可逆反应一并标记，避免刚分解放出的气体被立刻重新吸收。
            if (chosen.id in c.doneReactions) break
            apply(chosen, c)
            c.doneReactions.add(chosen.id)
            reversePartners[chosen.id]?.let { c.doneReactions.addAll(it) }
            happened.add(chosen)
            // 点燃为一次性：放过一次后熄灭引燃状态
            if (chosen.condition.needsIgnition) c.ignitionArmed = false
        }
        return happened
    }
}