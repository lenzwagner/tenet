package app.tenet.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.tenet.android.core.designsystem.theme.TenetTheme

/**
 * Privacy notice Health Connect links to from its permission screen
 * (required for the permission dialog to appear at all).
 */
class HealthPrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TenetTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .safeDrawingPadding()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text("Datenschutz", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "Tenet liest aus Health Connect Läufe (Distanz, Herzfrequenz, Höhenmeter und – falls " +
                                "freigegeben – die GPS-Strecke) sowie dein Körpergewicht. Tenet schreibt eigene Trainings " +
                                "und dein eingetragenes Gewicht nach Health Connect.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            "Die Daten werden in der Tenet-Datenbank auf diesem Gerät gespeichert, um deine Läufe, " +
                                "Serien und Trainingspläne anzuzeigen. Nur wenn du dich in Tenet mit Google anmeldest, " +
                                "wird die Datenbank zusätzlich in deinem eigenen Firebase-Bereich (Google Cloud) " +
                                "synchronisiert, damit sie auf deinen anderen Geräten verfügbar ist. " +
                                "Keine Weitergabe an Dritte, keine Werbung.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            "Du kannst den Zugriff jederzeit in Health Connect oder in den Tenet-Einstellungen " +
                                "widerrufen. Bereits importierte Läufe bleiben bis zum Löschen der App erhalten.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Button(onClick = { finish() }, shapes = ButtonDefaults.shapes()) { Text("Schließen") }
                    }
                }
            }
        }
    }
}
