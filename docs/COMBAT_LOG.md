# MoonFlower Combat Log

MoonFlower writes combat encounters as append-only JSON Lines files under:

```text
%APPDATA%\Haven and Hearth\MoonFlower\combat-logs\combat-YYYY-MM-DD.jsonl
```

Each line is one independently parseable event. Records contain a schema version,
encounter UUID, monotonically increasing sequence number, UTC timestamp, elapsed
encounter time, session-local opponent Gob ID, and an event-specific `detail`
object.

## Event types

- `encounter_started` and `encounter_ended`
- `target_selected`
- `initiative_updated`
- `action_used` for player and opponent actions
- `player_attack_cooldown`
- `damage_observed` and `player_damage_observed`
- `combat_state`, emitted at most four times per second and only when its
  openings, meters, damage, or relation state changed

## Evidence boundary

The initial player snapshot records base and effective Strength, Agility,
Constitution, Perception, Unarmed, and Melee values when available, plus the
equipped weapon resource and current meters.

The Haven protocol does not expose an opposing player's exact attributes or HP.
Those fields are explicitly marked unavailable. MoonFlower records only native
observations: action resources, IP/OIP, opening colors and percentages, observed
weapon, inferred agility range, and damage numbers. Known creature HP may include
the existing evidence-labelled animal health estimate; estimates are never stored
as exact values.

This schema is intended as a durable input for later analysis and supervised
automation. The logger does not make combat decisions or send combat actions.
