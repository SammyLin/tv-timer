#!/usr/bin/env python3
"""solidify.py RAW OUT [pad]:去綠幕,專給小電用。
key.py 會把薄荷綠身體誤判成半透明、去綠反光時又把身體洗成灰色;這裡改成:
輪廓(補洞)裡面一律不透明、直接用原圖顏色,只有邊緣 3px 做半透明和去綠反光。"""
import sys
import numpy as np
from PIL import Image
from scipy import ndimage
src, dst = sys.argv[1], sys.argv[2]
pad = int(sys.argv[3]) if len(sys.argv) > 3 else 30
im = np.asarray(Image.open(src).convert("RGB")).astype(np.float32)
c = 24
K = np.median(np.concatenate([im[:c, :c].reshape(-1, 3), im[:c, -c:].reshape(-1, 3), im[-c:, :c].reshape(-1, 3), im[-c:, -c:].reshape(-1, 3)]), axis=0)
d = np.sqrt(((im - K) ** 2).sum(-1))
a = np.clip((d - 60) / 100, 0, 1)
solid = a > 0.5
lab, n = ndimage.label(solid)
if n:
    sizes = ndimage.sum(solid, lab, range(1, n + 1))
    solid = np.isin(lab, [i + 1 for i, s in enumerate(sizes) if s >= 150])
body = ndimage.binary_fill_holes(ndimage.binary_closing(solid, iterations=6))
inner = ndimage.binary_erosion(body, iterations=3)
edge = ndimage.binary_dilation(body, iterations=2) & ~inner
a = np.where(inner, 1.0, np.where(edge, a, 0.0))
rgb = im.copy()
despill = np.minimum(rgb[..., 1], rgb[..., [0, 2]].max(-1))
rgb[..., 1] = np.where(edge, despill, rgb[..., 1])        # 只有邊緣去綠反光
out = np.dstack([rgb, a * 255]).clip(0, 255).astype(np.uint8)
ys, xs = np.where(a > 0.02)
out = out[ys.min():ys.max() + 1, xs.min():xs.max() + 1]
o = Image.new("RGBA", (out.shape[1] + 2 * pad, out.shape[0] + 2 * pad), (0, 0, 0, 0))
o.paste(Image.fromarray(out), (pad, pad))
o.save(dst)
print(dst, o.size)
