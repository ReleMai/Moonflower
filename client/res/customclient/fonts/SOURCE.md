# Moonflower Display

`MoonflowerDisplay-Regular.ttf` is the client display cut used by the
MoonFlower entry screens. It is an outline font, so Java2D can derive the
requested size at runtime instead of scaling a bitmap glyph sheet.

The file is derived from the Cormorant Garamond variable font at weight 520,
with the family/style/unique-name records changed to `Moonflower Display` and
the package metadata set to a medium display weight. The source family is
distributed under the SIL Open Font License 1.1; the complete license is in
`OFL.txt` beside this file.

Source:

<https://github.com/google/fonts/tree/main/ofl/cormorantgaramond>

Rebuild with `scripts/build_moonflower_display_font.py` and the original
`CormorantGaramond[wght].ttf` source.
