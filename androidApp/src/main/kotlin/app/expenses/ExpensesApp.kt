package app.expenses

import android.app.Application

class ExpensesApp : Application() {
    /** Created on first use, so nothing heavy runs before the first screen. */
    val container: AppContainer by lazy { AppContainer(this) }
}
