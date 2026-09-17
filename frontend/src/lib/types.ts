export interface Credentials {
  roomCode: string
  playerId: string
  playerToken: string
}
export interface Player {
  playerId: string
  nickname: string
  avatar: string
  connected: boolean
  isOwner: boolean
  joinedAt: number
  score: number
}
export interface RoomSettings {
  punishmentMode: 'challenge' | 'truth' | 'dare'
  gameMode: 'normal' | 'friends' | 'couple' | 'mellow'
  maxPlayers: number
  anonymousVote: boolean
  safeMode: boolean
  diceRule: 'lowest' | 'highest'
  zhaJinHua235: boolean
  zhaJinHuaDrink: boolean
  angryBirdsBombCount: number
}
export interface PokerCard {
  rank: number
  suit: 'spades' | 'hearts' | 'clubs' | 'diamonds'
}
export interface PokerSeat {
  playerId: string
  seen: boolean
  status: 'active' | 'folded' | 'lost' | 'winner'
  contribution: number
  cards?: PokerCard[]
  handType?: string
  netPoints?: number
}
export interface BigSmallView {
  seats: { playerId: string; card?: PokerCard }[]
}
export interface AngryBirdsView {
  bombCount: number
  currentPlayerId: string
  turnNumber: number
  birds: { id: number; status: 'hidden' | 'flown' | 'exploded' | 'bomb' }[]
  lastMove?: {
    birdId: number
    playerId: string
    turnNumber: number
    automatic: boolean
    at: number
  }
  loserId?: string
}
export interface PokerView {
  currentPlayerId: string
  turnNumber: number
  actionLimit: number
  compareOnly: boolean
  baseStake: number
  maxStake: number
  pot: number
  special235: boolean
  drinkMode: boolean
  winnerId?: string
  seats: PokerSeat[]
  myCards?: PokerCard[]
  myHandType?: string
  moves: { playerId: string; action: string; points: number; targetId: string; loserId: string }[]
}
export interface GameView {
  gameId: string
  instanceId: string
  round: number
  complete: boolean
  startsAt: number
  deadline: number
  participants: string[]
  submittedCount: number
  hasActed: boolean
  myChoice?: string
  question?: string
  options?: string[]
  selectedIds?: string[]
  counts?: Record<string, number>
  rolls?: Record<string, number>
  loserIds?: string[]
  answers?: Record<string, string>
  matched?: boolean
  cancelled?: boolean
  timedOut?: boolean
  resultIndex?: number
  result?: string
  spinnerId?: string
  needsSelection?: boolean
  cardType?: string
  rule?: string
  mode?: string
  ballots?: Record<string, string>
  poker?: PokerView
  bigSmall?: BigSmallView
  angryBirds?: AngryBirdsView
}
export interface Room {
  roomId: string
  roomCode: string
  ownerId: string
  status: string
  selectedGameId: string
  currentGameId?: string
  createdAt: number
  version: number
  players: Player[]
  settings: RoomSettings
  game?: GameView
  events: { type: string; playerIds: string[]; message: string }[]
}
export interface Envelope {
  type: string
  requestId?: string
  roomCode?: string
  version?: number
  serverTime?: number
  data?: Room
  error?: { code: string; message: string }
}
export interface GameMeta {
  id: string
  name: string
  english: string
  tag: string
  description: string
  rule: string
  color: string
  icon: string
  duration: string
  maxPlayers?: number
}
