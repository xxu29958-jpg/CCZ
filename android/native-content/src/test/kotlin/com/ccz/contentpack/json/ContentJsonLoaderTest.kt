package com.ccz.contentpack.json

import com.ccz.contentpack.ContentValidator
import com.ccz.core.model.DamageKind
import com.ccz.core.model.Faction
import com.ccz.core.model.Pos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ContentJsonLoaderTest {
    private fun samplePack(
        faction: String = "PLAYER",
        kind: String = "PHYSICAL",
        formatVersion: String = "1",
        relation: String = "FAVOR",
    ): String =
        """
        {
          "manifest": {
            "native_format_version": "$formatVersion",
            "content_id": "sample",
            "content_version": "0.1.0",
            "source": { "mod": "sample", "engine": "6.6" },
            "entry": "events/r.json"
          },
          "tables": {
            "classes": [
              { "id": "cavalry", "name": "Cavalry", "movement": { "move_type": "horse", "move": 6 },
                "combat": { "counters": { "cavalry": "$relation" } } }
            ],
            "units": [
              {
                "identity": { "id": "zhaoyun", "name": "Zhao Yun", "class_id": "cavalry", "faction": "$faction" },
                "profile": { "level": 1, "hp_max": 200, "stats": { "atk": 180, "def": 120, "mat": 60, "res": 90 } },
                "loadout": { "skills": ["atk"], "items": [] }
              }
            ],
            "terrain": [ { "id": "plain", "name": "Plain", "move_cost": 1 } ],
            "skills": [
              { "id": "atk", "name": "Attack", "kind": "$kind", "power_coeff": 100,
                "use": { "range": { "min": 1, "max": 1 }, "area": "single", "targeting": "enemy" } }
            ],
            "items": [],
            "maps": [
              { "id": "m001", "size": { "width": 2, "height": 2 }, "tileset": "plain",
                "tiles": [["plain","plain"],["plain","plain"]],
                "spawn_points": { "player": [ { "x": 0, "y": 0 } ] } }
            ]
          },
          "events": {
            "r_scripts": [
              { "id": "events/r.json", "ops": [] }
            ],
            "portrait_subjects": [
              { "id": "cao_cao", "name": "Cao Cao", "portrait": "cao_cao_neutral" }
            ]
          }
        }
        """.trimIndent()

    @Test
    fun terrainPassableDecodesExplicitFalseAndDefaultsTrueWhenOmitted() {
        // The sample's terrain omits `passable`, so it must default true (passable terrain).
        assertTrue(ContentJsonLoader.load(samplePack()).tables.terrain.first().passable)
        // An explicit `"passable": false` (a wall) must carry through the DTO + mapper.
        val withWall = samplePack().replace("\"move_cost\": 1", "\"move_cost\": 1, \"passable\": false")
        assertEquals(false, ContentJsonLoader.load(withWall).tables.terrain.first().passable)
    }

    @Test
    fun loadsValidPackThatValidatesCleanWithFaithfulNestedValues() {
        val content = ContentJsonLoader.load(samplePack())
        assertEquals("sample", content.manifest.contentId)
        assertTrue(ContentValidator.validate(content).isEmpty())

        val unit = content.tables.units.first()
        assertEquals(Faction.PLAYER, unit.identity.faction)
        assertEquals(200, unit.profile.hpMax)
        // nested stats/rates assert against field-swap: atk!=def etc.
        assertEquals(listOf(180, 120, 60, 90), listOf(unit.profile.stats.atk, unit.profile.stats.def, unit.profile.stats.mat, unit.profile.stats.res))
        assertEquals(100, unit.profile.rates.accuracy.hit) // defaulted when omitted

        val skill = content.tables.skills.first()
        assertEquals(DamageKind.PHYSICAL, skill.kind)
        assertEquals(1 to 1, skill.use.range.min to skill.use.range.max)

        assertEquals("FAVOR", content.tables.classes.first().combat.counters["cavalry"]) // decodeCounterRelation success path
        assertEquals("cao_cao_neutral", content.events.portraitSubjects.single().portrait)
        val map = content.tables.maps.first()
        assertEquals(Pos(0, 0), map.spawnPoints.getValue("player").first())
        assertEquals(listOf(listOf("plain", "plain"), listOf("plain", "plain")), map.tiles)
    }

    @Test
    fun unknownFactionFailsClosed() {
        assertFailsWith<ContentDecodeException> { ContentJsonLoader.load(samplePack(faction = "NEUTRAL")) }
    }

    @Test
    fun unknownDamageKindFailsClosed() {
        assertFailsWith<ContentDecodeException> { ContentJsonLoader.load(samplePack(kind = "MAGIC")) }
    }

    @Test
    fun unknownCounterRelationFailsClosed() {
        assertFailsWith<ContentDecodeException> { ContentJsonLoader.load(samplePack(relation = "SOMETIMES")) }
    }

    @Test
    fun missingRequiredFieldFailsClosed() {
        val broken = samplePack().replace("\"hp_max\": 200,", "")
        assertTrue(broken != samplePack(), "guard: hp_max removal must change the JSON")
        assertFailsWith<ContentDecodeException> { ContentJsonLoader.load(broken) }
    }

    @Test
    fun unknownJsonKeyFailsClosed() {
        val broken = samplePack().replace("\"entry\": \"events/r.json\"", "\"entry\": \"events/r.json\", \"bogus\": 1")
        assertTrue(broken.contains("\"bogus\""), "guard: bogus key must be inserted")
        assertFailsWith<ContentDecodeException> { ContentJsonLoader.load(broken) }
    }

    @Test
    fun unsupportedFormatVersionDecodesButFailsValidation() {
        val content = ContentJsonLoader.load(samplePack(formatVersion = "2"))
        assertEquals("sample", content.manifest.contentId) // decode still succeeded
        val issues = ContentValidator.validate(content)
        assertEquals(1, issues.size)
        assertEquals("manifest.native_format_version", issues.first().path)
    }
}
