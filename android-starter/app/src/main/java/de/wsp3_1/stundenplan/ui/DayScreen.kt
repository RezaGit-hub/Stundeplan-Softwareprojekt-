package de.wsp3_1.stundenplan.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.wsp3_1.stundenplan.R
import de.wsp3_1.stundenplan.data.EntryDto
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayTitleFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)

/** Ab dieser Wischstrecke gilt es als Tageswechsel (kurze Wische bleiben folgenlos). */
private val SWIPE_THRESHOLD_DP = 80.dp

/**
 * Tagesansicht (Startscreen, A-1, T-A1.1): Kopf mit Datum, Pfeile, "Heute"; Banner-Zone;
 * Liste der eigenen Stunden mit Position, Uhrzeit, Fach, Klasse, Raum und Markierung je Art.
 *
 * Drei Wege zu einem anderen Tag: Pfeile, Wischen nach links/rechts ueber der Liste und Tippen auf
 * das Datum im Kopf (Kalender). Der Kalender ist der kurze Weg zu einem weit entfernten Datum -
 * T-A1.1 und T-A2.1 verlangen die Tagesansicht auf einem festen Tag, und dorthin will niemand
 * sechzehnmal auf einen Pfeil tippen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayScreen(
    uiState: UiState,
    date: LocalDate,
    isTeacher: Boolean,
    today: LocalDate,
    refreshing: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onWeek: () -> Unit,
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
                        text = date.format(dayTitleFormat),
                        maxLines = 1,
                        modifier = Modifier
                            .clickable { pickerOpen = true }
                            .padding(vertical = 4.dp),
                    )
                },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.cd_settings)
                        )
                    }
                }
            )
        },

        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = true,
                    onClick = { /* Tagesansicht */ },
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
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                NavigationBarItem(
                    selected = false,
                    onClick = { onWeek() },
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
                    }
                )

                if (isTeacher) {

                    /*
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
                     */

                    SubstitutionTab(onClick = onSubstitute)
                }
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
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.day_prev))
                }
                TextButton(onClick = onToday, enabled = date != today) {
                    Text(stringResource(R.string.day_today))
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.day_next))
                }
            }

            when (uiState) {
                UiState.Loading -> CenteredLoading()

                is UiState.Data -> {
                    if (uiState.offline) OfflineBanner(uiState.loadedAtEpochMillis)
                    if (uiState.changedToday > 0 && date == today) ChangedTodayBanner(uiState.changedToday)
                    PullToRefreshBox(
                        isRefreshing = refreshing,
                        onRefresh = onRefresh,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        SwipeableDay(onPrev = onPrev, onNext = onNext) {
                            DayList(uiState, date, onEntry)
                        }
                    }
                }

                is UiState.Error -> ErrorBlock(uiState.message, onRetry = onRefresh, onSettings = onSettings)

                // Diese beiden werden im Einstellungs-Screen gerendert; hier nur zur Vollstaendigkeit.
                UiState.NoServerConfigured, is UiState.LoginRequired ->
                    ErrorBlock(Message.UnreachableNoCache, onRetry = onRefresh, onSettings = onSettings)
            }
        }
    }

    if (pickerOpen) {
        DatePickerSheet(
            date = date,
            onDismiss = { pickerOpen = false },
            onPick = { picked ->
                pickerOpen = false
                onPickDate(picked)
            },
        )
    }
}

/**
 * Kalender zum Springen auf ein Datum. Der Zustand des Material-Kalenders rechnet in
 * Millisekunden seit der Epoche **in UTC**; deshalb wird hier ueber [ZoneOffset.UTC] gerechnet -
 * eine lokale Zeitzone wuerde den Tag um eins verschieben.
 */
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

/**
 * Wischen nach links = naechster Tag, nach rechts = voriger (android.md: "Pfeil/Swipe").
 * Der Erkenner sitzt **um** die Liste: senkrechtes Ziehen verbraucht die Liste selbst
 * (Bildlauf, Ziehen zum Aktualisieren), waagerechtes kommt hier an.
 */
@Composable
private fun SwipeableDay(onPrev: () -> Unit, onNext: () -> Unit, content: @Composable () -> Unit) {
    val threshold = with(LocalDensity.current) { SWIPE_THRESHOLD_DP.toPx() }
    // Die Erkennung wird einmal aufgesetzt (pointerInput(Unit)); die aktuellen Rueckrufe holt sie
    // sich ueber rememberUpdatedState, sonst startet jede Neuzeichnung die Gestenerkennung neu.
    val prev by rememberUpdatedState(onPrev)
    val next by rememberUpdatedState(onNext)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // Die Strecke ist eine gewoehnliche Variable, kein Compose-Zustand: sie wird nur
                // innerhalb der Gestenerkennung gelesen, nie beim Zeichnen. Sie lebt in derselben
                // Koroutine wie detectHorizontalDragGestures und uebersteht damit die ganze Geste.
                var dragged = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragged = 0f },
                    onDragCancel = { dragged = 0f },
                    onDragEnd = {
                        val total = dragged
                        dragged = 0f
                        if (total <= -threshold) next() else if (total >= threshold) prev()
                    },
                ) { _, amount -> dragged += amount }
            }
    ) {
        content()
    }
}

@Composable
private fun CenteredLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator()
            Text(stringResource(R.string.loading), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DayList(state: UiState.Data, date: LocalDate, onEntry: (EntryDto) -> Unit) {
    val day = WeekView.dayFor(state.week, date)
    val entries = day?.let { WeekView.sortedEntries(it) }.orEmpty()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (day == null || !day.schoolDay) {
            item { EmptyText(stringResource(R.string.day_no_school)) }
        } else if (entries.isEmpty()) {
            item { EmptyText(stringResource(R.string.day_empty)) }
        } else {
            // Der Schluessel muss eindeutig sein, sonst bricht die Liste ab. Zusatzstunden haben
            // kein lessonId - zwei davon in derselben Klasse und Stunde unterscheiden sich nur
            // ueber die exceptionId der Aenderung.
            items(entries, key = { entryKey(it) }) { entry ->
                Row(verticalAlignment = Alignment.Top) {
                    TimeColumn(position = entry.position, time = WeekView.timeText(state.timegrid, entry))
                    EntryCard(
                        entry = entry,
                        timeText = null,
                        onClick = { onEntry(entry) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Eindeutiger Listenschluessel eines Eintrags. */
internal fun entryKey(entry: EntryDto): String =
    "${entry.timeslotId}-${entry.classId}-${entry.kind}-${entry.lessonId ?: 0}-${entry.exceptionId ?: 0}"

/** Links: Stundennummer gross, Uhrzeit klein. */
@Composable
private fun TimeColumn(position: Int, time: String?) {
    Column(
        modifier = Modifier.width(80.dp).padding(top = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.position_format, position),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = time ?: stringResource(R.string.time_unknown),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun EmptyText(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
