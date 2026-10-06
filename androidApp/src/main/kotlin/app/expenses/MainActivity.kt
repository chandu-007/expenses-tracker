package app.expenses

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Text("Expenses · ${Money.zero(Currency.INR).minorUnits}")
            }
        }
    }
}
