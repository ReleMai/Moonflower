# MoonFlower Entry Art v2

This pass adds six original 16:9 environment backgrounds and the packaged
`Moonflower Display` outline font for the MoonFlower login and character-select
screens.

## Background set

| Screen | Runtime asset | Composition intent |
| --- | --- | --- |
| Login | `moonflower-login-v2-01-moonrise-valley.png` | Dark panel-safe lake and mountain valley with a low hearth glow |
| Login | `moonflower-login-v2-02-lantern-fen.png` | Misty wetland boardwalk with quiet center space and lantern accents |
| Login | `moonflower-login-v2-03-starlit-grove.png` | Standing stones, river valley, and moonflowers under a clean night sky |
| Character select | `moonflower-character-v2-01-lakeside-homestead.png` | Dark left roster, open center-right lake, secondary homestead on the right |
| Character select | `moonflower-character-v2-02-dawn-meadow.png` | Dark left grove, soft center-right meadow, distant settlement at dawn |
| Character select | `moonflower-character-v2-03-moonlit-harbor.png` | Dark left dock, open center water, harbor detail held to the right |

High-resolution source renders live in `client/branding/moonflower-screens-v2/`.
The 1067x600 runtime copies live in `client/res/customclient/screens/` because
the entry-screen canvas uses the existing `gfx/loginscr` dimensions. The
loader crops to the target aspect ratio before high-quality resizing, so a
future canvas size does not stretch the scene.

The screen catalog advances one variant each time its screen is opened and
stores only the next index in ordinary client preferences. The catalog is
screen-specific, so login and character select do not consume each other's
rotation.

## Type

`MoonflowerDisplay-Regular.ttf` is a medium-weight, outline-based display cut
derived from the OFL Cormorant Garamond variable font. It is loaded from the
packaged resource directory, scaled through `UI.scale`, and used for
MoonFlower headings and character names. Body/input text remains on the
client's native sans face for maximum legibility and compatibility with
localized strings. If the TTF is missing or invalid, the screen falls back to
the built-in serif without preventing login.
