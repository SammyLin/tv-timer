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
    theme: string
  }
  /** 電視可以選的配色 */
  themes: TvTheme[]
  updatedAt: string
  /** 電視上 App 的版本 */
  version: string
  update: UpdateStatus
}

export type TvTheme = { id: string; name: string; light: boolean; ground: string; surface: string; text: string; accent: string }

/** 手機按「檢查更新 / 更新」後電視的進度 */
export type UpdateStatus = {
  phase: "idle" | "checking" | "latest" | "available" | "downloading" | "installing" | "confirm" | "failed"
  latest: string | null
  notes: string | null
  progress: number
  message: string | null
}

export async function checkUpdate(): Promise<UpdateStatus> {
  const r = await fetch("/api/update/check", { cache: "no-store" })
  if (!r.ok) throw new Error(String(r.status))
  return r.json()
}

export type Settings = {
  kids: string[]
  presets: number[]
  customMinutes: number
  dailyLimit: number
  warnMinutes: number
  newPin?: string
}

/** 每天的觀看紀錄(GET /api/history);時間都是秒,kids 的 key "" 是沒選人的「全家」 */
export type HistoryDay = { date: string; kids: Record<string, number>; unlimited: number; hours: number[] }
export type History = { retentionDays: number; retentionChoices: number[]; today: string; kids: string[]; days: HistoryDay[] }

export type Action =
  | { action: "check" | "approve" | "deny" | "stop" | "unlimited" | "chime" }
  | { action: "history-clear"; date?: string }
  | { action: "update-install" }
  | { action: "theme"; theme: string }
  | { action: "retention"; days: number }
  | { action: "add"; minutes: number }
  | ({ action: "save" } & Settings)

export type ActResult = { ok: boolean; message: string; state: TvState }

export async function getState(): Promise<TvState> {
  const r = await fetch("/api/state", { cache: "no-store" })
  if (!r.ok) throw new Error(String(r.status))
  return r.json()
}

export async function getHistory(): Promise<History> {
  const r = await fetch("/api/history", { cache: "no-store" })
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
