package app.expenses

/** A build for a real iPhone: the test entry points ([runningUnderXCTest]) are never reachable in it. */
internal actual val isSimulatorBuild: Boolean = false
