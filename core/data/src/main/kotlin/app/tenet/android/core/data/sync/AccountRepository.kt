package app.tenet.android.core.data.sync

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/** Signed-in Google account as Tenet shows it. */
data class Account(
    val uid: String,
    val name: String?,
    val email: String?,
    val photoUrl: String?,
)

/**
 * "Mit Google anmelden": Credential Manager asks for the Google account,
 * its ID token signs in to Firebase Auth. The Firebase project comes from
 * app/google-services.json; without it [configured] is false and the
 * settings explain what is missing instead of crashing.
 */
@Singleton
class AccountRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Firebase is set up (google-services.json was present at build time). */
    val configured: Boolean by lazy {
        runCatching { FirebaseApp.getApps(context).isNotEmpty() || FirebaseApp.initializeApp(context) != null }
            .getOrDefault(false) && webClientId() != null
    }

    private val auth: FirebaseAuth? get() = if (configured) FirebaseAuth.getInstance() else null

    private val _account = MutableStateFlow(current())
    val account: StateFlow<Account?> = _account.asStateFlow()

    init {
        auth?.addAuthStateListener { _account.value = current() }
    }

    private fun current(): Account? = auth?.currentUser?.let {
        Account(uid = it.uid, name = it.displayName, email = it.email, photoUrl = it.photoUrl?.toString())
    }

    /** OAuth web client id the google-services plugin puts into the resources. */
    private fun webClientId(): String? {
        val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return if (id == 0) null else context.getString(id).takeIf { it.isNotBlank() }
    }

    /**
     * Shows the Google account picker. [activityContext] must be an Activity
     * (Credential Manager draws its sheet above it).
     */
    suspend fun signIn(activityContext: Context): Result<Account> = runCatching {
        val auth = auth ?: error("Google-Anmeldung ist nicht eingerichtet (google-services.json fehlt).")
        val option = GetSignInWithGoogleOption.Builder(webClientId()!!).build()
        val response = try {
            CredentialManager.create(activityContext)
                .getCredential(activityContext, GetCredentialRequest.Builder().addCredentialOption(option).build())
        } catch (e: GetCredentialCancellationException) {
            error("Abgebrochen")
        } catch (e: NoCredentialException) {
            error("Kein Google-Konto auf dem Gerät gefunden.")
        }
        val credential = response.credential
        require(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Unerwartete Anmeldedaten"
        }
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
        current() ?: error("Anmeldung fehlgeschlagen")
    }.onSuccess { _account.value = it }

    /** Signs out of Firebase and forgets the chosen Google account; local data stays. */
    suspend fun signOut() {
        auth?.signOut()
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
        _account.value = null
    }
}
