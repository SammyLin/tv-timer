import { useCallback, useEffect, useRef, useState } from "react"
import { toast } from "sonner"
import { AlarmClock, BellRing, ChevronDown, Hand, Hourglass, Infinity, Lock, Moon, Pause, Plus, Square, Tv, type LucideIcon } from "lucide-react"

import { act, getState, savedPin, type Action, type Settings, type TvState } from "@/lib/api"
import { cn } from "@/lib/utils"
import { Toaster } from "@/components/ui/sonner"
import { InstallButton } from "@/Install"
import { AnalyticsCard } from "@/Analytics"
import { UpdateBanner, UpdateCard } from "@/Update"
import { NavMenu, usePage } from "@/Nav"
import { AboutPage } from "@/About"
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { colorOf } from "@/lib/colors"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Progress } from "@/components/ui/progress"
import { Separator } from "@/components/ui/separator"
import { Avatar, AvatarFallback } from "@/components/ui/avatar"
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card"
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible"
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription,
  AlertDialogFooter, AlertDialogHeader, AlertDialogTitle, AlertDialogTrigger,
} from "@/components/ui/alert-dialog"

const initial = (n: string) => (n.length >= 2 && "小阿大老".includes(n[0]) ? n.at(-1)! : n[0])

/** 手機一眼看出電視現在怎樣:每種狀態一個顏色(Tailwind 要完整 class 名稱才會打包) */
type Phase = TvState["status"] | "warn"
const PHASE: Record<Phase, { label: string; icon: LucideIcon; live: boolean; text: string; box: string; card: string; dot: string }> = {
  watching: { label: "看電視中", icon: Tv, live: true, text: "text-emerald-400", box: "bg-emerald-500/15 border-emerald-500/40", card: "border-emerald-500/50", dot: "bg-emerald-400" },
  warn: { label: "快到了", icon: Hourglass, live: true, text: "text-orange-400", box: "bg-orange-500/15 border-orange-500/40", card: "border-orange-500/50", dot: "bg-orange-400" },
  unlimited: { label: "不限時間", icon: Infinity, live: true, text: "text-sky-400", box: "bg-sky-500/15 border-sky-500/40", card: "border-sky-500/50", dot: "bg-sky-400" },
  paused: { label: "暫停中", icon: Pause, live: false, text: "text-yellow-300", box: "bg-yellow-400/15 border-yellow-400/40", card: "border-yellow-400/50", dot: "bg-yellow-300" },
  locked: { label: "休息中", icon: Moon, live: false, text: "text-zinc-300", box: "bg-zinc-500/15 border-zinc-500/40", card: "border-zinc-600", dot: "bg-zinc-400" },
  timeup: { label: "時間到", icon: AlarmClock, live: false, text: "text-red-400", box: "bg-red-500/15 border-red-500/40", card: "border-red-500/50", dot: "bg-red-400" },
  pending: { label: "等你同意", icon: Hand, live: true, text: "text-violet-300", box: "bg-violet-500/15 border-violet-500/40", card: "border-violet-500/50", dot: "bg-violet-300" },
}

const phaseOf = (s: TvState): Phase =>
  s.status === "watching" && s.remainingMinutes != null && s.remainingMinutes <= s.config.warnMinutes ? "warn" : s.status

function title(s: TvState) {
  const who = s.who.join("、")
  switch (s.status) {
    case "pending": return "小孩想要「不限制」"
    case "timeup": return "時間到了,電視鎖著"
    case "locked": return "電視鎖著,沒有人在看"
    case "unlimited": return `${who || "全家"}在看,不限時間`
    case "watching": return `${who || "有人"}在看`
    case "paused": return `${who || "有人"}暫停中`
  }
}

export default function App() {
  const [state, setState] = useState<TvState | null>(null)
  const [offline, setOffline] = useState(false)
  const [pin, setPin] = useState(savedPin.get())
  const [remembered, setRemembered] = useState(!!savedPin.get())
  const [busy, setBusy] = useState<string | null>(null)
  const pinRef = useRef<HTMLInputElement>(null)
  const [page, go] = usePage()
  // 不在首頁(沒有密碼欄)時按了要密碼的按鈕:跳出輸入密碼的視窗,輸入後接著做
  const [ask, setAsk] = useState<{ key: string; a: Action } | null>(null)

  const refresh = useCallback(async () => {
    try { setState(await getState()); setOffline(false) } catch { setOffline(true) }
  }, [])

  // 每 5 秒更新一次(畫面在背景時不更新)
  useEffect(() => {
    refresh()
    const id = setInterval(() => { if (!document.hidden) refresh() }, 5000)
    const onVis = () => { if (!document.hidden) refresh() }
    document.addEventListener("visibilitychange", onVis)
    return () => { clearInterval(id); document.removeEventListener("visibilitychange", onVis) }
  }, [refresh])

  const run = async (key: string, a: Action, typed?: string): Promise<boolean> => {
    const p = (typed ?? pin).replace(/\D/g, "")
    if (!p) {
      if (pinRef.current) { pinRef.current.focus(); toast.error("先輸入爸媽密碼") } else setAsk({ key, a })
      return false
    }
    setBusy(key)
    try {
      const r = await act(p, a)
      setState(r.state)
      if (r.ok) {
        savedPin.set(a.action === "save" && a.newPin ? a.newPin : p)
        if (a.action === "save" && a.newPin) setPin(a.newPin)
        setRemembered(true)
        toast.success(r.message)
      } else {
        savedPin.set(""); setRemembered(false); setPin("")
        toast.error(r.message)
        if (!pinRef.current && /密碼不對/.test(r.message)) setAsk({ key, a })
        setTimeout(() => pinRef.current?.focus(), 50)
      }
      return r.ok
    } catch {
      toast.error("連不到電視,手機有連家裡的 Wi-Fi 嗎?")
      return false
    } finally { setBusy(null) }
  }

  return (
    <div className="mx-auto min-h-svh max-w-md px-4 pt-[max(1rem,env(safe-area-inset-top))] pb-[max(2rem,env(safe-area-inset-bottom))]">
      <header className="flex h-11 items-center justify-between gap-2 text-sm text-muted-foreground">
        <span className="flex items-center gap-1 font-medium text-foreground">
          <NavMenu page={page} go={go} version={state?.version} hasUpdate={state?.update.phase === "available"} />
          <button className="flex items-center gap-2" onClick={() => go("home")}>
            <img src="/logo.svg" alt="" className="size-7 rounded-md" />看電視計時
          </button>
        </span>
        <span className="tabular-nums">
          {offline ? "連不到電視" : state ? `v${state.version} · ${state.updatedAt} 更新` : ""}
        </span>
      </header>

      {!state ? <Loading offline={offline} onRetry={refresh} /> : (
        <div className="mt-3 flex flex-col gap-3">
          {page === "home" && <>
            <UpdateBanner state={state} onOpen={() => go("update")} />
            <StatusCard
              state={state} pin={pin} setPin={setPin} pinRef={pinRef} remembered={remembered}
              forget={() => { savedPin.set(""); setRemembered(false); setPin(""); setTimeout(() => pinRef.current?.focus(), 50) }}
              busy={busy} run={run}
            />
            <TodayCard state={state} />
            <InstallButton />
          </>}
          {page === "analytics" && <AnalyticsCard state={state} busy={busy} run={run} />}
          {page === "settings" && <>
            <SettingsCard state={state} busy={busy === "save"} onSave={(s) => run("save", { action: "save", ...s })} onChime={() => run("chime", { action: "chime" })} />
            <InstallButton />
          </>}
          {page === "update" && <UpdateCard state={state} busy={busy} run={run} />}
          {page === "about" && <AboutPage version={state.version} />}
        </div>
      )}
      <PinDialog ask={ask} onClose={() => setAsk(null)} onSubmit={(typed) => { const x = ask; setAsk(null); if (x) run(x.key, x.a, typed) }} />
      <Toaster position="top-center" theme="dark" richColors />
    </div>
  )
}

function Loading({ offline, onRetry }: { offline: boolean; onRetry: () => void }) {
  return (
    <Card className="mt-4">
      <CardHeader>
        <CardTitle>{offline ? "連不到電視" : "讀取中…"}</CardTitle>
        {offline && <CardDescription>手機要連家裡的 Wi-Fi,電視也要開著或在待機。</CardDescription>}
      </CardHeader>
      {offline && <CardFooter><Button variant="outline" onClick={onRetry}>再試一次</Button></CardFooter>}
    </Card>
  )
}

type RunFn = (key: string, a: Action) => Promise<boolean>

function StatusCard({ state, pin, setPin, pinRef, remembered, forget, busy, run }: {
  state: TvState; pin: string; setPin: (v: string) => void; pinRef: React.RefObject<HTMLInputElement | null>
  remembered: boolean; forget: () => void; busy: string | null; run: RunFn
}) {
  const s = state.status
  const p = PHASE[phaseOf(state)]
  const showLeft = state.remainingMinutes != null && s !== "pending" && s !== "timeup"
  const verb = s === "watching" ? "多" : "開放"
  const canStop = s !== "locked"
  const sub = {
    pending: "同意後電視就不限時間",
    unlimited: "停止前電視都可以用",
    locked: "小孩在電視上選人就會開始計時",
    timeup: "小孩在電視上選人就會開始計時",
    watching: null, paused: "電視關掉了,下次打開可以繼續",
  }[s]

  return (
    <Card className={cn("border-2 transition-colors", p.card)}>
      <CardHeader>
        <div className={cn("flex items-center justify-between gap-3 rounded-xl border px-4 py-3", p.box)}>
          <div className={cn("flex items-center gap-2.5", p.text)}>
            <p.icon className="size-7 shrink-0" />
            <span className="text-2xl font-bold">{p.label}</span>
            {p.live && (
              <span className="relative ml-1 flex size-2.5">
                <span className={cn("absolute inline-flex size-full animate-ping rounded-full opacity-75", p.dot)} />
                <span className={cn("relative inline-flex size-2.5 rounded-full", p.dot)} />
              </span>
            )}
          </div>
          {showLeft && (
            <p className="flex shrink-0 items-baseline gap-1 text-muted-foreground">
              還剩 <span className={cn("text-4xl font-bold tabular-nums", p.text)}>{state.remainingMinutes}</span> 分
            </p>
          )}
        </div>
        <CardTitle className="pt-1 text-xl leading-tight">{title(state)}</CardTitle>
        {sub && <CardDescription>{sub}</CardDescription>}
      </CardHeader>

      <CardContent className="flex flex-col gap-3">
        {remembered ? (
          <div className="flex min-h-11 items-center justify-between text-sm text-muted-foreground">
            <span className="flex items-center gap-2"><Lock className="size-4" />這支手機已記住密碼</span>
            <Button variant="link" className="h-11 px-0" onClick={forget}>換密碼</Button>
          </div>
        ) : (
          <div className="grid gap-2">
            <Label htmlFor="pin">爸媽密碼</Label>
            <Input
              id="pin" ref={pinRef} value={pin} onChange={(e) => setPin(e.target.value)}
              inputMode="numeric" pattern="[0-9]*" autoComplete="off" maxLength={8}
              className="h-12 text-lg tracking-[0.3em] [-webkit-text-security:disc]"
            />
          </div>
        )}

        {s === "pending" ? (
          <div className="grid gap-2">
            <Button size="lg" className="h-14 text-base" disabled={!!busy} onClick={() => run("approve", { action: "approve" })}>
              同意不限制
            </Button>
            <Button size="lg" variant="ghost" className="h-12" disabled={!!busy} onClick={() => run("deny", { action: "deny" })}>
              不同意
            </Button>
          </div>
        ) : s !== "unlimited" && (
          <div className="grid grid-cols-2 gap-2">
            {[15, 30].map((m) => (
              <Button key={m} size="lg" variant="secondary" className="h-12 text-base" disabled={!!busy}
                onClick={() => run(`add${m}`, { action: "add", minutes: m })}>
                <Plus className="size-4" />{verb} {m} 分
              </Button>
            ))}
            <Button variant="ghost" className="col-span-2 h-11" disabled={!!busy} onClick={() => run("unlimited", { action: "unlimited" })}>
              <Infinity className="size-4" />改成不限制
            </Button>
          </div>
        )}
      </CardContent>

      {canStop && (
        <CardFooter>
          <AlertDialog>
            <AlertDialogTrigger asChild>
              <Button variant="ghost" className="h-11 w-full text-destructive hover:text-destructive" disabled={!!busy}>
                <Square className="size-4" />停止並鎖定電視
              </Button>
            </AlertDialogTrigger>
            <AlertDialogContent>
              <AlertDialogHeader>
                <AlertDialogTitle>停止並鎖定電視?</AlertDialogTitle>
                <AlertDialogDescription>
                  {state.who.length ? `${state.who.join("、")}正在看的會馬上結束,` : ""}電視回到選人畫面。
                </AlertDialogDescription>
              </AlertDialogHeader>
              <AlertDialogFooter>
                <AlertDialogCancel>取消</AlertDialogCancel>
                <AlertDialogAction className="bg-destructive text-white hover:bg-destructive/90" onClick={() => run("stop", { action: "stop" })}>
                  停止
                </AlertDialogAction>
              </AlertDialogFooter>
            </AlertDialogContent>
          </AlertDialog>
        </CardFooter>
      )}
    </Card>
  )
}

function TodayCard({ state }: { state: TvState }) {
  const limit = state.config.dailyLimit
  return (
    <Card>
      <CardHeader><CardTitle>今天看了多久</CardTitle></CardHeader>
      <CardContent className="flex flex-col gap-4">
        {state.today.map((k) => {
          // 跟觀看分析同一個顏色
          const c = colorOf(state.config.kids, k.name)
          return (
            <div key={k.name} className="flex items-center gap-3">
              <Avatar className="size-9">
                <AvatarFallback className="font-semibold text-white" style={{ background: c }}>
                  {initial(k.name)}
                </AvatarFallback>
              </Avatar>
              <div className="min-w-0 flex-1">
                <div className="flex items-baseline justify-between">
                  <span className="font-medium">{k.name}</span>
                  <span className="text-sm text-muted-foreground tabular-nums">
                    <span className="text-base font-semibold text-amber-400">{k.minutes}</span> 分{limit > 0 && ` / ${limit}`}
                  </span>
                </div>
                {limit > 0 && <Progress value={Math.min(100, (k.minutes * 100) / limit)} className="mt-2 h-1.5 [&>*]:bg-amber-400" />}
              </div>
            </div>
          )
        })}
      </CardContent>
    </Card>
  )
}

function SettingsCard({ state, busy, onSave, onChime }: { state: TvState; busy: boolean; onSave: (s: Settings) => Promise<boolean>; onChime: () => void }) {
  const c = state.config
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState({
    kids: c.kids.join("、"), presets: c.presets.join("、"),
    customMinutes: String(c.customMinutes), dailyLimit: String(c.dailyLimit), warnMinutes: String(c.warnMinutes), newPin: "",
  })
  // 收起來時跟著電視上的設定走;打開編輯時不覆蓋
  useEffect(() => {
    if (!open) setForm((f) => ({ ...f, kids: c.kids.join("、"), presets: c.presets.join("、"),
      customMinutes: String(c.customMinutes), dailyLimit: String(c.dailyLimit), warnMinutes: String(c.warnMinutes) }))
  }, [open, c.kids, c.presets, c.customMinutes, c.dailyLimit, c.warnMinutes])

  const split = (v: string) => v.split(/[、,,\s]+/).map((x) => x.trim()).filter(Boolean)
  const pinBad = form.newPin !== "" && !/^\d{4,8}$/.test(form.newPin)
  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) => setForm({ ...form, [k]: e.target.value })

  const save = async () => {
    const ok = await onSave({
      kids: split(form.kids).slice(0, 6),
      presets: split(form.presets).map(Number).filter((n) => n > 0).slice(0, 3),
      customMinutes: Number(form.customMinutes) || c.customMinutes,
      dailyLimit: Number(form.dailyLimit) || 0,
      warnMinutes: Number(form.warnMinutes) || 0,
      newPin: form.newPin || undefined,
    })
    if (ok) { setForm((f) => ({ ...f, newPin: "" })); setOpen(false) }
  }

  const summary = `${c.kids.join("、")} · ${c.dailyLimit > 0 ? `每天 ${c.dailyLimit} 分` : "每天不限"} · 剩 ${c.warnMinutes} 分提醒`

  return (
    <Card>
      <Collapsible open={open} onOpenChange={setOpen}>
        <CollapsibleTrigger asChild>
          <button className="flex w-full items-center justify-between gap-3 px-6 text-left">
            <div className="min-w-0">
              <div className="font-semibold">設定</div>
              <div className="truncate text-sm text-muted-foreground">{summary}</div>
            </div>
            <ChevronDown className={cn("size-5 shrink-0 text-muted-foreground transition-transform", open && "rotate-180")} />
          </button>
        </CollapsibleTrigger>
        <CollapsibleContent>
          <Separator className="my-4" />
          <CardContent className="flex flex-col gap-5">
            <Field id="kids" label="小孩名字" hint="用「、」分開,最多 6 個人">
              <Input id="kids" className="h-11" value={form.kids} onChange={set("kids")} />
            </Field>
            <Field id="presets" label="電視上的快速按鈕(分鐘)" hint="最多 3 個">
              <Input id="presets" className="h-11" inputMode="numeric" value={form.presets} onChange={set("presets")} />
            </Field>
            <div className="grid grid-cols-2 gap-3">
              <Field id="custom" label="「自訂」預設">
                <Input id="custom" className="h-11" type="number" inputMode="numeric" value={form.customMinutes} onChange={set("customMinutes")} />
              </Field>
              <Field id="warn" label="剩幾分鐘倒數">
                <Input id="warn" className="h-11" type="number" inputMode="numeric" value={form.warnMinutes} onChange={set("warnMinutes")} />
              </Field>
            </div>
            <Field id="daily" label="每人每天最多幾分鐘" hint="填 0 就不限">
              <Input id="daily" className="h-11" type="number" inputMode="numeric" value={form.dailyLimit} onChange={set("dailyLimit")} />
            </Field>
            <Field id="newpin" label="新的爸媽密碼" hint={pinBad ? "要 4~8 位數字" : "4~8 位數字,不改就留空"} error={pinBad}>
              <Input id="newpin" className="h-11 tracking-[0.3em]" inputMode="numeric" pattern="[0-9]*" autoComplete="off" maxLength={8}
                aria-invalid={pinBad} value={form.newPin} onChange={set("newPin")} />
            </Field>
          </CardContent>
          <CardFooter className="flex-col gap-2 pt-5">
            <Button size="lg" className="h-12 w-full text-base" disabled={busy || pinBad} onClick={save}>儲存設定</Button>
            <Button variant="ghost" className="h-11 w-full" disabled={busy} onClick={onChime}>
              <BellRing className="size-4" />在電視上試聽時間到的音效
            </Button>
          </CardFooter>
        </CollapsibleContent>
      </Collapsible>
    </Card>
  )
}

function Field({ id, label, hint, error, children }: { id: string; label: string; hint?: string; error?: boolean; children: React.ReactNode }) {
  return (
    <div className="grid gap-2">
      <Label htmlFor={id}>{label}</Label>
      {children}
      {hint && <p id={`${id}-hint`} className={cn("text-xs", error ? "text-destructive" : "text-muted-foreground")}>{hint}</p>}
    </div>
  )
}

/** 在別的頁面按了要密碼的按鈕時跳出來 */
function PinDialog({ ask, onClose, onSubmit }: { ask: unknown; onClose: () => void; onSubmit: (pin: string) => void }) {
  const [v, setV] = useState("")
  useEffect(() => { if (ask) setV("") }, [ask])
  return (
    <Dialog open={!!ask} onOpenChange={(o) => { if (!o) onClose() }}>
      <DialogContent>
        <form onSubmit={(e) => { e.preventDefault(); if (v.trim()) onSubmit(v) }} className="grid gap-4">
          <DialogHeader>
            <DialogTitle>輸入爸媽密碼</DialogTitle>
            <DialogDescription>輸入一次,這支手機就會記住</DialogDescription>
          </DialogHeader>
          <Input autoFocus value={v} onChange={(e) => setV(e.target.value)} inputMode="numeric" pattern="[0-9]*" autoComplete="off" maxLength={8}
            className="h-12 text-lg tracking-[0.3em] [-webkit-text-security:disc]" aria-label="爸媽密碼" />
          <DialogFooter><Button type="submit" size="lg" className="h-12 w-full">確定</Button></DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
