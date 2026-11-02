#!/usr/bin/env python3
"""key.py IN OUT [pad]  - chroma-key flat background (sampled from corners), unmix edges, despill, keep main components, trim+pad."""
import sys, numpy as np
from PIL import Image
from scipy import ndimage
src, dst = sys.argv[1], sys.argv[2]
pad = int(sys.argv[3]) if len(sys.argv) > 3 else 40
MINC = int(sys.argv[5]) if len(sys.argv) > 5 else 150
mode = sys.argv[4] if len(sys.argv) > 4 else 'edge'
im = np.asarray(Image.open(src).convert('RGB')).astype(np.float32)
h, w, _ = im.shape
c = 24
K = np.median(np.concatenate([im[:c,:c].reshape(-1,3), im[:c,-c:].reshape(-1,3), im[-c:,:c].reshape(-1,3), im[-c:,-c:].reshape(-1,3)]), axis=0)
d = np.sqrt(((im - K) ** 2).sum(-1))
t0, t1 = 60.0, 160.0
a = np.clip((d - t0) / (t1 - t0), 0, 1)
# keep components connected to substantial regions (drop specks)
solid = a > 0.5
lab, n = ndimage.label(solid)
if n:
    sizes = ndimage.sum(solid, lab, range(1, n + 1))
    keep = np.isin(lab, [i + 1 for i, s in enumerate(sizes) if s >= MINC])
    keep = ndimage.binary_dilation(keep, iterations=4)
    a = a * keep
# edge colour: take colour from nearest fully-opaque interior pixel (avoids key-coloured/dark fringes)
interior = ndimage.binary_erosion(a > 0.99, iterations=2)
_, (iy, ix) = ndimage.distance_transform_edt(~interior, return_indices=True)
F = np.where((a < 0.99)[..., None] | ~interior[..., None], im[iy, ix], im)
# despill toward key colour. mode 'all' = whole image (subject has no key-like colours), 'edge' = 12px band only
band = ndimage.binary_dilation(~interior, iterations=12) if mode == 'edge' else np.ones(a.shape, bool)
if K[1] > 150 and K[0] < 120 and K[2] < 120:      # green key: G <= max(R,B)
    F[..., 1] = np.where(band, np.minimum(F[..., 1], F[..., [0, 2]].max(-1)), F[..., 1])
elif K[0] > 150 and K[2] > 150 and K[1] < 120:    # magenta key: R,B excess over G gets reduced
    spill = np.clip(np.minimum(F[..., 0], F[..., 2]) - F[..., 1], 0, None)
    for ch in (0, 2):
        F[..., ch] = np.where(band, F[..., ch] - spill, F[..., ch])
# choke matte ~1px and firm it up
a = ndimage.grey_erosion(a, size=(3, 3))
a = np.clip((a - 0.08) / 0.84, 0, 1)
a8 = (a * 255).round().astype(np.uint8)
out = np.dstack([F.round().astype(np.uint8), a8])
ys, xs = np.where(a8 > 8)
y0, y1, x0, x1 = ys.min(), ys.max() + 1, xs.min(), xs.max() + 1
out = out[y0:y1, x0:x1]
oh, ow = out.shape[:2]
canvas = np.zeros((oh + 2 * pad, ow + 2 * pad, 4), np.uint8)
canvas[pad:pad + oh, pad:pad + ow] = out
canvas[canvas[..., 3] == 0, :3] = 0
Image.fromarray(canvas, 'RGBA').save(dst, optimize=True)
print(dst, canvas.shape[1], 'x', canvas.shape[0], 'key', K.round())
