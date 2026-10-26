package com.chemlab.world

import com.chemlab.game.Container
import com.chemlab.game.Equipment
import com.chemlab.game.Equipments

/** 端口传输的物质/能量种类 */
enum class PortKind { LIQUID, GAS, HEAT, ELECTRIC }

/**
 * 端口方向：
 * IN  只能作为接收端，OUT 只能作为输出端，BIDIRECTIONAL 两者皆可。
 * 连接规则不允许 OUT→OUT 与 IN→IN（没有明确的物质流向）。
 */
enum class Direction { IN, OUT, BIDIRECTIONAL }

/**
 * 器材上的一个物理接口。
 *
 * @param id  器材内唯一（如 "heat_in"、"gas_out"），连接图按器材 id + 端口 id 定位
 * @param kind 传输内容，只有同 kind 的端口才能互连
 * @param dir  流向约束
 */
data class Port(
    val id: String,
    val kind: PortKind,
    val dir: Direction = Direction.BIDIRECTIONAL
)

/**
 * 器材 → 端口表。
 * 未列出的器材（蒸发皿等）返回空表，即不可参与联动。
 *
 * 三种反应容器都带 `elec_in`：电源的接线端可直接接上来，
 * 容器通电后由 [com.chemlab.world.WorldEngine] 置位 linkPowered 触发容器内电解。
 */
object EquipmentPorts {
    fun of(e: Equipment): List<Port> = when (e) {
        Equipments.BEAKER -> listOf(
            Port("top", PortKind.LIQUID),
            Port("heat_in", PortKind.HEAT, Direction.IN),
            Port("elec_in", PortKind.ELECTRIC, Direction.IN)
        )

        Equipments.TEST_TUBE -> listOf(
            Port("mouth", PortKind.LIQUID),
            Port("gas_out", PortKind.GAS, Direction.OUT),
            Port("heat_in", PortKind.HEAT, Direction.IN),
            Port("elec_in", PortKind.ELECTRIC, Direction.IN)
        )

        Equipments.FLASK -> listOf(
            Port("mouth", PortKind.LIQUID),
            Port("gas_out", PortKind.GAS, Direction.OUT),
            Port("heat_in", PortKind.HEAT, Direction.IN),
            Port("elec_in", PortKind.ELECTRIC, Direction.IN)
        )

        Equipments.ALCOHOL_LAMP -> listOf(
            Port("heat_out", PortKind.HEAT, Direction.OUT)
        )

        Equipments.GAS_COLLECTOR -> listOf(
            Port("gas_in", PortKind.GAS, Direction.IN),
            Port("water_out", PortKind.LIQUID, Direction.OUT)
        )

        Equipments.FUNNEL -> listOf(
            Port("liquid_in", PortKind.LIQUID, Direction.IN),
            Port("liquid_out", PortKind.LIQUID, Direction.OUT)
        )

        Equipments.BATTERY -> listOf(
            Port("elec_out", PortKind.ELECTRIC, Direction.OUT)
        )

        // 其余器材（含原有集气瓶/蒸发皿）不参与联动
        else -> emptyList()
    }
}

/**
 * 实验台上的一件器材。
 *
 * 每件器材自带一个 [Container]，所以「单容器反应引擎」原样复用：
 * 器材之间只通过 [Port] + 连接图交换物质/热量，不在容器层面耦合。
 */
class Apparatus(
    val id: String,
    val equipment: Equipment,
    var x: Float = 0f,
    var y: Float = 0f
) {
    val container = Container(equipment)
    val ports: List<Port> = EquipmentPorts.of(equipment)

    /**
     * 内容版本号：容器内容或温度发生变化时自增。
     * UI 据此判断是否需要重绘该器材（脏标记），避免每帧全量重绘。
     */
    var revision: Long = 0L

    fun port(portId: String): Port? = ports.firstOrNull { it.id == portId }

    /** 标记「内容已变化」，驱动 UI 重绘 */
    fun markDirty() {
        revision++
    }
}