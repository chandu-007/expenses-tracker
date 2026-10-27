package app.expenses.core.storage

/** The portable implementation: Kotlin/Native compiles it to machine code, so it does not need the help the JVM-based phones do. */
internal actual fun newSha256Engine(): Sha256Engine = PureSha256()
