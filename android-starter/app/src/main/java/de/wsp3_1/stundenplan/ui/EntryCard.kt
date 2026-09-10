package de.wsp3_1.stundenplan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import de.wsp3_1.stundenplan.R
import de.wsp3_1.stundenplan.data.EntryDto
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Wortlaut der Textlabel (Farbe ist nie der einzige Traeger). */
@Composable
fun entryLabelText(label: EntryLabel): String = when (label) {
    EntryLabel.Substitution -> stringResource(R.string.label_substitution)
    EntryLabel.RoomChange -> stringResource(R.string.label_room_change)
    EntryLabel.NeedsSubstitution -> stringResource(R.string.label_needs_substitution)
    is EntryLabel.GivenAway ->
        label.toTeacherShort?.let { stringResource(R.string.label_given_away, it) }
            ?: stringResource(R.string.label_given_away_plain)
    EntryLabel.Cancelled -> stringResource(R.string.label_cancelled)
    is EntryLabel.CancelledMoved -> label.serverText
    is EntryLabel.Moved ->
        label.from?.let { stringResource(R.string.label_moved_from, it) } ?: stringResource(R.string.label_moved)
    EntryLabel.Added -> stringResource(R.string.label_added)
}

/** Rahmen in Bedeutungsfarbe, durchgezogen oder gestrichelt - ueber dem Inhalt, innerhalb der Kante. */
private fun Modifier.toneFrame(color: Color?, dashed: Boolean, cornerDp: Float): Modifier {
    if (color == null) return this
    return drawWithContent {
        drawContent()
        val stroke = 2.dp.toPx()
        val effect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(10f, 6f)) else null
        drawRoundRect(
            color = color,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(size.width - stroke, size.height - stroke),
            cornerRadius = CornerRadius(cornerDp.dp.toPx()),
            style = Stroke(width = stroke, pathEffect = effect),
        )
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Karte der Tagesansicht: Fach · Klasse · Raum, Farbbalken in Fachfarbe, Rahmen und Label je Art. */
@Composable
fun EntryCard(
    entry: EntryDto,
    timeText: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui = entry.kindUi()
    val frame = toneColor(ui.tone)
    val subject = parseHexColor(entry.subjectColor, MaterialTheme.colorScheme.primary)
    val shape = RoundedCornerShape(10.dp)
    val decoration = if (ui.strike) TextDecoration.LineThrough else TextDecoration.None
    val fontStyle = if (ui.dim) FontStyle.Italic else FontStyle.Normal

    Column(modifier = modifier.alpha(if (ui.dim) 0.55f else 1f)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(subject.copy(alpha = 0.18f))
                .toneFrame(frame, ui.dashed, 10f)
                .clickable(onClick = onClick),
        ) {
            Row(modifier = Modifier.height(72.dp)) {
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .fillMaxHeight()
                        .background(subject),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = entry.subjectShort,
                        style = MaterialTheme.typography.titleMedium,
                        textDecoration = decoration,
                        fontStyle = fontStyle,
                    )
                    Text(
                        text = entry.classShort,
                        style = MaterialTheme.typography.bodyMedium,
                        textDecoration = decoration,
                        fontStyle = fontStyle,
                    )
                    Text(
                        text = entry.roomShort ?: stringResource(R.string.time_unknown),
                        style = MaterialTheme.typography.bodySmall,
                        textDecoration = decoration,
                        fontStyle = fontStyle,
                    )
                }
                Column(
                    modifier = Modifier.padding(8.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    val badge = ui.badge
                    if (badge != null && frame != null) Badge(badge, frame)
                    Text(
                        text = timeText ?: stringResource(R.string.time_unknown),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        val label = ui.label
        if (label != null) {
            Text(
                text = entryLabelText(label),
                style = MaterialTheme.typography.labelMedium,
                color = toneTextColor(ui.tone),
                fontStyle = fontStyle,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
            )
        }
    }
}

/** Kachel der Wochenmatrix: Kuerzel (Fach fett, Klasse, Raum) mit derselben Farblogik. */
@Composable
fun EntryTile(
    entry: EntryDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui = entry.kindUi()
    val frame = toneColor(ui.tone)
    val subject = parseHexColor(entry.subjectColor, MaterialTheme.colorScheme.primary)
    val shape = RoundedCornerShape(6.dp)
    val decoration = if (ui.strike) TextDecoration.LineThrough else TextDecoration.None
    val fontStyle = if (ui.dim) FontStyle.Italic else FontStyle.Normal

    Box(
        modifier = modifier
            .alpha(if (ui.dim) 0.55f else 1f)
            .clip(shape)
            .background(subject.copy(alpha = 0.18f))
            .toneFrame(frame, ui.dashed, 6f)
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Column {
            Text(
                text = entry.subjectShort,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                textDecoration = decoration,
                fontStyle = fontStyle,
                maxLines = 1,
            )
            Text(
                text = entry.classShort,
                style = MaterialTheme.typography.labelSmall,
                textDecoration = decoration,
                fontStyle = fontStyle,
                maxLines = 1,
            )
            Text(
                text = entry.roomShort ?: "",
                style = MaterialTheme.typography.labelSmall,
                textDecoration = decoration,
                fontStyle = fontStyle,
                maxLines = 1,
            )
        }
        val badge = ui.badge
        if (badge != null && frame != null) {
            Box(modifier = Modifier.align(Alignment.TopEnd)) { Badge(badge, frame) }
        }
    }
}

// ---------------------------------------------------------------- Banner und Zustandsbloecke

/** "05.09. 12:15" aus der Geraetezeit des letzten erfolgreichen Abrufs. */
fun formatStand(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("dd.MM. HH:mm"))

@Composable
fun OfflineBanner(loadedAtEpochMillis: Long) {
    val (bg, fg) = offlineBannerColors()
    Surface(color = bg, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.banner_offline, formatStand(loadedAtEpochMillis)),
            color = fg,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
fun ChangedTodayBanner(count: Int) {
    val color = toneColor(Tone.SUBSTITUTION) ?: MaterialTheme.colorScheme.primary
    Surface(color = color.copy(alpha = 0.15f), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = pluralStringResource(R.plurals.banner_changed_today, count, count),
            color = toneTextColor(Tone.SUBSTITUTION),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
fun messageText(message: Message): String = when (message) {
    Message.UnreachableNoCache -> stringResource(R.string.error_unreachable_no_cache)
    Message.NotATeacher -> stringResource(R.string.error_not_teacher)
    is Message.ServerText ->
        message.text?.let { stringResource(R.string.error_server, it) }
            ?: stringResource(R.string.error_server_generic)
}

@Composable
fun ErrorBlock(message: Message, onRetry: () -> Unit, onSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = messageText(message),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onRetry) { Text(stringResource(R.string.error_retry)) }
            OutlinedButton(onClick = onSettings) { Text(stringResource(R.string.error_settings)) }
        }
    }
}
