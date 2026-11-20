package com.genkaim.zeppglass.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import com.genkaim.zeppglass.data.DietStore
import java.io.File

/**
 * A camera trampoline with no UI. The home-screen widget opens this instead of
 * the diet page, so tapping the fork-and-knife goes straight to the camera: the
 * diet page would otherwise appear for a frame, launch the camera, and wait
 * underneath while the photo is taken.
 *
 * On a successful capture the diet page opens with the photo path, analyses it,
 * and the user lands on the estimate exactly as if they had used the in-page
 * camera button.
 */
class DietCameraActivity : ComponentActivity() {

    private var photo: File? = null

    private val capture = registerForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
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
        val file = DietStore.newPhotoFile()
        photo = file
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        runCatching { capture.launch(uri) }.onFailure {
            file.delete()
            photo = null
            finish()
        }
    }
}
