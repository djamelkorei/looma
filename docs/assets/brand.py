#!/usr/bin/env python3
# Regenerates the brand files from one place:
#   python3 -m venv .venv && .venv/bin/pip install fonttools
#   .venv/bin/python docs/assets/brand.py . path/to/IBMPlexSans-SemiBold.ttf
# Writes docs/assets/{logo,logo-header,wordmark,wordmark-on-dark}.svg, the IntelliJ plugin icons and its 16 px file icons.
# favicon-32.png and apple-touch-icon.png are renders of logo.svg (the touch icon without rounded corners).
"""Generates Looma's brand SVGs: the woven mark, the wordmark (text outlined from IBM Plex Sans SemiBold),
the IntelliJ plugin icons and a full-bleed tile for the Apple touch icon."""
import sys, pathlib
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen

repo, font_path = pathlib.Path(sys.argv[1]), sys.argv[2]
INDIGO, INDIGO_DARK_IDE, SAFFRON, CREAM, INK = "#232D73", "#3B4CB0", "#F2A83B", "#F4F2EC", "#1B2150"

def bar(x1, x2, y, t, round_start, round_end, vertical=False):
    """One thread segment from x1 to x2 (thickness t at y), round where it ends and square where it dives under."""
    r = t / 2
    a, b = x1 + (r if round_start else 0), x2 - (r if round_end else 0)
    d = f"M{a:g},{y:g} H{b:g}"
    d += f" A{r:g},{r:g} 0 0 1 {b:g},{y + t:g}" if round_end else f" V{y + t:g}"
    d += f" H{a:g}"
    d += f" A{r:g},{r:g} 0 0 1 {a:g},{y:g}" if round_start else f" V{y:g}"
    d += " Z"
    if vertical:  # mirror across the diagonal: swap x and y in every coordinate pair
        import re
        def swap(m):
            nums = m.group(0).split(",")
            return f"{nums[1]},{nums[0]}"
        d = re.sub(r"-?[\d.]+,-?[\d.]+", swap, d)
        d = d.replace("H", "\0").replace("V", "H").replace("\0", "V")
        d = d.replace(" 0 0 1 ", " 0 0 0 ")  # mirroring flips the arc direction
    return d

def mark_shapes(tile, rx=14):
    """The mark: two cream weft threads and two saffron warp threads on a flat indigo tile. The weft passes over the
    warp at two of the four crossings (top-right and bottom-left), which is all it takes to read as a weave."""
    s = [f'<rect width="64" height="64" rx="{rx}" fill="{tile}"/>']
    s += [f'<rect x="10" y="{y}" width="44" height="8" rx="4" fill="{CREAM}"/>' for y in (18, 38)]
    s += [f'<rect x="{x}" y="10" width="8" height="44" rx="4" fill="{SAFFRON}"/>' for x in (18, 38)]
    s += [f'<rect x="{x}" y="{y}" width="11" height="8" fill="{CREAM}"/>' for x, y in ((36.5, 18), (16.5, 38))]
    return s

def svg(view_w, size_attr, body, extra_head=""):
    inner = "\n  ".join(body)
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {view_w} 64" {size_attr} role="img" aria-label="Looma">\n'
            f'  <title>Looma</title>{extra_head}\n  {inner}\n</svg>\n')

def word_path(text="Looma", x0=82.0, baseline=46.0, size=42.0, tracking=-1.0):
    font = TTFont(font_path)
    upm, cmap, glyphs, hmtx = font["head"].unitsPerEm, font.getBestCmap(), font.getGlyphSet(), font["hmtx"]
    scale, x, d = size / upm, x0, []
    for ch in text:
        name = cmap[ord(ch)]
        pen = SVGPathPen(glyphs, ntos=lambda v: f"{v:.2f}".rstrip("0").rstrip("."))
        glyphs[name].draw(TransformPen(pen, (scale, 0, 0, -scale, x, baseline)))
        d.append(pen.getCommands())
        x += hmtx[name][0] * scale + tracking
    return " ".join(d), x

out = repo / "docs" / "assets"
open(out / "logo.svg", "w").write(svg(64, 'width="64" height="64"', mark_shapes(INDIGO)))
# The docs header is the same indigo as the tile, so there the threads sit straight on the bar (flat, no tile).
header = mark_shapes(INDIGO)[1:]
open(out / "logo-header.svg", "w").write(
    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="8 8 48 48" width="48" height="48" role="img" aria-label="Looma">\n'
    '  <title>Looma</title>\n  ' + "\n  ".join(header) + "\n</svg>\n")

path, end = word_path()
width = round(end + 6)
open(out / "wordmark.svg", "w").write(svg(width, f'width="{width}" height="64"', mark_shapes(INDIGO) + [f'<path fill="{INK}" d="{path}"/>']))
open(out / "wordmark-on-dark.svg", "w").write(svg(width, f'width="{width}" height="64"', mark_shapes(INDIGO) + [f'<path fill="{CREAM}" d="{path}"/>']))

icons = repo / "looma-intellij" / "src" / "main" / "resources" / "META-INF"
open(icons / "pluginIcon.svg", "w").write(svg(64, 'width="40" height="40"', mark_shapes(INDIGO)))
open(icons / "pluginIcon_dark.svg", "w").write(svg(64, 'width="40" height="40"', mark_shapes(INDIGO_DARK_IDE)))
def file_icon(tile):
    """The 16 px file icon: the same mark on the pixel grid."""
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 16 16">\n'
            f'  <rect x="1" y="1" width="14" height="14" rx="3" fill="{tile}"/>\n'
            f'  <rect x="3" y="5" width="10" height="2" rx="1" fill="{CREAM}"/>\n'
            f'  <rect x="3" y="9" width="10" height="2" rx="1" fill="{CREAM}"/>\n'
            f'  <rect x="5" y="3" width="2" height="10" rx="1" fill="{SAFFRON}"/>\n'
            f'  <rect x="9" y="3" width="2" height="10" rx="1" fill="{SAFFRON}"/>\n'
            f'  <rect x="8.5" y="5" width="3" height="2" fill="{CREAM}"/>\n'
            f'  <rect x="4.5" y="9" width="3" height="2" fill="{CREAM}"/>\n</svg>\n')

file_icons = repo / "looma-intellij" / "src" / "main" / "resources" / "icons"
open(file_icons / "looma.svg", "w").write(file_icon(INDIGO))
open(file_icons / "looma_dark.svg", "w").write(file_icon("#3B4AA8"))
print("written; wordmark width", width)
