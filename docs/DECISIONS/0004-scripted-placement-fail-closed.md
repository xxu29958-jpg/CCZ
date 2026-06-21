# 0004 脚本 placement op 尊重占用/地形（无 teleport 逃逸口）

Date: 2026-06-21

Status: Accepted

## Context

事件脚本的 placement op —— `BattleOp.SpawnUnit`（落援军）与 `BattleOp.MoveUnit`（编剧强制移动）—— 直接操纵棋盘。引擎其余部分（`MoveReachability`、`CommandValidator`）依赖两条空间不变式：**「一格至多一个存活单位」**（占用单源自 `BattleState.units`，经 `occupancyOf` 派生）与**地形可通行**（`BattleMap.tileAt(pos).passable`、`inBounds`）。

这两个 op 此前零落点校验，可悄悄破坏不变式：把两个存活单位叠到同格（后续 occupancy 派生丢一个、可达性/射程算错）、把单位放到越界或不可通行格（单位滑出棋盘 / 站墙上）。需要定一个跨 op 的策略：编剧 op 究竟该**绕过**这些约束（teleport 语义，"剧情高于规则"），还是 **fail-closed 尊重**它们。

## Decision

脚本 placement op **fail-closed 尊重落点约束**，无 teleport / ignore-terrain 逃逸口。

- 共享 `BattleOps.blockedTile(state, at, excludeUnit, map)`：**占用恒查**（从 state 派生，不需 map）；**边界 / 可通行**在 `ScriptContext.map` 提供时查。
- 被拒 = **no-op，不伪造 state**，surface `Event.SpawnRejected` / `Event.MoveRejected(unit, reason)`（`PlacementReject`）让表现层可观测。
- 占用一致用 `occupancyOf(state, exclude = op.unit)`，目标即自格不自阻（镜像 `CommandValidator` 的玩家移动）。
- `PlacementReject.NO_TEMPLATE` 为 spawn 专有（move 作用于已在场单位，`MoveRejected` 不携带它）。

## Consequences

- 守住「一格一活单位」占用单源不变式；spawn/move 对称，编剧无法意外叠单位 / 出界 / 上墙。
- `OUT_OF_BOUNDS` / `IMPASSABLE` 目前**生产 dormant**——`ScriptContext.map` 尚未接进生产 spawn/move 路径（`BattleAssembler.scriptContext` 不传 map，待战役驱动层 / `:app`）；占用恒生效。该 dormant 状态在 `PlacementReject` KDoc 诚实标注，不写现在时断言。
- **限制**：需要把单位 teleport 到**不可通行**地形或**已占用**格的 cutscene 当前不支持。若未来确有此需求（如剧情站上特殊塔 / 水面），须**新增显式 op 变体**（如 `TeleportUnit` 或 op 上的 `ignoreTerrain` flag）并另开 ADR——**不得悄悄放宽**既有 placement op 的默认 fail-closed。

## Rollback Conditions

- 若证实编剧确需 teleport 语义：新增**显式**逃逸 op（保持既有 `SpawnUnit`/`MoveUnit` 的 fail-closed 默认不变），更新本 ADR。
- 若占用/地形不变式本身被重构（如允许同格叠放的特殊规则）：须先改 `occupancyOf` / `MoveReachability` 的权威定义，本策略随之复核。
