"""Turns the pixel-art icon image into Android launcher icons at all densities.
The generated artwork has white rounded corners; they are filled with the
icon's own navy background so the icon looks right at any size."""
import os
import sys

from PIL import Image

src = sys.argv[1]
img = Image.open(src).convert('RGB')
w, h = img.size
navy = img.getpixel((w // 2, 15))
px = img.load()
for y in range(h):
    for x in range(w):
        r, g, b = px[x, y]
        if r > 235 and g > 235 and b > 235:
            px[x, y] = navy

base = 'app/src/main/res'
sizes = {
    'mipmap-mdpi': 48,
    'mipmap-hdpi': 72,
    'mipmap-xhdpi': 96,
    'mipmap-xxhdpi': 144,
    'mipmap-xxxhdpi': 192,
}
for folder, s in sizes.items():
    d = os.path.join(base, folder)
    os.makedirs(d, exist_ok=True)
    out = img.resize((s, s), Image.NEAREST)
    out.save(os.path.join(d, 'ic_launcher.png'))
    out.save(os.path.join(d, 'ic_launcher_round.png'))
print('icons done')
