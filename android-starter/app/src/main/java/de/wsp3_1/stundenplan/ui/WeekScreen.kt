package de.wsp3_1.stundenplan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.wsp3_1.stundenplan.R
import de.wsp3_1.stundenplan.data.EntryDto
import de.wsp3_1.stundenplan.data.PlanDates
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val shortDate: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.")

/**
 * Wochenansicht: Matrix Tage x Positionen als LazyVerticalGrid (erste Spalte Position + Uhrzeit,
 * Kopfzeile Mo-Fr; Samstag nur, wenn der Server ihn liefert). Tipp auf Kachel -> Detail,
 * Tipp auf Tageskopf -> Tagesansicht. Pausenzeilen grau.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekScreen(
    uiState: UiState,
    monday: LocalDate,
    today: LocalDate,
    refreshing: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onDay: (LocalDate) -> Unit,
    onRefresh: () -> Unit,
    onEntry: (EntryDto) -> Unit,
    onSettings: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.week_title, monday.format(shortDate))) },
                actions = {
                    TextButton(onClick = { onDay(if (PlanDates.mondayOf(today) == monday) today else monday) }) {
                        Text(stringResource(R.string.week_day))
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.cd_settings))
                    }
                },
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPrev) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.week_prev))
                }
                Text(
                    text = "${monday.format(shortDate)} – ${monday.plusDays(4).format(shortDate)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                IconButton(onClick = onNext) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.week_next))
                }
            }

            when (uiState) {
                UiState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                is UiState.Data -> {
                    if (uiState.offline) OfflineBanner(uiState.loadedAtEpochMillis)
                    if (uiState.changedToday > 0 && PlanDates.mondayOf(today) == monday) {
                        ChangedTodayBanner(uiState.changedToday)
                    }
                    PullToRefreshBox(
                        isRefreshing = refreshing,
                        onRefresh = onRefresh,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        WeekGrid(uiState, today, onDay, onEntry)
                    }
                }

                is UiState.Error -> ErrorBlock(uiState.message, onRetry = onRefresh, onSettings = onSettings)

                UiState.NoServerConfigured, is UiState.LoginRequired ->
                    ErrorBlock(Message.UnreachableNoCache, onRetry = onRefresh, onSettings = onSettings)
            }
        }
    }
}

@Composable
private fun WeekGrid(
    state: UiState.Data,
    today: LocalDate,
    onDay: (LocalDate) -> Unit,
    onEntry: (EntryDto) -> Unit,
) {
    val days = state.week.days
    val positions = WeekView.positions(state.week, state.timegrid)
    val columns = days.size + 1
    val rows = positions.size + 1
    val sunken = MaterialTheme.colorScheme.surfaceVariant

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        items(count = columns * rows) { index ->
            val row = index / columns
            val col = index % columns
            when {
                row == 0 && col == 0 -> Box(Modifier.heightIn(min = 36.dp))

                row == 0 -> {
                    val day = days[col - 1]
                    val date = PlanDates.parse(day.date)
                    val isToday = date == today
                    Column(
                        modifier = Modifier
                            .heightIn(min = 36.dp)
                            .clickable { onDay(date) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = PlanDates.shortGerman(date),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(text = date.format(shortDate), style = MaterialTheme.typography.labelSmall)
                    }
                }

                col == 0 -> {
                    val position = positions[row - 1]
                    Column(
                        modifier = Modifier.heightIn(min = 56.dp).padding(top = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.position_format, position),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = WeekView.rowTime(state.timegrid, position) ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                else -> {
                    val position = positions[row - 1]
                    val day = days[col - 1]
                    val entries = WeekView.entriesAt(day, position)
                    // Pause je Zelle: das Raster darf an jedem Wochentag andere Zeitfenster fuehren.
                    val isBreak = WeekView.isBreak(
                        state.timegrid,
                        PlanDates.weekdayCode(PlanDates.parse(day.date)),
                        position,
                    )
                    Column(
                        modifier = Modifier
                            .heightIn(min = 56.dp)
                            .background(if (isBreak || !day.schoolDay) sunken else MaterialTheme.colorScheme.surface),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        if (isBreak && entries.isEmpty()) {
                            Text(
                                text = stringResource(R.string.week_break),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(4.dp),
                            )
                        }
                        entries.forEach { entry ->
                            EntryTile(entry = entry, onClick = { onEntry(entry) }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}
