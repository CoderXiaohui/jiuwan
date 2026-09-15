# 酒玩 WebSocket 协议 v1

## 连接与身份

端点为同源 `/ws`，生产环境用 `wss://`。浏览器创建房间或加入房间拿到 `{ roomCode, playerId, playerToken }` 后保存到 localStorage。建立 WebSocket 后 15 秒内发送：

```json
{"type":"RECONNECT","requestId":"id","roomCode":"123456","playerToken":"secret"}
```

服务端根据 Token 摘要查找 Player 并绑定连接，不信任消息中的 `playerId` 作为认证依据。认证成功回复 `PLAYER_RECONNECTED`，包含当前玩家完整视图。身份失效不可通过自报 playerId 重新进入。

相同身份的新连接替换旧连接；旧连接收到 `SESSION_REPLACED`，停止重连，但可主动重新连接。切换浏览器或设备时原 localStorage 不会自动同步，应使用新玩家加入。

## 客户端消息

通用结构：

```ts
type Command = {
  type: 'RECONNECT' | 'GAME_ACTION' | 'HEARTBEAT'
  requestId?: string       // 所有 GAME_ACTION 必填，1–80 字符
  roomCode: string
  playerId?: string        // 可选，但提供后必须与绑定身份相同
  playerToken?: string     // 仅 RECONNECT 使用
  gameId?: string
  instanceId?: string      // 具体游戏操作 / NEXT_ROUND 必须匹配本轮
  action?: string
  data?: Record<string, unknown>
}
```

| action | 权限 | data / 其他字段 |
|---|---|---|
| START_GAME | 房主，至少 2 人在线 | `gameId` |
| END_GAME | 房主 | 结束当前游戏，回到选择界面 |
| NEXT_ROUND | 房主且已结算 | 当前 `gameId`、`instanceId` |
| UPDATE_PROFILE | 本人 | `{ nickname, avatar }` |
| UPDATE_SETTINGS | 房主且非游戏中 | 完整 RoomSettings |
| KICK_PLAYER | 房主 | `{ targetPlayerId }` |
| LEAVE_ROOM | 本人 | `{}` |
| CLOSE_ROOM | 房主 | `{}` |
| VOTE | 本轮玩家 | `{ targetPlayerId }` |
| SHAKE_DICE | 本轮玩家 | `{}`，客户端提供的点数不参与计算 |
| SPIN | 本轮指定转盘玩家 | `{}`，服务端生成结果索引 |
| SELECT_PLAYER | 转盘要求指定时的转盘玩家 | `{ targetPlayerId }` |
| DRAW | 真心话被选中玩家 | `{ type: 'truth' \| 'dare' }` |
| ANSWER | 默契测试选中的两位玩家 | `{ answer: '给定选项之一' }` |
| LOOK | 炸金花仍在场玩家（可非本人回合） | `{ turnNumber }` |
| CALL / RAISE / FOLD | 炸金花当前行动玩家 | `{ turnNumber }`，RAISE 将底分加 1，服务端计算积分 |
| COMPARE | 炸金花当前行动玩家 | `{ turnNumber, targetPlayerId }` |

`UPDATE_SETTINGS`：

```json
{"punishmentMode":"challenge","gameMode":"normal","maxPlayers":12,"anonymousVote":true,"safeMode":true,"diceRule":"lowest","zhaJinHua235":false,"zhaJinHuaDrink":true}
```

心跳每 20 秒：

```json
{"type":"HEARTBEAT","roomCode":"123456"}
```

服务端回应 `{ "type": "HEARTBEAT", "serverTime": ... }`。

## 服务器消息

```ts
type Envelope = {
  type: string
  requestId?: string
  roomCode?: string
  gameId?: string
  version?: number
  serverTime?: number
  data?: RoomView
  error?: { code: string; message: string }
}
```

- `ROOM_CREATED`：创建事件（通常此时创建者尚未连接，身份由 REST 返回）。
- `PLAYER_JOINED` / `PLAYER_LEFT` / `PLAYER_RECONNECTED`：成员变化。
- `ROOM_STATE_UPDATE`：离线、房主自动转移、定时超时等房间变化。
- `GAME_STARTED`：开始游戏，包含 `startsAt` 倒计时。
- `GAME_STATE_UPDATE`：动作后的玩家视图。
- `GAME_RESULT`：游戏动作完成结算；定时器结算也可通过 `ROOM_STATE_UPDATE` 的 `game.complete` 判断。
- `GAME_CHANGED`：开始下一轮，含新的 `instanceId`。
- `GAME_ENDED`：房主结束游戏，`game` 清空，可选择下一款游戏。
- `ACK`：命令确认，带原 `requestId` 和最新个人快照。重复 requestId 不重复执行，但仍返回 ACK。
- `ERROR`：统一错误消息，有 requestId 时匹配对应请求。
- `HEARTBEAT`：连接保活 / 服务端时钟。

被移出和关闭房间会给受影响连接发送 `INVALID_PLAYER` / `ROOM_CLOSED` 并关闭连接。主动退出者把该终止消息视作离场成功，清理身份并回到首页。

前端不依赖某个特定事件才更新状态，收到 `data` 就按版本应用完整快照。不得把“未收到某类消息”当作结果仍未生成；断线后的快照是最终依据。

## RoomView 和私有信息

`RoomView`：`roomId, roomCode, ownerId, status, currentGameId, createdAt, version, players, settings, game, events`。

玩家：`playerId, nickname, avatar, connected, isOwner, joinedAt, score`。**没有 Token 或 Token 摘要**。

游戏基础字段：`gameId, instanceId, round, complete, startsAt, deadline, participants, submittedCount, hasActed, myChoice?`。

公开附加字段由游戏定义：

- Vote：`question`，结束后 `counts, selectedIds`。匿名模式不提供 `ballots`。
- Dice：`rule`，结束后 `rolls, loserIds`。
- Truth：`selectedIds, mode`，抽取后 `cardType, question`。
- Roulette：`options, spinnerId`，转动后 `resultIndex, result, needsSelection, selectedIds`。
- Compatibility：`question, options, selectedIds`，两人完成后 `answers, matched`；超时未完成则 `cancelled`，不提供另一人的秘密答案。

### 炸金花个人视图

`gameId: "zhajinhua"`，额外返回 `game.poker`：

- `currentPlayerId, turnNumber, actionLimit, compareOnly`：当前行动者、递增的行动序号、开局人数 ×5 次行动上限、是否只允许比牌 / 弃牌。LOOK 不消耗回合，也不重置截止时间。
- `baseStake, maxStake, pot`：底分（1–5）、上限、总积分池；客户端不得指定费用或牌面。
- `special235, drinkMode`：本局设置快照。
- `seats[]`：`playerId, seen, status, contribution`；status 为 `active / folded / lost / winner`。结束后添加 `netPoints`，非 folded 的座位额外添加 `cards, handType`。
- `myCards?, myHandType?`：只在本人看牌后或本局结束后提供给本人；观战身份没有这两个字段。牌为 `{ rank: 2..14, suit: "spades" | "hearts" | "clubs" | "diamonds" }`。
- `winnerId?`：结束后提供；`moves[]` 只含行动、积分和比牌输赢，不含手牌，最多保留最近 80 条。

所有炸金花动作带当前 `turnNumber`，防止延迟消息跨回合执行。`instanceId` 仍防止跨局执行；requestId 幂等约定不变。`deadline` 表示**当前玩家**的 30 秒行动截止时间，超时自动弃牌并推进；只剩一人时 `complete: true`。`hasActed` 表示已出局 / 非参与者，不能用它判断能否看牌，应看座位 `seen` 与 `status`。

禁止直接序列化 `GameState.zhaJinHua`。完整规则见 [炸金花规则](zhajinhua-rules.md)。

`myChoice` 只包含当前玩家自己的操作。禁止广播整个 Domain Object 或 `privateChoices`。

## 状态与错误

服务端以每个房间为单位串行处理变更，版本可跳跃、不可倒退。局次 ID 不依赖整数 round：切换游戏后 round 可以重新从 1 开始，但 `instanceId` 永不复用。

常见错误：

- `ROOM_NOT_FOUND` / `ROOM_CLOSED`：房间不存在、过期或关闭。
- `INVALID_ROOM` / `INVALID_PLAYER` / `UNAUTHENTICATED`：身份或房间不匹配。
- `OWNER_ONLY` / `NOT_PARTICIPANT` / `NOT_SELECTED`：权限不足。
- `ALREADY_ACTED`：这位玩家已经完成本轮操作。
- `STALE_ACTION`：游戏或局次已变化，不能重放旧操作。
- `COUNTDOWN` / `ROUND_EXPIRED` / `ROUND_FINISHED`：不在允许操作的时间窗口。
- `NEED_PLAYERS` / `ROOM_FULL` / `GAME_IN_PROGRESS`：人数、容量或房间阶段不满足。
- `INVALID_TARGET` / `INVALID_ANSWER` / `INVALID_ACTION` / `INVALID_MESSAGE`：请求无效。
- `NOT_YOUR_TURN` / `STALE_TURN` / `PLAYER_OUT`：炸金花非本人行动、行动序号过期或已出局。
- `COMPARE_ONLY` / `STAKE_LIMIT` / `TOO_MANY_PLAYERS`：炸金花进入决胜阶段、底分封顶或在线超过 17 人。
- `RATE_LIMIT`：短时间消息过多。
- `SERVICE_UNAVAILABLE`：Redis 或服务暂时不可用，可稍后重试。
- `SESSION_REPLACED`：同一身份的新页面已接管连接，停止自动重连。

## 重连与动画

前端退避 1、2、4、8、10 秒（上限）。连接后重新鉴权，恢复完整视图，保留自己的已提交选择。不会自动重发未确认的游戏动作；用户应先看到最新状态再决定是否操作。

`serverTime` 用于修正客户端时钟偏差。轮盘分 7 个等角扇区，指针固定在顶端：

```text
最终角度 = 360 - (resultIndex + 0.5) × 360 / 7
动画旋转 = 5 × 360 + 最终角度
```

刷新进入已有结果时直接显示最终位置；不重新随机，也不重新抽取。CSS 动画仅用于展示，不影响权威状态或结算。
