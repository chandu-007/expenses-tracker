# App-specific R8 rules. Add keep rules here as libraries that need them are introduced.

# PDF statements (PDFBox-Android). Its JPEG 2000 image filter refers to an optional decoder library that is not included, so PDFs whose pages are JPEG 2000
# pictures are not read as text by it (they are drawn by the system's PdfRenderer and recognised instead). Nothing else needs a rule: PDFBox loads its fonts
# and tables from the library's assets, and ML Kit (statement photos) brings its own rules.
-dontwarn com.gemalto.jp2.**

# ML Kit text recognition (bill photos and picture statements). With R8's optimising rules, merged lambda classes in the library lost a field and every
# recognition ended in a NullPointerException inside the release app ("Attempt to read from field ... on a null object reference"), while the debug build
# worked. The library is kept as it ships: it is already obfuscated by Google, so this costs a little size and nothing else.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-keep class com.google.android.gms.tasks.** { *; }

# Home-screen widgets (Glance). A tap on the widget's eye, a quick-entry chip or Undo runs an ActionCallback that Glance creates BY NAME:
# Class.forName(name).getDeclaredConstructor().newInstance(). Glance's own rule keeps the classes but, with R8's full mode, not their constructors, and nothing in the
# app calls them, so R8 deleted them: every tap on the release build ended in `NoSuchMethodException: <init> []` in the log and did nothing on the screen, while the
# debug build and every instrumented test worked. The constructors are therefore kept for every callback, present and future.
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }
# Glance tells widgets apart by their class name (GlanceAppWidgetManager.getGlanceIds(javaClass), updateAll). R8 merged TodayWidget into QuickAddWidget (one class, one name, a hidden
# class-id field), so an update of one asked Glance to redraw the slots of both with the wrong widget. Keeping every GlanceAppWidget as its own class stops the merge.
-keep class * extends androidx.glance.appwidget.GlanceAppWidget { <init>(); }
