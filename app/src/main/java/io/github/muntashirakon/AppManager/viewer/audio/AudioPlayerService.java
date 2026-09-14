// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.viewer.audio;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Process;
import android.os.PowerManager;
import android.content.pm.ServiceInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.CpuUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;


public class AudioPlayerService extends Service {
    private static final String TAG = AudioPlayerService.class.getSimpleName();
    private static final String CHANNEL_ID = "audio_player";
    private static final int NOTIFICATION_ID = 0x415544;
    public static final String ACTION_PLAY_PAUSE = BuildConfig.APPLICATION_ID + ".audio.PLAY_PAUSE";
    public static final String ACTION_STOP = BuildConfig.APPLICATION_ID + ".audio.STOP";
    public static final String ACTION_PREVIOUS = BuildConfig.APPLICATION_ID + ".audio.PREVIOUS";
    public static final String ACTION_NEXT = BuildConfig.APPLICATION_ID + ".audio.NEXT";

    public interface Listener {
        void onAudioPlayerStateChanged(@NonNull AudioPlayerState state);
    }

    public final class LocalBinder extends Binder {
        @NonNull
        public AudioPlayerService getService() {
            return AudioPlayerService.this;
        }
    }

    private static final long POSITION_UPDATE_INTERVAL_MILLIS = 250;

    private final IBinder mBinder = new LocalBinder();
    private final List<AudioMetadata> mPlaylist = new ArrayList<>();
    private final CopyOnWriteArrayList<Listener> mListeners = new CopyOnWriteArrayList<>();
    private final Runnable mPositionUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            if (mPlayer != null && mPrepared) {
                notifyState();
                if (mPlaying) {
                    mPlayerHandler.postDelayed(this, POSITION_UPDATE_INTERVAL_MILLIS);
                }
            }
        }
    };

    @Nullable
    private HandlerThread mPlayerThread;
    @Nullable
    private Handler mPlayerHandler;
    @Nullable
    private MediaPlayer mPlayer;
    @Nullable
    private volatile AudioPlayerState mState;

    private int mCurrentPlaylistIndex = -1;
    private boolean mPrepared;
    private boolean mPreparing;
    private boolean mPlaying;
    private boolean mCompleted;
    private boolean mForegroundStarted;
    private boolean mHasAudioFocus;
    private boolean mResumeOnFocusGain;
    private boolean mIsDucked;
    private boolean mNoisyReceiverRegistered;
    private float mPlaybackSpeed = 1.0f;
    @RepeatMode
    private int mRepeatMode = RepeatMode.NO_REPEAT;
    @Nullable
    private String mError;
    @Nullable
    private MediaSession mMediaSession;
    @Nullable
    private AudioManager mAudioManager;
    @Nullable
    private AudioFocusRequest mAudioFocusRequest;
    @Nullable
    private AudioAttributes mAudioAttributes;
    @Nullable
    private PowerManager.WakeLock mWakeLock;

    private final AudioManager.OnAudioFocusChangeListener mAudioFocusChangeListener = focusChange ->
            postToPlayerThread(() -> handleAudioFocusChange(focusChange));
    private final BroadcastReceiver mNoisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                postToPlayerThread(() -> {
                    mResumeOnFocusGain = false;
                    pauseInternal();
                });
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        mPlayerThread = new HandlerThread("AudioPlayer", Process.THREAD_PRIORITY_AUDIO);
        mPlayerThread.start();
        mPlayerHandler = new Handler(mPlayerThread.getLooper());
        mAudioManager = getSystemService(AudioManager.class);
        mAudioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        mWakeLock = CpuUtils.getPartialWakeLock("audioPlayer");
        IntentFilter noisyFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        // The system audio service sends this broadcast, so the receiver must accept system
        // broadcasts. It handles only ACTION_AUDIO_BECOMING_NOISY and has no mutable state.
        ContextCompat.registerReceiver(this, mNoisyReceiver, noisyFilter, ContextCompat.RECEIVER_EXPORTED);
        mNoisyReceiverRegistered = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mAudioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(mAudioAttributes)
                    .setOnAudioFocusChangeListener(mAudioFocusChangeListener, mPlayerHandler)
                    .setWillPauseWhenDucked(true)
                    .build();
        }
        mMediaSession = new MediaSession(this, TAG);
        mMediaSession.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                play();
            }

            @Override
            public void onPause() {
                pause();
            }

            @Override
            public void onStop() {
                stopServicePlayback();
            }

            @Override
            public void onSkipToPrevious() {
                playPrevious();
            }

            @Override
            public void onSkipToNext() {
                postToPlayerThread(() -> playNextInternal(mRepeatMode == RepeatMode.REPEAT_INDEFINITELY));
            }

            @Override
            public void onSeekTo(long pos) {
                seekTo((int) pos);
            }

            @Override
            public void onCustomAction(@NonNull String action, Bundle extras) {
                if (ACTION_STOP.equals(action)) {
                    stopServicePlayback();
                }
            }
        }, mPlayerHandler);
        createNotificationChannel();
        mPlayerHandler.post(() -> {
            mPlayer = new MediaPlayer();
            mPlayer.setAudioAttributes(mAudioAttributes);
            mPlayer.setOnPreparedListener(player -> {
                mPrepared = true;
                mPreparing = false;
                mError = null;
                applyPlaybackSpeed();
                mCompleted = false;
                notifyState();
                startPlayback();
            });
            mPlayer.setOnCompletionListener(player -> handleCompletion());
            mPlayer.setOnErrorListener((player, what, extra) -> {
                mPrepared = false;
                mPreparing = false;
                mPlaying = false;
                mCompleted = false;
                mError = "MediaPlayer error: " + what + ", " + extra;
                if (mMediaSession != null) {
                    mMediaSession.setActive(false);
                }
                abandonAudioFocus();
                releaseWakeLock();
                stopForeground(true);
                mForegroundStarted = false;
                notifyState();
                return true;
            });
            notifyState();
        });
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case ACTION_PLAY_PAUSE:
                    postToPlayerThread(() -> {
                        if (mPlaying) pauseInternal();
                        else startPlayback();
                    });
                    break;
                case ACTION_STOP:
                    stopServicePlayback();
                    break;
                case ACTION_PREVIOUS:
                    playPrevious();
                    break;
                case ACTION_NEXT:
                    postToPlayerThread(() -> playNextInternal(mRepeatMode == RepeatMode.REPEAT_INDEFINITELY));
                    break;
            }
        }
        return START_NOT_STICKY;
    }

    public void addListener(@NonNull Listener listener) {
        mListeners.addIfAbsent(listener);
        postToPlayerThread(() -> {
            AudioPlayerState state = mState;
            if (state != null) {
                listener.onAudioPlayerStateChanged(state);
            }
        });
    }

    public void removeListener(@NonNull Listener listener) {
        mListeners.remove(listener);
    }

    @Nullable
    public AudioPlayerState getState() {
        return mState;
    }

    public void replacePlaylist(@NonNull Uri[] uris) {
        postToPlayerThread(() -> replacePlaylistInternal(uris));
    }

    /**
     * Replaces the playlist unless it is already the same playlist.
     */
    public void replacePlaylistIfDifferent(@NonNull Uri[] uris) {
        postToPlayerThread(() -> {
            if (isSamePlaylist(uris)) {
                notifyState();
                return;
            }
            replacePlaylistInternal(uris);
        });
    }

    private void replacePlaylistInternal(@NonNull Uri[] uris) {
        mPlaylist.clear();
        mCurrentPlaylistIndex = -1;
        mPrepared = false;
        mPlaying = false;
        mCompleted = false;
        mError = null;
        for (Uri uri : uris) {
            mPlaylist.add(fetchAudioMetadata(uri));
        }
        if (!mPlaylist.isEmpty()) {
            mCurrentPlaylistIndex = 0;
            prepareCurrentTrack();
        } else {
            resetPlayer();
            notifyState();
        }
    }

    private boolean isSamePlaylist(@NonNull Uri[] uris) {
        if (mPlaylist.size() != uris.length) {
            return false;
        }
        for (int i = 0; i < uris.length; i++) {
            if (!uris[i].equals(mPlaylist.get(i).uri)) {
                return false;
            }
        }
        return true;
    }

    public void appendToPlaylist(@NonNull Uri[] uris) {
        postToPlayerThread(() -> {
            boolean wasEmpty = mPlaylist.isEmpty();
            for (Uri uri : uris) {
                mPlaylist.add(fetchAudioMetadata(uri));
            }
            if (wasEmpty && !mPlaylist.isEmpty()) {
                mCurrentPlaylistIndex = 0;
                prepareCurrentTrack();
            } else {
                notifyState();
            }
        });
    }

    public void play() {
        postToPlayerThread(this::startPlayback);
    }

    public void pause() {
        postToPlayerThread(this::pauseInternal);
    }

    public void stop() {
        postToPlayerThread(() -> stopPlaybackInternal(false));
    }

    public void seekTo(int position) {
        postToPlayerThread(() -> {
            if (mPlayer != null && mPrepared) {
                int duration = mPlayer.getDuration();
                mPlayer.seekTo(Math.max(0, Math.min(position, duration)));
                mCompleted = false;
                notifyState();
            }
        });
    }

    public void playNext(boolean repeat) {
        postToPlayerThread(() -> playNextInternal(repeat));
    }

    public void playPrevious() {
        postToPlayerThread(() -> {
            if (mCurrentPlaylistIndex > 0) {
                mCurrentPlaylistIndex--;
                prepareCurrentTrack();
            }
        });
    }

    public void setRepeatMode(@RepeatMode int repeatMode) {
        postToPlayerThread(() -> {
            mRepeatMode = repeatMode;
            notifyState();
        });
    }

    public void setPlaybackSpeed(float playbackSpeed) {
        if (playbackSpeed < 0.5f || playbackSpeed > 2.0f) {
            throw new IllegalArgumentException("Playback speed must be between 0.5x and 2x");
        }
        postToPlayerThread(() -> {
            mPlaybackSpeed = playbackSpeed;
            applyPlaybackSpeed();
            notifyState();
        });
    }

    @Override
    public void onDestroy() {
        Handler handler = mPlayerHandler;
        if (handler != null) {
            handler.post(() -> {
                resetPlayer();
                if (mPlayer != null) {
                    mPlayer.release();
                    mPlayer = null;
                }
            });
        }
        if (mPlayerThread != null) {
            mPlayerThread.quitSafely();
            mPlayerThread = null;
        }
        if (mMediaSession != null) {
            mMediaSession.setActive(false);
            mMediaSession.release();
            mMediaSession = null;
        }
        if (mNoisyReceiverRegistered) {
            unregisterReceiver(mNoisyReceiver);
            mNoisyReceiverRegistered = false;
        }
        abandonAudioFocus();
        releaseWakeLock();
        stopForeground(true);
        mPlayerHandler = null;
        mListeners.clear();
        super.onDestroy();
    }

    private void postToPlayerThread(@NonNull Runnable runnable) {
        Handler handler = mPlayerHandler;
        if (handler == null) {
            return;
        }
        handler.post(runnable);
    }

    private void pauseInternal() {
        if (mPlayer != null && mPrepared && mPlaying) {
            mPlayer.pause();
            mPlaying = false;
            mPositionUpdateRunnableStop();
            releaseWakeLock();
            notifyState();
        }
    }

    private void playNextInternal(boolean repeat) {
        if (mCurrentPlaylistIndex < mPlaylist.size() - 1) {
            mCurrentPlaylistIndex++;
            prepareCurrentTrack();
        } else if (repeat && !mPlaylist.isEmpty()) {
            mCurrentPlaylistIndex = 0;
            prepareCurrentTrack();
        }
    }

    private void stopPlaybackInternal(boolean stopService) {
        resetPlayer();
        abandonAudioFocus();
        releaseWakeLock();
        mResumeOnFocusGain = false;
        mError = null;
        if (mMediaSession != null) {
            mMediaSession.setActive(false);
        }
        stopForeground(true);
        mForegroundStarted = false;
        notifyState();
        if (stopService) {
            stopSelf();
        }
    }

    private void stopServicePlayback() {
        postToPlayerThread(() -> stopPlaybackInternal(true));
    }

    private boolean requestAudioFocus() {
        if (mHasAudioFocus) {
            return true;
        }
        if (mAudioManager == null) {
            return false;
        }
        int result;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && mAudioFocusRequest != null) {
            result = mAudioManager.requestAudioFocus(mAudioFocusRequest);
        } else {
            result = mAudioManager.requestAudioFocus(mAudioFocusChangeListener,
                    AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        }
        mHasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        return mHasAudioFocus;
    }

    private void abandonAudioFocus() {
        if (!mHasAudioFocus || mAudioManager == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && mAudioFocusRequest != null) {
            mAudioManager.abandonAudioFocusRequest(mAudioFocusRequest);
        } else {
            mAudioManager.abandonAudioFocus(mAudioFocusChangeListener);
        }
        mHasAudioFocus = false;
    }

    private void handleAudioFocusChange(int focusChange) {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                if (mIsDucked && mPlayer != null) {
                    mPlayer.setVolume(1.0f, 1.0f);
                    mIsDucked = false;
                }
                if (mResumeOnFocusGain) {
                    mResumeOnFocusGain = false;
                    startPlayback();
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                if (mPlaying && mPlayer != null) {
                    mPlayer.setVolume(0.2f, 0.2f);
                    mIsDucked = true;
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                if (mPlaying) {
                    mResumeOnFocusGain = true;
                    pauseInternal();
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                mResumeOnFocusGain = false;
                pauseInternal();
                abandonAudioFocus();
                break;
        }
    }

    private void acquireWakeLock() {
        if (mWakeLock != null && !mWakeLock.isHeld()) {
            mWakeLock.acquire();
        }
    }

    private void releaseWakeLock() {
        CpuUtils.releaseWakeLock(mWakeLock);
    }

    private void prepareCurrentTrack() {
        if (mPlayer == null || mCurrentPlaylistIndex < 0 || mCurrentPlaylistIndex >= mPlaylist.size()) {
            return;
        }
        resetPlayer();
        mPreparing = true;
        mError = null;
        AudioMetadata metadata = mPlaylist.get(mCurrentPlaylistIndex);
        try {
            mPlayer.setDataSource(this, metadata.uri);
            mPlayer.prepareAsync();
            notifyState();
        } catch (IOException | RuntimeException e) {
            mPrepared = false;
            mPreparing = false;
            mPlaying = false;
            mError = e.toString();
            notifyState();
        }
    }

    private void startPlayback() {
        if (mPlayer != null && !mPrepared) {
            if (!mPreparing) {
                prepareCurrentTrack();
            }
            return;
        }
        if (mPlayer != null && mPrepared && !mPlaying) {
            if (!requestAudioFocus()) {
                mError = "Audio focus was not granted";
                notifyState();
                return;
            }
            if (mCompleted) {
                mPlayer.seekTo(0);
                mCompleted = false;
            }
            if (mIsDucked) {
                mPlayer.setVolume(1.0f, 1.0f);
                mIsDucked = false;
            }
            mPlayer.start();
            mPlaying = true;
            acquireWakeLock();
            mPositionUpdateRunnableStop();
            mPlayerHandler.post(mPositionUpdateRunnable);
            notifyState();
        }
    }

    private void handleCompletion() {
        mPlaying = false;
        mCompleted = true;
        releaseWakeLock();
        if (mRepeatMode == RepeatMode.REPEAT_SINGLE_INDEFINITELY) {
            mPlayer.seekTo(0);
            mCompleted = false;
            startPlayback();
        } else if (mCurrentPlaylistIndex < mPlaylist.size() - 1) {
            mCurrentPlaylistIndex++;
            prepareCurrentTrack();
        } else if (mRepeatMode == RepeatMode.REPEAT_INDEFINITELY && !mPlaylist.isEmpty()) {
            mCurrentPlaylistIndex = 0;
            prepareCurrentTrack();
        } else {
            abandonAudioFocus();
            notifyState();
        }
    }

    private void applyPlaybackSpeed() {
        if (mPlayer != null && mPrepared
                && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            try {
                mPlayer.setPlaybackParams(mPlayer.getPlaybackParams().setSpeed(mPlaybackSpeed));
            } catch (IllegalStateException | IllegalArgumentException ignored) {
                // The player will report a playback error through its error listener if needed.
            }
        }
    }

    private void resetPlayer() {
        mPositionUpdateRunnableStop();
        if (mPlayer != null) {
            try {
                mPlayer.reset();
            } catch (IllegalStateException ignored) {
                // The player is released only during service teardown.
            }
        }
        mPrepared = false;
        mPreparing = false;
        mPlaying = false;
        mCompleted = false;
    }

    private void mPositionUpdateRunnableStop() {
        Handler handler = mPlayerHandler;
        if (handler != null) {
            handler.removeCallbacks(mPositionUpdateRunnable);
        }
    }

    private void notifyState() {
        int duration = 0;
        int position = 0;
        if (mPlayer != null && mPrepared) {
            try {
                duration = mPlayer.getDuration();
                position = mPlayer.getCurrentPosition();
            } catch (IllegalStateException ignored) {
                // Keep the empty values while the player is transitioning state.
            }
        }
        AudioMetadata metadata = mCurrentPlaylistIndex >= 0 && mCurrentPlaylistIndex < mPlaylist.size()
                ? mPlaylist.get(mCurrentPlaylistIndex) : null;
        AudioPlayerState state = new AudioPlayerState(metadata, mCurrentPlaylistIndex,
                mPlaylist.size(), duration, position, mPlaybackSpeed, mRepeatMode, mPrepared,
                mPlaying, mCompleted, mError);
        mState = state;
        updateMediaSession(state);
        if (mPrepared || mPlaying || mCompleted) {
            updateNotification(state);
        }
        for (Listener listener : mListeners) {
            listener.onAudioPlayerStateChanged(state);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, getString(R.string.title_audio_player),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(getString(R.string.title_audio_player));
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private void updateMediaSession(@NonNull AudioPlayerState state) {
        if (mMediaSession == null) {
            return;
        }
        AudioMetadata metadata = state.getMetadata();
        if (metadata != null) {
            MediaMetadata.Builder metadataBuilder = new MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, metadata.title)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, metadata.artist)
                    .putString(MediaMetadata.METADATA_KEY_ALBUM, metadata.album)
                    .putString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST, metadata.albumArtist)
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, state.getDuration());
            if (metadata.cover != null) {
                metadataBuilder.putBitmap(MediaMetadata.METADATA_KEY_ART, metadata.cover);
            }
            mMediaSession.setMetadata(metadataBuilder.build());
        }
        long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
                | PlaybackState.ACTION_STOP | PlaybackState.ACTION_SEEK_TO
                | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_SKIP_TO_NEXT;
        int playbackState = state.getError() != null ? PlaybackState.STATE_ERROR
                : state.isPlaying() ? PlaybackState.STATE_PLAYING
                : state.isCompleted() ? PlaybackState.STATE_STOPPED
                : state.isPrepared() ? PlaybackState.STATE_PAUSED : PlaybackState.STATE_NONE;
        PlaybackState.Builder playbackStateBuilder = new PlaybackState.Builder()
                .setActions(actions)
                .setState(playbackState, state.getPosition(), state.getPlaybackSpeed())
                .setErrorMessage(state.getError());
        playbackStateBuilder.addCustomAction(new PlaybackState.CustomAction.Builder(
                ACTION_STOP, getString(R.string.action_stop_service), R.drawable.ic_stop).build());
        mMediaSession.setPlaybackState(playbackStateBuilder.build());
        mMediaSession.setActive(state.isPrepared() || state.isPlaying() || state.isCompleted());
    }

    private void updateNotification(@NonNull AudioPlayerState state) {
        Notification notification = buildNotification(state);
        if (!mForegroundStarted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
            mForegroundStarted = true;
        } else {
            getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, notification);
        }
    }

    @NonNull
    private Notification buildNotification(@NonNull AudioPlayerState state) {
        AudioMetadata metadata = state.getMetadata();
        Intent openIntent = new Intent(this, AudioPlayerActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPendingIntent = PendingIntent.getActivity(this, 0, openIntent, pendingIntentFlags());
        PendingIntent previousPendingIntent = createServicePendingIntent(ACTION_PREVIOUS, 1);
        PendingIntent playPausePendingIntent = createServicePendingIntent(ACTION_PLAY_PAUSE, 2);
        PendingIntent nextPendingIntent = createServicePendingIntent(ACTION_NEXT, 3);
        PendingIntent stopPendingIntent = createServicePendingIntent(ACTION_STOP, 4);

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        builder.setSmallIcon(R.drawable.ic_default_notification)
                .setContentTitle(metadata != null ? metadata.title : getString(R.string.title_audio_player))
                .setContentText(metadata != null ? metadata.artist : null)
                .setContentIntent(openPendingIntent)
                .setDeleteIntent(stopPendingIntent)
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .setOngoing(true);
        if (metadata != null && metadata.cover != null) {
            builder.setLargeIcon(metadata.cover);
        }
        builder.addAction(new Notification.Action.Builder(R.drawable.ic_previous,
                        getString(R.string.audio_action_previous), previousPendingIntent).build())
                .addAction(new Notification.Action.Builder(
                        state.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play_arrow,
                        state.isPlaying() ? getString(R.string.audio_action_pause) : getString(R.string.audio_action_play),
                        playPausePendingIntent).build())
                .addAction(new Notification.Action.Builder(R.drawable.ic_next,
                        getString(R.string.audio_action_next), nextPendingIntent).build())
                .addAction(new Notification.Action.Builder(R.drawable.ic_stop,
                        getString(R.string.action_stop_service), stopPendingIntent).build());
        if (state.getDuration() > 0) {
            int position = Math.max(0, Math.min(state.getPosition(), state.getDuration()));
            builder.setProgress(state.getDuration(), position, false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && mMediaSession != null) {
            builder.setStyle(new Notification.MediaStyle()
                    .setMediaSession(mMediaSession.getSessionToken())
                    .setShowActionsInCompactView(0, 1, 2));
        }
        return builder.build();
    }

    private PendingIntent createServicePendingIntent(@NonNull String action, int requestCode) {
        Intent intent = new Intent(this, AudioPlayerService.class).setAction(action);
        return PendingIntent.getService(this, requestCode, intent, pendingIntentFlags());
    }

    private int pendingIntentFlags() {
        return PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
    }

    @NonNull
    private AudioMetadata fetchAudioMetadata(@NonNull Uri uri) {
        AudioMetadata metadata = new AudioMetadata();
        metadata.uri = uri;
        try (MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {
            retriever.setDataSource(this, uri);
            byte[] raw = retriever.getEmbeddedPicture();
            if (raw != null) {
                metadata.cover = BitmapFactory.decodeByteArray(raw, 0, raw.length);
            }
            metadata.title = getMetadataOrFallback(retriever, MediaMetadataRetriever.METADATA_KEY_TITLE,
                    uri.getLastPathSegment(), "<Unknown Title>");
            metadata.artist = getMetadataOrFallback(retriever, MediaMetadataRetriever.METADATA_KEY_ARTIST,
                    null, "<Unknown Artist>");
            metadata.album = getMetadataOrFallback(retriever, MediaMetadataRetriever.METADATA_KEY_ALBUM,
                    null, "<Unknown Album>");
            metadata.albumArtist = getMetadataOrFallback(retriever,
                    MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST, null, "<Unknown Artist>");
        } catch (RuntimeException | IOException e) {
            metadata.title = uri.getLastPathSegment() != null ? uri.getLastPathSegment() : "<Unknown Title>";
            metadata.artist = "<Unknown Artist>";
            metadata.album = "<Unknown Album>";
            metadata.albumArtist = "<Unknown Artist>";
        }
        return metadata;
    }

    @NonNull
    private static String getMetadataOrFallback(@NonNull MediaMetadataRetriever retriever, int key,
                                                @Nullable String fallback, @NonNull String defaultValue) {
        String value = retriever.extractMetadata(key);
        if (value != null) {
            return value;
        }
        return fallback != null ? fallback : defaultValue;
    }
}
