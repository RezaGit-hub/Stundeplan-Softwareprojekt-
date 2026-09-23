package de.wsp3_1.stundenplan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.wsp3_1.stundenplan.data.ThemeMode
import de.wsp3_1.stundenplan.ui.App
import de.wsp3_1.stundenplan.ui.StundenplanTheme
import de.wsp3_1.stundenplan.ui.ThemePreferences
import de.wsp3_1.stundenplan.ui.TimetableViewModel

/**
 * Einzige Activity der App. Der Screen-Wechsel ist Zustand im ViewModel,
 * es gibt bewusst kein Navigation-Compose.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as StundenplanApp).container
        setContent {
            val viewModel: TimetableViewModel = viewModel(
                factory = TimetableViewModel.factory(container)
            )

            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()

            StundenplanTheme(
                themeMode = themeMode
            ) {
                App(
                    viewModel = viewModel,
                    themeMode = themeMode,
                    onThemeModeChange = viewModel::setThemeMode
                )
            }
        }
    }
}
