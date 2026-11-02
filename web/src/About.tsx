import { Bug, ExternalLink, GitPullRequest, Heart, MessageSquare, Share2, Star, Tv } from "lucide-react"

import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"

const REPO = "https://github.com/SammyLin/tv-timer"

const HELP = [
  { icon: Star, title: "到 GitHub 按顆星星 ⭐", text: "讓更多爸媽找得到這個 App", href: REPO },
  { icon: Share2, title: "分享給其他爸媽", text: "家裡也有小孩一直說「再看一集」的朋友", share: true },
  { icon: Tv, title: "告訴我們你的電視能不能用", text: "Sony 以外的 Google TV / Android TV 都歡迎回報型號", href: `${REPO}/issues/new?title=${encodeURIComponent("電視型號回報:")}` },
  { icon: Bug, title: "回報問題、提建議", text: "哪裡不好用、想要什麼功能,都可以說", href: `${REPO}/issues/new` },
  { icon: GitPullRequest, title: "一起寫程式", text: "Kotlin + React,歡迎發 Pull Request", href: `${REPO}/pulls` },
]

const LINKS = [
  { label: "GitHub 原始碼", href: REPO },
  { label: "安裝教學", href: `${REPO}/blob/main/docs/INSTALL.zh-TW.md` },
  { label: "版本紀錄", href: `${REPO}/releases` },
  { label: "回報問題", href: `${REPO}/issues` },
]

export function AboutPage({ version }: { version: string }) {
  const share = async () => {
    const data = { title: "看電視計時", text: "給 Google TV 的看電視計時器:小孩自己選時間,時間到自動鎖,爸媽用手機管。免費開源。", url: REPO }
    try {
      if (navigator.share) await navigator.share(data)
      else { await navigator.clipboard.writeText(`${data.text} ${data.url}`); alert("已複製,可以貼給朋友") }
    } catch { /* 取消分享 */ }
  }
  return (
    <div className="flex flex-col gap-3">
      <Card>
        <CardContent className="flex flex-col items-center gap-3 pt-2 text-center">
          <img src="/logo.svg" alt="" className="size-20 rounded-2xl" />
          <div>
            <h1 className="text-2xl font-bold">看電視計時</h1>
            <p className="text-sm text-muted-foreground">版本 {version} · MIT 授權 · 免費開源</p>
          </div>
          <p className="text-[15px] leading-relaxed text-muted-foreground">
            給 Google TV 的看電視計時器。小孩打開電視先選誰看、看多久,時間到播一段音樂自動鎖起來;爸媽用手機看狀態、加時間、同意不限制。
            全部在家裡的電視上跑,<span className="text-foreground">不用帳號、不用雲端、不收集任何資料</span>。
          </p>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2"><Heart className="size-5 text-red-500" />可以幫我們的事</CardTitle>
          <CardDescription>做給自己家裡用的小專案,開源分享給大家</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-1">
          {HELP.map((h) => {
            const body = (
              <>
                <h.icon className="mt-0.5 size-5 shrink-0 text-amber-400" />
                <span className="flex-1">
                  <span className="block font-medium text-foreground">{h.title}</span>
                  <span className="block text-sm text-muted-foreground">{h.text}</span>
                </span>
                <ExternalLink className="mt-1 size-4 shrink-0 text-muted-foreground" />
              </>
            )
            const cls = "flex min-h-14 items-start gap-3 rounded-lg px-2 py-3 text-left hover:bg-accent/50"
            return h.share
              ? <button key={h.title} className={cls} onClick={share}>{body}</button>
              : <a key={h.title} className={cls} href={h.href} target="_blank" rel="noreferrer">{body}</a>
          })}
        </CardContent>
      </Card>

      <Card>
        <CardHeader><CardTitle className="flex items-center gap-2"><MessageSquare className="size-5" />連結</CardTitle></CardHeader>
        <CardContent className="grid grid-cols-2 gap-2">
          {LINKS.map((l) => (
            <Button key={l.label} variant="secondary" className="h-11" asChild>
              <a href={l.href} target="_blank" rel="noreferrer">{l.label}</a>
            </Button>
          ))}
          <p className="col-span-2 pt-2 text-center text-xs text-muted-foreground break-all">{REPO.replace("https://", "")}</p>
        </CardContent>
      </Card>
    </div>
  )
}
