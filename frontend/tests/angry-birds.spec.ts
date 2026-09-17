import { test, expect, type Page, type WebSocketRoute } from '@playwright/test'
import type { Credentials, Envelope, Room } from '../src/lib/types'

test('three players share settings, hidden birds, turns, results, recovery and offline auto-picks', async ({
  browser,
  request,
}) => {
  const base = test.info().project.use.baseURL as string
  const contexts = await Promise.all(
    [375, 390, 430].map((width) => browser.newContext({ viewport: { width, height: 844 } })),
  )
  const pages: Page[] = []
  const credentials: Credentials[] = []
  const received: Room[] = []
  const errors: string[] = []
  let rejectSettings = true
  let holdSettings = true
  const held: (() => void)[] = []
  const ready = (page: Page) =>
    expect(page.getByRole('button', { name: '连接状态：已连接', exact: true })).toBeVisible()
  async function snapshot(): Promise<Room> {
    const response = await request.get(`${base}/api/rooms/${credentials[0]!.roomCode}`, {
      headers: { Authorization: `Bearer ${credentials[0]!.playerToken}` },
    })
    expect(response.ok()).toBe(true)
    const value: Room = (await response.json()).data
    received.push(value)
    return value
  }
  function checkPrivacy(room: Room) {
    const game = room.game
    if (game?.gameId !== 'angry-birds') return
    const board = game.angryBirds!
    expect(Object.keys(board).sort()).toEqual(
      [
        'birds',
        'bombCount',
        'currentPlayerId',
        'turnNumber',
        ...(board.lastMove ? ['lastMove'] : []),
        ...(game.complete ? ['loserId'] : []),
      ].sort(),
    )
    expect(JSON.stringify(game)).not.toContain('bombIds')
    expect(JSON.stringify(game)).not.toContain('privateChoices')
    expect(game.myChoice).toBeUndefined()
    expect(board.birds).toHaveLength(16)
    expect(new Set(game.participants)).toEqual(new Set(credentials.map((c) => c.playerId)))
    for (const bird of board.birds) {
      expect(Object.keys(bird).sort()).toEqual(['id', 'status'])
      if (!game.complete) expect(['hidden', 'flown']).toContain(bird.status)
    }
    if (game.complete) {
      expect(board.birds.filter((b) => b.status === 'exploded')).toHaveLength(1)
      expect(
        board.birds.filter((b) => b.status === 'exploded' || b.status === 'bomb'),
      ).toHaveLength(6)
      expect(board.loserId).toBe(board.lastMove!.playerId)
      expect(game.deadline).toBe(0)
    }
    expect(room.events).toEqual([])
    expect(room.players.every((p) => p.score === 0)).toBe(true)
  }

  try {
    for (let i = 0; i < 3; i++) {
      const response = await request.post(
        i === 0 ? `${base}/api/rooms` : `${base}/api/rooms/${credentials[0]!.roomCode}/join`,
        {
          data: {
            nickname: ['小辉', '老王', '阿琳'][i],
            avatar: ['😎', '🐼', '🦊'][i],
            ...(i === 0 ? { selectedGameId: 'angry-birds' } : {}),
          },
        },
      )
      expect(response.ok()).toBe(true)
      const session: Credentials = (await response.json()).data
      credentials.push(session)
      const page = await contexts[i]!.newPage()
      pages.push(page)
      page.on('pageerror', (error) => errors.push(error.message))
      if (i === 0) {
        await page.routeWebSocket('**/ws', (socket) => {
          const server = socket.connectToServer()
          socket.onMessage((message) => {
            const command = JSON.parse(String(message))
            if (command.action === 'UPDATE_SETTINGS' && rejectSettings) {
              rejectSettings = false
              server.send(
                JSON.stringify({ ...command, data: { ...command.data, angryBirdsBombCount: 7 } }),
              )
            } else server.send(message)
          })
          server.onMessage((message) => {
            const envelope: Envelope = JSON.parse(String(message))
            if (envelope.data) received.push(envelope.data)
            if (holdSettings && envelope.data?.settings.angryBirdsBombCount === 6)
              held.push(() => socket.send(message))
            else socket.send(message)
          })
        })
      } else {
        page.on('websocket', (socket) =>
          socket.on('framereceived', ({ payload }) => {
            const envelope: Envelope = JSON.parse(String(payload))
            if (envelope.data) received.push(envelope.data)
          }),
        )
      }
      await page.addInitScript(
        (session) => localStorage.setItem('jiuwan.session', JSON.stringify(session)),
        session,
      )
      await page.goto(`${base}/room/${session.roomCode}`)
      await ready(page)
    }
    const [owner, guest] = pages as [Page, Page, Page]
    await expect(owner.getByLabel('炸弹鸟数量')).toHaveValue('1')
    await expect(guest.getByLabel('炸弹鸟数量')).toBeDisabled()
    await owner.getByLabel('炸弹鸟数量').selectOption('2')
    await expect(owner.getByRole('alert')).toContainText('炸弹鸟数量需为 1–6 的整数')
    await expect(owner.getByLabel('炸弹鸟数量')).toHaveValue('1')
    await owner.getByRole('button', { name: '关闭提示', exact: true }).click()
    await owner.getByLabel('炸弹鸟数量').selectOption('6')
    await expect.poll(() => held.length).toBeGreaterThanOrEqual(2)
    await expect(owner.getByLabel('炸弹鸟数量')).toHaveValue('1')
    await expect(owner.locator('.lobby-start button')).toBeDisabled()
    await expect(guest.getByLabel('炸弹鸟数量')).toHaveValue('6')
    holdSettings = false
    held.splice(0).forEach((deliver) => deliver())
    await expect(owner.getByLabel('炸弹鸟数量')).toHaveValue('6')
    await owner.getByRole('button', { name: '开始游戏 · 愤怒的小鸟', exact: true }).click()
    await expect(owner.locator('.angry-birds-game')).toBeVisible()
    await expect(owner.locator('.countdown-overlay')).toBeVisible()
    await expect(owner.locator('.bird-cell:enabled')).toHaveCount(0)
    await expect(owner.locator('.countdown-overlay')).toHaveCount(0)
    let state = await snapshot()
    for (let i = 0; i < pages.length; i++) {
      const page = pages[i]!
      await expect(page.locator('.bird-cell')).toHaveCount(16)
      await expect(page.locator('.bird-cell:enabled')).toHaveCount(
        credentials[i]!.playerId === state.game!.angryBirds!.currentPlayerId ? 16 : 0,
      )
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
        true,
      )
      expect(
        await page
          .locator('.bird-grid')
          .evaluate((el) => getComputedStyle(el).gridTemplateColumns.split(' ').length),
      ).toBe(4)
    }
    await owner.screenshot({ path: 'test-results/angry-birds-mobile.png', fullPage: true })
    await owner.setViewportSize({ width: 1440, height: 1000 })
    await owner.screenshot({ path: 'test-results/angry-birds-desktop.png', fullPage: true })
    expect(await owner.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
      true,
    )
    await owner.setViewportSize({ width: 375, height: 844 })
    await guest.reload()
    await ready(guest)
    expect((await snapshot()).game).toEqual(state.game)
    await expect(guest.locator('.flying, .bursting')).toHaveCount(0)

    // No test-only knowledge of bomb locations: play the real randomized board to completion.
    while (!state.game!.complete) {
      const board = state.game!.angryBirds!
      const actorIndex = credentials.findIndex((c) => c.playerId === board.currentPlayerId)
      const bird = board.birds.find((b) => b.status === 'hidden')!
      await pages[actorIndex]!.getByRole('button', {
        name: `小鸟 ${bird.id + 1} · 未点击`,
        exact: true,
      }).press('Enter')
      await expect
        .poll(async () => (await snapshot()).game!.angryBirds!.lastMove?.turnNumber)
        .toBe(board.turnNumber)
      state = await snapshot()
      expect(state.game!.angryBirds!.lastMove!.automatic).toBe(false)
      for (const page of pages) {
        await expect(page.locator(`[data-bird-id="${bird.id}"]`)).toHaveAttribute(
          'data-status',
          state.game!.complete ? 'exploded' : 'flown',
        )
      }
    }
    for (const page of pages) {
      await expect(page.locator('.bird-turn-heading')).toContainText('点中了炸弹')
      await expect(page.locator('.bird-cell:enabled')).toHaveCount(0)
      await expect(page.locator('.bird-cell.bomb')).toHaveCount(5)
    }
    await expect(owner.locator('.bird-blast')).toHaveCount(0)
    await owner.screenshot({ path: 'test-results/angry-birds-result.png', fullPage: true })
    await guest.reload()
    await ready(guest)
    expect((await snapshot()).game).toEqual(state.game)
    await expect(guest.locator('.flying, .bursting')).toHaveCount(0)
    await owner.getByRole('button', { name: '下一局', exact: true }).click()
    await expect(owner.locator('.countdown-overlay')).toHaveCount(0)
    await expect(owner.locator('.bird-cell.hidden')).toHaveCount(16)
    const second = await snapshot()
    expect(second.game!.instanceId).not.toBe(state.game!.instanceId)
    expect(second.game!.angryBirds!.bombCount).toBe(6)
    expect(second.game!.angryBirds!.lastMove).toBeUndefined()
    const offline = credentials.findIndex(
      (c) => c.playerId === second.game!.angryBirds!.currentPlayerId,
    )
    await contexts[offline]!.setOffline(true)
    await expect
      .poll(async () => (await snapshot()).game!.angryBirds!.lastMove?.automatic, {
        timeout: 15000,
      })
      .toBe(true)
    const automatic = await snapshot()
    expect(automatic.game!.angryBirds!.lastMove!.playerId).toBe(credentials[offline]!.playerId)
    expect(automatic.game!.angryBirds!.lastMove!.turnNumber).toBe(1)
    expect(automatic.game!.angryBirds!.lastMove!.at).toBeGreaterThanOrEqual(second.game!.deadline)
    await contexts[offline]!.setOffline(false)
    await ready(pages[offline]!)
    await expect(pages[offline]!.locator('.bird-last-move')).toContainText('超时，系统自动点击')
    await expect(pages[offline]!.locator('.flying, .bursting')).toHaveCount(0)
    expect((await snapshot()).game).toEqual(automatic.game)
    received.forEach(checkPrivacy)
    expect(errors).toEqual([])
    await owner.getByRole('button', { name: '返回大厅 · 换个游戏' }).click()
    await expect(guest.getByLabel('炸弹鸟数量')).toHaveValue('6')
  } finally {
    await Promise.all(contexts.map((context) => context.close()))
  }
})

for (const reducedMotion of ['no-preference', 'reduce'] as const) {
  test(`confirmed flight and explosion animate once; snapshots restore directly (${reducedMotion})`, async ({
    page,
  }) => {
    await page.emulateMedia({ reducedMotion })
    const session: Credentials = {
      roomCode: '123456',
      playerId: 'p0',
      playerToken: 'fixture-token',
    }
    const room: Room = {
      roomId: 'fixture',
      roomCode: '123456',
      ownerId: 'p0',
      status: 'PLAYING',
      selectedGameId: 'angry-birds',
      currentGameId: 'angry-birds',
      createdAt: Date.now(),
      version: 1,
      players: ['小辉', '老王'].map((nickname, i) => ({
        playerId: `p${i}`,
        nickname,
        avatar: ['😎', '🐼'][i]!,
        connected: true,
        isOwner: i === 0,
        joinedAt: i,
        score: 0,
      })),
      settings: {
        punishmentMode: 'challenge',
        gameMode: 'normal',
        maxPlayers: 12,
        anonymousVote: true,
        safeMode: true,
        diceRule: 'lowest',
        zhaJinHua235: false,
        zhaJinHuaDrink: true,
        angryBirdsBombCount: 3,
      },
      events: [],
      game: {
        gameId: 'angry-birds',
        instanceId: 'fixture-round',
        round: 1,
        complete: false,
        startsAt: Date.now() - 1000,
        deadline: Date.now() + 10000,
        participants: ['p0', 'p1'],
        submittedCount: 0,
        hasActed: false,
        angryBirds: {
          bombCount: 3,
          currentPlayerId: 'p0',
          turnNumber: 1,
          birds: Array.from({ length: 16 }, (_, id) => ({ id, status: 'hidden' })),
        },
      },
    }
    let socket: WebSocketRoute
    const send = (type = 'GAME_STATE_UPDATE', requestId?: string) =>
      socket.send(JSON.stringify({ type, requestId, serverTime: Date.now(), data: room }))
    await page.routeWebSocket('**/ws', (connection) => {
      socket = connection
      connection.onMessage((message) => {
        const command = JSON.parse(String(message))
        if (command.type === 'RECONNECT') send('PLAYER_RECONNECTED', command.requestId)
        if (command.action === 'PICK_BIRD') {
          expect(command.data).toEqual({ birdId: 4, turnNumber: 1 })
          room.version++
          const board = room.game!.angryBirds!
          board.birds[4]!.status = 'flown'
          board.lastMove = {
            birdId: 4,
            playerId: 'p0',
            turnNumber: 1,
            automatic: false,
            at: Date.now(),
          }
          board.currentPlayerId = 'p1'
          board.turnNumber = 2
          room.game!.deadline = Date.now() + 10000
          send('GAME_STATE_UPDATE')
          send('ACK', command.requestId)
        }
      })
    })
    await page.addInitScript(
      (session) => localStorage.setItem('jiuwan.session', JSON.stringify(session)),
      session,
    )
    await page.goto('/room/123456')
    await expect(page.locator('.bird-cell:enabled')).toHaveCount(16)
    await page.getByRole('button', { name: '小鸟 5 · 未点击', exact: true }).click()
    const flown = page.locator('[data-bird-id="4"]')
    await expect(flown).toHaveClass(/flying/)
    expect(await flown.locator('svg').evaluate((el) => getComputedStyle(el).animationName)).toMatch(
      reducedMotion === 'reduce' ? /^none$/ : /^bird-fly(?:-|$)/,
    )
    await expect(page.locator('.bird-cell:enabled')).toHaveCount(0)
    await expect(flown).not.toHaveClass(/flying/)
    send('ACK')
    await expect(flown).not.toHaveClass(/flying/)
    await expect(page.locator('.bird-turn-heading')).toContainText('轮到 老王')
    room.version++
    room.game!.complete = true
    room.game!.deadline = 0
    const board = room.game!.angryBirds!
    board.lastMove = { birdId: 7, playerId: 'p1', turnNumber: 2, automatic: true, at: Date.now() }
    board.loserId = 'p1'
    board.birds[7]!.status = 'exploded'
    board.birds[8]!.status = 'bomb'
    board.birds[9]!.status = 'bomb'
    send('GAME_RESULT')
    await expect(page.locator('.bird-blast')).toHaveCount(1)
    expect(
      await page.locator('.bird-blast').evaluate((el) => getComputedStyle(el).animationName),
    ).toMatch(reducedMotion === 'reduce' ? /^none$/ : /^bird-boom(?:-|$)/)
    await expect(page.locator('.bird-last-move')).toContainText(
      '老王 超时，系统自动点击了 8 号小鸟，引爆了炸弹',
    )
    await expect(page.locator('.bird-cell.bomb')).toHaveCount(2)
    await expect(page.locator('.bird-blast')).toHaveCount(0)
    send('ACK')
    await expect(page.locator('.bursting, .flying')).toHaveCount(0)
    await page.reload()
    await expect(page.locator('.bird-cell.exploded')).toHaveCount(1)
    await expect(page.locator('.bird-cell.flown')).toHaveCount(1)
    await expect(page.locator('.bursting, .flying')).toHaveCount(0)
  })
}
