package de.wsp3_1.stundenplan.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.wsp3_1.stundenplan.data.SubstitutionRequestDto
import de.wsp3_1.stundenplan.data.SubstitutionRequestStatus

data class SubstitutionUiState(
    val requests: List<SubstitutionRequestDto> = emptyList(),
    val loading: Boolean = false,
    val answeringId: Long? = null,
    val error: String? = null,
    val notice: String? = null,
)

@Composable
fun RowScope.SubstitutionTab(selected: Boolean = false, onClick: () -> Unit) {
    NavigationBarItem(selected = selected, onClick = onClick,
        icon = { Icon(Icons.Default.Email, contentDescription = null) },
        label = { Text("Vertretung", maxLines = 1) })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubstituteScreen(
    state: SubstitutionUiState,
    onDay: () -> Unit,
    onWeek: () -> Unit,
    onAbsence: () -> Unit,
    onSettings: () -> Unit,
    onAnswer: (Long, SubstitutionRequestStatus) -> Unit,
) {
    val busy = state.loading || state.answeringId != null
    Scaffold(
        topBar = { TopAppBar(title = { Text("Vertretungsanfragen") }, actions = {
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Einstellungen") }
        }) },
        bottomBar = { NavigationBar {
            NavigationBarItem(selected = false, onClick = onDay,
                icon = { Icon(Icons.Default.DateRange, null) }, label = { Text("Tag") })
            NavigationBarItem(selected = false, onClick = onWeek,
                icon = { Icon(Icons.Default.DateRange, null) }, label = { Text("Woche") })
            NavigationBarItem(selected = false, onClick = onAbsence,
                icon = { Icon(Icons.Default.Email, null) }, label = { Text("Abwesenheit") })
            SubstitutionTab(selected = true, onClick = {})
        } },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                if (state.loading) CircularProgressIndicator()
                if (state.requests.isNotEmpty()) {
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    state.notice?.let { Text(it) }
                }
            }
            items(state.requests, key = { it.id }) { request ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Vertretungsanfrage #${request.id}", style = MaterialTheme.typography.titleMedium)
                        Text("Ereignis #${request.eventId}")
                        // The API currently supplies only eventId; never infer a lesson from it.
                        Text("Datum: nicht übermittelt\nKlasse: nicht übermittelt\nStunde: nicht übermittelt\nFach: nicht übermittelt")
                        Text("Der Server liefert noch keine Stundendetails zu dieser Anfrage.",
                            style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onAnswer(request.id, SubstitutionRequestStatus.ACCEPTED) }, enabled = !busy) { Text("Annehmen") }
                            OutlinedButton(onClick = { onAnswer(request.id, SubstitutionRequestStatus.DECLINED) }, enabled = !busy) { Text("Ablehnen") }
                        }
                        if (state.answeringId == request.id) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text("Antwort wird gespeichert …")
                        }
                    }
                }
            }
        }
    }
}
