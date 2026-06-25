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

    /** Injects a skill `effects` array (ADR 0008) onto the sample pack's lone skill. */
    private fun packWithEffect(effectJson: String): String =
        samplePack().replace(
            "\"targeting\": \"enemy\" } }",
            "\"targeting\": \"enemy\" }, \"effects\": [ $effectJson ] }",
        )

    @Test
    fun decodesSkillHealEffect() {
        val content = ContentJsonLoader.load(packWithEffect("{ \"type\": \"heal\", \"target\": \"ally\", \"amount\": 30 }"))
        val heal = content.tables.skills.first().effects.single() as com.ccz.core.model.SkillEffect.Heal
        assertEquals(com.ccz.core.model.EffectTarget.ALLY, heal.target)
        assertEquals(30, heal.amount)
    }

    @Test
    fun unknownSkillEffectTypeFailsClosed() {
        assertFailsWith<ContentDecodeException> {
            ContentJsonLoader.load(packWithEffect("{ \"type\": \"teleport\", \"target\": \"ally\", \"amount\": 30 }"))
        }
    }

    @Test
    fun unknownEffectTargetFailsClosed() {
        assertFailsWith<ContentDecodeException> {
            ContentJsonLoader.load(packWithEffect("{ \"type\": \"heal\", \"target\": \"martian\", \"amount\": 30 }"))
        }
    }

    @Test
    fun nonPositiveHealAmountIsAValidationIssue() {
        // Shape decodes (amount is a plain Int); ContentValidator catches the bound (>= 1), fail-closed.
        val content = ContentJsonLoader.load(packWithEffect("{ \"type\": \"heal\", \"target\": \"ally\", \"amount\": 0 }"))
        assertTrue(ContentValidator.validate(content).any { it.path.contains("effects") && it.message.contains("heal amount") })
    }

    @Test
    fun decodesPercentMaxHealMode() {
        val content = ContentJsonLoader.load(
            packWithEffect("{ \"type\": \"heal\", \"target\": \"ally\", \"amount\": 30, \"mode\": \"percent_max\" }"),
        )
        val heal = content.tables.skills.first().effects.single() as com.ccz.core.model.SkillEffect.Heal
        assertEquals(com.ccz.core.model.HealMode.PERCENT_MAX, heal.mode)
        assertEquals(30, heal.amount)
    }

    @Test
    fun unknownHealModeFailsClosed() {
        assertFailsWith<ContentDecodeException> {
            ContentJsonLoader.load(packWithEffect("{ \"type\": \"heal\", \"target\": \"ally\", \"amount\": 30, \"mode\": \"divine\" }"))
        }
    }

    @Test
    fun outOfRangePercentHealIsAValidationIssue() {
        val content = ContentJsonLoader.load(
            packWithEffect("{ \"type\": \"heal\", \"target\": \"ally\", \"amount\": 150, \"mode\": \"percent_max\" }"),
        )
        assertTrue(ContentValidator.validate(content).any { it.path.contains("effects") && it.message.contains("percent heal") })
    }

    @Test
    fun decodesStatDeltaEffect() {
        val content = ContentJsonLoader.load(
            packWithEffect("{ \"type\": \"stat_delta\", \"target\": \"ally\", \"stat\": \"atk\", \"amount\": 15 }"),
        )
        val sd = content.tables.skills.first().effects.single() as com.ccz.core.model.SkillEffect.StatDelta
        assertEquals(com.ccz.core.model.AffectedStat.ATK, sd.stat)
        assertEquals(15, sd.amount)
    }

    @Test
    fun unknownStatFailsClosed() {
        assertFailsWith<ContentDecodeException> {
            ContentJsonLoader.load(packWithEffect("{ \"type\": \"stat_delta\", \"target\": \"ally\", \"stat\": \"luck\", \"amount\": 15 }"))
        }
    }

    @Test
    fun zeroStatDeltaIsAValidationIssue() {
        val content = ContentJsonLoader.load(
            packWithEffect("{ \"type\": \"stat_delta\", \"target\": \"ally\", \"stat\": \"atk\", \"amount\": 0 }"),
        )
        assertTrue(ContentValidator.validate(content).any { it.path.contains("effects") && it.message.contains("stat delta") })
    }

    @Test
    fun decodesEnemyTargetedDebuff() {
        // A signed negative StatDelta on an ENEMY band (ADR 0008 Phase 2 debuff) decodes + validates clean.
        val content = ContentJsonLoader.load(
            packWithEffect("{ \"type\": \"stat_delta\", \"target\": \"enemy\", \"stat\": \"atk\", \"amount\": -15 }"),
        )
        val sd = content.tables.skills.first().effects.single() as com.ccz.core.model.SkillEffect.StatDelta
        assertEquals(com.ccz.core.model.EffectTarget.ENEMY, sd.target)
        assertEquals(-15, sd.amount)
        assertTrue(ContentValidator.validate(content).isEmpty())
    }

    @Test
    fun negativeStatDeltaDurationIsAValidationIssue() {
        // duration >= 0 (0 = instant, > 0 = timed; ADR 0008 Phase 3); a negative is malformed content.
        val content = ContentJsonLoader.load(
            packWithEffect("{ \"type\": \"stat_delta\", \"target\": \"ally\", \"stat\": \"atk\", \"amount\": 15, \"duration\": -1 }"),
        )
        assertTrue(ContentValidator.validate(content).any { it.path.contains("duration") && it.message.contains("duration") })
    }

    @Test
    fun healCannotTargetAnEnemyIsAValidationIssue() {
        // A heal is a friendly effect — targeting an enemy is a coherence error (decodes, then fails validation).
        val content = ContentJsonLoader.load(packWithEffect("{ \"type\": \"heal\", \"target\": \"enemy\", \"amount\": 30 }"))
        assertTrue(ContentValidator.validate(content).any { it.path.contains("effects") && it.message.contains("enemy") })
    }

    @Test
    fun decodesApplyAilmentEffect() {
        // ADR 0008 command-legality ailment: an enemy-targeted timed silence decodes + validates clean.
        val content = ContentJsonLoader.load(
            packWithEffect("{ \"type\": \"apply_ailment\", \"target\": \"enemy\", \"ailment\": \"silence\", \"duration\": 2 }"),
        )
        val ail = content.tables.skills.first().effects.single() as com.ccz.core.model.SkillEffect.ApplyAilment
        assertEquals(com.ccz.core.model.EffectTarget.ENEMY, ail.target)
        assertEquals(com.ccz.core.model.Ailment.SILENCE, ail.ailment)
        assertEquals(2, ail.duration)
        assertTrue(ContentValidator.validate(content).isEmpty())
    }

    @Test
    fun unknownAilmentKindFailsClosed() {
        assertFailsWith<ContentDecodeException> {
            ContentJsonLoader.load(packWithEffect("{ \"type\": \"apply_ailment\", \"target\": \"enemy\", \"ailment\": \"curse\", \"duration\": 2 }"))
        }
    }

    @Test
    fun ailmentMustTargetAnEnemyIsAValidationIssue() {
        // An ailment is a hostile effect (the mirror of a heal staying friendly) — targeting an ally is incoherent.
        val content = ContentJsonLoader.load(
            packWithEffect("{ \"type\": \"apply_ailment\", \"target\": \"ally\", \"ailment\": \"silence\", \"duration\": 2 }"),
        )
        assertTrue(ContentValidator.validate(content).any { it.path.contains("effects") && it.message.contains("ailment") })
    }

    @Test
    fun nonPositiveAilmentDurationIsAValidationIssue() {
        // duration >= 1 (a 0-duration ailment is meaningless — the resolver would no-op it); fail-closed.
        val content = ContentJsonLoader.load(
            packWithEffect("{ \"type\": \"apply_ailment\", \"target\": \"enemy\", \"ailment\": \"silence\", \"duration\": 0 }"),
        )
        assertTrue(ContentValidator.validate(content).any { it.path.contains("duration") && it.message.contains("ailment") })
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
