package com.marcorable.ccrecorder.di

import android.content.Context
import com.marcorable.ccrecorder.core.media.AndroidAudioClock
import com.marcorable.ccrecorder.core.media.AndroidMedia3PlayerFactory
import com.marcorable.ccrecorder.core.media.AndroidMediaRecorderFactory
import com.marcorable.ccrecorder.core.media.AndroidRecordingFileCleanup
import com.marcorable.ccrecorder.core.media.AppAudioSourceResolver
import com.marcorable.ccrecorder.core.media.AudioEngine
import com.marcorable.ccrecorder.core.media.DefaultAudioEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AudioModule {
    @Provides
    @Singleton
    fun provideAudioEngine(
        @ApplicationContext context: Context,
    ): AudioEngine = DefaultAudioEngine(
        recorderBackendFactory = AndroidMediaRecorderFactory(context),
        clock = AndroidAudioClock(),
        recordingFileCleanup = AndroidRecordingFileCleanup(),
        playerBackendFactory = AndroidMedia3PlayerFactory(context),
        audioSourceResolver = AppAudioSourceResolver(context.filesDir),
    )
}
