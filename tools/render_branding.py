"""Render the optional branding PNG. Requires Pillow; not an Android build dependency."""
from pathlib import Path
from PIL import Image, ImageDraw
size = 2048
scale = size / 108
image = Image.new("RGB", (size, size), "#14213D")
draw = ImageDraw.Draw(image)
draw.ellipse(tuple(int(v * scale) for v in (22, 22, 86, 86)), fill="#10B981")
draw.polygon([(int(x * scale), int(y * scale)) for x, y in [(31,61),(43,49),(54,57),(74,35),(79,40),(55,68),(44,59),(36,67)]], fill="#14213D")
def quadratic(start, control, end):
    return [((1-t)**2*start[0]+2*(1-t)*t*control[0]+t*t*end[0], (1-t)**2*start[1]+2*(1-t)*t*control[1]+t*t*end[1]) for t in [i/100 for i in range(101)]]
arc = quadratic((31,37),(53,17),(76,37)) + quadratic((76,37),(50,29),(31,37))
draw.polygon([(int(x*scale),int(y*scale)) for x,y in arc], fill="#93DFC2")
image.resize((1024,1024), Image.Resampling.LANCZOS).save(Path(__file__).resolve().parents[1]/"branding"/"budgetsphere-1024.png")
