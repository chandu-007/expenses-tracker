# Bundled fonts

| Font | Use | Licence | Source |
|---|---|---|---|
| Inter (400, 500, 600, 700; opsz 14) | text | SIL Open Font License 1.1, `OFL-Inter.txt` | https://github.com/google/fonts/tree/main/ofl/inter (The Inter Project Authors) |
| Space Grotesk (500, 600, 700) | headline figures and screen titles | SIL Open Font License 1.1, `OFL-SpaceGrotesk.txt` | https://github.com/google/fonts/tree/main/ofl/spacegrotesk (The Space Grotesk Project Authors) |

`make_fonts.py` (fontTools: `instancer`, `subset`) made the seven static files from the variable fonts: weights instanced, **subset** to Basic Latin, Latin-1 and Latin Extended-A, general punctuation, the rupee sign (U+20B9), arrows, the multiply and divide signs; and **tabular figures are frozen in**: the digits 0 to 9 in the character map point at the fonts' own tabular glyphs (`tnum`), so every digit is as wide as every other without the app asking for the OpenType feature (the script checks it: one advance width for the ten digits). About 308 KB in all.
They are plain asset files: `androidApp/src/main/assets/fonts/` (Android: `Font(path, assetManager)`) and `iosApp/Resources/Fonts/` (iPhone: read from the app bundle in `IosPlatform`). No font library and no Compose resources artifact.
The OFL allows bundling and redistribution with software; the licence texts stay here. The reserved font names are not used for a renamed derivative: the files keep their names.
