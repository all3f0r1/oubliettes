"""Check the actual VectorDrawables at every join (Python standard library only)."""

import re
import unittest
import xml.etree.ElementTree as ET

from make_tiles import ANDROID, DRAWABLE, PALETTES, assets, xml


def polygons(data):
    """Read the straight, filled paths used by these tiles, without Android or an SDK."""
    tokens = iter(re.findall(r"[A-Za-z]|-?(?:\d*\.\d+|\d+)", data))
    x = y = 0
    points = []
    for command in tokens:
        if command == "M":
            x, y = float(next(tokens)), float(next(tokens))
            points = [(x, y)]
        elif command == "h":
            x += float(next(tokens))
            points.append((x, y))
        elif command == "v":
            y += float(next(tokens))
            points.append((x, y))
        elif command == "l":
            x += float(next(tokens))
            y += float(next(tokens))
            points.append((x, y))
        elif command == "z":
            yield points
            x, y = points[0]
        else:
            raise ValueError(f"Unsupported tile path command: {command}")


def load(name):
    return [
        (polygon, path.get(f"{{{ANDROID}}}fillColor"))
        for path in ET.parse(DRAWABLE / name).getroot()
        for polygon in polygons(path.get(f"{{{ANDROID}}}pathData"))
    ]


def color_at(shapes, x, y):
    for points, color in reversed(shapes):
        inside = False
        for (ax, ay), (bx, by) in zip(points, points[1:] + points[:1]):
            if (ay > y) != (by > y) and x < (bx - ax) * (y - ay) / (by - ay) + ax:
                inside = not inside
        if inside:
            return color
    return None


class TileTest(unittest.TestCase):
    def test_checked_in_assets_are_current(self):
        for name, root in assets():
            with self.subTest(asset=name):
                self.assertEqual((DRAWABLE / name).read_text(), xml(root))

    def test_every_floor_variant_can_join_every_other_variant(self):
        for palette in PALETTES:
            floors = [load(f"floor_{palette}_{i}.xml") for i in range(4)]
            for a in floors:
                for b in floors:
                    for step in range(480):
                        p = (step + 0.5) / 20
                        self.assertEqual(color_at(a, 23.999, p), color_at(b, 0.001, p), palette)
                        self.assertEqual(color_at(a, p, 23.999), color_at(b, p, 0.001), palette)

    def test_all_connected_wall_masks_join(self):
        # Outside corners may end a bevel where an exposed edge meets a neighbour.
        # Along the joined edge itself, neither cell may add a rim or restart its texture.
        for palette in PALETTES:
            walls = [load(f"wall_{palette}_m{i:02d}.xml") for i in range(16)]
            for a in range(16):
                for b in range(16):
                    for step in range(400):
                        p = 2 + (step + 0.5) / 20
                        if a & 2 and b & 8:
                            self.assertEqual(color_at(walls[a], 23.999, p), color_at(walls[b], 0.001, p), (palette, a, b, "east"))
                        if a & 4 and b & 1:
                            self.assertEqual(color_at(walls[a], p, 23.999), color_at(walls[b], p, 0.001), (palette, a, b, "south"))

    def test_tiles_are_opaque(self):
        for name, _ in assets():
            tile = load(name)
            for y in range(24):
                for x in range(24):
                    self.assertIsNotNone(color_at(tile, x + 0.5, y + 0.5), name)


if __name__ == "__main__":
    unittest.main()
