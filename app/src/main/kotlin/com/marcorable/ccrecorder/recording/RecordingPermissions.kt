package com.marcorable.ccrecorder.recording

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

data class RecordingPermissionState(
    val microphoneGranted: Boolean,
    val notificationGranted: Boolean,
) {
    val canStartRecording: Boolean get() = microphoneGranted
}

fun Context.recordingPermissionState(): RecordingPermissionState = RecordingPermissionState(
    microphoneGranted = ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED,
    notificationGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED,
)
