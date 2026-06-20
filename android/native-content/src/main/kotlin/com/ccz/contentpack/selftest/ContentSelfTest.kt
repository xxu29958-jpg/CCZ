package com.ccz.contentpack.selftest

import com.ccz.contentpack.ClassDef
import com.ccz.contentpack.ClassMovement
import com.ccz.contentpack.ContentManifest
import com.ccz.contentpack.ContentTables
import com.ccz.contentpack.ContentValidator
import com.ccz.contentpack.MapDef
import com.ccz.contentpack.MapSize
import com.ccz.contentpack.NativeContent
import com.ccz.contentpack.SourceInfo
import com.ccz.contentpack.TerrainDef
import com.ccz.contentpack.UnitDef
import com.ccz.contentpack.UnitIdentity
import com.ccz.contentpack.UnitProfile
import com.ccz.contentpack.assembly.BattleAssembler
import com.ccz.core.model.CombatStats
import com.ccz.core.model.Faction
import com.ccz.core.model.Pos

fun main() {
    val content = NativeContent(
        manifest = ContentManifest(
            nativeFormatVersion = "1",
            contentId = "sample",
            contentVersion = "0.1.0",
            source = SourceInfo(mod = "sample", engine = "6.6"),
            entry = "events/r_001.json",
        ),
        tables = ContentTables(
            classes = listOf(
                ClassDef(
                    id = "cavalry",
                    name = "Cavalry",
                    movement = ClassMovement(moveType = "horse", move = 6),
                ),
            ),
            units = listOf(
                UnitDef(
                    identity = UnitIdentity(
                        id = "zhaoyun",
                        name = "Zhao Yun",
                        classId = "cavalry",
                        faction = Faction.PLAYER,
                    ),
                    profile = UnitProfile(
                        level = 1,
                        hpMax = 200,
                        stats = CombatStats(atk = 180, def = 120, mat = 60, res = 90),
                    ),
                ),
            ),
            terrain = listOf(
                TerrainDef(id = "plain", name = "Plain", moveCost = 1),
            ),
            skills = emptyList(),
            items = emptyList(),
            maps = listOf(
                MapDef(
                    id = "m001",
                    size = MapSize(width = 2, height = 2),
                    tileset = "plain",
                    tiles = listOf(
                        listOf("plain", "plain"),
                        listOf("plain", "plain"),
                    ),
                    spawnPoints = mapOf("player" to listOf(Pos(0, 0))),
                ),
            ),
        ),
    )

    val issues = ContentValidator.validate(content)
    check(issues.isEmpty()) {
        "expected sample native content to validate, got: $issues"
    }
    println("OK native content validator self-test passed")

    checkReserveAssembly(content)
}

private fun checkReserveAssembly(content: NativeContent) {
    val zhaoyun = BattleAssembler.reserves(content.tables.units)["zhaoyun"]
    check(zhaoyun != null && zhaoyun.hp == 200 && zhaoyun.hp == zhaoyun.hpMax) {
        "expected assembled reserve zhaoyun at full hp 200, got $zhaoyun"
    }
    println("OK native content reserve assembler self-test passed")
}
