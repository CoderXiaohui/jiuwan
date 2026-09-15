import { test, expect, type Page } from '@playwright/test'
import type { Credentials, Envelope, Room } from '../src/lib/types'

test('three players see only others cards until the owner reveals, then start a fresh round', async ({
  browser,
  request,
}) => {
  const base = test.info().project.use.baseURL as string
  const contexts = await Promise.all(
    [375, 390, 430].map((width) => browser.newContext({ viewport: { width, height: 844 } })),
  )
  const pages: Page[] = []
  const credentials: Credentials[] = []
  const received: Room[][] = [[], [], []]
  const errors: string[] = []
  const ready = async (page: Page) =>
    expect(page.getByRole('button', { name: '连接状态：已连接', exact: true })).toBeVisible()
  async function snapshot(index: number): Promise<Room> {
    const response = await request.get(`${base}/api/rooms/${credentials[0]!.roomCode}`, {
      headers: { Authorization: `Bearer ${credentials[index]!.playerToken}` },
    })
    expect(response.ok()).toBe(true)
    return (await response.json()).data
  }
  function checkVisibility(room: Room, viewer: number) {
    const game = room.game!
    expect(game.gameId).toBe('big-small')
    expect(game.deadline).toBe(0)
    expect(game.myChoice).toBeUndefined()
    expect(Object.keys(game.bigSmall!)).toEqual(['seats'])
    expect(game.bigSmall!.seats.map((s) => s.playerId)).toEqual(credentials.map((s) => s.playerId))
    for (const seat of game.bigSmall!.seats) {
      if (!game.complete && seat.playerId === credentials[viewer]!.playerId) {
        expect(seat).toEqual({ playerId: credentials[viewer]!.playerId })
      } else {
        expect(seat.card!.rank).toBeGreaterThanOrEqual(2)
        expect(seat.card!.rank).toBeLessThanOrEqual(14)
        expect(['spades', 'hearts', 'clubs', 'diamonds']).toContain(seat.card!.suit)
      }
    }
    expect(room.events).toEqual([])
    expect(room.players.every((p) => p.score === 0)).toBe(true)
  }

  try {
    for (let i = 0; i < 3; i++) {
      const response = await request.post(
        i === 0 ? `${base}/api/rooms` : `${base}/api/rooms/${credentials[0]!.roomCode}/join`,
        { data: { nickname: ['小辉', '老王', '阿琳'][i], avatar: ['😎', '🐼', '🦊'][i] } },
      )
      expect(response.ok()).toBe(true)
      const session: Credentials = (await response.json()).data
      credentials.push(session)
      const page = await contexts[i]!.newPage()
      pages.push(page)
      page.on('pageerror', (error) => errors.push(error.message))
      page.on('websocket', (socket) => {
        socket.on('framereceived', ({ payload }) => {
          const message: Envelope = JSON.parse(String(payload))
          if (message.data?.game?.gameId === 'big-small') received[i]!.push(message.data)
        })
      })
      await page.addInitScript(
        (session) => localStorage.setItem('jiuwan.session', JSON.stringify(session)),
        session,
      )
      await page.goto(`${base}/room/${session.roomCode}`)
      await ready(page)
    }
    const [owner, guest, third] = pages as [Page, Page, Page]
    await expect(owner.locator('.lobby-player')).toHaveCount(3)
    await owner
      .locator('.lobby-game')
      .filter({ has: owner.getByRole('heading', { name: '大的喝小的喝', exact: true }) })
      .click()
    await expect(owner.locator('.selected-rule')).toContainText('系统只负责发牌和亮牌')
    await owner.getByRole('button', { name: '开始游戏 · 大的喝小的喝', exact: true }).click()
    await expect(owner.locator('.big-small-game')).toBeVisible()
    await expect(owner.getByRole('button', { name: '结束本轮 · 全部亮牌' })).toBeDisabled()
    for (const page of pages) {
      await expect(page.locator('.countdown-overlay')).toHaveCount(0)
      await expect(page.locator('.big-small-seat.self .card-back')).toHaveCount(1)
      await expect(page.locator('.big-small-seat.self .face')).toHaveCount(0)
      await expect(page.locator('.big-small-seats .face')).toHaveCount(2)
      await expect(page.locator('.stage-status')).toContainText('等待房主结束本轮')
      await expect(page.locator('.stage-status')).toContainText('3 人参与')
      await expect(page.locator('.stage-status')).not.toContainText('秒')
      await expect(page.locator('.stage-status')).not.toContainText('人已完成')
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
        true,
      )
    }
    await expect(guest.getByRole('button', { name: '结束本轮 · 全部亮牌' })).toHaveCount(0)
    await expect(third.getByRole('button', { name: '结束本轮 · 全部亮牌' })).toHaveCount(0)
    const initial = await Promise.all([0, 1, 2].map(snapshot))
    initial.forEach(checkVisibility)
    const cards = credentials.map(
      (session, i) =>
        initial[(i + 1) % 3]!.game!.bigSmall!.seats.find((s) => s.playerId === session.playerId)!
          .card!,
    )
    expect(new Set(cards.map((card) => `${card.suit}-${card.rank}`)).size).toBe(3)
    await guest.reload()
    await ready(guest)
    expect((await snapshot(1)).game).toEqual(initial[1]!.game)
    await expect(guest.locator('.big-small-seat.self .card-back')).toHaveCount(1)
    await contexts[2]!.setOffline(true)
    await expect(third.getByRole('button', { name: '连接状态：离线', exact: true })).toBeVisible()
    await contexts[2]!.setOffline(false)
    await ready(third)
    expect((await snapshot(2)).game).toEqual(initial[2]!.game)

    await owner.screenshot({ path: 'test-results/big-small-mobile.png', fullPage: true })
    await owner.setViewportSize({ width: 1440, height: 1000 })
    expect(await owner.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
      true,
    )
    await owner.screenshot({ path: 'test-results/big-small-desktop.png', fullPage: true })
    await owner.setViewportSize({ width: 375, height: 844 })
    await owner.getByRole('button', { name: '结束本轮 · 全部亮牌' }).click()
    for (const page of pages) {
      await expect(page.locator('.big-small-seats .face')).toHaveCount(3)
      await expect(page.locator('.big-small-seats .card-back')).toHaveCount(0)
      await expect(page.locator('.stage-status')).toContainText('本轮已揭晓')
      await expect(page.locator('.event-list')).toHaveCount(0)
    }
    for (let i = 0; i < 3; i++) {
      const revealed = await snapshot(i)
      checkVisibility(revealed, i)
      expect(revealed.game!.bigSmall!.seats.map((s) => s.card)).toEqual(cards)
    }
    await owner.screenshot({ path: 'test-results/big-small-revealed.png', fullPage: true })
    await guest.reload()
    await ready(guest)
    await expect(guest.locator('.big-small-seats .face')).toHaveCount(3)

    await owner.getByRole('button', { name: '下一局', exact: true }).click()
    for (const page of pages) {
      await expect(page.getByText('ROUND 02', { exact: true })).toBeVisible()
      await expect(page.locator('.big-small-seat.self .face')).toHaveCount(0)
      await expect(page.locator('.big-small-seat.self .card-back')).toHaveCount(1)
    }
    const second = await snapshot(0)
    expect(second.game!.instanceId).not.toBe(initial[0]!.game!.instanceId)
    expect(second.game!.complete).toBe(false)
    checkVisibility(second, 0)
    // Audit every received snapshot, including ACKs, next-round updates and reconnects.
    received.forEach((snapshots, viewer) => {
      expect(snapshots.some((s) => s.game!.complete)).toBe(true)
      expect(snapshots.some((s) => s.game!.round === 2)).toBe(true)
      snapshots.forEach((room) => checkVisibility(room, viewer))
    })
    expect(errors).toEqual([])
    await owner.getByRole('button', { name: '返回大厅 · 换个游戏' }).click()
    await expect(guest.locator('.lobby-games')).toBeVisible()
    await owner.getByRole('button', { name: '房间设置', exact: true }).click()
    await owner.getByRole('button', { name: '关闭这个房间', exact: true }).click()
    await owner.getByRole('button', { name: '确认离开', exact: true }).click()
  } finally {
    await Promise.all(contexts.map((context) => context.close()))
  }
})
