package de.wsp3_1.stundenplan.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.wsp3_1.stundenplan.data.SubstitutionRequestDto
import de.wsp3_1.stundenplan.data.SubstitutionRequestStatus
import de.wsp3_1.stundenplan.data.TimegridDto
import de.wsp3_1.stundenplan.data.PlanDates

data class SubstitutionUiState(
    val requests: List<SubstitutionRequestDto> = emptyList(),
    val loading: Boolean = false,
    val answeringId: Long? = null,
    val error: String? = null,
    val notice: String? = null,
    val timegrid: TimegridDto? = null,
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
    var expandedRequestId by rememberSaveable { mutableStateOf<Long?>(null) }
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
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                if (state.requests.isNotEmpty()) {
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            items(state.requests, key = { it.id }) { request ->
                val expanded = expandedRequestId == request.id
                Card(
                    modifier = Modifier.fillMaxWidth().animateContentSize().clickable(
                        enabled = !busy,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { expandedRequestId = if (expanded) null else request.id },
                    ).semantics {
                        stateDescription = if (expanded) "Ausgeklappt" else "Eingeklappt"
                    },
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(substitutionDateText(request.date), style = MaterialTheme.typography.titleMedium)
                        Text("${substitutionWeekdayText(request.date)} · ${substitutionLessonText(state.timegrid, request.date, request.position)}")
                        Text("${request.classShort} · ${request.subjectShort} für ${request.originalTeacherShort}")
                        if (expanded) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onAnswer(request.id, SubstitutionRequestStatus.DECLINED) },
                                enabled = !busy,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E), contentColor = Color.White),
                            ) { Text("Ablehnen") }
                            Button(
                                onClick = { onAnswer(request.id, SubstitutionRequestStatus.ACCEPTED) },
                                enabled = !busy,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF256B35), contentColor = Color.White),
                            ) { Text("Annehmen") }
                        }
                        }
                        if (state.answeringId == request.id) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text("Antwort wird gespeichert …")
                        }
                    }
                }
            }
        }
        if (state.loading) CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}

internal fun substitutionDateText(date: String): String = try {
    LocalDate.parse(date).format(DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN))
} catch (_: java.time.format.DateTimeParseException) {
    date
}

internal fun substitutionWeekdayText(date: String): String = try {
    LocalDate.parse(date).format(DateTimeFormatter.ofPattern("EEEE", Locale.GERMAN))
} catch (_: java.time.format.DateTimeParseException) {
    "Wochentag unbekannt"
}

/** Positionen enthalten Pausen; nur Unterrichtszeitfenster am betreffenden Tag zählen. */
internal fun substitutionLessonText(timegrid: TimegridDto?, date: String, position: Int): String {
    val day = try {
        PlanDates.weekdayCode(LocalDate.parse(date))
    } catch (_: java.time.format.DateTimeParseException) {
        return "Stunde unbekannt"
    }
    val slots = timegrid?.slots.orEmpty().filter { it.day == day }.sortedBy { it.position }
    val slot = slots.firstOrNull { it.position == position } ?: return "Stunde unbekannt"
    if (slot.isBreak) return "Pause"
    val number = slots.count { !it.isBreak && it.position <= position }
    return "$number. Stunde"
}
