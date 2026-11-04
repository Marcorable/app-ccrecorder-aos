package com.marcorable.ccrecorder.core.media

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppAudioSourceResolverTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `resolver accepts existing file beneath audio root`() {
        val root = temporaryFolder.root
        val recordings = root.resolve("audio/recordings").also { it.mkdirs() }
        recordings.resolve("conversation.m4a").writeBytes(byteArrayOf(1))
        val resolver = AppAudioSourceResolver(root)

        val result = resolver.resolve("audio/recordings/conversation.m4a")

        assertIs<AudioSourceResolution.Resolved>(result)
    }

    @Test
    fun `resolver rejects traversal outside audio root`() {
        val root = temporaryFolder.root
        val resolver = AppAudioSourceResolver(root)

        val result = resolver.resolve("audio/recordings/../../../outside.m4a")

        val rejected = assertIs<AudioSourceResolution.Rejected>(result)
        assertIs<PlaybackError.InvalidPath>(rejected.error)
    }

    @Test
    fun `resolver distinguishes a missing valid relative file`() {
        val root = temporaryFolder.root
        val resolver = AppAudioSourceResolver(root)

        val result = resolver.resolve("audio/recordings/missing.m4a")

        val rejected = assertIs<AudioSourceResolution.Rejected>(result)
        assertIs<PlaybackError.MissingFile>(rejected.error)
    }
}
