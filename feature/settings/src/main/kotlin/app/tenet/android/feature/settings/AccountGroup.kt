package app.tenet.android.feature.settings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.tenet.android.core.data.sync.Account
import app.tenet.android.core.data.sync.AccountRepository
import app.tenet.android.core.data.sync.CloudSync
import app.tenet.android.core.data.sync.SyncWorker
import app.tenet.android.core.designsystem.component.LocalAppSnackbar
import coil3.compose.AsyncImage
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val accounts: AccountRepository,
    private val sync: CloudSync,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {
    val configured: Boolean get() = accounts.configured
    val account: StateFlow<Account?> = accounts.account
    val status: StateFlow<CloudSync.Status> = sync.status

    fun signIn(activityContext: Context, onMessage: (String) -> Unit, onSignedIn: () -> Unit = {}) {
        viewModelScope.launch {
            accounts.signIn(activityContext)
                .onSuccess {
                    onSignedIn()
                    SyncWorker.schedule(appContext, true)
                    onMessage("Angemeldet als ${it.email ?: it.name}. Daten werden synchronisiert …")
                    sync.sync().onFailure { e -> onMessage(e.message ?: "Synchronisierung fehlgeschlagen") }
                }
                .onFailure { if (it.message != "Abgebrochen") onMessage(it.message ?: "Anmeldung fehlgeschlagen") }
        }
    }

    fun syncNow(onMessage: (String) -> Unit) {
        viewModelScope.launch {
            sync.sync()
                .onSuccess { onMessage("Synchronisiert") }
                .onFailure { onMessage(it.message ?: "Synchronisierung fehlgeschlagen") }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            sync.sync() // last changes up before leaving
            accounts.signOut()
            sync.reset()
            SyncWorker.schedule(appContext, false)
        }
    }

    fun deleteCloud(onMessage: (String) -> Unit) {
        viewModelScope.launch {
            sync.deleteCloudData()
                .onSuccess {
                    accounts.signOut()
                    sync.reset()
                    SyncWorker.schedule(appContext, false)
                    onMessage("Cloud-Daten gelöscht und abgemeldet. Auf dem Handy bleibt alles erhalten.")
                }
                .onFailure { onMessage(it.message ?: "Löschen fehlgeschlagen") }
        }
    }
}

/** Settings → Konto: Google sign-in and the Firebase sync. */
@Composable
internal fun AccountGroup(viewModel: AccountViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val snackbar = LocalAppSnackbar.current
    val account by viewModel.account.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    val say: (String) -> Unit = { snackbar?.show(it) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Outlined.DeleteForever, contentDescription = null) },
            title = { Text("Cloud-Daten löschen?") },
            text = {
                Text(
                    "Alle Tenet-Daten dieses Google-Kontos werden aus der Cloud entfernt und du wirst abgemeldet. " +
                        "Auf diesem Handy bleibt alles erhalten, andere Geräte bekommen keine Änderungen mehr.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDelete = false
                        viewModel.deleteCloud(say)
                    },
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }, shapes = ButtonDefaults.shapes()) { Text("Abbrechen") } },
        )
    }

    val acc = account
    when {
        !viewModel.configured -> SettingsGroup { shapes ->
            NavItem(
                shapes = shapes(0, 1),
                icon = Icons.Outlined.CloudOff,
                title = "Google-Anmeldung",
                supporting = "Noch nicht eingerichtet: Die App wurde ohne Firebase-Konfiguration gebaut.",
                onClick = {},
            )
        }
        acc == null -> Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingsIcon(Icons.Outlined.CloudSync)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Mit Google anmelden", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Deine Saffron-Rezepte kommen automatisch dazu, Journal, Sport und Ernährung sind auf allen Geräten gesichert.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Button(
                    onClick = { viewModel.signIn(context, say) },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.AccountCircle, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Mit Google anmelden")
                }
            }
        }
        else -> SettingsGroup { shapes ->
            Card(
                shape = shapes(0, 4).shape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (acc.photoUrl != null) {
                        val fallback = rememberVectorPainter(Icons.Outlined.AccountCircle)
                        AsyncImage(
                            model = acc.photoUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            placeholder = fallback,
                            error = fallback,
                            modifier = Modifier.size(48.dp).clip(CircleShape),
                        )
                    } else {
                        SettingsIcon(Icons.Outlined.AccountCircle)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(acc.name ?: "Google-Konto", style = MaterialTheme.typography.titleMedium)
                        acc.email?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            NavItem(
                shapes = shapes(1, 4),
                icon = Icons.Outlined.CloudSync,
                title = "Jetzt synchronisieren",
                supporting = when {
                    status.syncing -> "Synchronisiere …"
                    status.error != null -> status.error!!
                    status.lastSync != null ->
                        "Zuletzt " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, java.util.Locale.GERMAN).format(Date(status.lastSync!!)) +
                            " · ${status.pushed} hoch, ${status.pulled} runter"
                    else -> "Noch nicht synchronisiert"
                },
                onClick = { viewModel.syncNow(say) },
            )
            NavItem(
                shapes = shapes(2, 4),
                icon = Icons.AutoMirrored.Outlined.Logout,
                title = "Abmelden",
                supporting = "Daten bleiben auf diesem Handy und in der Cloud",
                onClick = viewModel::signOut,
            )
            NavItem(
                shapes = shapes(3, 4),
                icon = Icons.Outlined.DeleteForever,
                title = "Cloud-Daten löschen",
                supporting = "Entfernt alles aus der Cloud und meldet ab",
                onClick = { confirmDelete = true },
            )
        }
    }
}
