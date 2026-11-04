package com.marcorable.ccrecorder

import android.app.Application
import android.util.Log
import com.marcorable.ccrecorder.core.data.LocalDataInitializer
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

@HiltAndroidApp
class CCRecorderApplication : Application() {
    @Inject
    lateinit var localDataInitializer: LocalDataInitializer

    override fun onCreate() {
        super.onCreate()
        runBlocking(Dispatchers.IO) {
            runCatching { localDataInitializer.initialize() }
                .onSuccess { report ->
                    if (report.missingReferencedRecordings.isNotEmpty()) {
                        Log.w(
                            "CCRecorderApplication",
                            "Missing audio files: ${report.missingReferencedRecordings.joinToString()}",
                        )
                    }
                }
                .onFailure { error -> Log.e("CCRecorderApplication", "Local data reconciliation failed", error) }
        }
    }
}
