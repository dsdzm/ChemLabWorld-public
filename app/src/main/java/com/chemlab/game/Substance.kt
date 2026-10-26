package com.chemlab.game

/**
 * 物质分类
 */
enum class Category(val label: String) {
    ELEMENT("单质"),
    OXIDE("氧化物"),
    ACID("酸"),
    BASE("碱"),
    SALT("盐")
}

/**
 * 物质常态（刚放入时的形态）
 */
enum class Phase(val label: String) {
    SOLID("固体"),
    LIQUID("液体"),
    GAS("气体"),
    AQUEOUS("溶液")
}

/**
 * 水溶性：可溶 / 微溶 / 不溶
 */
enum class Solubility { SOLUBLE, SPARINGLY, INSOLUBLE }

/**
 * 一种化学物质
 *
 * @param solidColor   固体外观颜色（ARGB）
 * @param solutionColor 水溶液颜色，null 表示无色
 */
data class Substance(
    val id: String,
    val cn: String,            // 中文名
    val formula: String,       // 化学式
    val category: Category,
    val phase: Phase,          // 常温/试剂常态
    val solidColor: Int,
    val solutionColor: Int?,
    val solubility: Solubility,
    val note: String = ""
) {
    val isGas: Boolean get() = phase == Phase.GAS
    val isSoluble: Boolean get() = solubility == Solubility.SOLUBLE || solubility == Solubility.SPARINGLY
}

/** 常用颜色常量（ARGB） */
object C {
    val WHITE = 0xFFF1F3F4.toInt()
    val BLACK = 0xFF23272B.toInt()
    val GRAY = 0xFF8A939B.toInt()
    val SILVER = 0xFFC6CCD2.toInt()     // 金属银灰
    val COPPER = 0xFFD98E4A.toInt()     // 铜红
    val RED_BROWN = 0xFFB45A3C.toInt()  // 红棕色（铁锈）
    val YELLOW = 0xFFF0C94F.toInt()     // 硫黄
    val YELLOW_GREEN = 0xFFB7D94B.toInt() // 氯气黄绿
    val BLUE = 0xFF4A90D9.toInt()       // 蓝色（铜盐）
    val LIGHT_GREEN = 0xFF9FE2BF.toInt()// 浅绿（亚铁离子）
    val PURPLE = 0xFFA268C8.toInt()     // 紫红（高锰酸钾）
    val COLORLESS = 0xFFB9E3EE.toInt()  // 无色溶液占位（水面光效）
    val RED = 0xFFE34A44.toInt()        // 血红（硫氰化铁 / 砖红）
    val GREEN = 0xFF3E9E5A.toInt()      // 绿色（三氧化二铬）
}

/**
 * 物质注册表：涵盖单质、气体、常见酸、碱、盐、氧化物。
 * 数据驱动，扩展只需在此追加。
 */
object Substances {

    // ============ 单质 ============
    val H2 = Substance("h2", "氢气", "H₂", Category.ELEMENT, Phase.GAS, C.GRAY, null, Solubility.SPARINGLY)
    val O2 = Substance("o2", "氧气", "O₂", Category.ELEMENT, Phase.GAS, C.GRAY, null, Solubility.SPARINGLY)
    val N2 = Substance("n2", "氮气", "N₂", Category.ELEMENT, Phase.GAS, C.GRAY, null, Solubility.INSOLUBLE)
    val CL2 = Substance("cl2", "氯气", "Cl₂", Category.ELEMENT, Phase.GAS, C.YELLOW_GREEN, null, Solubility.SOLUBLE)
    val C_S = Substance("c", "碳", "C", Category.ELEMENT, Phase.SOLID, C.BLACK, null, Solubility.INSOLUBLE)
    val S = Substance("s", "硫", "S", Category.ELEMENT, Phase.SOLID, C.YELLOW, null, Solubility.INSOLUBLE)
    val P = Substance("p", "磷", "P", Category.ELEMENT, Phase.SOLID, C.YELLOW, null, Solubility.INSOLUBLE)
    val NA = Substance("na", "钠", "Na", Category.ELEMENT, Phase.SOLID, C.SILVER, null, Solubility.INSOLUBLE, "遇水剧烈反应")
    val MG = Substance("mg", "镁", "Mg", Category.ELEMENT, Phase.SOLID, C.SILVER, null, Solubility.INSOLUBLE)
    val AL = Substance("al", "铝", "Al", Category.ELEMENT, Phase.SOLID, C.SILVER, null, Solubility.INSOLUBLE)
    val ZN = Substance("zn", "锌", "Zn", Category.ELEMENT, Phase.SOLID, C.SILVER, null, Solubility.INSOLUBLE)
    val FE = Substance("fe", "铁", "Fe", Category.ELEMENT, Phase.SOLID, C.SILVER, null, Solubility.INSOLUBLE)
    val CU = Substance("cu", "铜", "Cu", Category.ELEMENT, Phase.SOLID, C.COPPER, null, Solubility.INSOLUBLE)
    val AG = Substance("ag", "银", "Ag", Category.ELEMENT, Phase.SOLID, C.WHITE, null, Solubility.INSOLUBLE)
    val BR2 = Substance("br2", "溴", "Br₂", Category.ELEMENT, Phase.LIQUID, C.RED_BROWN, null, Solubility.SOLUBLE, "红棕色液体，易挥发")
    val I2 = Substance("i2", "碘", "I₂", Category.ELEMENT, Phase.SOLID, C.PURPLE, null, Solubility.SPARINGLY, "紫黑色固体，受热升华")
    val SI = Substance("si", "硅", "Si", Category.ELEMENT, Phase.SOLID, C.GRAY, null, Solubility.INSOLUBLE)
    val TI = Substance("ti", "钛", "Ti", Category.ELEMENT, Phase.SOLID, C.SILVER, null, Solubility.INSOLUBLE, "银白色过渡金属")
    val PB = Substance("pb", "铅", "Pb", Category.ELEMENT, Phase.SOLID, C.SILVER, null, Solubility.INSOLUBLE)
    val SN = Substance("sn", "锡", "Sn", Category.ELEMENT, Phase.SOLID, C.SILVER, null, Solubility.INSOLUBLE)
    val HG = Substance("hg", "汞", "Hg", Category.ELEMENT, Phase.LIQUID, C.SILVER, null, Solubility.INSOLUBLE, "常温下唯一液态金属，俗称水银")
    val CR = Substance("cr", "铬", "Cr", Category.ELEMENT, Phase.SOLID, C.SILVER, null, Solubility.INSOLUBLE)
    val K = Substance("k", "钾", "K", Category.ELEMENT, Phase.SOLID, C.SILVER, null, Solubility.INSOLUBLE, "遇水剧烈反应并燃烧")
    val CA = Substance("ca", "钙", "Ca", Category.ELEMENT, Phase.SOLID, C.SILVER, null, Solubility.INSOLUBLE, "遇水反应放出氢气")
    val MN = Substance("mn", "锰", "Mn", Category.ELEMENT, Phase.SOLID, C.GRAY, null, Solubility.INSOLUBLE, "银灰色金属，可被铝热还原")

    // ============ 氧化物 ============
    val H2O = Substance("h2o", "水", "H₂O", Category.OXIDE, Phase.LIQUID, C.COLORLESS, null, Solubility.SOLUBLE, "万能溶剂")
    val H2O2 = Substance("h2o2", "过氧化氢", "H₂O₂", Category.OXIDE, Phase.LIQUID, C.COLORLESS, null, Solubility.SOLUBLE)
    val CO = Substance("co", "一氧化碳", "CO", Category.OXIDE, Phase.GAS, C.GRAY, null, Solubility.INSOLUBLE)
    val CO2 = Substance("co2", "二氧化碳", "CO₂", Category.OXIDE, Phase.GAS, C.GRAY, null, Solubility.SOLUBLE)
    val SO2 = Substance("so2", "二氧化硫", "SO₂", Category.OXIDE, Phase.GAS, C.GRAY, null, Solubility.SOLUBLE)
    val NO2 = Substance("no2", "二氧化氮", "NO₂", Category.OXIDE, Phase.GAS, C.RED_BROWN, null, Solubility.SOLUBLE, "红棕色有毒气体")
    val CAO = Substance("cao", "氧化钙", "CaO", Category.OXIDE, Phase.SOLID, C.WHITE, null, Solubility.SOLUBLE, "生石灰，遇水放热")
    val CUO = Substance("cuo", "氧化铜", "CuO", Category.OXIDE, Phase.SOLID, C.BLACK, null, Solubility.INSOLUBLE)
    val FE2O3 = Substance("fe2o3", "氧化铁", "Fe₂O₃", Category.OXIDE, Phase.SOLID, C.RED_BROWN, null, Solubility.INSOLUBLE, "铁锈")
    val FE3O4 = Substance("fe3o4", "四氧化三铁", "Fe₃O₄", Category.OXIDE, Phase.SOLID, C.BLACK, null, Solubility.INSOLUBLE)
    val MGO = Substance("mgo", "氧化镁", "MgO", Category.OXIDE, Phase.SOLID, C.WHITE, null, Solubility.INSOLUBLE)
    val AL2O3 = Substance("al2o3", "氧化铝", "Al₂O₃", Category.OXIDE, Phase.SOLID, C.WHITE, null, Solubility.INSOLUBLE)
    val P2O5 = Substance("p2o5", "五氧化二磷", "P₂O₅", Category.OXIDE, Phase.SOLID, C.WHITE, null, Solubility.SOLUBLE)
    val MNO2 = Substance("mno2", "二氧化锰", "MnO₂", Category.OXIDE, Phase.SOLID, C.BLACK, null, Solubility.INSOLUBLE, "常用催化剂")
    val ZNO = Substance("zno", "氧化锌", "ZnO", Category.OXIDE, Phase.SOLID, C.WHITE, null, Solubility.INSOLUBLE, "两性氧化物")
    val NA2O = Substance("na2o", "氧化钠", "Na₂O", Category.OXIDE, Phase.SOLID, C.WHITE, null, Solubility.SOLUBLE, "遇水剧烈放热")
    val K2O = Substance("k2o", "氧化钾", "K₂O", Category.OXIDE, Phase.SOLID, C.WHITE, null, Solubility.SOLUBLE, "遇水剧烈放热")
    val AG2O = Substance("ag2o", "氧化银", "Ag₂O", Category.OXIDE, Phase.SOLID, C.RED_BROWN, null, Solubility.INSOLUBLE, "棕黑色，受热分解")
    val FEO = Substance("feo", "氧化亚铁", "FeO", Category.OXIDE, Phase.SOLID, C.BLACK, null, Solubility.INSOLUBLE, "黑色粉末")
    val CR2O3 = Substance("cr2o3", "三氧化二铬", "Cr₂O₃", Category.OXIDE, Phase.SOLID, C.GREEN, null, Solubility.INSOLUBLE, "绿色固体")
    val NA2O2 = Substance("na2o2", "过氧化钠", "Na₂O₂", Category.OXIDE, Phase.SOLID, C.YELLOW, null, Solubility.SOLUBLE, "淡黄色强氧化剂")
    val V2O5 = Substance("v2o5", "五氧化二钒", "V₂O₅", Category.OXIDE, Phase.SOLID, C.YELLOW, null, Solubility.INSOLUBLE, "接触法制硫酸的催化剂")
    val SIO2 = Substance("sio2", "二氧化硅", "SiO₂", Category.OXIDE, Phase.SOLID, C.WHITE, null, Solubility.INSOLUBLE, "沙子、石英的主要成分")
    val PBO = Substance("pbo", "氧化铅", "PbO", Category.OXIDE, Phase.SOLID, C.YELLOW, null, Solubility.INSOLUBLE)
    val CU2O = Substance("cu2o", "氧化亚铜", "Cu₂O", Category.OXIDE, Phase.SOLID, C.RED_BROWN, null, Solubility.INSOLUBLE, "砖红色固体")
    val HGO = Substance("hgo", "氧化汞", "HgO", Category.OXIDE, Phase.SOLID, C.RED_BROWN, null, Solubility.INSOLUBLE, "红色固体，受热分解放出氧气")
    val TIO2 = Substance("tio2", "二氧化钛", "TiO₂", Category.OXIDE, Phase.SOLID, C.WHITE, null, Solubility.INSOLUBLE, "白色颜料钛白粉")
    val SO3 = Substance("so3", "三氧化硫", "SO₃", Category.OXIDE, Phase.SOLID, C.WHITE, null, Solubility.SOLUBLE, "遇水形成硫酸")
    val N2O = Substance("n2o", "一氧化二氮", "N₂O", Category.OXIDE, Phase.GAS, C.GRAY, null, Solubility.INSOLUBLE, "笑气")
    val NO = Substance("no", "一氧化氮", "NO", Category.OXIDE, Phase.GAS, C.GRAY, null, Solubility.INSOLUBLE, "无色气体，遇氧气变红棕色")

    // ============ 酸 ============
    val HCL = Substance("hcl", "盐酸", "HCl", Category.ACID, Phase.AQUEOUS, C.COLORLESS, null, Solubility.SOLUBLE)
    val H2SO4 = Substance("h2so4", "硫酸", "H₂SO₄", Category.ACID, Phase.AQUEOUS, C.COLORLESS, null, Solubility.SOLUBLE)
    val HNO3 = Substance("hno3", "硝酸", "HNO₃", Category.ACID, Phase.AQUEOUS, C.COLORLESS, null, Solubility.SOLUBLE)
    val H2CO3 = Substance("h2co3", "碳酸", "H₂CO₃", Category.ACID, Phase.AQUEOUS, C.COLORLESS, null, Solubility.SOLUBLE, "不稳定，易分解")
    val H2S = Substance("h2s", "硫化氢", "H₂S", Category.ACID, Phase.GAS, C.GRAY, null, Solubility.SOLUBLE, "臭鸡蛋气味、有毒")
    val CH3COOH = Substance("ch3cooh", "醋酸", "CH₃COOH", Category.ACID, Phase.AQUEOUS, C.COLORLESS, null, Solubility.SOLUBLE)
    val H2SO3 = Substance("h2so3", "亚硫酸", "H₂SO₃", Category.ACID, Phase.AQUEOUS, C.COLORLESS, null, Solubility.SOLUBLE, "不稳定，易分解")
    val H3PO4 = Substance("h3po4", "磷酸", "H₃PO₄", Category.ACID, Phase.AQUEOUS, C.COLORLESS, null, Solubility.SOLUBLE)
    val HF = Substance("hf", "氢氟酸", "HF", Category.ACID, Phase.AQUEOUS, C.COLORLESS, null, Solubility.SOLUBLE, "弱酸，能腐蚀玻璃")
    val HCLO = Substance("hclo", "次氯酸", "HClO", Category.ACID, Phase.AQUEOUS, C.COLORLESS, null, Solubility.SOLUBLE, "弱酸，具有漂白性")

    // ============ 碱 ============
    val NAOH = Substance("naoh", "氢氧化钠", "NaOH", Category.BASE, Phase.SOLID, C.WHITE, null, Solubility.SOLUBLE, "强碱，俗称烧碱")
    val KOH = Substance("koh", "氢氧化钾", "KOH", Category.BASE, Phase.SOLID, C.WHITE, null, Solubility.SOLUBLE)
    val CAOH2 = Substance("caoh2", "氢氧化钙", "Ca(OH)₂", Category.BASE, Phase.SOLID, C.WHITE, null, Solubility.SPARINGLY, "熟石灰，微溶")
    val BAOH2 = Substance("baoh2", "氢氧化钡", "Ba(OH)₂", Category.BASE, Phase.SOLID, C.WHITE, null, Solubility.SOLUBLE)
    val NH3H2O = Substance("nh3h2o", "氨水", "NH₃·H₂O", Category.BASE, Phase.AQUEOUS, C.COLORLESS, null, Solubility.SOLUBLE)
    val NH3 = Substance("nh3", "氨气", "NH₃", Category.BASE, Phase.GAS, C.GRAY, null, Solubility.SOLUBLE)

    // ============ 盐（可溶） ============
    val NACL = Substance("nacl", "氯化钠", "NaCl", Category.SALT, Phase.SOLID, C.WHITE, null, Solubility.SOLUBLE, "食盐")
    val KCL = Substance("kcl", "氯化钾", "KCl", Category.SALT, Phase.SOLID, C.WHITE, null, Solubility.SOLUBLE)
    val CACL2 = Substance("cacl2", "氯化钙", "CaCl₂", Category.SALT, Phase.SOLID, C.WHITE, null, Solubility.SOLUBLE)
    val MGCL2 = Substance("mgcl2", "氯化镁", "MgCl₂", Category.SALT, Phase.SOLID, C.WHITE, null, Solubility.SOLUBLE)
    val ALCL3 = Substance("alcl3", "氯化铝", "AlCl₃", Category.SALT, Phase.SOLID, C.WHITE, null, Solubility.SOLUBLE)
    val ZNCL2 = Substance("zncl2", "氯化锌", "ZnCl₂", Category.SALT, Phase.SOLID, C.WHITE, null, Solubility.SOLUBLE)
    val CUCL2 = Substance("cucl2", "氯化铜", "CuCl₂", Category.SALT, Phase.SOLID, C.WHITE, C.BLUE, Solubility.SOLUBLE)
    val FECL2 = Substance("fecl2", "氯化亚铁", "FeCl₂", Category.SALT, Phase.SOLID, C.WHITE, C.LIGHT_GREEN, Solubility.SOLUBLE)
    val FECL3 = Substance("fecl3", "氯化铁", "FeCl₃", Category.SALT, Phase.SOLID, C.WHITE, C.YELLOW, Solubility.SOLUBLE)
    val BACL2 = Substance("bacl2", "氯化钡", "BaCl₂", Category.SALT, Phase.SOLID, C.WHITE, null, Solubility.SOLUBLE)
    val NH4CL = Substance("nh4cl", "氯化铵", "NH₄Cl", Category.SALT, Phase.SOLID, C.WHITE, null, Solubility.SOLUBLE)
    val AGNO3 = Substance("agno3", "硝酸银", "AgNO₃", Category.SALT, Phase.SOLID, C.WHITE, null, Solubility.SOLUBLE)
    val NANO3 = Substance("nano3", "硝酸钠", "NaNO₃", Category.SALT, Phase.SOLID, C.WHITE, null, Solubility.SOLUBLE)
    val CUNO32 = Substance("cuno32", "硝酸铜", "Cu(NO₃)₂", Category.SALT, Phase.SOLID, C.WHITE, C.BLUE, Solubility.SOLUBLE)
    val ZNNO32 = Substance("znno32", "硝酸锌", "Zn(NO₃)₂", Category.SALT, Phase.SOLID, C.WHITE, null, Solubility.SOLUBLE)
    val FENO32 = Substance("feno32", "硝酸亚铁", "Fe(NO₃)₂", Category.SALT, Phase.SOLID, C.WHITE, C.LIGHT_GREEN, Solubility.SOLUBLE)
    val BANO32 = Substance("bano32", "硝酸钡", "Ba(NO₃)₂", Category.SALT, Phase.SOLID, C.WHITE, null, Solubility.SOLUBLE)
    val NA2CO3 = Substance("na2co3", "碳酸钠", "Na₂CO₃", Category.SALT, Phase.SOLID, C.WHITE, null, Solubility.SOLUBLE, "纯碱、苏打")
    val NAHCO3 = Substance("nahco3", "碳酸氢钠", "NaHCO₃", Category.SALT, Phase.SOLID, C.WHITE, null, Solubility.SOLUBLE, "小苏打")
    val NA2SO4 = Substance("na2so4", "硫酸钠", "Na₂SO₄", Category.SALT, Phase.SOLID, C.WHITE, null, Solubility.SOLUBLE)
    val K2SO4 = Substance("k2so4", "硫酸钾", "K₂SO₄", Category.SALT, Phase.SOLID, C.WHITE, null, Solubility.SOLUBLE)
    val MGSO4 = Substance("mgso4", "硫酸镁", "MgSO₄", Category.SALT, Phase.SOLID, C.WHITE, null, Solubility.SOLUBLE)
    val AL2SO43 = Substance("al2so43", "硫酸铝", "Al₂(SO₄)₃", Category.SALT, Phase.SOLID, C.WHITE, null, Solubility.SOLUBLE)
    val ZNSO4 = Substance("znso4", "硫酸锌", "ZnSO₄", Category.SALT, Phase.SOLID, C.WHITE, null, Solubility.SOLUBLE)
    val FESO4 = Substance("feso4", "硫酸亚铁", "FeSO₄", Category.SALT, Phase.SOLID, C.WHITE, C.LIGHT_GREEN, Solubility.SOLUBLE)
    val FE2SO43 = Substance("fe2so43", "硫酸铁", "Fe₂(SO₄)₃", Category.SALT, Phase.SOLID, C.WHITE, C.YELLOW, Solubility.SOLUBLE)
    val CUSO4 = Substance("cuso4", "硫酸铜", "CuSO₄", Category.SALT, Phase.SOLID, C.BLUE, C.BLUE, Solubility.SOLUBLE, "蓝矾/胆矾晶体")
    val KMNO4 = Substance("kmno4", "高锰酸钾", "KMnO₄", Category.SALT, Phase.SOLID, C.PURPLE, C.PURPLE, Solubility.SOLUBLE)
    val K2MNO4 = Substance("k2mno4", "锰酸钾", "K₂MnO₄", Category.SALT, Phase.SOLID, C.BLACK, C.PURPLE, Solubility.SOLUBLE)
    val KCLO3 = Substance("kclo3", "氯酸钾", "KClO₃", Category.SALT, Phase.SOLID, C.WHITE, null, Solubility.SOLUBLE)
    val NA2SO3 = Substance("na2so3", "亚硫酸钠", "Na₂SO₃", Category.SALT, Phase.SOLID, C.WHITE, null, Solubility.SOLUBLE)
    val NA3PO4 = Substance("na3po4", "磷酸钠", "Na₃PO₄", Category.SALT, Phase.SOLID, C.WHITE, null, Solubility.SOLUBLE)
    val KI = Substance("ki", "碘化钾", "KI", Category.SALT, Phase.SOLID, C.WHITE, null, Solubility.SOLUBLE)
    val KBR = Substance("kbr", "溴化钾", "KBr", Category.SALT, Phase.SOLID, C.WHITE, null, Solubility.SOLUBLE)
    val KNO3 = Substance("kno3", "硝酸钾", "KNO₃", Category.SALT, Phase.SOLID, C.WHITE, null, Solubility.SOLUBLE, "受热易分解")
    val KNO2 = Substance("kno2", "亚硝酸钾", "KNO₂", Category.SALT, Phase.SOLID, C.WHITE, null, Solubility.SOLUBLE)
    val NACLO = Substance("naclo", "次氯酸钠", "NaClO", Category.SALT, Phase.SOLID, C.WHITE, null, Solubility.SOLUBLE, "漂白液，具有漂白性")
    val KSCN = Substance("kscn", "硫氰化钾", "KSCN", Category.SALT, Phase.SOLID, C.WHITE, null, Solubility.SOLUBLE)
    val FESCN = Substance("fescn", "硫氰化铁", "Fe(SCN)₃", Category.SALT, Phase.AQUEOUS, C.COLORLESS, C.RED, Solubility.SOLUBLE, "遇Fe³⁺变血红")
    val PBNO32 = Substance("pbno32", "硝酸铅", "Pb(NO₃)₂", Category.SALT, Phase.SOLID, C.WHITE, null, Solubility.SOLUBLE)
    val NABR = Substance("nabr", "溴化钠", "NaBr", Category.SALT, Phase.SOLID, C.WHITE, null, Solubility.SOLUBLE)
    val NAF = Substance("naf", "氟化钠", "NaF", Category.SALT, Phase.SOLID, C.WHITE, null, Solubility.SOLUBLE)
    val NH4NO3 = Substance("nh4no3", "硝酸铵", "NH₄NO₃", Category.SALT, Phase.SOLID, C.WHITE, null, Solubility.SOLUBLE, "受热易分解")
    val NA2SIO3 = Substance("na2sio3", "硅酸钠", "Na₂SiO₃", Category.SALT, Phase.SOLID, C.WHITE, null, Solubility.SOLUBLE, "水玻璃")
    val SNCL2 = Substance("sncl2", "氯化亚锡", "SnCl₂", Category.SALT, Phase.SOLID, C.WHITE, null, Solubility.SOLUBLE)
    val NA2S2O3 = Substance("na2s2o3", "硫代硫酸钠", "Na₂S₂O₃", Category.SALT, Phase.SOLID, C.WHITE, null, Solubility.SOLUBLE)
    val MNSO4 = Substance("mnso4", "硫酸锰", "MnSO₄", Category.SALT, Phase.SOLID, C.WHITE, null, Solubility.SOLUBLE, "淡粉红色溶液")

    // ============ 盐 / 碱（难溶，沉淀） ============
    val FES = Substance("fes", "硫化亚铁", "FeS", Category.SALT, Phase.SOLID, C.BLACK, null, Solubility.INSOLUBLE, "黑色固体")
    val CACO3 = Substance("caco3", "碳酸钙", "CaCO₃", Category.SALT, Phase.SOLID, C.WHITE, null, Solubility.INSOLUBLE, "石灰石")
    val AGCL = Substance("agcl", "氯化银", "AgCl", Category.SALT, Phase.SOLID, C.WHITE, null, Solubility.INSOLUBLE, "白色沉淀")
    val BASO4 = Substance("baso4", "硫酸钡", "BaSO₄", Category.SALT, Phase.SOLID, C.WHITE, null, Solubility.INSOLUBLE, "白色沉淀")
    val CASO4 = Substance("caso4", "硫酸钙", "CaSO₄", Category.SALT, Phase.SOLID, C.WHITE, null, Solubility.SPARINGLY)
    val BACO3 = Substance("baco3", "碳酸钡", "BaCO₃", Category.SALT, Phase.SOLID, C.WHITE, null, Solubility.INSOLUBLE, "白色沉淀")
    val CUOH2 = Substance("cuoh2", "氢氧化铜", "Cu(OH)₂", Category.BASE, Phase.SOLID, C.BLUE, null, Solubility.INSOLUBLE, "蓝色沉淀")
    val FEOH2 = Substance("feoh2", "氢氧化亚铁", "Fe(OH)₂", Category.BASE, Phase.SOLID, C.WHITE, null, Solubility.INSOLUBLE, "白色沉淀")
    val FEOH3 = Substance("feoh3", "氢氧化铁", "Fe(OH)₃", Category.BASE, Phase.SOLID, C.RED_BROWN, null, Solubility.INSOLUBLE, "红褐色沉淀")
    val MGOH2 = Substance("mgoh2", "氢氧化镁", "Mg(OH)₂", Category.BASE, Phase.SOLID, C.WHITE, null, Solubility.INSOLUBLE, "白色沉淀")
    val ALOH3 = Substance("aloh3", "氢氧化铝", "Al(OH)₃", Category.BASE, Phase.SOLID, C.WHITE, null, Solubility.INSOLUBLE, "白色沉淀")
    val ZNOH2 = Substance("znoh2", "氢氧化锌", "Zn(OH)₂", Category.BASE, Phase.SOLID, C.WHITE, null, Solubility.INSOLUBLE, "白色沉淀")
    val CUS = Substance("cus", "硫化铜", "CuS", Category.SALT, Phase.SOLID, C.BLACK, null, Solubility.INSOLUBLE, "黑色沉淀")
    val AG2S = Substance("ag2s", "硫化银", "Ag₂S", Category.SALT, Phase.SOLID, C.BLACK, null, Solubility.INSOLUBLE, "黑色沉淀")
    val PBI2 = Substance("pbi2", "碘化铅", "PbI₂", Category.SALT, Phase.SOLID, C.YELLOW, null, Solubility.INSOLUBLE, "黄色沉淀")
    val AGBR = Substance("agbr", "溴化银", "AgBr", Category.SALT, Phase.SOLID, C.YELLOW, null, Solubility.INSOLUBLE, "浅黄色沉淀")
    val AGI = Substance("agi", "碘化银", "AgI", Category.SALT, Phase.SOLID, C.YELLOW, null, Solubility.INSOLUBLE, "黄色沉淀")
    val CAF2 = Substance("caf2", "氟化钙", "CaF₂", Category.SALT, Phase.SOLID, C.WHITE, null, Solubility.INSOLUBLE, "白色沉淀，萤石")
    val PBSO4 = Substance("pbso4", "硫酸铅", "PbSO₄", Category.SALT, Phase.SOLID, C.WHITE, null, Solubility.INSOLUBLE, "白色沉淀")
    val AL2S3 = Substance("al2s3", "硫化铝", "Al₂S₃", Category.SALT, Phase.SOLID, C.GRAY, null, Solubility.INSOLUBLE, "遇水双水解")
    val FES2 = Substance("fes2", "二硫化亚铁", "FeS₂", Category.SALT, Phase.SOLID, C.YELLOW, null, Solubility.INSOLUBLE, "黄铁矿")
    val HGS = Substance("hgs", "硫化汞", "HgS", Category.SALT, Phase.SOLID, C.RED, null, Solubility.INSOLUBLE, "红色固体，俗称辰砂")

    // ============ 补充物质（用于扩充反应库） ============
    val K2CO3 = Substance("k2co3", "碳酸钾", "K₂CO₃", Category.SALT, Phase.SOLID, C.WHITE, null, Solubility.SOLUBLE, "草木灰主要成分")
    val CANO32 = Substance("cano32", "硝酸钙", "Ca(NO₃)₂", Category.SALT, Phase.SOLID, C.WHITE, null, Solubility.SOLUBLE)
    val MGNO32 = Substance("mgno32", "硝酸镁", "Mg(NO₃)₂", Category.SALT, Phase.SOLID, C.WHITE, null, Solubility.SOLUBLE)
    val ALNO33 = Substance("alno33", "硝酸铝", "Al(NO₃)₃", Category.SALT, Phase.SOLID, C.WHITE, null, Solubility.SOLUBLE)
    val FENO33 = Substance("feno33", "硝酸铁", "Fe(NO₃)₃", Category.SALT, Phase.SOLID, C.WHITE, C.YELLOW, Solubility.SOLUBLE)
    val MNCL2 = Substance("mncl2", "氯化锰", "MnCl₂", Category.SALT, Phase.SOLID, C.WHITE, null, Solubility.SOLUBLE, "近无色，淡粉色溶液")
    val K2SO3 = Substance("k2so3", "亚硫酸钾", "K₂SO₃", Category.SALT, Phase.SOLID, C.WHITE, null, Solubility.SOLUBLE)
    val H2SIO3 = Substance("h2sio3", "硅酸", "H₂SiO₃", Category.ACID, Phase.SOLID, C.WHITE, null, Solubility.INSOLUBLE, "白色胶状沉淀")
    val CASO3 = Substance("caso3", "亚硫酸钙", "CaSO₃", Category.SALT, Phase.SOLID, C.WHITE, null, Solubility.INSOLUBLE, "白色沉淀")
    val BASO3 = Substance("baso3", "亚硫酸钡", "BaSO₃", Category.SALT, Phase.SOLID, C.WHITE, null, Solubility.INSOLUBLE, "白色沉淀")
    val PBCL2 = Substance("pbcl2", "氯化铅", "PbCl₂", Category.SALT, Phase.SOLID, C.WHITE, null, Solubility.SPARINGLY, "白色微溶固体")

    private val all = listOf(
        H2, O2, N2, CL2, C_S, S, P, NA, MG, AL, ZN, FE, CU, AG, BR2, I2, SI, TI, PB, SN, HG, CR, K, CA, MN,
        H2O, H2O2, CO, CO2, SO2, NO2, CAO, CUO, FE2O3, FE3O4, MGO, AL2O3, P2O5, MNO2, ZNO, NA2O, K2O, AG2O,
        FEO, CR2O3, NA2O2, V2O5, SIO2, PBO, CU2O, HGO, TIO2, SO3, N2O, NO,
        HCL, H2SO4, HNO3, H2CO3, CH3COOH, H2SO3, H3PO4, H2S, HF, HCLO,
        NAOH, KOH, CAOH2, BAOH2, NH3H2O, NH3,
        NACL, KCL, CACL2, MGCL2, ALCL3, ZNCL2, CUCL2, FECL2, FECL3, BACL2, NH4CL, AGNO3, NANO3, CUNO32, ZNNO32, FENO32, BANO32,
        NA2CO3, NAHCO3, NA2SO4, K2SO4, MGSO4, AL2SO43, ZNSO4, FESO4, FE2SO43, CUSO4, KMNO4, K2MNO4, KCLO3, NA2SO3, NA3PO4, KI, KBR, KNO3, KNO2, NACLO,
        KSCN, FESCN, PBNO32, NABR, NAF, NH4NO3, NA2SIO3, SNCL2, NA2S2O3, MNSO4,
        FES, CACO3, AGCL, BASO4, CASO4, BACO3, CUOH2, FEOH2, FEOH3, MGOH2, ALOH3, ZNOH2,
        CUS, AG2S, PBI2, AGBR, AGI, CAF2, PBSO4, AL2S3, FES2, HGS,
        K2CO3, CANO32, MGNO32, ALNO33, FENO33, MNCL2, K2SO3, H2SIO3, CASO3, BASO3, PBCL2
    )

    private val byId: Map<String, Substance> = all.associateBy { it.id }

    fun get(id: String): Substance = byId[id] ?: throw IllegalArgumentException("未知物质: $id")

    fun all(): List<Substance> = all

    fun byCategory(c: Category): List<Substance> = all.filter { it.category == c }

    // ============ 二级分类（物质选择界面的子分类滑动标签） ============

    /** "全部"子分类标签，表示不做过筛 */
    const val SUB_ALL = "全部"

    /** 某大类下可用的二级分类标签（始终以"全部"开头，供横向滑动选择） */
    fun subGroups(c: Category): List<String> = when (c) {
        Category.ELEMENT -> listOf(SUB_ALL, "金属", "非金属")
        Category.OXIDE -> listOf(SUB_ALL, "金属氧化物", "非金属氧化物")
        Category.ACID -> listOf(SUB_ALL, "含氧酸", "无氧酸")
        Category.BASE -> listOf(SUB_ALL, "可溶碱", "难溶碱")
        Category.SALT -> listOf(
            SUB_ALL, "钠盐", "钾盐", "钙盐", "镁盐", "铝盐", "铁盐", "铜盐",
            "锌盐", "钡盐", "银盐", "铵盐", "铅盐", "锰盐", "锡盐", "汞盐", "其他盐"
        )
    }

    /** 物质是否属于指定二级分类（sub 为"全部"时恒为真） */
    fun inSubGroup(s: Substance, sub: String): Boolean =
        sub == SUB_ALL || subGroupOf(s) == sub

    /** 计算物质的二级分类标签 */
    fun subGroupOf(s: Substance): String = when (s.category) {
        Category.ELEMENT -> if (s.id in NONMETAL_ELEMENTS) "非金属" else "金属"
        Category.OXIDE -> if (s.id in NONMETAL_OXIDES) "非金属氧化物" else "金属氧化物"
        Category.ACID -> if (s.id in OXY_ACIDS) "含氧酸" else "无氧酸"
        Category.BASE -> if (s.isSoluble) "可溶碱" else "难溶碱"
        Category.SALT -> saltCationGroup(s.id)
    }

    private val NONMETAL_ELEMENTS = setOf("h2", "o2", "n2", "cl2", "c", "s", "p", "br2", "i2", "si")
    private val NONMETAL_OXIDES = setOf("h2o", "h2o2", "co", "co2", "so2", "no2", "p2o5", "sio2", "so3", "n2o", "no")
    private val OXY_ACIDS = setOf("h2so4", "hno3", "h2co3", "ch3cooh", "h2so3", "h3po4", "hclo", "h2sio3")

    /** 按盐的阳离子（物质 id 前缀）归类 */
    private fun saltCationGroup(id: String): String {
        val rules = listOf(
            "nh4" to "铵盐", "na" to "钠盐", "k" to "钾盐", "ca" to "钙盐", "mg" to "镁盐",
            "al" to "铝盐", "zn" to "锌盐", "fe" to "铁盐", "cu" to "铜盐", "ba" to "钡盐",
            "ag" to "银盐", "pb" to "铅盐", "mn" to "锰盐", "sn" to "锡盐", "hg" to "汞盐"
        )
        for ((prefix, label) in rules) if (id.startsWith(prefix)) return label
        return "其他盐"
    }
}