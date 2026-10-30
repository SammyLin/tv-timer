// 電視上 ParentServer 的 API(app/src/main/java/ai/threemi/tvtimer/ParentServer.kt)

export type Status = "pending" | "timeup" | "locked" | "unlimited" | "watching" | "paused"

export type TvState = {
  status: Status
  who: string[]
  remainingMinutes: number | null
  today: { name: string; minutes: number }[]
  config: {
    kids: string[]
    presets: number[]
    customMinutes: number
    dailyLimit: number
    warnMinutes: number
  }
  updatedAt: string
}

export type Settings = {
  kids: string[]
  presets: number[]
  customMinutes: number
  dailyLimit: number
  warnMinutes: number
  newPin?: string
}

export type Action =
  | { action: "check" | "approve" | "deny" | "stop" | "unlimited" | "chime" }
  | { action: "add"; minutes: number }
  | ({ action: "save" } & Settings)

export type ActResult = { ok: boolean; message: string; state: TvState }

export async function getState(): Promise<TvState> {
  const r = await fetch("/api/state", { cache: "no-store" })
  if (!r.ok) throw new Error(String(r.status))
  return r.json()
}

export async function act(pin: string, a: Action): Promise<ActResult> {
  const r = await fetch("/api/do", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ pin, ...a }),
  })
  if (!r.ok) throw new Error(String(r.status))
  return r.json()
}

// 爸媽密碼記在這支手機;密碼錯就清掉
const KEY = "tvtimer-pin"
export const savedPin = {
  get: () => { try { return localStorage.getItem(KEY) ?? "" } catch { return "" } },
  set: (v: string) => { try { if (v) localStorage.setItem(KEY, v); else localStorage.removeItem(KEY) } catch { /* 私密瀏覽 */ } },
}
