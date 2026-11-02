import { useState } from "react"
import { toast } from "sonner"
import { Download, RefreshCw } from "lucide-react"

import { checkUpdate, type Action, type TvState, type UpdateStatus } from "@/lib/api"
import { Button } from "@/components/ui/button"
import { Progress } from "@/components/ui/progress"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"

type RunFn = (key: string, a: Action) => Promise<boolean>

/** App 版本、檢查更新、用手機叫電視自己更新 */
export function UpdateCard({ state, busy, run }: { state: TvState; busy: string | null; run: RunFn }) {
  const [checked, setChecked] = useState<UpdateStatus | null>(null)
  const [checking, setChecking] = useState(false)
  // 電視上的進度(每 5 秒跟著狀態更新)優先;剛按完「檢查」用回傳的結果
  const u = state.update.phase !== "idle" ? state.update : checked ?? state.update
  const working = ["downloading", "installing", "confirm"].includes(u.phase)

  const check = async () => {
    setChecking(true)
    try {
      const r = await checkUpdate()
      setChecked(r)
      if (r.phase === "latest") toast.success(`已經是最新版 ${state.version}`)
      if (r.phase === "failed") toast.error(r.message ?? "檢查失敗")
    } catch { toast.error("連不到電視") } finally { setChecking(false) }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>App 版本 {state.version}</CardTitle>
        <CardDescription>
          {u.phase === "available" && `有新版 ${u.latest} 可以更新`}
          {u.phase === "latest" && "已經是最新版"}
          {u.phase === "downloading" && `電視正在下載 ${u.latest}…`}
          {u.phase === "installing" && "電視正在安裝,裝好會自己重新開啟,正在看的不會中斷"}
          {u.phase === "confirm" && "請拿遙控器在電視上按「安裝」"}
          {u.phase === "failed" && (u.message ?? "更新失敗")}
          {(u.phase === "idle" || u.phase === "checking") && "可以直接用手機叫電視更新到最新版"}
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        {u.phase === "downloading" && <Progress value={u.progress} className="h-2" />}
        {u.phase === "available" && u.notes && (
          <pre className="max-h-40 overflow-auto whitespace-pre-wrap rounded-md bg-muted p-3 font-sans text-sm text-muted-foreground">{u.notes}</pre>
        )}
        {u.phase === "available" ? (
          <Button size="lg" className="h-12 text-base" disabled={!!busy || working}
            onClick={async () => { if (await run("update", { action: "update-install" })) setChecked(null) }}>
            <Download className="size-4" />更新到 {u.latest}
          </Button>
        ) : (
          <Button variant="secondary" className="h-11" disabled={checking || working} onClick={check}>
            <RefreshCw className={checking ? "size-4 animate-spin" : "size-4"} />檢查更新
          </Button>
        )}
      </CardContent>
    </Card>
  )
}
