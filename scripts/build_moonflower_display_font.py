#!/usr/bin/env python3
"""Build the redistributable Moonflower Display client font.

The source is an OFL-licensed Cormorant Garamond variable font.  We freeze it
at a medium display weight and rename the family so the client can request a
stable, project-owned face while retaining the source font's broad Latin
coverage and vector outlines.
"""

from __future__ import annotations

import argparse
from pathlib import Path

from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont


FAMILY = "Moonflower Display"
STYLE = "Regular"
UNIQUE_ID = "Moonflower Display Regular; 2026; Codex Project"
POSTSCRIPT_NAME = "MoonflowerDisplay-Regular"
WEIGHT = 520


def set_name(font: TTFont, name_id: int, value: str) -> None:
    """Replace common platform records and add Unicode/Windows records."""
    name_table = font["name"]
    for record in name_table.names:
        if record.nameID == name_id:
            record.string = value.encode(record.getEncoding(), errors="replace")
    name_table.setName(value, name_id, 3, 1, 0x409)
    name_table.setName(value, name_id, 1, 0, 0)


def build(source: Path, destination: Path) -> None:
    font = TTFont(str(source))
    if "fvar" in font:
        font = instantiateVariableFont(font, {"wght": WEIGHT}, inplace=False)

    set_name(font, 1, FAMILY)
    set_name(font, 2, STYLE)
    set_name(font, 3, UNIQUE_ID)
    set_name(font, 4, FAMILY)
    set_name(font, 6, POSTSCRIPT_NAME)
    set_name(font, 16, FAMILY)
    set_name(font, 17, STYLE)

    os2 = font["OS/2"]
    os2.usWeightClass = 500
    os2.usWidthClass = 5
    font["head"].fontRevision = 1.0

    destination.parent.mkdir(parents=True, exist_ok=True)
    font.save(str(destination))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path, help="OFL Cormorant variable TTF")
    parser.add_argument("destination", type=Path, help="Moonflower Display output TTF")
    args = parser.parse_args()
    build(args.source, args.destination)


if __name__ == "__main__":
    main()
