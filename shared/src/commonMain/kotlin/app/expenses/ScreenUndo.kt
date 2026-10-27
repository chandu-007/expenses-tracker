package app.expenses

/**
 * "Skipped X · Undo" on a screen other than Home's list (To review, Bills): [id] is what was skipped (the item to bring back), [text] what the bar says.
 * It holds no amounts, so nothing in it needs hiding when amounts are hidden.
 */
data class ScreenUndo(val id: String, val text: String)
