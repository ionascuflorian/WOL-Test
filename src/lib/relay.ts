export type WakeRequest = {
  id: string
  requestedAt: number
}

export type WakeStatus = "sent" | "up"

export type WakeConfirmation = {
  id: string
  requestedAt: number
  ackedAt: number
  status: WakeStatus
}

type Storage = {
  set(key: string, value: string, ttlSeconds: number): Promise<void>
  get(key: string): Promise<string | null>
  del(key: string): Promise<void>
}

const PENDING_KEY = "wol:pending"
const LAST_KEY = "wol:last"
const TTL_SECONDS = 180
const LAST_TTL_SECONDS = 600

function getMemoryStore(): Map<string, { value: string; exp: number }> {
  const g = globalThis as { __wolRelayMemory?: Map<string, { value: string; exp: number }> }
  if (!g.__wolRelayMemory) {
    g.__wolRelayMemory = new Map()
  }
  return g.__wolRelayMemory
}

const memoryStorage: Storage = {
  async set(key, value, ttlSeconds) {
    getMemoryStore().set(key, { value, exp: Date.now() + ttlSeconds * 1000 })
  },
  async get(key) {
    const entry = getMemoryStore().get(key)
    if (!entry) return null
    if (entry.exp < Date.now()) {
      getMemoryStore().delete(key)
      return null
    }
    return entry.value
  },
  async del(key) {
    getMemoryStore().delete(key)
  },
}

function makeUpstashStorage(): Storage {
  const url = process.env.UPSTASH_REDIS_REST_URL!
  const token = process.env.UPSTASH_REDIS_REST_TOKEN!

  async function command(command: string[]): Promise<unknown> {
    const res = await fetch(url, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(command),
      cache: "no-store",
    })
    if (!res.ok) {
      throw new Error(`Upstash error ${res.status}: ${await res.text()}`)
    }
    const data = await res.json()
    return data.result
  }

  return {
    async set(key, value, ttlSeconds) {
      await command(["SET", key, value, "EX", String(ttlSeconds)])
    },
    async get(key) {
      const result = await command(["GET", key])
      return typeof result === "string" ? result : null
    },
    async del(key) {
      await command(["DEL", key])
    },
  }
}

function resolveStorage(): Storage {
  if (process.env.UPSTASH_REDIS_REST_URL && process.env.UPSTASH_REDIS_REST_TOKEN) {
    return makeUpstashStorage()
  }
  return memoryStorage
}

export async function queueWake(): Promise<WakeRequest> {
  const request: WakeRequest = {
    id: crypto.randomUUID(),
    requestedAt: Date.now(),
  }
  const storage = resolveStorage()
  await storage.set(PENDING_KEY, JSON.stringify(request), TTL_SECONDS)
  await storage.del(LAST_KEY)
  return request
}

export async function peekWake(): Promise<WakeRequest | null> {
  const value = await resolveStorage().get(PENDING_KEY)
  if (!value) return null
  try {
    return JSON.parse(value) as WakeRequest
  } catch {
    return null
  }
}

export async function consumeWake(status: WakeStatus): Promise<void> {
  const storage = resolveStorage()
  const request = await peekWake()
  await storage.del(PENDING_KEY)
  if (request) {
    const confirmation: WakeConfirmation = {
      id: request.id,
      requestedAt: request.requestedAt,
      ackedAt: Date.now(),
      status,
    }
    await storage.set(LAST_KEY, JSON.stringify(confirmation), LAST_TTL_SECONDS)
  }
}

export async function peekLast(): Promise<WakeConfirmation | null> {
  const value = await resolveStorage().get(LAST_KEY)
  if (!value) return null
  try {
    return JSON.parse(value) as WakeConfirmation
  } catch {
    return null
  }
}