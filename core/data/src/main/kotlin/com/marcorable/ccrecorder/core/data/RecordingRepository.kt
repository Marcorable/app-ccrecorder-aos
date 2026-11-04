package com.marcorable.ccrecorder.core.data

import com.marcorable.ccrecorder.core.model.Recording

interface RecordingRepository {
    suspend fun recordings(): List<Recording>
}
