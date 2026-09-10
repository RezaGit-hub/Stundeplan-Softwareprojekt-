package de.wsp3_1.stundenplan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.wsp3_1.stundenplan.Defaults
import de.wsp3_1.stundenplan.R

/**
 * Einstellungen: Serveradresse eintragen, Verbindung pruefen, speichern (A-3, T-A3.1);
 * darunter Anmeldung bzw. "Angemeldet als ..." mit Abmelden. Alle Texte aus strings.xml.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    uiState: UiState,
    onServerUrlChange: (String) -> Unit,
    onCheck: () -> Unit,
    onSave: () -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit,
) {
    val canGoBack = uiState !is UiState.NoServerConfigured && uiState !is UiState.LoginRequired
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.setup_title)) },
                actions = {
                    if (canGoBack) {
                        TextButton(onClick = onBack) { Text(stringResource(R.string.setup_back_to_plan)) }
                    }
                },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ServerSection(state, onServerUrlChange, onCheck, onSave)

            HorizontalDivider()

            LoginSection(state, uiState, onUsernameChange, onPasswordChange, onLogin, onLogout)

            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.setup_version, Defaults.APP_VERSION),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun ServerSection(
    state: SettingsUiState,
    onServerUrlChange: (String) -> Unit,
    onCheck: () -> Unit,
    onSave: () -> Unit,
) {
    OutlinedTextField(
        value = state.serverUrlInput,
        onValueChange = onServerUrlChange,
        label = { Text(stringResource(R.string.setup_server_label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
    )
    Text(text = stringResource(R.string.setup_server_hint), style = MaterialTheme.typography.bodySmall)

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedButton(onClick = onCheck, enabled = state.check != CheckState.Running) {
            Text(stringResource(R.string.setup_check))
        }
        Button(onClick = onSave) { Text(saveLabel(state.check)) }
        if (state.check == CheckState.Running) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
    }

    CheckMessage(state.check)

    if (state.savedNotice) {
        Text(text = stringResource(R.string.setup_saved), style = MaterialTheme.typography.bodyMedium)
    }
    Text(
        text = state.savedServerUrl?.let { stringResource(R.string.setup_current_server, it) }
            ?: stringResource(R.string.setup_no_server),
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun LoginSection(
    state: SettingsUiState,
    uiState: UiState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
) {
    Text(text = stringResource(R.string.login_title), style = MaterialTheme.typography.titleMedium)

    if (state.loggedIn) {
        Text(
            text = stringResource(R.string.logged_in_as, state.displayName ?: ""),
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(onClick = onLogout) { Text(stringResource(R.string.logout)) }
        return
    }

    val loginRequired = uiState as? UiState.LoginRequired
    if (loginRequired != null) {
        Text(
            text = if (loginRequired.reason == LoginReason.SESSION_EXPIRED) {
                stringResource(R.string.login_session_expired)
            } else {
                stringResource(R.string.login_required)
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }

    OutlinedTextField(
        value = state.username,
        onValueChange = onUsernameChange,
        label = { Text(stringResource(R.string.login_username)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.password,
        onValueChange = onPasswordChange,
        label = { Text(stringResource(R.string.login_password)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(onClick = onLogin, enabled = !state.loginRunning) {
            Text(stringResource(R.string.login_button))
        }
        if (state.loginRunning) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
            Text(stringResource(R.string.login_running), style = MaterialTheme.typography.bodySmall)
        }
    }
    val error = state.loginError
    if (error != null) {
        Text(
            text = loginErrorText(error),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun loginErrorText(error: LoginError): String = when (error) {
    LoginError.WrongCredentials -> stringResource(R.string.login_wrong)
    LoginError.Unreachable -> stringResource(R.string.login_unreachable)
    LoginError.NoServer -> stringResource(R.string.login_no_server)
    LoginError.EmptyFields -> stringResource(R.string.login_empty)
    is LoginError.ServerText ->
        error.text?.let { stringResource(R.string.login_server_error, it) }
            ?: stringResource(R.string.login_server_error_generic)
}

@Composable
private fun saveLabel(check: CheckState): String =
    if (check == CheckState.Unreachable) {
        stringResource(R.string.setup_save_anyway)
    } else {
        stringResource(R.string.setup_save)
    }

@Composable
private fun CheckMessage(check: CheckState) {
    when (check) {
        CheckState.Idle -> Unit
        CheckState.Running -> Text(
            text = stringResource(R.string.setup_checking),
            style = MaterialTheme.typography.bodyMedium,
        )
        is CheckState.Reachable -> Text(
            text = check.schoolName?.let { stringResource(R.string.setup_reachable, it) }
                ?: stringResource(R.string.setup_reachable_unnamed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        CheckState.Unreachable -> Text(
            text = stringResource(R.string.setup_unreachable),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        CheckState.InvalidUrl -> Text(
            text = stringResource(R.string.setup_url_invalid),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    StundenplanTheme {
        SettingsScreen(
            state = SettingsUiState(serverUrlInput = Defaults.DEFAULT_SERVER_URL),
            uiState = UiState.LoginRequired(),
            onServerUrlChange = {},
            onCheck = {},
            onSave = {},
            onUsernameChange = {},
            onPasswordChange = {},
            onLogin = {},
            onLogout = {},
            onBack = {},
        )
    }
}
