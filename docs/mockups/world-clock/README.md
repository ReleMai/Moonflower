# MoonFlower World Clock Mockups

These concept renders use the live top-HUD screenshot for scale and the existing
portrait dock as the visual reference. They are review assets, not integrated
client resources yet.

## Concept A - Four Seasons Orrery

File: `concept-a-four-seasons-orrery.png`

- Strongest immediate clock read.
- Spring, summer, autumn, and winter occupy four equal quadrants.
- Wide side plaques provide the most room for date, countdown, moon, and area.
- Tradeoff: the widest option and potentially too dominant at low resolutions.

## Concept B - Sun-and-Moon Astrolabe

File: `concept-b-sun-moon-astrolabe.png`

- Strongest sunrise, sunset, moon, and astronomy presentation.
- Seasonal state uses four compact cardinal medallions.
- Closest to a substantial fantasy mantle clock.
- Tradeoff: the tallest option and the most mechanical rather than botanical.

## Concept C - Living MoonFlower Almanac Clock

File: `concept-c-living-almanac-clock.png`

- Closest match to the portrait's botanical silhouette and materials.
- Large seasonal relief panels make winter, spring/summer, and autumn readable.
- Three compact lower plaques keep the data subordinate to the clock.
- Tradeoff: the seasonal composition may need clearer four-way separation in
  the production pass.

## Recommended Direction

Use Concept C's shallow botanical silhouette and plaque layout with Concept A's
unambiguous four-quadrant seasonal wheel. Retain Concept B's separate animated
sun-and-moon orbit as the inner ring around the clock face.

## Concept D - Inverted Seasonal Hybrid

File: `concept-d-inverted-seasonal-hybrid.png`

- Selected hybrid of Concept C's botanical frame and Concept A's four-season
  wheel.
- Uses a strict horizontal mounting rail so the HUD sits flush against the top
  screen edge.
- The clock, seasonal relief, plaques, flowers, and vines all hang downward into
  the game view; nothing protrudes above the rail.
- This is the preferred direction for the next production pass.
- Production implementation uses
  `client/src/haven/hud/moonflower-clock-inverted-hybrid-v1-alpha.png`, extracted
  to genuine RGBA while retaining client-rendered sky, hands, highlights, and
  text.

## Production Notes

- The generated review PNGs are opaque RGB concept renders. The checkerboard is
  baked into the preview files; it is not genuine transparency.
- Files ending in `-alpha.png` are failed extraction attempts and are not
  production assets. They remain untracked and should not be integrated or
  committed.
- After selection, produce a transparent static frame plus separate transparent
  seasonal overlays and aperture masks for the animated sky, clock hands, and
  active-season highlight.
- All live text should remain client-rendered. Do not bake time, date, countdown,
  moon phase, area, or guidance text into the artwork.
