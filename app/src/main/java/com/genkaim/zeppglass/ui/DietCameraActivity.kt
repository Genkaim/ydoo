package com.genkaim.zeppglass.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.core.content.FileProvider
import com.genkaim.zeppglass.data.AppSettings
import com.genkaim.zeppglass.data.DietStore
import com.genkaim.zeppglass.ui.theme.ZeppGlassTheme
import java.io.File

/**
 * A plain themed page whose only job is to open the camera. The home-screen
 * widget's fork-and-knife opens this instead of the diet page, so the tap goes
 * straight to the viewfinder: no diet page appears first, and when the camera
 * closes the diet page opens on the photo it just took.
 *
 * The page itself is intentionally empty. It is painted with the app's own
 * background colour (light or dark, following the theme setting), which is what
 * shows for the instant before the camera activity comes up — an empty white
 * window in dark mode was the thing that looked like a stray page.
 */
class DietCameraActivity : ComponentActivity() {

    private var photo: File? = null
    private var launched = false

    private val capture = registerForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        android.util.Log.i("YdooCamera", "capture result saved=$saved photo=${photo?.name}")
        val shot = photo
        photo = null
        if (saved && shot != null && shot.exists()) {
            startActivity(
                DetailActivity.intent(
                    this,
                    DetailTarget(DetailKind.DIET, DietStore.logicalToday()),
                    photoPath = shot.absolutePath
                )
            )
        } else {
            shot?.delete()
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A configuration change while the camera is open must not retake the
        // photo: the contract restores its own result.
        if (savedInstanceState != null) return
        // The widget can start this activity in a fresh process, where nothing has
        // attached the diet store yet; without it the photo file has no directory
        // and the FileProvider URI throws.
        DietStore.attach(this)

        val settings = AppSettings.get(this)
        setContent {
            val systemDark = isSystemInDarkTheme()
            val dark = when (settings.themeMode) {
                "light" -> false
                "dark" -> true
                else -> systemDark
            }
            ZeppGlassTheme(darkTheme = dark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) { }
            }
            // Launched from composition so the background is laid out before the
            // camera takes over, instead of a bare window flashing first.
            LaunchedEffect(Unit) {
                if (launched) return@LaunchedEffect
                launched = true
                openCamera()
            }
        }
    }

    private fun openCamera() {
        photo?.delete()
        val file = DietStore.newPhotoFile()
        photo = file
        val uri = runCatching {
            FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        }.getOrNull()
        if (uri == null) {
            // The provider refused the path: do not leave the user on a blank page.
            file.delete()
            photo = null
            openDietPage()
            return
        }
        android.util.Log.i("YdooCamera", "launching system camera uri=$uri")
        runCatching { capture.launch(uri) }.onFailure {
            // No camera app, or the intent was refused: land on the diet page
            // rather than leaving the user on a blank screen that vanishes.
            file.delete()
            photo = null
            openDietPage()
        }
    }

    /**
     * A second tap on the widget while this activity is still alive delivers a
     * new intent instead of creating the activity again. Without this the page
     * came up and did nothing — the "only the first tap works" symptom.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openCamera()
    }

    private fun openDietPage() {
        runCatching {
            startActivity(
                DetailActivity.intent(
                    this,
                    DetailTarget(DetailKind.DIET, DietStore.logicalToday())
                )
            )
        }
        finish()
    }
}
