# Bundled fonts

| Font | Use | Licence | Source |
|---|---|---|---|
| Inter (400, 500, 600; opsz 14) | text | SIL Open Font License 1.1, `OFL-Inter.txt` | https://github.com/google/fonts/tree/main/ofl/inter (Inter 4, The Inter Project Authors) |
| Space Grotesk (500, 600) | headline figures and screen titles | SIL Open Font License 1.1, `OFL-SpaceGrotesk.txt` | https://github.com/google/fonts/tree/main/ofl/spacegrotesk (The Space Grotesk Project Authors) |

Both were instanced from the variable fonts to the static weights above and **subset** (fontTools `instancer` and `subset`) to Basic Latin, Latin-1 and Latin Extended-A, general punctuation, the rupee sign (U+20B9), arrows and the multiply/divide signs, keeping the `tnum`, `kern`, `case`, `zero` and `liga` features. Five files, about 240 KB in all.
They are plain asset files: `androidApp/src/main/assets/fonts/` (read with `Font(path, assetManager)`) and `iosApp/Resources/Fonts/` (read from the app bundle in `IosPlatform`). No font library and no Compose resources artifact.
The OFL allows bundling and redistribution with software; the licence texts stay with the fonts here. The names "Inter" and "Space Grotesk" are reserved font names, and these subsets are not renamed derivatives for sale: they are used as they are, subset.
