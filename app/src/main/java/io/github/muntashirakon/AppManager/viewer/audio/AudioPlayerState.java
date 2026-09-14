// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.viewer.audio;

import androidx.annotation.Nullable;

/**
 * Immutable snapshot of the playback state owned by {@link AudioPlayerService}.
 */
public final class AudioPlayerState {
    @Nullable
    private final AudioMetadata mMetadata;
    private final int mPlaylistIndex;
    private final int mPlaylistSize;
    private final int mDuration;
    private final int mPosition;
    private final float mPlaybackSpeed;
    @RepeatMode
    private final int mRepeatMode;
    private final boolean mPrepared;
    private final boolean mPlaying;
    private final boolean mCompleted;
    @Nullable
    private final String mError;

    AudioPlayerState(@Nullable AudioMetadata metadata, int playlistIndex, int playlistSize,
                     int duration, int position, float playbackSpeed, @RepeatMode int repeatMode,
                     boolean prepared, boolean playing, boolean completed, @Nullable String error) {
        mMetadata = metadata;
        mPlaylistIndex = playlistIndex;
        mPlaylistSize = playlistSize;
        mDuration = duration;
        mPosition = position;
        mPlaybackSpeed = playbackSpeed;
        mRepeatMode = repeatMode;
        mPrepared = prepared;
        mPlaying = playing;
        mCompleted = completed;
        mError = error;
    }

    @Nullable
    public AudioMetadata getMetadata() {
        return mMetadata;
    }

    public int getPlaylistIndex() {
        return mPlaylistIndex;
    }

    public int getPlaylistSize() {
        return mPlaylistSize;
    }

    public int getDuration() {
        return mDuration;
    }

    public int getPosition() {
        return mPosition;
    }

    public float getPlaybackSpeed() {
        return mPlaybackSpeed;
    }

    @RepeatMode
    public int getRepeatMode() {
        return mRepeatMode;
    }

    public boolean isPrepared() {
        return mPrepared;
    }

    public boolean isPlaying() {
        return mPlaying;
    }

    public boolean isCompleted() {
        return mCompleted;
    }

    @Nullable
    public String getError() {
        return mError;
    }
}
