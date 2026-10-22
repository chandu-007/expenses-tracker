# App-specific R8 rules. Add keep rules here as libraries that need them are introduced.

# PDF statements (PDFBox-Android). Its JPEG 2000 image filter refers to an optional decoder library that is not included, so PDFs whose pages are JPEG 2000
# pictures are not read as text by it (they are drawn by the system's PdfRenderer and recognised instead). Nothing else needs a rule: PDFBox loads its fonts
# and tables from the library's assets, and ML Kit (statement photos) brings its own rules.
-dontwarn com.gemalto.jp2.**
