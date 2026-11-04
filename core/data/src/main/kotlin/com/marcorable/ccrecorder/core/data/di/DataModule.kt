package com.marcorable.ccrecorder.core.data.di

import android.content.Context
import com.marcorable.ccrecorder.core.data.ConversationRepository
import com.marcorable.ccrecorder.core.data.LocalDataInitializer
import com.marcorable.ccrecorder.core.data.NoteRepository
import com.marcorable.ccrecorder.core.data.PartyRepository
import com.marcorable.ccrecorder.core.data.SettingsRepository
import com.marcorable.ccrecorder.core.data.file.AppAudioFileStore
import com.marcorable.ccrecorder.core.data.file.AudioFileStore
import com.marcorable.ccrecorder.core.data.repository.DefaultConversationRepository
import com.marcorable.ccrecorder.core.data.repository.DefaultNoteRepository
import com.marcorable.ccrecorder.core.data.repository.DefaultPartyRepository
import com.marcorable.ccrecorder.core.data.repository.DefaultSettingsRepository
import com.marcorable.ccrecorder.core.data.repository.LocalDataReconciler
import com.marcorable.ccrecorder.core.database.CCRecorderDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun provideApplicationFilesDirectory(@ApplicationContext context: Context): File = context.filesDir

    @Provides
    @Singleton
    fun provideAudioFileStore(filesDir: File): AudioFileStore = AppAudioFileStore(filesDir)

    @Provides
    @Singleton
    fun provideConversationRepository(
        database: CCRecorderDatabase,
        audioFileStore: AudioFileStore,
    ): ConversationRepository = DefaultConversationRepository(database, audioFileStore)

    @Provides
    fun provideNoteRepository(repository: DefaultNoteRepository): NoteRepository = repository

    @Provides
    fun providePartyRepository(repository: DefaultPartyRepository): PartyRepository = repository

    @Provides
    fun provideSettingsRepository(repository: DefaultSettingsRepository): SettingsRepository = repository

    @Provides
    fun provideLocalDataInitializer(reconciler: LocalDataReconciler): LocalDataInitializer = reconciler
}
