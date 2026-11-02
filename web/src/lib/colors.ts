// 每個人一個固定顏色:小孩照設定裡的順序,「全家」接在小孩後面。
// 深色底(卡片 #171717)用的類別色,順序照 dataviz 驗證過的色盲安全順序;
// 兩個小孩 + 全家(前三色)兩兩都分得開。
export const SERIES = ["#3987e5", "#d95926", "#199e70", "#c98500", "#d55181", "#008300", "#9085e9"]
export const FAMILY = ""

/** 名字 → 顏色;不在設定裡的舊名字排在全家後面 */
export function seriesOrder(kids: string[], extra: string[] = []) {
  const names = [...kids, FAMILY, ...extra.filter((n) => n !== FAMILY && !kids.includes(n))]
  return names.map((name, i) => ({ name, label: name === FAMILY ? "全家" : name, color: SERIES[Math.min(i, SERIES.length - 1)] }))
}

export const colorOf = (kids: string[], name: string) =>
  seriesOrder(kids).find((s) => s.name === name)?.color ?? SERIES[SERIES.length - 1]
