package com.marcorable.ccrecorder.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingTest {
    @Test
    fun recordingRetainsIdentity() {
        val recording = Recording(id = "recording-id", title = "Practice")

        assertEquals("recording-id", recording.id)
    }
}
