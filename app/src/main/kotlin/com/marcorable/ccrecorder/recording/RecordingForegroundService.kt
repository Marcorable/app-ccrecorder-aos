package com.marcorable.ccrecorder.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.marcorable.ccrecorder.MainActivity
import com.marcorable.ccrecorder.R
import com.marcorable.ccrecorder.core.media.AudioEngine
import com.marcorable.ccrecorder.core.media.PlaybackState
import com.marcorable.ccrecorder.core.media.RecordingController
import com.marcorable.ccrecorder.core.media.RecordingState
import com.marcorable.ccrecorder.core.media.StateObserver
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject

@AndroidEntryPoint
class RecordingForegroundService : Service() {
    @Inject lateinit var audioEngine: AudioEngine

    private lateinit var controller: RecordingController
    private lateinit var commandExecutor: ExecutorService
    private val binder = RecordingBinder()
    private val stateObserver = StateObserver<RecordingState> { state -> onRecordingState(state) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        controller = audioEngine.newRecordingController()
        commandExecutor = Executors.newSingleThreadExecutor()
        controller.addObserver(stateObserver)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val command = intent?.toCommand() ?: return START_NOT_STICKY
        if (command is RecordingCommand.Prepare) {
            if (!recordingPermissionState().microphoneGranted) {
                stopSelf(startId)
                return START_NOT_STICKY
            }
            val promoted = runCatching { promoteToForeground(RecordingState.Preparing(command.sessionId)) }
            if (promoted.isFailure) {
                stopSelf(startId)
                return START_NOT_STICKY
            }
        }
        commandExecutor.execute { execute(command) }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        controller.removeObserver(stateObserver)
        commandExecutor.shutdown()
        super.onDestroy()
    }

    private fun execute(command: RecordingCommand) {
        when (command) {
            is RecordingCommand.Prepare -> controller.prepare(command.sessionId, command.outputPath)
            is RecordingCommand.Start -> controller.start(command.sessionId)
            is RecordingCommand.Pause -> controller.pause(command.sessionId)
            is RecordingCommand.Resume -> controller.resume(command.sessionId)
            is RecordingCommand.Finish -> controller.finish(command.sessionId)
            is RecordingCommand.Cancel -> controller.cancel(command.sessionId)
            is RecordingCommand.CloseFailure -> controller.closeFailure(command.sessionId)
        }
    }

    private fun onRecordingState(state: RecordingState) {
        when (state) {
            RecordingState.NoSession -> Unit
            is RecordingState.Preparing,
            is RecordingState.Ready,
            is RecordingState.Recording,
            is RecordingState.Paused,
            is RecordingState.Saving,
            is RecordingState.Cancelling,
            -> notificationManager().notify(NOTIFICATION_ID, buildNotification(state))
            is RecordingState.PrepareFailed,
            is RecordingState.SaveFailed,
            is RecordingState.Finished,
            -> {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun promoteToForeground(state: RecordingState) {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(state),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                0
            },
        )
    }

    private fun buildNotification(state: RecordingState): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(getString(R.string.recording_notification_title))
            .setContentText(getString(state.notificationTextRes()))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun RecordingState.notificationTextRes(): Int = when (this) {
        is RecordingState.Preparing -> R.string.recording_notification_preparing
        is RecordingState.Ready -> R.string.recording_notification_ready
        is RecordingState.Recording -> R.string.recording_notification_active
        is RecordingState.Paused -> R.string.recording_notification_paused
        is RecordingState.Saving,
        is RecordingState.Cancelling,
        is RecordingState.Finished,
        -> R.string.recording_notification_finishing
        RecordingState.NoSession,
        is RecordingState.PrepareFailed,
        is RecordingState.SaveFailed,
        -> R.string.recording_notification_preparing
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.recording_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.recording_notification_channel_description)
            setSound(null, null)
        }
        notificationManager().createNotificationChannel(channel)
    }

    private fun notificationManager(): NotificationManager =
        getSystemService(NotificationManager::class.java)

    inner class RecordingBinder : Binder() {
        val recordingState: RecordingState get() = controller.state

        val playbackState: PlaybackState get() = audioEngine.playback.state

        fun addRecordingObserver(observer: StateObserver<RecordingState>) {
            controller.addObserver(observer)
        }

        fun removeRecordingObserver(observer: StateObserver<RecordingState>) {
            controller.removeObserver(observer)
        }

        fun submit(command: RecordingCommand) {
            commandExecutor.execute { execute(command) }
        }
    }

    sealed interface RecordingCommand {
        val sessionId: String

        data class Prepare(
            override val sessionId: String,
            val outputPath: String,
        ) : RecordingCommand

        data class Start(override val sessionId: String) : RecordingCommand

        data class Pause(override val sessionId: String) : RecordingCommand

        data class Resume(override val sessionId: String) : RecordingCommand

        data class Finish(override val sessionId: String) : RecordingCommand

        data class Cancel(override val sessionId: String) : RecordingCommand

        data class CloseFailure(override val sessionId: String) : RecordingCommand
    }

    sealed interface LaunchResult {
        data class Started(val outputPath: String) : LaunchResult

        data object PermissionDenied : LaunchResult

        data class Rejected(val cause: Throwable) : LaunchResult
    }

    companion object {
        private const val NOTIFICATION_ID = 14
        private const val NOTIFICATION_CHANNEL_ID = "conversation_recording"
        private const val PENDING_AUDIO_DIRECTORY = "audio/pending"
        private const val ACTION_PREPARE = "com.marcorable.ccrecorder.action.PREPARE_RECORDING"
        private const val ACTION_START = "com.marcorable.ccrecorder.action.START_RECORDING"
        private const val ACTION_PAUSE = "com.marcorable.ccrecorder.action.PAUSE_RECORDING"
        private const val ACTION_RESUME = "com.marcorable.ccrecorder.action.RESUME_RECORDING"
        private const val ACTION_FINISH = "com.marcorable.ccrecorder.action.FINISH_RECORDING"
        private const val ACTION_CANCEL = "com.marcorable.ccrecorder.action.CANCEL_RECORDING"
        private const val ACTION_CLOSE_FAILURE = "com.marcorable.ccrecorder.action.CLOSE_RECORDING_FAILURE"
        private const val EXTRA_SESSION_ID = "session_id"
        private const val EXTRA_OUTPUT_PATH = "output_path"

        /**
         * Starts a microphone foreground service only after a visible UI has obtained RECORD_AUDIO.
         * Android can reject the call when the app is not eligible for a while-in-use FGS start.
         */
        fun prepare(context: Context, sessionId: String): LaunchResult {
            if (!context.recordingPermissionState().microphoneGranted) return LaunchResult.PermissionDenied
            val pendingDirectory = File(context.filesDir, PENDING_AUDIO_DIRECTORY)
            if (!pendingDirectory.exists() && !pendingDirectory.mkdirs()) {
                return LaunchResult.Rejected(IllegalStateException("Unable to create pending audio directory"))
            }
            val outputFile = File(pendingDirectory, "$sessionId.pending.m4a")
            val intent = commandIntent(context, ACTION_PREPARE, sessionId)
                .putExtra(EXTRA_OUTPUT_PATH, outputFile.absolutePath)
            return try {
                ContextCompat.startForegroundService(context, intent)
                LaunchResult.Started(outputFile.absolutePath)
            } catch (cause: Throwable) {
                LaunchResult.Rejected(cause)
            }
        }

        fun commandIntent(
            context: Context,
            command: RecordingCommand,
        ): Intent = when (command) {
            is RecordingCommand.Prepare -> commandIntent(context, ACTION_PREPARE, command.sessionId)
                .putExtra(EXTRA_OUTPUT_PATH, command.outputPath)
            is RecordingCommand.Start -> commandIntent(context, ACTION_START, command.sessionId)
            is RecordingCommand.Pause -> commandIntent(context, ACTION_PAUSE, command.sessionId)
            is RecordingCommand.Resume -> commandIntent(context, ACTION_RESUME, command.sessionId)
            is RecordingCommand.Finish -> commandIntent(context, ACTION_FINISH, command.sessionId)
            is RecordingCommand.Cancel -> commandIntent(context, ACTION_CANCEL, command.sessionId)
            is RecordingCommand.CloseFailure -> commandIntent(context, ACTION_CLOSE_FAILURE, command.sessionId)
        }

        private fun commandIntent(context: Context, action: String, sessionId: String): Intent =
            Intent(context, RecordingForegroundService::class.java)
                .setAction(action)
                .putExtra(EXTRA_SESSION_ID, sessionId)

        private fun Intent.toCommand(): RecordingCommand? {
            val sessionId = getStringExtra(EXTRA_SESSION_ID) ?: return null
            return when (action) {
                ACTION_PREPARE -> getStringExtra(EXTRA_OUTPUT_PATH)?.let {
                    RecordingCommand.Prepare(sessionId, it)
                }
                ACTION_START -> RecordingCommand.Start(sessionId)
                ACTION_PAUSE -> RecordingCommand.Pause(sessionId)
                ACTION_RESUME -> RecordingCommand.Resume(sessionId)
                ACTION_FINISH -> RecordingCommand.Finish(sessionId)
                ACTION_CANCEL -> RecordingCommand.Cancel(sessionId)
                ACTION_CLOSE_FAILURE -> RecordingCommand.CloseFailure(sessionId)
                else -> null
            }
        }
    }
}
