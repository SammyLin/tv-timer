import { useCallback, useEffect, useMemo, useState } from "react"
import { ChevronDown, Trash2 } from "lucide-react"

import { getHistory, type Action, type History, type HistoryDay, type TvState } from "@/lib/api"
import { FAMILY, seriesOrder } from "@/lib/colors"
import { cn } from "@/lib/utils"
import { Bar, BarChart, Cell, ReferenceLine, XAxis, YAxis } from "recharts"
import { ChartContainer, ChartTooltip, ChartTooltipContent, type ChartConfig } from "@/components/ui/chart"
import { Button } from "@/components/ui/button"
import { Separator } from "@/components/ui/separator"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible"
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription,
  AlertDialogFooter, AlertDialogHeader, AlertDialogTitle, AlertDialogTrigger,
} from "@/components/ui/alert-dialog"

type RunFn = (key: string, a: Action) => Promise<boolean>

const WEEK = "日一二三四五六"
const parseDate = (s: string) => { const [y, m, d] = s.split("-").map(Number); return new Date(y, m - 1, d) }
const ymd = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`
const short = (s: string) => { const d = parseDate(s); return `${d.getMonth() + 1}/${d.getDate()}` }
const withWeek = (s: string) => `${short(s)}(${WEEK[parseDate(s).getDay()]})`
const mins = (sec: number) => Math.round(sec / 60)
/** 42 分 / 5 小時 20 分 */
function dur(sec: number) {
  const m = mins(sec)
  if (m < 60) return `${m} 分`
  const h = Math.floor(m / 60), r = m % 60
  return r ? `${h} 小時 ${r} 分` : `${h} 小時`
}

/** 從今天往回 n 天,沒紀錄的日子補 0 */
function lastDays(h: History, n: number): HistoryDay[] {
  const byDate = new Map(h.days.map((d) => [d.date, d]))
  const today = parseDate(h.today)
  return Array.from({ length: n }, (_, i) => {
    const d = new Date(today); d.setDate(today.getDate() - (n - 1 - i))
    const key = ymd(d)
    return byDate.get(key) ?? { date: key, kids: {}, unlimited: 0, hours: Array(24).fill(0) }
  })
}

export function AnalyticsCard({ state, busy, run }: { state: TvState; busy: string | null; run: RunFn }) {
  const [hist, setHist] = useState<History | null>(null)
  const [range, setRange] = useState(7)

  const load = useCallback(async () => { try { setHist(await getHistory()) } catch { /* 連不到電視時上面會顯示 */ } }, [])
  useEffect(() => {
    load()
    const id = setInterval(() => { if (!document.hidden) load() }, 60_000)
    return () => clearInterval(id)
  }, [load])

  const act = async (key: string, a: Action) => { if (await run(key, a)) await load() }

  if (!hist) return null
  const ranges = [7, 30, hist.retentionDays].filter((n, i, a) => n <= hist.retentionDays && a.indexOf(n) === i).sort((a, b) => a - b)
  const n = Math.min(range, hist.retentionDays)
  const days = lastDays(hist, n)
  const extra = [...new Set(hist.days.flatMap((d) => Object.keys(d.kids)))]
  const series = seriesOrder(state.config.kids, extra)
    .filter((s) => s.name !== FAMILY && state.config.kids.includes(s.name) || days.some((d) => (d.kids[s.name] ?? 0) > 0))
  const tvSec = days.reduce((t, d) => t + d.hours.reduce((a, b) => a + b, 0), 0)
  const unlimitedSec = days.reduce((t, d) => t + d.unlimited, 0)

  return (
    <Card>
      <CardHeader>
        <CardTitle>觀看分析</CardTitle>
        <CardDescription>紀錄保留 {hist.retentionDays} 天,更早的會自動刪掉</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-5">
        <div className="flex gap-1 rounded-lg bg-muted p-1" role="tablist" aria-label="期間">
          {ranges.map((r) => (
            <button key={r} role="tab" aria-selected={n === r} onClick={() => setRange(r)}
              className={cn("h-9 flex-1 rounded-md text-sm font-medium transition-colors",
                n === r ? "bg-background text-foreground shadow-sm" : "text-muted-foreground")}>
              {r === hist.retentionDays && r > 30 ? `全部 ${r} 天` : `${r} 天`}
            </button>
          ))}
        </div>

        <div>
          <p className="text-sm text-muted-foreground">這 {n} 天電視開了</p>
          <p className="text-4xl font-semibold">{dur(tvSec)}</p>
          <p className="text-sm text-muted-foreground">
            平均每天 {dur(tvSec / n)}{unlimitedSec > 0 && `,其中不限制 ${dur(unlimitedSec)}`}
          </p>
        </div>

        {series.length === 0 || tvSec === 0
          ? <div className="flex flex-col items-center gap-2 py-2 text-center">
              <img src="/art/tvbot-point.webp" alt="" className="size-24 object-contain" />
              <p className="text-sm text-muted-foreground">這段期間還沒有看電視的紀錄。</p>
            </div>
          : <>
            {series.map((s) => (
              <PersonRow key={s.name} label={s.label} color={s.color} days={days.map((d) => ({ date: d.date, sec: d.kids[s.name] ?? 0 }))}
                limitMin={s.name === FAMILY ? 0 : (state.today.find((k) => k.name === s.name)?.limit ?? 0)} />
            ))}
            <Separator />
            <HoursChart days={days} />
          </>}

        <Separator />
        <DayTable days={[...days].reverse()} series={series} busy={busy} onDelete={(date) => act(`del-${date}`, { action: "history-clear", date })} />
        <RetentionControls hist={hist} busy={busy}
          onRetention={(days) => act("retention", { action: "retention", days })}
          onClear={() => act("history-clear", { action: "history-clear" })} />
      </CardContent>
    </Card>
  )
}

/** 一個人一排:總計、平均、到上限幾天,下面是每天一根的小長條圖 */
function PersonRow({ label, color, days, limitMin }: { label: string; color: string; days: { date: string; sec: number }[]; limitMin: number }) {
  const [pick, setPick] = useState<number | null>(null)
  const total = days.reduce((t, d) => t + d.sec, 0)
  const watchedDays = days.filter((d) => d.sec > 0).length
  const atLimit = limitMin > 0 ? days.filter((d) => mins(d.sec) >= limitMin).length : 0
  const maxMin = Math.max(limitMin, ...days.map((d) => mins(d.sec)), 10)
  const shown = pick ?? days.length - 1

  return (
    <div>
      <div className="flex items-baseline justify-between gap-2">
        <span className="flex items-center gap-2 font-medium">
          <span className="inline-block size-2.5 rounded-full" style={{ background: color }} aria-hidden />{label}
        </span>
        <span className="text-sm tabular-nums text-muted-foreground">共 <span className="font-semibold text-foreground">{dur(total)}</span></span>
      </div>
      <p className="mt-0.5 text-sm tabular-nums text-muted-foreground">
        {watchedDays === 0 ? "這段期間沒有看" : `平均每天 ${dur(total / days.length)}`}
        {atLimit > 0 && ` · ${atLimit} 天到上限`}
      </p>
      <p className="mt-1 h-5 text-xs text-muted-foreground tabular-nums">
        {withWeek(days[shown].date)}{" "}<span className="text-foreground">{dur(days[shown].sec)}</span>
      </p>
      <Columns values={days.map((d) => mins(d.sec))} max={maxMin} color={color} height={56}
        refLine={limitMin > 0 ? { value: limitMin, label: `上限 ${limitMin}` } : undefined}
        selected={shown} onSelect={setPick}
        labels={days.map((d) => short(d.date))} tipLabels={days.map((d) => withWeek(d.date))}
        ariaLabel={`${label}每天看了幾分鐘`} />
    </div>
  )
}

/** 電視實際開著的時間按整點分布(一起看只算一次) */
function HoursChart({ days }: { days: HistoryDay[] }) {
  const [pick, setPick] = useState<number | null>(null)
  const hours = useMemo(() => Array.from({ length: 24 }, (_, h) => days.reduce((t, d) => t + (d.hours[h] ?? 0), 0)), [days])
  const top = hours.indexOf(Math.max(...hours))
  const shown = pick ?? top
  return (
    <div>
      <div className="flex items-baseline justify-between">
        <span className="font-medium">幾點在看</span>
        <span className="text-sm text-muted-foreground">最常在 {top}~{top + 1} 點</span>
      </div>
      <p className="mt-1 h-5 text-xs text-muted-foreground tabular-nums">
        {shown}:00~{shown + 1}:00 共 <span className="text-foreground">{dur(hours[shown])}</span>
      </p>
      <Columns values={hours.map((s) => s / 60)} max={Math.max(...hours.map((s) => s / 60), 1)} color="#fbbf24" height={72}
        selected={shown} onSelect={setPick}
        labels={hours.map((_, h) => `${h}`)} tipLabels={hours.map((_, h) => `${h}:00~${h + 1}:00`)}
        ariaLabel="每個整點電視開了多久" axisEvery={6} />
    </div>
  )
}

/**
 * 直條圖(shadcn chart / Recharts):細條、頂端 4px 圓角、底部平的;
 * 選到的那根以外變淡,點(或滑過)任一根就在上面那行和提示框顯示數字
 */
function Columns({ values, max, color, height, refLine, selected, onSelect, labels, tipLabels, ariaLabel, axisEvery }: {
  values: number[]; max: number; color: string; height: number; refLine?: { value: number; label: string }
  selected: number; onSelect: (i: number) => void; labels: string[]; tipLabels: string[]; ariaLabel: string; axisEvery?: number
}) {
  const n = values.length
  const data = values.map((v, i) => ({ label: labels[i], tip: tipLabels[i], v: Math.round(v) }))
  const config = { v: { label: "時間", color } } satisfies ChartConfig
  const ticks = (axisEvery ? labels.filter((_, i) => i % axisEvery === 0) : [labels[0], labels[Math.floor((n - 1) / 2)], labels[n - 1]])
  const pickFrom = (s: { activeTooltipIndex?: number | string | null }) => {
    const i = Number(s?.activeTooltipIndex)
    if (Number.isInteger(i) && i >= 0 && i < n) onSelect(i)
  }
  return (
    <ChartContainer config={config} className="mt-1 aspect-auto w-full select-none" style={{ height: height + 20 }} aria-label={ariaLabel} role="img">
      <BarChart data={data} margin={{ top: 4, right: 14, bottom: 0, left: 14 }} barCategoryGap={1}
        onMouseMove={pickFrom} onClick={pickFrom}>
        <YAxis hide domain={[0, max]} allowDataOverflow />
        <XAxis dataKey="label" ticks={ticks} interval={0} tickLine={false} axisLine={{ stroke: "var(--border)" }}
          tick={{ fontSize: 10, fill: "var(--muted-foreground)" }} height={18} />
        {refLine && (
          <ReferenceLine y={refLine.value} stroke="var(--muted-foreground)" strokeOpacity={0.6} ifOverflow="extendDomain"
            label={{ value: refLine.label, position: "insideTopRight", fontSize: 10, fill: "var(--muted-foreground)" }} />
        )}
        <ChartTooltip cursor={false}
          content={<ChartTooltipContent labelFormatter={(_, p) => p?.[0]?.payload?.tip} formatter={(v) => dur(Number(v) * 60)} hideIndicator />} />
        <Bar dataKey="v" fill="var(--color-v)" radius={[4, 4, 0, 0]} maxBarSize={24} minPointSize={0} isAnimationActive={false}>
          {data.map((_, i) => <Cell key={i} fillOpacity={i === selected ? 1 : 0.55} />)}
        </Bar>
      </BarChart>
    </ChartContainer>
  )
}

/** 表格:每天每個人幾分,可以刪掉某一天 */
function DayTable({ days, series, busy, onDelete }: {
  days: HistoryDay[]; series: { name: string; label: string; color: string }[]; busy: string | null; onDelete: (date: string) => void
}) {
  const rows = days.filter((d) => d.hours.some((h) => h > 0) || Object.values(d.kids).some((v) => v > 0))
  return (
    <Collapsible>
      <CollapsibleTrigger asChild>
        <Button variant="ghost" className="group h-11 w-full justify-between px-0">
          每天明細({rows.length} 天有紀錄)
          <ChevronDown className="size-4 transition-transform group-data-[state=open]:rotate-180" />
        </Button>
      </CollapsibleTrigger>
      <CollapsibleContent>
        {rows.length === 0 ? <p className="py-2 text-sm text-muted-foreground">沒有紀錄。</p> : (
          <table className="w-full text-sm tabular-nums">
            <thead>
              <tr className="text-left text-xs text-muted-foreground">
                <th className="py-1 font-normal">日期</th>
                {series.map((s) => <th key={s.name} className="py-1 text-right font-normal">{s.label}</th>)}
                <th className="py-1 text-right font-normal">電視</th>
                <th className="w-10" />
              </tr>
            </thead>
            <tbody>
              {rows.map((d) => (
                <tr key={d.date} className="border-t border-border/60">
                  <td className="py-1.5">{withWeek(d.date)}</td>
                  {series.map((s) => <td key={s.name} className="py-1.5 text-right">{mins(d.kids[s.name] ?? 0)}</td>)}
                  <td className="py-1.5 text-right">{mins(d.hours.reduce((a, b) => a + b, 0))}</td>
                  <td className="py-0.5 text-right">
                    <AlertDialog>
                      <AlertDialogTrigger asChild>
                        <Button variant="ghost" size="icon" className="size-9 text-muted-foreground" disabled={!!busy} aria-label={`刪除 ${d.date}`}>
                          <Trash2 className="size-4" />
                        </Button>
                      </AlertDialogTrigger>
                      <AlertDialogContent>
                        <AlertDialogHeader>
                          <AlertDialogTitle>刪除 {withWeek(d.date)} 的紀錄?</AlertDialogTitle>
                          <AlertDialogDescription>只刪觀看分析裡這一天的紀錄。如果是今天,「今天還能看」不會因此變多。</AlertDialogDescription>
                        </AlertDialogHeader>
                        <AlertDialogFooter>
                          <AlertDialogCancel>取消</AlertDialogCancel>
                          <AlertDialogAction className="bg-destructive text-white hover:bg-destructive/90" onClick={() => onDelete(d.date)}>刪除</AlertDialogAction>
                        </AlertDialogFooter>
                      </AlertDialogContent>
                    </AlertDialog>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        <p className="pt-2 text-xs text-muted-foreground">單位:分鐘。兩個人一起看時兩個人都會算,「電視」是電視實際開著的時間。</p>
      </CollapsibleContent>
    </Collapsible>
  )
}

function RetentionControls({ hist, busy, onRetention, onClear }: {
  hist: History; busy: string | null; onRetention: (days: number) => void; onClear: () => void
}) {
  return (
    <div className="flex flex-col gap-3">
      <div className="flex items-center justify-between gap-3">
        <label htmlFor="retention" className="text-sm">紀錄保留</label>
        <select id="retention" value={hist.retentionDays} disabled={!!busy}
          onChange={(e) => onRetention(Number(e.target.value))}
          className="h-11 rounded-md border border-input bg-transparent px-3 text-base">
          {hist.retentionChoices.map((d) => <option key={d} value={d} className="bg-background">{d} 天</option>)}
        </select>
      </div>
      <AlertDialog>
        <AlertDialogTrigger asChild>
          <Button variant="ghost" className="h-11 w-full text-destructive hover:text-destructive" disabled={!!busy}>
            <Trash2 className="size-4" />刪除全部觀看紀錄
          </Button>
        </AlertDialogTrigger>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>刪除全部觀看紀錄?</AlertDialogTitle>
            <AlertDialogDescription>觀看分析會從頭開始記。今天的「今天還能看」不受影響。</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>取消</AlertDialogCancel>
            <AlertDialogAction className="bg-destructive text-white hover:bg-destructive/90" onClick={onClear}>全部刪除</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}
