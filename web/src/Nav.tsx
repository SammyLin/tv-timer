import { useEffect, useState } from "react"
import { BarChart3, Download, Home, Info, Menu, Settings, type LucideIcon } from "lucide-react"

import { cn } from "@/lib/utils"
import { Button } from "@/components/ui/button"
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle, SheetTrigger } from "@/components/ui/sheet"

export type Page = "home" | "analytics" | "settings" | "update" | "about"

export const PAGES: { id: Page; label: string; icon: LucideIcon }[] = [
  { id: "home", label: "電視狀態", icon: Home },
  { id: "analytics", label: "觀看分析", icon: BarChart3 },
  { id: "settings", label: "設定", icon: Settings },
  { id: "update", label: "App 更新", icon: Download },
  { id: "about", label: "關於", icon: Info },
]

/** 頁面記在網址 #analytics 這種,手機的返回鍵可以回上一頁 */
export function usePage(): [Page, (p: Page) => void] {
  const read = (): Page => {
    const h = location.hash.replace("#", "") as Page
    return PAGES.some((p) => p.id === h) ? h : "home"
  }
  const [page, setPage] = useState<Page>(read)
  useEffect(() => {
    const on = () => { setPage(read()); window.scrollTo(0, 0) }
    window.addEventListener("hashchange", on)
    return () => window.removeEventListener("hashchange", on)
  }, [])
  const go = (p: Page) => { if (p === page) return; location.hash = p === "home" ? "" : p }
  return [page, go]
}

/** 紅點:有新版時出現在三條線和「App 更新」上 */
export function Dot({ className }: { className?: string }) {
  return <span className={cn("size-2.5 rounded-full bg-red-500 ring-2 ring-background", className)} aria-label="有新版" />
}

export function NavMenu({ page, go, version, hasUpdate }: { page: Page; go: (p: Page) => void; version?: string; hasUpdate: boolean }) {
  const [open, setOpen] = useState(false)
  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetTrigger asChild>
        <Button variant="ghost" size="icon" className="relative -ml-2 size-11" aria-label="選單">
          <Menu className="size-6" />
          {hasUpdate && <Dot className="absolute top-2 right-2" />}
        </Button>
      </SheetTrigger>
      <SheetContent side="left" className="w-72 gap-0 p-0">
        <SheetHeader className="border-b p-5">
          <SheetTitle className="flex items-center gap-3 text-lg">
            <img src="/logo.svg" alt="" className="size-9 rounded-lg" />看電視計時
          </SheetTitle>
          <SheetDescription>{version ? `版本 ${version}` : ""}</SheetDescription>
        </SheetHeader>
        <nav className="flex flex-col gap-1 p-3">
          {PAGES.map((p) => (
            <button key={p.id} onClick={() => { go(p.id); setOpen(false) }}
              className={cn("flex h-12 items-center gap-3 rounded-lg px-3 text-left text-base transition-colors",
                page === p.id ? "bg-accent font-semibold text-accent-foreground" : "text-muted-foreground hover:bg-accent/50")}>
              <p.icon className="size-5" />
              <span className="flex-1">{p.label}</span>
              {p.id === "update" && hasUpdate && <span className="rounded-full bg-red-500 px-2 py-0.5 text-xs font-semibold text-white">新版</span>}
            </button>
          ))}
        </nav>
      </SheetContent>
    </Sheet>
  )
}
