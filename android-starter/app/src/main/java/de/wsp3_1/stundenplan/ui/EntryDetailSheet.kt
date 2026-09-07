package de.wsp3_1.stundenplan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.wsp3_1.stundenplan.R
import de.wsp3_1.stundenplan.data.EntryDto
import de.wsp3_1.stundenplan.data.EntryKind
import de.wsp3_1.stundenplan.data.PlanDates
import de.wsp3_1.stundenplan.data.TimegridDto
import java.time.format.DateTimeFormatter
import java.util.Locale

private val longDate: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMAN)

/**
 * Stundendetail als Bottom Sheet (A-2, T-A2.1): Fach, Klasse, Raum, Zeit, Art der Aenderung,
 * alle Anlaesse (reasons) und das Original (Lehrkraft / Raum / Termin) bei Aenderungen.
 *
 * Der Vertrag liefert heute nur Kuerzel; das Pflichtenheft erwartet Langnamen ("Mathematik").
 * Sobald der Server `subjectName`, `roomName` und `teacherName` mitschickt (additive Aenderung,
 * offener Punkt in ANLEITUNG.md Abschnitt 3), steht hier der Langname, sonst weiter das Kuerzel.
 *
 * Der Inhalt ist rollbar: viele Anlaesse, grosse Schrift oder Querformat schoeben sonst den
 * Schliessen-Knopf aus dem Bild.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailSheet(
    entry: EntryDto,
    timegrid: TimegridDto?,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val ui = entry.kindUi()
    val date = PlanDates.parse(entry.date)
    val time = WeekView.timeText(timegrid, entry)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = ui.label?.let { entryLabelText(it) } ?: stringResource(R.string.label_regular),
                style = MaterialTheme.typography.titleLarge,
                color = toneTextColor(ui.tone),
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "${date.format(longDate)} · ${stringResource(R.string.position_format, entry.position)}" +
                    (time?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()

            // Langname, wenn der Server ihn liefert; sonst das Kuerzel des Vertrags.
            DetailRow(stringResource(R.string.detail_subject), entry.subjectName ?: entry.subjectShort)
            DetailRow(stringResource(R.string.detail_class), entry.classShort)
            DetailRow(
                stringResource(R.string.detail_room),
                entry.roomName ?: entry.roomShort ?: stringResource(R.string.time_unknown),
            )
            DetailRow(stringResource(R.string.detail_time), time ?: stringResource(R.string.time_unknown))
            DetailRow(
                stringResource(R.string.detail_teacher),
                entry.teacherName ?: entry.teacherShort ?: stringResource(R.string.time_unknown),
            )

            if (ui.changed) {
                HorizontalDivider()
                Text(text = stringResource(R.string.detail_kind), style = MaterialTheme.typography.labelLarge)
                Text(text = ui.label?.let { entryLabelText(it) } ?: "", style = MaterialTheme.typography.bodyMedium)

                OriginalBlock(entry)

                Text(text = stringResource(R.string.detail_reasons), style = MaterialTheme.typography.labelLarge)
                if (entry.reasons.isEmpty()) {
                    Text(text = stringResource(R.string.detail_no_reasons), style = MaterialTheme.typography.bodyMedium)
                } else {
                    entry.reasons.forEach { reason ->
                        Text(text = "• $reason", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.detail_close)) }
            }
        }
    }
}

@Composable
private fun OriginalBlock(entry: EntryDto) {
    val original = entry.original
    val lines = mutableListOf<String>()
    val originalTeacher = original?.teacherShort
    if (originalTeacher != null) {
        lines += when (entry.kind) {
            EntryKind.GIVEN_AWAY -> stringResource(R.string.detail_given_to, entry.teacherShort ?: "")
            EntryKind.SUBSTITUTED -> stringResource(R.string.detail_you_substitute, originalTeacher)
            else -> stringResource(R.string.detail_original_teacher, originalTeacher)
        }
        if (entry.kind == EntryKind.GIVEN_AWAY) {
            lines += stringResource(R.string.detail_original_teacher, originalTeacher)
        }
    }
    val originalRoom = original?.roomShort
    if (originalRoom != null) {
        lines += stringResource(R.string.detail_original_room, "$originalRoom → ${entry.roomShort ?: ""}")
    }
    val originalDate = original?.date
    val originalPosition = original?.position
    if (originalDate != null && originalPosition != null) {
        val text = "${PlanDates.shortGerman(PlanDates.parse(originalDate))} $originalPosition."
        lines += stringResource(R.string.detail_original_slot, text)
    }
    if (lines.isEmpty()) return

    Text(text = stringResource(R.string.detail_original), style = MaterialTheme.typography.labelLarge)
    lines.forEach { Text(text = it, style = MaterialTheme.typography.bodyMedium) }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}
