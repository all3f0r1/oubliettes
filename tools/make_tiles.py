#!/usr/bin/env python3
"""Build the board's periodic VectorDrawables; no bitmap scaling or external dependencies.

Run from any directory. Stonework wraps on a 24-unit torus. All four floor variants
have the same edges and vary only inside, so the board may mix them freely. Walls
share that periodic texture; their four-bit mask adds relief only on exposed sides.
"""

from pathlib import Path
import xml.etree.ElementTree as ET

ANDROID = "http://schemas.android.com/apk/res/android"
DRAWABLE = Path(__file__).resolve().parents[1] / "app/src/main/res/drawable"
ET.register_namespace("android", ANDROID)

# Existing difficulty palettes: wall, lit wall, shadow, mortar, highlight, floor,
# lit floor, floor mortar.
PALETTES = {
    "dungeon": ("#827D98", "#918BA5", "#5C566F", "#302B3E", "#B6ACC6", "#454052", "#4B455A", "#2B2737"),
    "cistern": ("#717F7D", "#818F89", "#4C6261", "#283C3D", "#ACBBB0", "#354B4A", "#3D5250", "#253736"),
    "crypt": ("#989183", "#A89E8B", "#746D66", "#403B3C", "#C9BEA5", "#4A454A", "#524B4F", "#322E36"),
    "depths": ("#706384", "#817093", "#50445F", "#2D243C", "#AA93B3", "#3E334E", "#483957", "#292035"),
}


def path(root, data, color):
    ET.SubElement(root, "path", {f"{{{ANDROID}}}pathData": data, f"{{{ANDROID}}}fillColor": color})


def rect(root, x, y, width, height, color):
    path(root, f"M{x:g},{y:g}h{width:g}v{height:g}h{-width:g}z", color)


def periodic_rect(root, x, y, width, height, color):
    """Wrap and clip the rectangle, including its lighting, at both texture edges."""
    for dx in (-24, 0, 24):
        for dy in (-24, 0, 24):
            left, top = max(0, x + dx), max(0, y + dy)
            right, bottom = min(24, x + dx + width), min(24, y + dy + height)
            if right > left and bottom > top:
                rect(root, left, top, right - left, bottom - top, color)


def vector():
    return ET.Element("vector", {f"{{{ANDROID}}}{key}": value for key, value in {
        "width": "24dp", "height": "24dp", "viewportWidth": "24", "viewportHeight": "24",
    }.items()})


def wall(palette, mask):
    base, lit, shadow, mortar, highlight, *_ = palette
    root = vector()
    rect(root, 0, 0, 24, 24, mortar)
    # The last course crosses the top/bottom boundary. Bricks cross left/right too.
    for row, offset in enumerate((2, 8, 14)):
        y = 4 + row * 8
        for brick in range(2):
            x = offset + brick * 12
            periodic_rect(root, x, y, 11.4, 7.4, lit if (row + brick) % 2 == 0 else base)
            periodic_rect(root, x, y, 11.4, 0.3, highlight)
            periodic_rect(root, x, y + 7, 11.4, 0.4, shadow)
    # A connected edge gets no outline, bevel or cell frame.
    if not mask & 1:
        rect(root, 0, 0, 24, 1.1, highlight)
    if not mask & 2:
        rect(root, 22.9, 0, 1.1, 24, shadow)
    if not mask & 4:
        rect(root, 0, 22.5, 24, 1.5, mortar)
    if not mask & 8:
        rect(root, 0, 0, 1.1, 24, lit)
    path(root, "M10,6l1.5,-.2l-.5,.5zM4,18.8l1.1,-.2v.6z", shadow)
    return root


def floor(palette, variant):
    _, lit_wall, _, _, _, base, lit, mortar = palette
    root = vector()
    rect(root, 0, 0, 24, 24, mortar)
    # Every variant uses these same joints. Rotating a laying independently per cell
    # would break the edge contract, so only the chips and scratches below vary.
    for row, offset in enumerate((8, 16)):
        y = 4 + row * 12
        periodic_rect(root, offset, y, 23.45, 11.45, base)
        periodic_rect(root, offset, y, 11.4, 11.45, lit)
        periodic_rect(root, offset, y, 23.45, 0.18, lit_wall)
    x = 3 + variant * 4
    path(root, f"M{x},7l1.2,-.2l.6,.3l-1.5,.3zM{20 - variant * 3},19l1.2,.1l-.2,.35z", mortar)
    rect(root, x, 10, 2.4, 0.18, lit_wall)
    return root


def assets():
    for name, palette in PALETTES.items():
        for mask in range(16):
            yield f"wall_{name}_m{mask:02d}.xml", wall(palette, mask)
        for variant in range(4):
            yield f"floor_{name}_{variant}.xml", floor(palette, variant)


def xml(root):
    ET.indent(root, space="  ")
    return ET.tostring(root, encoding="unicode", xml_declaration=True) + "\n"


if __name__ == "__main__":
    for name, root in assets():
        (DRAWABLE / name).write_text(xml(root))
