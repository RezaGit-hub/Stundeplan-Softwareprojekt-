package de.wsp3_1.stundenplan.ui


import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.wsp3_1.stundenplan.data.Absence
import de.wsp3_1.stundenplan.data.PlanDates
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AbsenceScreen(
    monday: LocalDate,
    today: LocalDate,
    isTeacher: Boolean,
    onDay: (LocalDate) -> Unit,
    onWeek: () -> Unit,
    onSettings: () -> Unit,
    onSubstitute: () -> Unit = {},
) {
    var showDialog by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf("") }
    var fromDate by remember { mutableStateOf(today) }
    var toDate by remember { mutableStateOf(today) }
    var showFromDatePicker by remember { mutableStateOf(false) }
    var showToDatePicker by remember { mutableStateOf(false) }
    var absences by remember { mutableStateOf(listOf<Absence>()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Abwesenheiten",
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Einstellungen öffnen"
                        )
                    }
                }
            )
        },

        bottomBar = {
            NavigationBar{
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
                    NavigationBarItem(
                        selected = true,
                        onClick = { /* Abwesenheiten */ },
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
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
                if (isTeacher) SubstitutionTab(onClick = onSubstitute)
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                absences.forEach { absence ->

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "${absence.from} - ${absence.to}",
                                style = MaterialTheme.typography.titleMedium
                            )

                            Spacer(
                                modifier = Modifier.height(8.dp)
                            )

                            Text(
                                text = absence.reason,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            FloatingActionButton(
                onClick = { showDialog = true},
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(30.dp),
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Abwesenheit hier einfügen"
                )
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Text("Abwesenheiten hinzufügen")
            },
            text = {
                Column {

                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = fromDate.toString(),
                            onValueChange = { },
                            readOnly = true,
                            label = { Text("von:") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Box (
                            modifier = Modifier
                                .matchParentSize()
                                .clickable {
                                    showFromDatePicker = true
                                }
                        )
                    }

                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = toDate.toString(),
                            onValueChange = { },
                            readOnly = true,
                            label = { Text("bis:") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Box (
                            modifier = Modifier
                                .matchParentSize()
                                .clickable {
                                    showToDatePicker = true
                                }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = reason,
                        onValueChange = {
                            reason = it
                        },
                        label = {
                            Text("Grund der Abwesenheit")
                        },
                        placeholder = {
                            Text("Bitte Text eingeben")
                        },
                        minLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (reason.isNotBlank()) {
                            absences = absences + Absence(
                                from = fromDate,
                                to = toDate,
                                reason = reason
                            )

                            showDialog = false
                            reason = ""
                        }
                    }
                ) {
                    Text("Hinzufügen")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDialog = false
                    }
                ) {
                    Text("Abbrechen")
                }
            }
        )
    }

    if (showFromDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = fromDate
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        )

        DatePickerDialog(
            onDismissRequest = {
                showFromDatePicker = false
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            fromDate = Instant
                                .ofEpochMilli(millis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()

                            if (toDate.isBefore(fromDate)) {
                                toDate = fromDate
                            }
                        }

                        showFromDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showFromDatePicker = false
                    }
                ) {
                    Text("Abbrechen")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showToDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = toDate
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli(),

            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val date = Instant
                        .ofEpochMilli(utcTimeMillis)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()

                    return !date.isBefore(fromDate)

                }
            }
        )

        DatePickerDialog(
            onDismissRequest = {
                showToDatePicker = false
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            toDate = Instant
                                .ofEpochMilli(millis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                        }

                        showToDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showToDatePicker = false
                    }
                ) {
                    Text("Abbrechen")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
