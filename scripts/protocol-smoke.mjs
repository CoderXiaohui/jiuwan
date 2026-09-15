// Live protocol checks against the real backend. Node.js >= 22.
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
const base = process.env.E2E_BASE_URL ?? 'http://localhost:8088';
const sockets = [];
async function api(path, body, token) {
  const res = await fetch(base + '/api' + path, { method: body === undefined ? 'GET' : 'POST', headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) }, ...(body === undefined ? {} : { body: JSON.stringify(body) }) });
  return res.json();
}
async function connect(credentials) {
  const ws = new WebSocket(base.replace(/^http/, 'ws') + '/ws');
  const queue = [];
  ws.addEventListener('message', event => queue.push(JSON.parse(event.data)));
  const wait = async predicate => {
    const start = Date.now();
    while (Date.now() - start < 12000) {
      const index = queue.findIndex(predicate);
      if (index !== -1) return queue.splice(index, 1)[0];
      await new Promise(resolve => setTimeout(resolve, 25));
    }
    throw new Error('Protocol response timed out');
  };
  await new Promise((resolve, reject) => { ws.addEventListener('open', resolve, { once: true }); ws.addEventListener('error', reject, { once: true }); });
  sockets.push(ws);
  const send = body => ws.send(JSON.stringify({ roomCode: credentials.roomCode, ...body }));
  send({ type: 'RECONNECT', playerToken: credentials.playerToken, requestId: randomUUID() });
  await wait(m => m.type === 'PLAYER_RECONNECTED');
  return { ws, wait, send };
}
let owner;
try {
  const created = await api('/rooms', { nickname: '协议测试 A', avatar: '😎' });
  assert.equal(created.success, true); owner = created.data;
  const joined = await api(`/rooms/${owner.roomCode}/join`, { nickname: '协议测试 B', avatar: '🐼' });
  assert.equal(joined.success, true); const guest = joined.data;
  const a = await connect(owner), b = await connect(guest);
  const preview = await api(`/rooms/${owner.roomCode}`);
  assert.equal(preview.data.players, undefined); assert.equal(preview.data.game, undefined);
  const invalid = await api(`/rooms/${owner.roomCode}`, undefined, 'x'.repeat(43));
  assert.equal(invalid.error.code, 'INVALID_PLAYER');
  const startAsGuest = await api(`/rooms/${owner.roomCode}/games/vote/start`, { requestId: randomUUID() }, guest.playerToken);
  assert.equal(startAsGuest.error.code, 'OWNER_ONLY');
  const started = await api(`/rooms/${owner.roomCode}/games/vote/start`, { requestId: randomUUID() }, owner.playerToken);
  assert.equal(started.success, true);
  const game = started.data.game;
  const id = randomUUID();
  b.send({ type: 'GAME_ACTION', requestId: id, playerId: owner.playerId, gameId: 'vote', instanceId: game.instanceId, action: 'VOTE', data: { targetPlayerId: owner.playerId } });
  assert.equal((await b.wait(m => m.requestId === id)).error.code, 'INVALID_PLAYER');
  await new Promise(resolve => setTimeout(resolve, Math.max(0, game.startsAt - Date.now() + 100)));
  const voteId = randomUUID();
  const vote = { type: 'GAME_ACTION', requestId: voteId, gameId: 'vote', instanceId: game.instanceId, action: 'VOTE', data: { targetPlayerId: guest.playerId } };
  a.send(vote); await a.wait(m => m.type === 'ACK' && m.requestId === voteId);
  a.send(vote); await a.wait(m => m.type === 'ACK' && m.requestId === voteId);
  const privateView = (await api(`/rooms/${owner.roomCode}`, undefined, guest.playerToken)).data;
  assert.equal(privateView.game.submittedCount, 1);
  for (const key of ['myChoice', 'privateChoices', 'counts', 'ballots']) assert.equal(privateView.game[key], undefined);
  assert.equal(JSON.stringify(privateView).includes('tokenHash'), false);
  a.ws.close();
  const restored = await connect(owner);
  const mine = (await api(`/rooms/${owner.roomCode}`, undefined, owner.playerToken)).data;
  assert.equal(mine.game.myChoice, guest.playerId);
  assert.equal(mine.players.find(p => p.playerId === owner.playerId).connected, true);
  const guestVote = randomUUID();
  b.send({ ...vote, requestId: guestVote, data: { targetPlayerId: owner.playerId } });
  await b.wait(m => m.type === 'ACK' && m.requestId === guestVote);
  const final = (await api(`/rooms/${owner.roomCode}`, undefined, owner.playerToken)).data;
  assert.equal(final.game.complete, true); assert.equal(final.game.selectedIds.length, 2);
  assert.equal(final.game.ballots, undefined);
  const closeId = randomUUID();
  restored.send({ type: 'GAME_ACTION', action: 'CLOSE_ROOM', requestId: closeId });
  await restored.wait(m => m.type === 'ERROR' && m.error?.code === 'ROOM_CLOSED');
  owner = undefined;
  console.log('PASS: live REST permissions, WebSocket identity binding, anonymous views, idempotency, reconnect, ties, and room close.');
} finally {
  for (const ws of sockets) ws.close();
}
