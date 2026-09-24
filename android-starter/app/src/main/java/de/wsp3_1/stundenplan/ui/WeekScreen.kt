package de.wsp3_1.stundenplan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.wsp3_1.stundenplan.R
import de.wsp3_1.stundenplan.data.EntryDto
import de.wsp3_1.stundenplan.data.PlanDates
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields

private val shortDate: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.")

private val SWIPE_THRESHOLD_DP = 80.dp

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
    isTeacher: Boolean,
    refreshing: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onDay: (LocalDate) -> Unit,
    onAbsence: () -> Unit,
    onRefresh: () -> Unit,
    onEntry: (EntryDto) -> Unit,
    onSettings: () -> Unit,
    onSubstitute: () -> Unit = {},
    onPickDate: (LocalDate) -> Unit,
) {
    var pickerOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "KW ${monday.get(WeekFields.ISO.weekOfWeekBasedYear())}: ${
                            monday.format(
                                shortDate
                            )
                        } - ${monday.plusDays(4).format(shortDate)}",
                        modifier = Modifier
                            .clickable { pickerOpen = true }
                            .padding(vertical = 4.dp),
                    )
                },
                        actions = {
                    //TextButton(onClick = { onDay(if (PlanDates.mondayOf(today) == monday) today else monday) }) {
                    //    Text(stringResource(R.string.week_day))
                    //}
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.cd_settings))
                    }
                },
            )
        },

        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = false,
                    onClick = { onDay(if (PlanDates.mondayOf(today) == monday) today else monday) },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.DateRange,
                            contentDescription = "Tagesansicht"
                        )
                    },
                    label = {
                        Text(
                            text = "Tag",
                            maxLines = 1
                        )
                    }
                )

                NavigationBarItem(
                    selected = true,
                    onClick = { /* Wochenansicht */},
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.DateRange,
                            contentDescription = "Wochenansicht"
                        )
                    },
                    label = {
                        Text(
                            text = "Woche",
                            maxLines = 1
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                if (isTeacher) {
                    NavigationBarItem(
                        selected = false,
                        onClick = { onAbsence() },
                        icon = {
                            Icon(
                                imageVector = Icons.Filled.Email,
                                contentDescription = "Abwesenheiten"
                            )
                        },
                        label = {
                            Text(
                                text = "Abwesenheit",
                            maxLines = 1
                            )
                        }
                    )
                }
                if (isTeacher) SubstitutionTab(onClick = onSubstitute)
            }
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
                TextButton( onClick = { onPickDate(today) }, enabled = PlanDates.mondayOf(today) != monday) {
                    Text(stringResource(R.string.week_today))
                }
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
                        SwipeableWeek(onPrev = onPrev, onNext = onNext) {
                            WeekGrid(uiState, today, onDay, onEntry)
                        }
                    }
                }

                is UiState.Error -> ErrorBlock(uiState.message, onRetry = onRefresh, onSettings = onSettings)

                UiState.NoServerConfigured, is UiState.LoginRequired ->
                    ErrorBlock(Message.UnreachableNoCache, onRetry = onRefresh, onSettings = onSettings)
            }
        }
    }

    if (pickerOpen) {
        DatePickerSheet(
            date = if (PlanDates.mondayOf(today) == monday) today else monday,
            onDismiss = { pickerOpen = false },
            onPick = { picked ->
                pickerOpen = false
                onPickDate(picked)
            }
        )
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
                        Text(
                            text = date.format(shortDate),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
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
                            maxLines = 1,
                            softWrap = false,
                            fontSize = 9.sp,
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
                            EntryTile(
                                entry = entry,
                                onClick = { onEntry(entry) },
                                modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerSheet(date: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = state.selectedDateMillis
                    if (millis != null) {
                        onPick(java.time.Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    } else {
                        onDismiss()
                    }
                }
            ) { Text(stringResource(R.string.day_pick_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.day_pick_cancel)) }
        },
    ) {
        DatePicker(state = state, title = { Text(stringResource(R.string.day_pick_title), modifier = Modifier.padding(16.dp)) })
    }
}

/*
* Funktion, um auch in Wochenansicht wischen zu können
*
*/
@Composable
private fun SwipeableWeek(onPrev: () -> Unit, onNext: () -> Unit, content: @Composable () -> Unit) {
    val threshold = with(LocalDensity.current) { SWIPE_THRESHOLD_DP.toPx() }
    val prev by rememberUpdatedState(onPrev)
    val next by rememberUpdatedState(onNext)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var dragged = 0f
                detectHorizontalDragGestures(
                    onDragStart = {dragged = 0f},
                    onDragCancel = {dragged = 0f},
                    onDragEnd = {
                        val total = dragged
                        dragged = 0f
                        if (total <= -threshold) next() else if (total >= threshold) prev()
                    },
                ) { _, amount -> dragged += amount}
            }
    ) {
        content()
    }
}
