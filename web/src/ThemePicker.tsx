import { Check } from "lucide-react"

import type { Action, TvState, TvTheme } from "@/lib/api"
import { cn } from "@/lib/utils"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"

type RunFn = (key: string, a: Action) => Promise<boolean>

/** 電視配色:點一下電視馬上換 */
export function ThemeCard({ state, busy, run }: { state: TvState; busy: string | null; run: RunFn }) {
  const current = state.config.theme
  return (
    <Card>
      <CardHeader>
        <CardTitle>電視配色</CardTitle>
        <CardDescription>點一下,電視上的選人畫面馬上換顏色</CardDescription>
      </CardHeader>
      <CardContent className="grid grid-cols-2 gap-3">
        {state.themes.map((t) => (
          <button key={t.id} disabled={!!busy} aria-pressed={t.id === current}
            onClick={() => t.id !== current && run(`theme-${t.id}`, { action: "theme", theme: t.id })}
            className={cn("overflow-hidden rounded-xl border-2 text-left transition-colors",
              t.id === current ? "border-foreground" : "border-transparent")}>
            <Preview t={t} />
            <span className="flex items-center justify-between px-3 py-2 text-sm font-medium">
              {t.name}
              <span className="flex items-center gap-1 text-xs text-muted-foreground">
                {t.id === current ? <><Check className="size-4 text-foreground" />使用中</> : t.light ? "亮色" : "暗色"}
              </span>
            </span>
          </button>
        ))}
      </CardContent>
    </Card>
  )
}

/** 縮小版的電視選人畫面:底色、標題字、兩張卡片、強調色數字、反白按鈕 */
function Preview({ t }: { t: TvTheme }) {
  return (
    <div className="flex aspect-video flex-col gap-1.5 p-2.5" style={{ background: t.ground }} aria-hidden>
      <div className="h-2 w-14 rounded-full" style={{ background: t.text }} />
      <div className="flex flex-1 gap-1.5">
        {[0, 1].map((i) => (
          <div key={i} className="flex flex-1 flex-col justify-end gap-1 rounded-md p-1.5" style={{ background: t.surface }}>
            <div className="h-1.5 w-8 rounded-full" style={{ background: t.text, opacity: 0.8 }} />
            <div className="h-1.5 w-5 rounded-full" style={{ background: t.accent }} />
          </div>
        ))}
      </div>
      <div className="flex justify-end">
        <div className="h-3 w-12 rounded-full" style={{ background: t.text }} />
      </div>
    </div>
  )
}
