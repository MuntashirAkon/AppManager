// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.viewer.audio;

import android.content.DialogInterface;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

    private ImageView iconView;
    private TextView playlistSizeView;
    private TextView titleView;
    private TextView infoView;
    private Slider slider;
    private TextView progressView;
    private TextView durationView;
    private TextView playbackSpeedView;
    private ImageView rewindButton;
    private ImageView forwardButton;
    private ImageView playPauseButton;
    private ImageView repeatButton;

    private Handler updateHandler;
    private Runnable updateRunnable;

    private boolean isTimeReversed = false;
    private boolean shouldCheckRepeat = true;

    private MediaPlayer mediaPlayer;
    private AudioPlayerViewModel viewModel;

    private boolean closeActivity;

    @NonNull
    @Override
    public View initRootView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_audio_player, container, false);
    }

    @Override
    public void onBodyInitialized(@NonNull View bodyView, @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(this).get(AudioPlayerViewModel.class);
        Uri[] uriList = (Uri[]) requireArguments().getParcelableArray(ARG_URI_LIST);
        closeActivity = requireArguments().getBoolean(ARG_CLOSE_ACTIVITY);
        iconView = bodyView.findViewById(android.R.id.icon);
        playlistSizeView = bodyView.findViewById(R.id.size);
        titleView = bodyView.findViewById(android.R.id.title);
        infoView = bodyView.findViewById(R.id.info);
        slider = bodyView.findViewById(R.id.slider);
        progressView = bodyView.findViewById(R.id.progress);
        durationView = bodyView.findViewById(R.id.duration);
        playbackSpeedView = bodyView.findViewById(R.id.playback_speed);
        rewindButton = bodyView.findViewById(R.id.action_rewind);
        forwardButton = bodyView.findViewById(R.id.action_forward);
        playPauseButton = bodyView.findViewById(R.id.action_play_pause);
        repeatButton = bodyView.findViewById(R.id.action_repeat);
        // Set tags
        playbackSpeedView.setTag(1.0f);
        repeatButton.setTag(RepeatMode.NO_REPEAT);
        // Listeners
        mediaPlayer = new MediaPlayer();
        setListeners();
        viewModel.getAudioMetadataLiveData().observe(getViewLifecycleOwner(), this::setupMetadata);
        viewModel.getMediaPlayerPreparedLiveData().observe(getViewLifecycleOwner(), prepared -> {
            if (!prepared) {
                return;
            }
            startPlayingMedia();
            shouldCheckRepeat = true;
        });
        viewModel.getPlaylistLoadedLiveData().observe(getViewLifecycleOwner(), loaded -> updatePlaylistSize());
        viewModel.addToPlaylist(uriList);
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        if (mediaPlayerInitialized()) {
            mediaPlayer.stop();
            mediaPlayer.release();
        }
        super.onDismiss(dialog);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (closeActivity) {
            requireActivity().finish();
        }
    }

    private void setListeners() {
        slider.addOnChangeListener((slider, value, fromUser) -> {
            if (!fromUser) {
                return;
            }
            if (mediaPlayerInitialized()) {
                mediaPlayer.seekTo((int) value);

                if (!mediaPlayer.isPlaying()) {
                    if (value != mediaPlayer.getDuration()) {
                        playPauseButton.setImageResource(R.drawable.ic_play_arrow);
                    } else resetMediaPlayer();
                }
            }
        });
        slider.setLabelFormatter(value -> getFormattedTime((long) value, isTimeReversed));

        updateHandler = new Handler(Looper.myLooper());
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                if (mediaPlayerInitialized()) {
                    final int currentPosition = mediaPlayer.getCurrentPosition();
                    if (currentPosition <= slider.getValueTo()) {
                        slider.setValue(currentPosition);
                    }

                    progressView.setText(getFormattedTime(currentPosition, isTimeReversed));
                    updateHandler.postDelayed(this, 10);
                }
            }
        };


        // Play/Pause: Click -> Play/pause, Long click -> Set duration to zero
        playPauseButton.setOnClickListener(v -> {
            if (mediaPlayerInitialized())
                if (!mediaPlayer.isPlaying()) {
                    if (mediaPlayer.getCurrentPosition() == mediaPlayer.getDuration()) {
                        mediaPlayer.seekTo(0);
                    }
                    resumeMediaPlayer();
                } else {
                    pauseMediaPlayer();
                }
        });
        playPauseButton.setOnLongClickListener(v -> {
            mediaPlayer.seekTo(0);
            return true;
        });

        progressView.setOnClickListener(v -> isTimeReversed = !isTimeReversed);

        playbackSpeedView.setOnClickListener(v -> {
            float speed = (float) playbackSpeedView.getTag();
            if (speed == 0.5F) {
                playbackSpeedView.setText("0.75x");
                playbackSpeedView.setTag(0.75F);
            } else if (speed == 0.75F) {
                playbackSpeedView.setText("1x");
                playbackSpeedView.setTag(1F);
            } else if (speed == 1.0F) {
                playbackSpeedView.setText("1.25x");
                playbackSpeedView.setTag(1.25F);
            } else if (speed == 1.25F) {
                playbackSpeedView.setText("1.5x");
                playbackSpeedView.setTag(1.5F);
            } else if (speed == 1.5F) {
                playbackSpeedView.setText("2.0x");
                playbackSpeedView.setTag(2.0F);
            } else if (speed == 2.0F) {
                playbackSpeedView.setText("0.5x");
                playbackSpeedView.setTag(0.5F);
            }
            updatePlaybackSpeed();
        });

        repeatButton.setOnClickListener(v -> {
            @RepeatMode
            int state = (int) repeatButton.getTag();
            switch (state) {
                case RepeatMode.NO_REPEAT:
                    repeatButton.setImageResource(R.drawable.ic_repeat);
                    repeatButton.setTag(RepeatMode.REPEAT_INDEFINITELY);
                    break;
                case RepeatMode.REPEAT_INDEFINITELY:
                    repeatButton.setImageResource(R.drawable.ic_repeat_one);
                    repeatButton.setTag(RepeatMode.REPEAT_SINGLE_INDEFINITELY);
                    break;
                case RepeatMode.REPEAT_SINGLE_INDEFINITELY:
                    repeatButton.setImageResource(R.drawable.ic_repeat_off);
                    repeatButton.setTag(RepeatMode.NO_REPEAT);
                    break;
            }
        });

        // Rewind: Click -> -10 sec, Long click -> Play previous item
        rewindButton.setOnClickListener(v -> {
            if (mediaPlayerInitialized()) {
                mediaPlayer.seekTo(mediaPlayer.getCurrentPosition() - 10 * 1000);
            }
        });
        rewindButton.setOnLongClickListener(v -> {
            viewModel.playPrevious();
            return true;
        });

        // Forward: Click -> +10 sec, Long click -> Play next item
        forwardButton.setOnClickListener(v -> {
            if (mediaPlayerInitialized()) {
                mediaPlayer.seekTo(mediaPlayer.getCurrentPosition() + 10 * 1000);
            }
        });
        forwardButton.setOnLongClickListener(v -> {
            viewModel.playNext(false);
            return true;
        });

        mediaPlayer.setOnCompletionListener(mediaPlayer -> {
            if (!shouldCheckRepeat) {
                shouldCheckRepeat = true;
                return;
            }
            @RepeatMode
            int state = (int) repeatButton.getTag();
            resetMediaPlayer();
            if (state == RepeatMode.REPEAT_SINGLE_INDEFINITELY) {
                if (mediaPlayer.getCurrentPosition() == mediaPlayer.getDuration()) {
                    mediaPlayer.seekTo(0);
                }
                resumeMediaPlayer();
            } else {
                viewModel.playNext(state);
            }
        });
    }

    private void updatePlaybackSpeed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && mediaPlayerInitialized()) {
            boolean isPlaying = mediaPlayer.isPlaying();
            mediaPlayer.setPlaybackParams(mediaPlayer.getPlaybackParams().setSpeed((float) playbackSpeedView.getTag()));
            if (!isPlaying) {
                mediaPlayer.pause();
            }
        }
    }

    private void pauseMediaPlayer() {
        if (mediaPlayerInitialized()) {
            mediaPlayer.pause();
            playPauseButton.setImageResource(R.drawable.ic_play_arrow);
        }
    }

    private void resumeMediaPlayer() {
        if (mediaPlayerInitialized()) {
            mediaPlayer.start();
            playPauseButton.setImageResource(R.drawable.ic_pause);
        }
    }

    private void resetMediaPlayer() {
        playPauseButton.setImageResource(R.drawable.ic_replay);
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
        return "-" + getFormattedTime(mediaPlayer.getDuration() - millis, false);
    }

    private boolean mediaPlayerInitialized() {
        if (mediaPlayer == null) {
            return false;
        }
        try {
            mediaPlayer.isPlaying();
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private void updatePlaylistSize() {
        int currentAudio = viewModel.getCurrentPlaylistIndex() + 1;
        int playlistSize = viewModel.playlistSize();
        playlistSizeView.setVisibility(playlistSize > 1 ? View.VISIBLE : View.GONE);
        playlistSizeView.setText(String.format(Locale.ROOT, "%d/%d", currentAudio, playlistSize));
    }

    private void setupMetadata(@NonNull AudioMetadata meta) {
        if (meta.cover != null) {
            iconView.setImageBitmap(meta.cover);
        } else {
            iconView.setImageResource(R.drawable.ic_audio_file);
        }
        updatePlaylistSize();
        titleView.setText(String.format(Locale.ROOT, "%s - %s", meta.artist, meta.title));
        titleView.setSelected(true);
        infoView.setText(String.format(Locale.ROOT, "%s - %s", meta.albumArtist, meta.album));
        infoView.setSelected(true);

        shouldCheckRepeat = false;
        viewModel.prepareMediaPlayer(mediaPlayer, meta.uri);
    }

    private void startPlayingMedia() {
        int duration = mediaPlayer.getDuration();
        slider.setValueFrom(0);
        slider.setValueTo(duration);

        durationView.setText(getFormattedTime(duration, isTimeReversed));

        mediaPlayer.seekTo(0);
        resumeMediaPlayer();
        updateHandler.postDelayed(updateRunnable, 0);

        playPauseButton.setImageResource(R.drawable.ic_pause);
    }
}
