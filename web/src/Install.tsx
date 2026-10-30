import { useEffect, useState } from "react"
import { Download, EllipsisVertical, Share, SquarePlus } from "lucide-react"

import { Button } from "@/components/ui/button"
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from "@/components/ui/dialog"

/** Chrome 在 HTTPS 下才會給的「安裝」事件 */
type InstallEvent = Event & { prompt: () => Promise<void>; userChoice: Promise<{ outcome: "accepted" | "dismissed" }> }

const isStandalone = () =>
  window.matchMedia("(display-mode: standalone)").matches || (navigator as Navigator & { standalone?: boolean }).standalone === true
const isIOS = () => /iPhone|iPad|iPod/.test(navigator.userAgent) || (navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1)
const isIOSChrome = () => /CriOS/.test(navigator.userAgent)

/**
 * 「安裝到手機」:
 *  - Chrome 有給安裝事件(HTTPS 才有)→ 按一下就跳出系統的安裝視窗
 *  - 其他情況(iPhone 都不給,電視是 http 也不給)→ 跳出兩步驟的說明,照著按「加入主畫面」
 * 已經從主畫面打開的就不顯示。
 */
export function InstallButton() {
  const [deferred, setDeferred] = useState<InstallEvent | null>(null)
  const [installed, setInstalled] = useState(isStandalone())
  const [help, setHelp] = useState(false)

  useEffect(() => {
    const onPrompt = (e: Event) => { e.preventDefault(); setDeferred(e as InstallEvent) }
    const onInstalled = () => { setInstalled(true); setDeferred(null) }
    window.addEventListener("beforeinstallprompt", onPrompt)
    window.addEventListener("appinstalled", onInstalled)
    return () => {
      window.removeEventListener("beforeinstallprompt", onPrompt)
      window.removeEventListener("appinstalled", onInstalled)
    }
  }, [])

  if (installed) return null

  const install = async () => {
    if (!deferred) { setHelp(true); return }
    await deferred.prompt()
    const { outcome } = await deferred.userChoice
    if (outcome === "accepted") setInstalled(true)
    setDeferred(null)
  }

  const ios = isIOS()
  const steps = ios
    ? [
        { icon: <Share className="size-5" />, text: isIOSChrome() ? "按網址列右邊的「分享」" : "按下面工具列的「分享」" },
        { icon: <SquarePlus className="size-5" />, text: "往下找「加入主畫面」,再按「加入」" },
      ]
    : [
        { icon: <EllipsisVertical className="size-5" />, text: "按 Chrome 右上角的「⋮」" },
        { icon: <SquarePlus className="size-5" />, text: "選「加到主畫面」,再按「新增」" },
      ]

  return (
    <>
      <Button variant="outline" className="h-11 w-full" onClick={install}>
        <Download className="size-4" />安裝到手機
      </Button>
      <Dialog open={help} onOpenChange={setHelp}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>加到手機主畫面</DialogTitle>
            <DialogDescription>
              {ios ? "iPhone 不能一鍵安裝,照這兩步就好。" : "電視用的是 http 網址,Chrome 不會給一鍵安裝,照這兩步就好。"}
              之後從主畫面的「電視計時」打開,沒有網址列,像一般 App。
            </DialogDescription>
          </DialogHeader>
          <ol className="flex flex-col gap-3">
            {steps.map((s, i) => (
              <li key={i} className="flex items-center gap-3 rounded-lg bg-muted px-4 py-3">
                <span className="text-muted-foreground">{s.icon}</span>
                <span>{s.text}</span>
              </li>
            ))}
          </ol>
          <DialogFooter>
            <Button className="h-11 w-full" onClick={() => setHelp(false)}>知道了</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}

/** Service worker 只有安全網址(HTTPS 或 localhost)才能註冊 */
export function registerSW() {
  if ("serviceWorker" in navigator && window.isSecureContext) {
    navigator.serviceWorker.register("/sw.js").catch(() => {})
  }
}
