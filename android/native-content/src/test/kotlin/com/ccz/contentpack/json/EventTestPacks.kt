package com.ccz.contentpack.json

/**
 * Wraps an `events` JSON fragment in a minimal, reference-clean native content pack so event-decode
 * tests can focus on the script payload. Units `zhaoyun`/`extra` and item `potion` exist, so reference
 * validation stays clean for ops/conditions that point at them.
 */
internal fun eventPack(eventsJson: String, entry: String = "s1"): String =
    """
    {
      "manifest": {
        "native_format_version": "1",
        "content_id": "sample",
        "content_version": "0.1.0",
        "source": { "mod": "sample" },
        "entry": "$entry"
      },
      "tables": {
        "classes": [ { "id": "cavalry", "name": "Cavalry", "movement": { "move_type": "horse", "move": 6 } } ],
        "units": [
          {
            "identity": { "id": "zhaoyun", "name": "Zhao Yun", "class_id": "cavalry", "faction": "PLAYER" },
            "profile": { "level": 1, "hp_max": 200, "stats": { "atk": 180, "def": 120, "mat": 60, "res": 90 } }
          },
          {
            "identity": { "id": "extra", "name": "Extra", "class_id": "cavalry", "faction": "ENEMY" },
            "profile": { "level": 1, "hp_max": 100, "stats": { "atk": 80, "def": 70, "mat": 40, "res": 50 } }
          }
        ],
        "terrain": [ { "id": "plain", "name": "Plain", "move_cost": 1 } ],
        "skills": [],
        "items": [ { "id": "potion", "name": "Potion", "type": "consumable" } ],
        "maps": []
      },
      "events": $eventsJson
    }
    """.trimIndent()
