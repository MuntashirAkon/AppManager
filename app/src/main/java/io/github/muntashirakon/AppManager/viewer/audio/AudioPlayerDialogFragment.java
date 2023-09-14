// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.viewer.audio;

import android.content.DialogInterface;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.slider.Slider;

import java.util.Locale;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.CpuUtils;
import io.github.muntashirakon.dialog.CapsuleBottomSheetDialogFragment;

// Raw code taken from DialogMusicPlayer <https://github.com/VishnuSanal/DialogMusicPlayer>.
// Inspired by other players such as Music Player Go, Poweramp
public class AudioPlayerDialogFragment extends CapsuleBottomSheetDialogFragment {
    public static final String TAG = AudioPlayerDialogFragment.class.getSimpleName();
    private static final String ARG_URI_LIST = "uris";
    private static final String ARG_CLOSE_ACTIVITY = "close";

    @NonNull
    public static AudioPlayerDialogFragment getInstance(@NonNull Uri[] uriList, boolean closeActivity) {
        AudioPlayerDialogFragment dialog = new AudioPlayerDialogFragment();
        Bundle args = new Bundle();
        args.putParcelableArray(ARG_URI_LIST, uriList);
        args.putBoolean(ARG_CLOSE_ACTIVITY, closeActivity);
        dialog.setArguments(args);
        return dialog;
    }

    private ImageView mIconView;
    private TextView mPlaylistSizeView;
    private TextView mTitleView;
    private TextView mInfoView;
    private Slider mSlider;
    private TextView mProgressView;
    private TextView mDurationView;
    private TextView mPlaybackSpeedView;
    private ImageView mRewindButton;
    private ImageView mForwardButton;
    private ImageView mPlayPauseButton;
    private ImageView mRepeatButton;

    private Handler mUpdateHandler;
    private Runnable mUpdateRunnable;

    private boolean mIsTimeReversed = false;
    private boolean mShouldCheckRepeat = true;

    private MediaPlayer mMediaPlayer;
    private AudioPlayerViewModel mViewModel;
    private PowerManager.WakeLock mWakeLock;

    private boolean mCloseActivity;

    @NonNull
    @Override
    public View initRootView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_audio_player, container, false);
    }

    @Override
    public void onBodyInitialized(@NonNull View bodyView, @Nullable Bundle savedInstanceState) {
        mViewModel = new ViewModelProvider(this).get(AudioPlayerViewModel.class);
        Uri[] uriList = (Uri[]) requireArguments().getParcelableArray(ARG_URI_LIST);
        mCloseActivity = requireArguments().getBoolean(ARG_CLOSE_ACTIVITY);
        mIconView = bodyView.findViewById(android.R.id.icon);
        mPlaylistSizeView = bodyView.findViewById(R.id.size);
        mTitleView = bodyView.findViewById(android.R.id.title);
        mInfoView = bodyView.findViewById(R.id.info);
        mSlider = bodyView.findViewById(R.id.slider);
        mProgressView = bodyView.findViewById(R.id.progress);
        mDurationView = bodyView.findViewById(R.id.duration);
        mPlaybackSpeedView = bodyView.findViewById(R.id.playback_speed);
        mRewindButton = bodyView.findViewById(R.id.action_rewind);
        mForwardButton = bodyView.findViewById(R.id.action_forward);
        mPlayPauseButton = bodyView.findViewById(R.id.action_play_pause);
        mRepeatButton = bodyView.findViewById(R.id.action_repeat);
        // Set tags
        mPlaybackSpeedView.setTag(1.0f);
        mRepeatButton.setTag(RepeatMode.NO_REPEAT);
        // Listeners
        mMediaPlayer = new MediaPlayer();
        setListeners();
        mViewModel.getAudioMetadataLiveData().observe(getViewLifecycleOwner(), this::setupMetadata);
        mViewModel.getMediaPlayerPreparedLiveData().observe(getViewLifecycleOwner(), prepared -> {
            if (!prepared) {
                return;
            }
            startPlayingMedia();
            mShouldCheckRepeat = true;
        });
        mViewModel.getPlaylistLoadedLiveData().observe(getViewLifecycleOwner(), loaded -> updatePlaylistSize());
        mViewModel.addToPlaylist(uriList);
        mWakeLock = CpuUtils.getPartialWakeLock("pasteThread");
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mWakeLock != null && !mWakeLock.isHeld()) {
            mWakeLock.acquire();
        }
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        if (mediaPlayerInitialized()) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
        }
        super.onDismiss(dialog);
    }

    @Override
    public void onDestroy() {
        CpuUtils.releaseWakeLock(mWakeLock);
        super.onDestroy();
        if (mCloseActivity) {
            requireActivity().finish();
        }
    }

    private void setListeners() {
        mSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (!fromUser) {
                return;
            }
            if (mediaPlayerInitialized()) {
                mMediaPlayer.seekTo((int) value);

                if (!mMediaPlayer.isPlaying()) {
                    if (value != mMediaPlayer.getDuration()) {
                        mPlayPauseButton.setImageResource(R.drawable.ic_play_arrow);
                    } else resetMediaPlayer();
                }
            }
        });
        mSlider.setLabelFormatter(value -> getFormattedTime((long) value, mIsTimeReversed));

        mUpdateHandler = new Handler(Looper.myLooper());
        mUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                if (mediaPlayerInitialized()) {
                    final int currentPosition = mMediaPlayer.getCurrentPosition();
                    if (currentPosition <= mSlider.getValueTo()) {
                        mSlider.setValue(currentPosition);
                    }

                    mProgressView.setText(getFormattedTime(currentPosition, mIsTimeReversed));
                    mUpdateHandler.postDelayed(this, 10);
                }
            }
        };


        // Play/Pause: Click -> Play/pause, Long click -> Set duration to zero
        mPlayPauseButton.setOnClickListener(v -> {
            if (mediaPlayerInitialized())
                if (!mMediaPlayer.isPlaying()) {
                    if (mMediaPlayer.getCurrentPosition() == mMediaPlayer.getDuration()) {
                        mMediaPlayer.seekTo(0);
                    }
                    resumeMediaPlayer();
                } else {
                    pauseMediaPlayer();
                }
        });
        mPlayPauseButton.setOnLongClickListener(v -> {
            mMediaPlayer.seekTo(0);
            return true;
        });

        mProgressView.setOnClickListener(v -> mIsTimeReversed = !mIsTimeReversed);

        mPlaybackSpeedView.setOnClickListener(v -> {
            float speed = (float) mPlaybackSpeedView.getTag();
            if (speed == 0.5F) {
                mPlaybackSpeedView.setText("0.75x");
                mPlaybackSpeedView.setTag(0.75F);
            } else if (speed == 0.75F) {
                mPlaybackSpeedView.setText("1x");
                mPlaybackSpeedView.setTag(1F);
            } else if (speed == 1.0F) {
                mPlaybackSpeedView.setText("1.25x");
                mPlaybackSpeedView.setTag(1.25F);
            } else if (speed == 1.25F) {
                mPlaybackSpeedView.setText("1.5x");
                mPlaybackSpeedView.setTag(1.5F);
            } else if (speed == 1.5F) {
                mPlaybackSpeedView.setText("2.0x");
                mPlaybackSpeedView.setTag(2.0F);
            } else if (speed == 2.0F) {
                mPlaybackSpeedView.setText("0.5x");
                mPlaybackSpeedView.setTag(0.5F);
            }
            updatePlaybackSpeed();
        });

        mRepeatButton.setOnClickListener(v -> {
            @RepeatMode
            int state = (int) mRepeatButton.getTag();
            switch (state) {
                case RepeatMode.NO_REPEAT:
                    mRepeatButton.setImageResource(R.drawable.ic_repeat);
                    mRepeatButton.setTag(RepeatMode.REPEAT_INDEFINITELY);
                    break;
                case RepeatMode.REPEAT_INDEFINITELY:
                    mRepeatButton.setImageResource(R.drawable.ic_repeat_one);
                    mRepeatButton.setTag(RepeatMode.REPEAT_SINGLE_INDEFINITELY);
                    break;
                case RepeatMode.REPEAT_SINGLE_INDEFINITELY:
                    mRepeatButton.setImageResource(R.drawable.ic_repeat_off);
                    mRepeatButton.setTag(RepeatMode.NO_REPEAT);
                    break;
            }
        });

        // Rewind: Click -> -10 sec, Long click -> Play previous item
        mRewindButton.setOnClickListener(v -> {
            if (mediaPlayerInitialized()) {
                mMediaPlayer.seekTo(mMediaPlayer.getCurrentPosition() - 10 * 1000);
            }
        });
        mRewindButton.setOnLongClickListener(v -> {
            mViewModel.playPrevious();
            return true;
        });

        // Forward: Click -> +10 sec, Long click -> Play next item
        mForwardButton.setOnClickListener(v -> {
            if (mediaPlayerInitialized()) {
                mMediaPlayer.seekTo(mMediaPlayer.getCurrentPosition() + 10 * 1000);
            }
        });
        mForwardButton.setOnLongClickListener(v -> {
            mViewModel.playNext(false);
            return true;
        });

        mMediaPlayer.setOnCompletionListener(mediaPlayer -> {
            if (!mShouldCheckRepeat) {
                mShouldCheckRepeat = true;
                return;
            }
            @RepeatMode
            int state = (int) mRepeatButton.getTag();
            resetMediaPlayer();
            if (state == RepeatMode.REPEAT_SINGLE_INDEFINITELY) {
                if (mediaPlayer.getCurrentPosition() == mediaPlayer.getDuration()) {
                    mediaPlayer.seekTo(0);
                }
                resumeMediaPlayer();
            } else {
                mViewModel.playNext(state);
            }
        });
    }

    private void updatePlaybackSpeed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && mediaPlayerInitialized()) {
            boolean isPlaying = mMediaPlayer.isPlaying();
            mMediaPlayer.setPlaybackParams(mMediaPlayer.getPlaybackParams().setSpeed((float) mPlaybackSpeedView.getTag()));
            if (!isPlaying) {
                mMediaPlayer.pause();
            }
        }
    }

    private void pauseMediaPlayer() {
        if (mediaPlayerInitialized()) {
            mMediaPlayer.pause();
            mPlayPauseButton.setImageResource(R.drawable.ic_play_arrow);
        }
    }

    private void resumeMediaPlayer() {
        if (mediaPlayerInitialized()) {
            mMediaPlayer.start();
            mPlayPauseButton.setImageResource(R.drawable.ic_pause);
        }
    }

    private void resetMediaPlayer() {
        mPlayPauseButton.setImageResource(R.drawable.ic_replay);
    }

    private String getFormattedTime(long millis, boolean isTimeReversed) {
        if (!mediaPlayerInitialized()) {
            return "00:00";
        }
        long minutes = (millis / 1000) / 60;
        long seconds = (millis / 1000) % 60;
        String secondsStr = Long.toString(seconds);
        String secs = (secondsStr.length() >= 2) ? secondsStr.substring(0, 2) : "0" + secondsStr;
        if (!isTimeReversed) return minutes + ":" + secs;
        return "-" + getFormattedTime(mMediaPlayer.getDuration() - millis, false);
    }

    private boolean mediaPlayerInitialized() {
        if (mMediaPlayer == null) {
            return false;
        }
        try {
            mMediaPlayer.isPlaying();
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private void updatePlaylistSize() {
        int currentAudio = mViewModel.getCurrentPlaylistIndex() + 1;
        int playlistSize = mViewModel.playlistSize();
        mPlaylistSizeView.setVisibility(playlistSize > 1 ? View.VISIBLE : View.GONE);
        mPlaylistSizeView.setText(String.format(Locale.ROOT, "%d/%d", currentAudio, playlistSize));
    }

    private void setupMetadata(@NonNull AudioMetadata meta) {
        if (meta.cover != null) {
            mIconView.setImageBitmap(meta.cover);
        } else {
            mIconView.setImageResource(R.drawable.ic_audio_file);
        }
        updatePlaylistSize();
        mTitleView.setText(String.format(Locale.ROOT, "%s - %s", meta.artist, meta.title));
        mTitleView.setSelected(true);
        mInfoView.setText(String.format(Locale.ROOT, "%s - %s", meta.albumArtist, meta.album));
        mInfoView.setSelected(true);

        mShouldCheckRepeat = false;
        mViewModel.prepareMediaPlayer(mMediaPlayer, meta.uri);
    }

    private void startPlayingMedia() {
        int duration = mMediaPlayer.getDuration();
        mSlider.setValueFrom(0);
        mSlider.setValueTo(duration);

        mDurationView.setText(getFormattedTime(duration, mIsTimeReversed));

        mMediaPlayer.seekTo(0);
        resumeMediaPlayer();
        mUpdateHandler.postDelayed(mUpdateRunnable, 0);

        mPlayPauseButton.setImageResource(R.drawable.ic_pause);
    }
}
