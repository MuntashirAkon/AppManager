// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.viewer.audio;

import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.slider.Slider;

import java.util.Locale;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.dialog.CapsuleBottomSheetDialogFragment;

// Raw code taken from DialogMusicPlayer <https://github.com/VishnuSanal/DialogMusicPlayer>.
// Inspired by other players such as Music Player Go, Poweramp
public class AudioPlayerDialogFragment extends CapsuleBottomSheetDialogFragment {
    public static final String TAG = AudioPlayerDialogFragment.class.getSimpleName();
    private static final String ARG_URI_LIST = "uris";
    private static final String ARG_CLOSE_ACTIVITY = "close";
    private static final String STATE_TIME_REVERSED = "time_reversed";

    @NonNull
    public static AudioPlayerDialogFragment getInstance(@NonNull Uri[] uriList, boolean closeActivity) {
        AudioPlayerDialogFragment dialog = new AudioPlayerDialogFragment();
        Bundle args = new Bundle();
        args.putParcelableArray(ARG_URI_LIST, uriList);
        args.putBoolean(ARG_CLOSE_ACTIVITY, closeActivity);
        dialog.setArguments(args);
        return dialog;
    }

    public boolean isDialogShowing() {
        return getDialog() != null && getDialog().isShowing();
    }

    public void updatePlaylist(@NonNull Uri[] uriList) {
        mUriList = uriList;
        if (mAudioPlayerService != null && uriList.length > 0) {
            mAudioPlayerService.replacePlaylistIfDifferent(uriList);
        }
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

    @Nullable
    private AudioPlayerService mAudioPlayerService;
    @Nullable
    private AudioPlayerState mLastState;
    @Nullable
    private Uri mDisplayedMetadataUri;
    @Nullable
    private Uri[] mUriList;
    private boolean mServiceBound;
    private boolean mIsTimeReversed;
    private boolean mCloseActivity;

    private final AudioPlayerService.Listener mServiceListener = state ->
            ThreadUtils.postOnMainThread(() -> {
                if (getView() != null) {
                    updateFromState(state);
                }
            });

    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            AudioPlayerService.LocalBinder binder = (AudioPlayerService.LocalBinder) service;
            mAudioPlayerService = binder.getService();
            mServiceBound = true;
            mAudioPlayerService.addListener(mServiceListener);
            if (mUriList != null && mUriList.length > 0) {
                mAudioPlayerService.replacePlaylistIfDifferent(mUriList);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mServiceBound = false;
            mAudioPlayerService = null;
        }
    };

    @NonNull
    @Override
    public View initRootView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_audio_player, container, false);
    }

    @Override
    public void onBodyInitialized(@NonNull View bodyView, @Nullable Bundle savedInstanceState) {
        mUriList = (Uri[]) requireArguments().getParcelableArray(ARG_URI_LIST);
        mCloseActivity = requireArguments().getBoolean(ARG_CLOSE_ACTIVITY);
        mIsTimeReversed = savedInstanceState != null
                && savedInstanceState.getBoolean(STATE_TIME_REVERSED, false);
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
        mPlaybackSpeedView.setTag(1.0f);
        mRepeatButton.setTag(RepeatMode.NO_REPEAT);
        setListeners();

        Context context = requireContext();
        Intent serviceIntent = new Intent(context, AudioPlayerService.class);
        context.startService(serviceIntent);
        context.bindService(serviceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDestroyView() {
        if (mServiceBound) {
            if (mAudioPlayerService != null) {
                mAudioPlayerService.removeListener(mServiceListener);
            }
            requireContext().unbindService(mServiceConnection);
            mServiceBound = false;
        }
        mAudioPlayerService = null;
        mLastState = null;
        mDisplayedMetadataUri = null;
        super.onDestroyView();
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        // Playback is service-owned and survives dismissal.
        finishHostActivity();
        super.onDismiss(dialog);
    }

    @Override
    public void onCancel(@NonNull DialogInterface dialog) {
        // Same as onDismiss: needed as sometimes dismissing the dialog doesn't finish the activity.
        finishHostActivity();
        super.onCancel(dialog);
    }

    private void finishHostActivity() {
        if (mCloseActivity && getActivity() != null) {
            getActivity().finish();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putBoolean(STATE_TIME_REVERSED, mIsTimeReversed);
        super.onSaveInstanceState(outState);
    }

    private void setListeners() {
        mSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser && mAudioPlayerService != null) {
                mAudioPlayerService.seekTo((int) value);
            }
        });
        mSlider.setLabelFormatter(value -> getFormattedTime((long) value, mIsTimeReversed));

        // Play/Pause: click -> play/pause, long click -> reset to the beginning.
        mPlayPauseButton.setOnClickListener(v -> {
            AudioPlayerService service = mAudioPlayerService;
            AudioPlayerState state = mLastState;
            if (service == null || state == null || !state.isPrepared()) {
                return;
            }
            if (state.isPlaying()) {
                service.pause();
            } else {
                service.play();
            }
        });
        mPlayPauseButton.setOnLongClickListener(v -> {
            if (mAudioPlayerService != null) {
                mAudioPlayerService.seekTo(0);
            }
            return true;
        });

        mProgressView.setOnClickListener(v -> {
            mIsTimeReversed = !mIsTimeReversed;
            mSlider.setLabelFormatter(value -> getFormattedTime((long) value, mIsTimeReversed));
            if (mLastState != null) {
                updateTimeViews(mLastState);
            }
        });

        mPlaybackSpeedView.setOnClickListener(v -> {
            float speed = (float) mPlaybackSpeedView.getTag();
            if (speed == 0.5F) speed = 0.75F;
            else if (speed == 0.75F) speed = 1F;
            else if (speed == 1.0F) speed = 1.25F;
            else if (speed == 1.25F) speed = 1.5F;
            else if (speed == 1.5F) speed = 2.0F;
            else speed = 0.5F;
            if (mAudioPlayerService != null) {
                mAudioPlayerService.setPlaybackSpeed(speed);
            }
        });

        mRepeatButton.setOnClickListener(v -> {
            @RepeatMode int state = (int) mRepeatButton.getTag();
            @RepeatMode int nextState;
            if (state == RepeatMode.NO_REPEAT) nextState = RepeatMode.REPEAT_INDEFINITELY;
            else if (state == RepeatMode.REPEAT_INDEFINITELY)
                nextState = RepeatMode.REPEAT_SINGLE_INDEFINITELY;
            else nextState = RepeatMode.NO_REPEAT;
            if (mAudioPlayerService != null) {
                mAudioPlayerService.setRepeatMode(nextState);
            }
        });

        // Rewind: click -> -10 sec, long click -> previous item.
        mRewindButton.setOnClickListener(v -> {
            if (mAudioPlayerService != null && mLastState != null) {
                mAudioPlayerService.seekTo(mLastState.getPosition() - 10 * 1000);
            }
        });
        mRewindButton.setOnLongClickListener(v -> {
            if (mAudioPlayerService != null) mAudioPlayerService.playPrevious();
            return true;
        });

        // Forward: click -> +10 sec, long click -> next item.
        mForwardButton.setOnClickListener(v -> {
            if (mAudioPlayerService != null && mLastState != null) {
                mAudioPlayerService.seekTo(mLastState.getPosition() + 10 * 1000);
            }
        });
        mForwardButton.setOnLongClickListener(v -> {
            if (mAudioPlayerService != null) mAudioPlayerService.playNext(false);
            return true;
        });
    }

    private void updateFromState(@NonNull AudioPlayerState state) {
        mLastState = state;
        AudioMetadata metadata = state.getMetadata();
        if (metadata != null) {
            if (!metadata.uri.equals(mDisplayedMetadataUri)) {
                if (metadata.cover != null) mIconView.setImageBitmap(metadata.cover);
                else mIconView.setImageResource(R.drawable.ic_audio_file);
                mTitleView.setText(String.format(Locale.ROOT, "%s - %s", metadata.artist, metadata.title));
                mTitleView.setSelected(true);
                mInfoView.setText(String.format(Locale.ROOT, "%s - %s", metadata.albumArtist, metadata.album));
                mInfoView.setSelected(true);
                mDisplayedMetadataUri = metadata.uri;
            }
        } else {
            mDisplayedMetadataUri = null;
        }

        int playlistSize = state.getPlaylistSize();
        mPlaylistSizeView.setVisibility(playlistSize > 1 ? View.VISIBLE : View.GONE);
        if (playlistSize > 0) {
            mPlaylistSizeView.setText(String.format(Locale.ROOT, "%d/%d",
                    state.getPlaylistIndex() + 1, playlistSize));
        }

        if (state.isPrepared() && state.getDuration() > 0) {
            mSlider.setValueFrom(0);
            mSlider.setValueTo(state.getDuration());
            mSlider.setValue(Math.max(0, Math.min(state.getPosition(), state.getDuration())));
        }
        updateTimeViews(state);

        float speed = state.getPlaybackSpeed();
        mPlaybackSpeedView.setTag(speed);
        mPlaybackSpeedView.setText(String.format(Locale.ROOT, "%sx", speed));
        updateRepeatButton(state.getRepeatMode());

        if (state.isPlaying()) mPlayPauseButton.setImageResource(R.drawable.ic_pause);
        else if (state.isCompleted()) mPlayPauseButton.setImageResource(R.drawable.ic_replay);
        else mPlayPauseButton.setImageResource(R.drawable.ic_play_arrow);
    }

    private void updateTimeViews(@NonNull AudioPlayerState state) {
        mProgressView.setText(getFormattedTime(state.getPosition(), mIsTimeReversed));
        mDurationView.setText(getFormattedTime(state.getDuration(), false));
    }

    private void updateRepeatButton(@RepeatMode int repeatMode) {
        mRepeatButton.setTag(repeatMode);
        if (repeatMode == RepeatMode.REPEAT_INDEFINITELY) {
            mRepeatButton.setImageResource(R.drawable.ic_repeat);
        } else if (repeatMode == RepeatMode.REPEAT_SINGLE_INDEFINITELY) {
            mRepeatButton.setImageResource(R.drawable.ic_repeat_one);
        } else {
            mRepeatButton.setImageResource(R.drawable.ic_repeat_off);
        }
    }

    private String getFormattedTime(long millis, boolean isTimeReversed) {
        AudioPlayerState state = mLastState;
        long duration = state != null ? state.getDuration() : 0;
        millis = Math.max(0, millis);
        if (duration > 0) millis = Math.min(millis, duration);
        if (isTimeReversed) millis = Math.max(0, duration - millis);
        long minutes = (millis / 1000) / 60;
        long seconds = (millis / 1000) % 60;
        return String.format(Locale.ROOT, "%d:%02d", minutes, seconds);
    }
}
