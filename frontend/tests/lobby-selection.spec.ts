import { test, expect, type Page } from '@playwright/test'
import type { Credentials, Envelope, Room } from '../src/lib/types'

const ready = async (page: Page) =>
  expect(page.getByRole('button', { name: '连接状态：已连接', exact: true })).toBeVisible()

const lobbyGame = (page: Page, name: string) =>
  page.locator('.lobby-game').filter({ has: page.getByRole('heading', { name, exact: true }) })

async function selected(page: Page, name: string) {
  await expect(page.locator('.lobby-game.selected')).toHaveCount(1)
  await expect(lobbyGame(page, name)).toHaveAttribute('aria-pressed', 'true')
  await expect(page.locator('.selected-rule strong')).toHaveText(`${name} · 怎么玩`)
}

async function enter(page: Page, name: string) {
  await page.getByLabel('你的昵称').fill(name)
  await page.getByRole('button', { name: '准备好了，入场' }).click()
  await expect(page).toHaveURL(/\/room\/[0-9]{6}/)
  await ready(page)
}

async function join(page: Page, base: string, code: string) {
  await page.goto(`${base}/join?code=${code}`)
  await page.getByRole('button', { name: '加入房间', exact: true }).click()
  await enter(page, '玩家 B')
}

async function pickFromHome(page: Page, name: string) {
  await page
    .locator('.game-card')
    .filter({ has: page.getByRole('heading', { name, exact: true }) })
    .click()
  await page.getByRole('button', { name: '就玩这个', exact: true }).click()
}

test('lobby selection reaches late joiners, stays live, and survives reconnect and ownership transfer', async ({
  browser,
  request,
}) => {
  const base = test.info().project.use.baseURL as string
  const contexts = await Promise.all([browser.newContext(), browser.newContext()])
  const owner = await contexts[0]!.newPage()
  const guest = await contexts[1]!.newPage()
  const received: Envelope[] = []
  const errors: string[] = []
  for (const page of [owner, guest]) page.on('pageerror', (error) => errors.push(error.message))
  owner.on('websocket', (socket) => {
    socket.on('framereceived', ({ payload }) => received.push(JSON.parse(String(payload))))
  })
  try {
    await owner.goto(base)
    await owner.getByRole('button', { name: '创建房间', exact: true }).click()
    await enter(owner, '玩家 A')
    await selected(owner, '匿名投票')
    await lobbyGame(owner, '炸金花').click()
    await selected(owner, '炸金花')
    const code = owner.url().split('/').pop()!
    await join(guest, base, code)
    await selected(guest, '炸金花')
    await expect(guest.locator('.lobby-game:enabled')).toHaveCount(0)
    expect(
      received.some(
        (m) => m.type === 'ROOM_STATE_UPDATE' && m.data?.selectedGameId === 'zhajinhua',
      ),
    ).toBe(true)
    expect(received.some((m) => m.type === 'ACK' && m.data?.selectedGameId === 'zhajinhua')).toBe(
      true,
    )
    const credentials: Credentials = await guest.evaluate(() =>
      JSON.parse(localStorage.getItem('jiuwan.session')!),
    )
    const response = await request.get(`${base}/api/rooms/${code}`, {
      headers: { Authorization: `Bearer ${credentials.playerToken}` },
    })
    const snapshot: Room = (await response.json()).data
    expect(snapshot.selectedGameId).toBe('zhajinhua')

    await lobbyGame(owner, '摇骰子').click()
    await selected(owner, '摇骰子')
    await selected(guest, '摇骰子')
    await guest.reload()
    await ready(guest)
    await selected(guest, '摇骰子')
    await contexts[1]!.setOffline(true)
    await expect(guest.getByRole('button', { name: '连接状态：离线', exact: true })).toBeVisible()
    await lobbyGame(owner, '大的喝小的喝').click()
    await selected(owner, '大的喝小的喝')
    await contexts[1]!.setOffline(false)
    await ready(guest)
    await selected(guest, '大的喝小的喝')
    await owner.reload()
    await ready(owner)
    await selected(owner, '大的喝小的喝')
    await contexts[0]!.setOffline(true)
    await expect(owner.getByRole('button', { name: '连接状态：离线', exact: true })).toBeVisible()
    await expect(owner.locator('.lobby-game:enabled')).toHaveCount(0)
    await expect(owner.locator('.lobby-start button')).toBeDisabled()
    await contexts[0]!.setOffline(false)
    await ready(owner)

    await lobbyGame(owner, '炸金花').click()
    await owner.getByRole('button', { name: '开始游戏 · 炸金花', exact: true }).click()
    await expect(owner.locator('.poker-game')).toBeVisible()
    await expect(guest.locator('.poker-game')).toBeVisible()
    await owner.getByRole('button', { name: '返回大厅 · 换个游戏' }).click()
    await selected(owner, '炸金花')
    await selected(guest, '炸金花')
    await owner.getByRole('button', { name: '退出房间', exact: true }).click()
    await owner.getByRole('button', { name: '确认离开', exact: true }).click()
    await expect(owner).toHaveURL(`${base}/`)
    await selected(guest, '炸金花')
    await expect(lobbyGame(guest, '摇骰子')).toBeEnabled()
    await lobbyGame(guest, '摇骰子').click()
    await selected(guest, '摇骰子')
    expect(errors).toEqual([])
  } finally {
    await Promise.all(contexts.map((context) => context.close()))
  }
})

test('home preselection is saved at creation and existing rooms obey owner and lobby permissions', async ({
  browser,
}) => {
  const base = test.info().project.use.baseURL as string
  const contexts = await Promise.all([browser.newContext(), browser.newContext()])
  const owner = await contexts[0]!.newPage()
  const guest = await contexts[1]!.newPage()
  try {
    await owner.goto(base)
    await pickFromHome(owner, '炸金花')
    await expect(owner).toHaveURL(/\/profile/)
    await owner.reload()
    await ready(owner)
    await enter(owner, '玩家 A')
    await selected(owner, '炸金花')
    const code = owner.url().split('/').pop()!
    await join(guest, base, code)
    await selected(guest, '炸金花')

    await owner.getByRole('link', { name: '首页', exact: true }).click()
    await pickFromHome(owner, '摇骰子')
    await expect(owner).toHaveURL(`${base}/room/${code}`)
    await selected(owner, '摇骰子')
    await selected(guest, '摇骰子')
    await guest.getByRole('link', { name: '首页', exact: true }).click()
    await pickFromHome(guest, '匿名投票')
    await expect(guest).toHaveURL(`${base}/room/${code}`)
    await selected(guest, '摇骰子')
    await selected(owner, '摇骰子')

    await owner.getByRole('button', { name: '开始游戏 · 摇骰子', exact: true }).click()
    await expect(guest.locator('.dice-game')).toBeVisible()
    await owner.getByRole('link', { name: '首页', exact: true }).click()
    await pickFromHome(owner, '炸金花')
    await expect(owner).toHaveURL(`${base}/room/${code}`)
    await expect(owner.locator('.dice-game')).toBeVisible()
    await owner.getByRole('button', { name: '返回大厅 · 换个游戏' }).click()
    await selected(owner, '摇骰子')
    await selected(guest, '摇骰子')
  } finally {
    await Promise.all(contexts.map((context) => context.close()))
  }
})

test('selection errors preserve the snapshot and delayed confirmation blocks starting an old game', async ({
  browser,
  request,
}) => {
  const base = test.info().project.use.baseURL as string
  const contexts = await Promise.all([browser.newContext(), browser.newContext()])
  const owner = await contexts[0]!.newPage()
  const guest = await contexts[1]!.newPage()
  let hold = true
  let rejectNextSelection = true
  const held: (() => void)[] = []
  const actions: string[] = []
  await owner.routeWebSocket('**/ws', (socket) => {
    const server = socket.connectToServer()
    socket.onMessage((message) => {
      const command = JSON.parse(String(message))
      if (command.action) actions.push(command.action)
      if (command.action === 'SELECT_GAME' && rejectNextSelection) {
        rejectNextSelection = false
        server.send(JSON.stringify({ ...command, gameId: 'unknown' }))
      } else server.send(message)
    })
    server.onMessage((message) => {
      const envelope: Envelope = JSON.parse(String(message))
      if (hold && envelope.data?.selectedGameId === 'zhajinhua')
        held.push(() => socket.send(message))
      else socket.send(message)
    })
  })
  try {
    const invalid = await request.post(`${base}/api/rooms`, {
      data: { nickname: '玩家 A', avatar: '😎', selectedGameId: 'unknown' },
    })
    expect(invalid.status()).toBe(400)
    expect((await invalid.json()).error.code).toBe('INVALID_GAME')
    await owner.goto(base)
    await owner.getByRole('button', { name: '创建房间', exact: true }).click()
    await enter(owner, '玩家 A')
    await join(guest, base, owner.url().split('/').pop()!)
    await lobbyGame(owner, '摇骰子').click()
    await expect(owner.getByRole('alert')).toContainText('这个游戏还未上线')
    await selected(owner, '匿名投票')
    await selected(guest, '匿名投票')
    await expect(lobbyGame(owner, '炸金花')).toBeEnabled()
    await owner.getByRole('button', { name: '关闭提示', exact: true }).click()

    await lobbyGame(owner, '炸金花').click()
    await expect.poll(() => held.length).toBeGreaterThanOrEqual(2)
    await selected(owner, '匿名投票')
    await selected(guest, '炸金花')
    await expect(owner.locator('.lobby-game:enabled')).toHaveCount(0)
    await expect(owner.locator('.lobby-start button')).toBeDisabled()
    expect(actions).not.toContain('START_GAME')
    hold = false
    for (const deliver of held.splice(0)) deliver()
    await owner.getByRole('button', { name: '开始游戏 · 炸金花', exact: true }).click()
    await expect(owner.locator('.poker-game')).toBeVisible()
    await expect(guest.locator('.poker-game')).toBeVisible()
  } finally {
    await Promise.all(contexts.map((context) => context.close()))
  }
})
