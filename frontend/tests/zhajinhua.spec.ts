import { test, expect, type Page } from '@playwright/test'
import type { Credentials, PokerView, Room } from '../src/lib/types'

test('three players bluff, keep hands private, reconnect, settle and change table rules', async ({
  browser,
  request,
}) => {
  const base = test.info().project.use.baseURL as string
  const contexts = await Promise.all(
    [375, 390, 430].map((width) => browser.newContext({ viewport: { width, height: 844 } })),
  )
  const pages: Page[] = []
  const credentials: Credentials[] = []
  const errors: string[] = []
  const nicknames = ['小辉', '老王', '阿琳']
  async function snapshot(index: number): Promise<Room> {
    const response = await request.get(`${base}/api/rooms/${credentials[0]!.roomCode}`, {
      headers: { Authorization: `Bearer ${credentials[index]!.playerToken}` },
    })
    return (await response.json()).data
  }
  try {
    for (let i = 0; i < 3; i++) {
      const response = await request.post(
        i === 0 ? `${base}/api/rooms` : `${base}/api/rooms/${credentials[0]!.roomCode}/join`,
        {
          data: { nickname: nicknames[i], avatar: ['😎', '🐼', '🦊'][i] },
        },
      )
      expect(response.ok()).toBe(true)
      const session: Credentials = (await response.json()).data
      credentials.push(session)
      const page = await contexts[i]!.newPage()
      pages.push(page)
      page.on('pageerror', (error) => errors.push(error.message))
      await page.addInitScript(
        (session) => localStorage.setItem('jiuwan.session', JSON.stringify(session)),
        session,
      )
      await page.goto(`${base}/room/${session.roomCode}`)
      await expect(
        page.getByRole('button', { name: '连接状态：已连接', exact: true }),
      ).toBeVisible()
    }
    const [owner, guest, third] = pages as [Page, Page, Page]
    await expect(owner.locator('.lobby-player')).toHaveCount(3)
    await owner
      .locator('.lobby-game')
      .filter({ has: owner.getByRole('heading', { name: '炸金花', exact: true }) })
      .click()
    await owner.getByRole('button', { name: '开始游戏 · 炸金花', exact: true }).click()
    await expect(owner.locator('.poker-game')).toBeVisible()
    await expect(owner.locator('.countdown-overlay')).toHaveCount(0)
    await expect(third.locator('.countdown-overlay')).toHaveCount(0)
    for (const page of pages) {
      await expect(page.locator('.poker-seats .face')).toHaveCount(0)
      await expect(page.locator('.poker-hand .face')).toHaveCount(0)
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
        true,
      )
      expect(
        await page.locator('.poker-seat').evaluateAll((seats) =>
          seats.every((seat) => {
            const bounds = seat.getBoundingClientRect()
            return [...seat.querySelectorAll('.poker-card')].every((card) => {
              const rect = card.getBoundingClientRect()
              return rect.left >= bounds.left && rect.right <= bounds.right
            })
          }),
        ),
      ).toBe(true)
    }
    await expect(guest.getByRole('button', { name: '闷牌跟注 1 分', exact: true })).toBeDisabled()
    const initialGuest = await snapshot(1)
    expect(initialGuest.game!.poker!.myCards).toBeUndefined()
    // Looking off turn must reveal only the viewer's cards over both REST and WebSocket.
    await guest.getByRole('button', { name: /看牌 之后跟注/ }).click()
    await expect(guest.locator('.poker-hand .face')).toHaveCount(3)
    await expect(owner.locator('.poker-seats .face')).toHaveCount(0)
    const privateHand = (await snapshot(1)).game!.poker!.myCards
    const ownerPoker = (await snapshot(0)).game!.poker!
    expect(ownerPoker.myCards).toBeUndefined()
    expect(ownerPoker.seats.every((seat) => !seat.cards && !seat.handType)).toBe(true)
    await guest.reload()
    await expect(guest.getByRole('button', { name: '连接状态：已连接', exact: true })).toBeVisible()
    expect((await snapshot(1)).game!.poker!.myCards).toEqual(privateHand)
    await expect(guest.locator('.poker-hand .face')).toHaveCount(3)
    await guest.getByRole('button', { name: '收起手牌', exact: true }).click()
    await expect(guest.locator('.poker-hand .face')).toHaveCount(0)
    await guest.getByRole('button', { name: '显示手牌', exact: true }).click()

    await owner.getByRole('button', { name: '闷牌跟注 1 分', exact: true }).click()
    await guest.getByRole('button', { name: '加注 4 分', exact: true }).click()
    await third.getByRole('button', { name: '闷牌跟注 2 分', exact: true }).click()
    expect((await snapshot(0)).game!.poker!.pot).toBe(10)
    await owner.getByRole('button', { name: /看牌 之后跟注/ }).click()
    await owner.screenshot({ path: 'test-results/zhajinhua-375.png', fullPage: true })
    await owner.setViewportSize({ width: 1440, height: 1000 })
    expect(await owner.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
      true,
    )
    await owner.screenshot({ path: 'test-results/zhajinhua-desktop.png', fullPage: true })
    await owner.setViewportSize({ width: 375, height: 844 })
    await owner.getByRole('button', { name: '弃牌', exact: true }).click()
    await contexts[2]!.setOffline(true)
    await expect(third.getByRole('button', { name: '连接状态：离线', exact: true })).toBeVisible()
    await contexts[2]!.setOffline(false)
    await expect(third.getByRole('button', { name: '连接状态：已连接', exact: true })).toBeVisible()
    await guest.getByRole('button', { name: '比牌 8 分', exact: true }).click()
    await guest.getByRole('button', { name: '🦊 和 阿琳 比牌', exact: true }).click()
    for (const page of pages) await expect(page.locator('.poker-settlement')).toBeVisible()
    const final = (await snapshot(0)).game!.poker!
    expect(final.pot).toBe(18)
    expect(final.seats[0]!.cards).toBeUndefined() // Folded hand is never made public.
    await expect(owner.locator('.poker-hand .face')).toHaveCount(3)
    await expect(guest.locator('.poker-seats .face')).toHaveCount(6)
    await expect(guest.locator('.poker-hand .face')).toHaveCount(0)
    expect(final.seats.slice(1).every((seat) => seat.cards?.length === 3)).toBe(true)
    expect(final.seats.reduce((sum, seat) => sum + seat.netPoints!, 0)).toBe(0)
    await expect(owner.locator('.event-list')).toContainText('每人喝 1 小口')
    await owner.screenshot({ path: 'test-results/zhajinhua-result.png', fullPage: true })
    await owner.getByRole('button', { name: '下一局', exact: true }).click()
    await expect(owner.getByText('ROUND 02', { exact: true })).toBeVisible()
    await expect(guest.locator('.countdown-overlay')).toHaveCount(0)
    const second = (await snapshot(1)).game!.poker!
    expect(second.currentPlayerId).toBe(credentials[1]!.playerId)
    expect(second.pot).toBe(3)
    expect(second.myCards).toBeUndefined()
    await owner.getByRole('button', { name: '返回大厅 · 换个游戏' }).click()
    await owner.getByRole('button', { name: '房间设置', exact: true }).click()
    await owner.getByRole('switch', { name: /炸金花 · 酒局模式/ }).uncheck()
    await owner.getByRole('switch', { name: /炸金花 · 235 吃豹子/ }).check()
    await owner.getByRole('button', { name: '保存设置', exact: false }).click()
    await owner.getByRole('button', { name: '开始游戏 · 炸金花', exact: true }).click()
    await expect(owner.locator('.countdown-overlay')).toHaveCount(0)
    const changed: PokerView = (await snapshot(0)).game!.poker!
    expect(changed.special235).toBe(true)
    expect(changed.drinkMode).toBe(false)
    await owner.getByRole('button', { name: '弃牌', exact: true }).click()
    await guest.getByRole('button', { name: '弃牌', exact: true }).click()
    await expect(third.locator('.poker-settlement')).toContainText('每位输家，来个轻松挑战')
    await expect(third.locator('.event-list')).not.toContainText('喝 1 小口')
    expect(errors).toEqual([])
    await owner.getByRole('button', { name: '返回大厅 · 换个游戏' }).click()
    await owner.getByRole('button', { name: '房间设置', exact: true }).click()
    await owner.getByRole('button', { name: '关闭这个房间', exact: true }).click()
    await owner.getByRole('button', { name: '确认离开', exact: true }).click()
  } finally {
    await Promise.all(contexts.map((context) => context.close()))
  }
})
