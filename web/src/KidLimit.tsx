import { useEffect, useState } from "react"
import { CalendarOff } from "lucide-react"

import type { TvState } from "@/lib/api"
import { cn } from "@/lib/utils"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog"

const ymd = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`
const WEEK = "日一二三四五六"
/** "2026-11-08" → "11/8(日)" */
export function shortDate(s: string) {
  const [y, m, d] = s.split("-").map(Number)
  return `${m}/${d}(${WEEK[new Date(y, m - 1, d).getDay()]})`
}
/** 這個星期日(今天是星期日就是今天) */
function thisSunday() { const d = new Date(); d.setDate(d.getDate() + ((7 - d.getDay()) % 7)); return ymd(d) }

type Mode = "default" | "custom" | "none" | "unlimited"
type Until = "always" | "today" | "week" | "date"

/** 調整某個小孩的每日上限:跟大家一樣 / 自訂幾分 / 不能看 / 不限,可以設到哪天為止 */
export function KidLimitDialog({ state, kid, busy, onClose, onSave }: {
  state: TvState; kid: string | null; busy: string | null; onClose: () => void
  onSave: (minutes: number | null, until?: string) => void
}) {
  const row = state.today.find((k) => k.name === kid)
  const o = row?.override
  const [mode, setMode] = useState<Mode>("default")
  const [minutes, setMinutes] = useState("")
  const [until, setUntil] = useState<Until>("week")
  const [date, setDate] = useState(thisSunday())

  // 打開時帶入目前的設定
  useEffect(() => {
    if (!kid) return
    if (!o) { setMode("default"); setMinutes(""); setUntil("week"); setDate(thisSunday()); return }
    setMode(o.minutes === 0 ? "none" : o.minutes < 0 ? "unlimited" : "custom")
    if (o.minutes > 0) setMinutes(String(o.minutes))
    if (!o.until) setUntil("always")
    else { setUntil(o.until === thisSunday() ? "week" : "date"); setDate(o.until) }
  }, [kid]) // eslint-disable-line react-hooks/exhaustive-deps

  const def = state.config.dailyLimit
  const save = () => {
    if (mode === "default") return onSave(null)
    // 自訂打 0 就是不能看
    const m = mode === "none" ? 0 : mode === "unlimited" ? -1 : Math.max(0, Math.min(1440, Number(minutes.replace(/[^\d]/g, "")) || 0))
    const u = until === "always" ? undefined : until === "today" ? ymd(new Date()) : until === "week" ? thisSunday() : date
    onSave(m, u)
  }

  const opt = (m: Mode, label: string, sub?: string) => (
    <button type="button" onClick={() => setMode(m)} aria-pressed={mode === m}
      className={cn("flex min-h-12 flex-col items-start justify-center rounded-lg border-2 px-3 py-2 text-left",
        mode === m ? "border-foreground bg-accent" : "border-border")}>
      <span className="font-medium">{label}</span>
      {sub && <span className="text-xs text-muted-foreground">{sub}</span>}
    </button>
  )
  const chip = (u: Until, label: string) => (
    <button type="button" onClick={() => setUntil(u)} aria-pressed={until === u}
      className={cn("h-10 rounded-full border px-3 text-sm", until === u ? "border-foreground bg-foreground text-background" : "border-border")}>
      {label}
    </button>
  )

  return (
    <Dialog open={!!kid} onOpenChange={(v) => { if (!v) onClose() }}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{kid} 的每日上限</DialogTitle>
          <DialogDescription>只改 {kid},其他小孩照原本的設定</DialogDescription>
        </DialogHeader>

        <Button variant="secondary" className="h-12 justify-start gap-2 text-base" disabled={!!busy}
          onClick={() => onSave(0, thisSunday())}>
          <CalendarOff className="size-5 text-red-400" />這週不能看(到 {shortDate(thisSunday())})
        </Button>

        <div className="grid grid-cols-2 gap-2">
          {opt("default", "跟大家一樣", def > 0 ? `每天 ${def} 分` : "不限")}
          {opt("custom", "自訂")}
          {opt("none", "不能看", "0 分")}
          {opt("unlimited", "不限時間")}
        </div>
        {mode === "custom" && (
          <label className="flex items-center gap-2">
            每天
            <Input autoFocus value={minutes} onChange={(e) => setMinutes(e.target.value)} inputMode="numeric" placeholder="0"
              className="h-11 w-24 text-center text-lg" />
            分<span className="text-xs text-muted-foreground">(0 = 不能看)</span>
          </label>
        )}
        {mode !== "default" && (
          <div className="grid gap-2">
            <span className="text-sm text-muted-foreground">到什麼時候</span>
            <div className="flex flex-wrap gap-2">
              {chip("today", "只有今天")}{chip("week", `到這週日`)}{chip("date", "到某一天")}{chip("always", "一直")}
            </div>
            {until === "date" && (
              <Input type="date" value={date} min={ymd(new Date())} onChange={(e) => setDate(e.target.value)} className="h-11" />
            )}
            {until !== "always" && <span className="text-xs text-muted-foreground">過了之後自動改回跟大家一樣</span>}
          </div>
        )}
        <DialogFooter>
          <Button size="lg" className="h-12 w-full" disabled={!!busy || (mode === "custom" && minutes.trim() === "")} onClick={save}>儲存</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
