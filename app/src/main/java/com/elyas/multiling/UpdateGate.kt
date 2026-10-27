package com.elyas.multiling

import android.app.Activity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * Forced updates through Google Play's in-app update API.
 *
 * Whenever Play has a newer version of the app, the app opens Play's
 * full-screen "update required" flow (IMMEDIATE type) and cannot be used
 * until the update is installed: backing out of the flow closes the app,
 * and the next launch asks again. An update that was started but not
 * finished (app killed mid-install) is resumed.
 *
 * It only works for copies installed from Google Play — side-loaded APKs
 * and debug builds see "no update" and carry on normally.
 */
class UpdateGate(private val activity: AppCompatActivity) {

    private val manager: AppUpdateManager = AppUpdateManagerFactory.create(activity)

    private val launcher: ActivityResultLauncher<IntentSenderRequest> =
        activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
            // the user declined or the flow failed: the old version may not be used
            if (r.resultCode != Activity.RESULT_OK) activity.finishAffinity()
        }

    /** Call from onResume. */
    fun check() {
        manager.appUpdateInfo.addOnSuccessListener { info ->
            val inProgress = info.updateAvailability() ==
                UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
            val available = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
            if (inProgress || available) {
                try {
                    manager.startUpdateFlowForResult(
                        info, launcher,
                        AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()
                    )
                } catch (_: Exception) {
                }
            }
        }
    }
}
