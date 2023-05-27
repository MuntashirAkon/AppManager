// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.viewer.audio;

import android.app.Application;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.utils.ThreadUtils;

public class AudioPlayerViewModel extends AndroidViewModel {
    private final MutableLiveData<AudioMetadata> audioMetadataLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> mediaPlayerPreparedLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> playlistLoadedLiveData = new MutableLiveData<>();
    private final List<AudioMetadata> playlist = Collections.synchronizedList(new ArrayList<>());

    private int currentPlaylistIndex = -1;

    public AudioPlayerViewModel(@NonNull Application application) {
        super(application);
    }

    public int getCurrentPlaylistIndex() {
        return currentPlaylistIndex;
    }

    public int playlistSize() {
        return playlist.size();
    }

    public void addToPlaylist(@NonNull Uri[] uriList) {
        ThreadUtils.postOnBackgroundThread(() -> {
            for (Uri uri : uriList) {
                AudioMetadata audioMetadata = fetchAudioMetadata(uri);
                playlist.add(audioMetadata);
                int index = currentPlaylistIndex;
                if (index == -1) {
                    // Start playing immediately if nothing is playing
                    currentPlaylistIndex = 0;
                    audioMetadataLiveData.postValue(audioMetadata);
                }
            }
            playlistLoadedLiveData.postValue(true);
        });
    }

    public void playNext(@RepeatMode int repeatMode) {
        switch (repeatMode) {
            default:
            case RepeatMode.NO_REPEAT:
                playNext(false);
                break;
            case RepeatMode.REPEAT_INDEFINITELY:
                playNext(true);
                break;
            case RepeatMode.REPEAT_SINGLE_INDEFINITELY:
                audioMetadataLiveData.postValue(playlist.get(currentPlaylistIndex));
        }
    }

    public void playNext(boolean repeat) {
        int index = currentPlaylistIndex;
        if (index < (playlist.size() - 1)) {
            ++index;
            currentPlaylistIndex = index;
            audioMetadataLiveData.postValue(playlist.get(index));
        } else if (repeat) {
            // Reset index
            index = 0;
            currentPlaylistIndex = index;
            audioMetadataLiveData.postValue(playlist.get(index));
        }
    }

    public void playPrevious() {
        int index = currentPlaylistIndex;
        if (index > 0) {
            --index;
            currentPlaylistIndex = index;
            audioMetadataLiveData.postValue(playlist.get(index));
        }
    }

    public LiveData<AudioMetadata> getAudioMetadataLiveData() {
        return audioMetadataLiveData;
    }

    public LiveData<Boolean> getMediaPlayerPreparedLiveData() {
        return mediaPlayerPreparedLiveData;
    }

    public LiveData<Boolean> getPlaylistLoadedLiveData() {
        return playlistLoadedLiveData;
    }

    public void prepareMediaPlayer(@NonNull MediaPlayer mediaPlayer, @NonNull Uri uri) {
        ThreadUtils.postOnBackgroundThread(() -> {
            try {
                mediaPlayer.reset();
                mediaPlayer.setDataSource(getApplication(), uri);
                mediaPlayer.prepare();
                mediaPlayerPreparedLiveData.postValue(true);
            } catch (IOException e) {
                e.printStackTrace();
                mediaPlayerPreparedLiveData.postValue(false);
            }
        });
    }

    @NonNull
    private AudioMetadata fetchAudioMetadata(@NonNull Uri uri) {
        AudioMetadata audioMetadata = new AudioMetadata();
        audioMetadata.uri = uri;
        try (MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {
            retriever.setDataSource(getApplication(), uri);
            final byte[] raw = retriever.getEmbeddedPicture();
            if (raw != null) {
                audioMetadata.cover = BitmapFactory.decodeByteArray(raw, 0, raw.length);
            }
            String title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            if (title == null) {
                title = uri.getLastPathSegment();
                if (title == null) {
                    title = "<Unknown Title>";
                }
            }
            String artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            if (artist == null) {
                artist = "<Unknown Artist>";
            }
            String album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
            if (album == null) {
                album = "<Unknown Album>";
            }
            String albumArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST);
            if (albumArtist == null) {
                albumArtist = "<Unknown Artist>";
            }
            audioMetadata.title = title;
            audioMetadata.album = album;
            audioMetadata.albumArtist = albumArtist;
            audioMetadata.artist = artist;
        } catch (RuntimeException | IOException e) {
            e.printStackTrace();
            String title = uri.getLastPathSegment();
            if (title == null) {
                title = "<Unknown Title>";
            }
            audioMetadata.title = title;
            audioMetadata.album = "<Unknown Album>";
            audioMetadata.albumArtist = "<Unknown Artist>";
            audioMetadata.artist = "<Unknown Artist>";
        }
        return audioMetadata;
    }
}
