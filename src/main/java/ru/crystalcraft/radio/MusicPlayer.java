package ru.crystalcraft.radio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class MusicPlayer {
    public enum PlaybackMode {
        SEQUENTIAL,
        REPEAT_ONE,
        SHUFFLE
    }

    private static final MusicPlayer INSTANCE = new MusicPlayer();

    private volatile SourceDataLine line;
    private volatile Thread worker;
    private volatile long playbackId;
    private volatile boolean paused;
    private volatile boolean playing;
    private volatile Path currentTrack;
    private volatile float volume = 0.70f;
    private volatile List<Path> playlist = List.of();
    private volatile int playlistIndex = -1;
    private volatile PlaybackMode playbackMode = PlaybackMode.SEQUENTIAL;

    private MusicPlayer() {
    }

    public static MusicPlayer getInstance() {
        return INSTANCE;
    }

    public synchronized void play(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            System.err.println("[Crystal Radio] Track does not exist: " + file);
            return;
        }

        stop();

        Path track = file.toAbsolutePath().normalize();
        int foundIndex = playlist.indexOf(track);
        if (foundIndex >= 0) {
            playlistIndex = foundIndex;
        }
        long thisPlayback = ++playbackId;

        paused = false;
        playing = true;
        currentTrack = track;

        Thread newWorker = new Thread(
            () -> playFile(track, thisPlayback),
            "CrystalRadio-Audio"
        );
        newWorker.setDaemon(true);
        worker = newWorker;
        newWorker.start();
    }

    private void playFile(Path file, long thisPlayback) {
        SourceDataLine output = null;
        boolean reachedEnd = false;

        try (AudioInputStream source = AudioSystem.getAudioInputStream(file.toFile())) {
            AudioFormat sourceFormat = source.getFormat();

            int channels = sourceFormat.getChannels() > 0
                ? sourceFormat.getChannels()
                : 2;
            float sampleRate = sourceFormat.getSampleRate() > 0
                ? sourceFormat.getSampleRate()
                : 44100.0f;

            AudioFormat pcmFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                sampleRate,
                16,
                channels,
                channels * 2,
                sampleRate,
                false
            );

            try (AudioInputStream decoded = AudioSystem.getAudioInputStream(pcmFormat, source)) {
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, pcmFormat);
                output = (SourceDataLine) AudioSystem.getLine(info);
                output.open(pcmFormat);

                if (!isCurrentPlayback(thisPlayback)) {
                    return;
                }

                line = output;
                output.start();

                byte[] buffer = new byte[8192];
                int count;

                while (isCurrentPlayback(thisPlayback)) {
                    while (paused && isCurrentPlayback(thisPlayback)) {
                        Thread.sleep(50);
                    }

                    if (!isCurrentPlayback(thisPlayback)) {
                        break;
                    }

                    count = decoded.read(buffer, 0, buffer.length);
                    if (count < 0) {
                        reachedEnd = true;
                        break;
                    }

                    if (count > 0 && isCurrentPlayback(thisPlayback)) {
                        applyVolume(buffer, count, volume);
                        output.write(buffer, 0, count);
                    }
                }

                if (isCurrentPlayback(thisPlayback)) {
                    output.drain();
                }
            }
        } catch (Exception exception) {
            if (isCurrentPlayback(thisPlayback)) {
                System.err.println("[Crystal Radio] Could not play track: " + file);
                exception.printStackTrace(System.err);
            }
        } finally {
            closeLine(output);

            boolean shouldAdvance = false;
            synchronized (this) {
                if (thisPlayback == playbackId) {
                    line = null;
                    playing = false;
                    paused = false;
                    currentTrack = null;
                    worker = null;
                    shouldAdvance = reachedEnd;
                }
            }

            if (shouldAdvance) {
                playNextAutomatically(thisPlayback);
            }
        }
    }

    private void playNextAutomatically(long finishedPlaybackId) {
        Path nextTrack = null;

        synchronized (this) {
            if (playbackId != finishedPlaybackId || playlist.isEmpty()) {
                return;
            }

            switch (playbackMode) {
                case REPEAT_ONE -> {
                    if (playlistIndex >= 0 && playlistIndex < playlist.size()) {
                        nextTrack = playlist.get(playlistIndex);
                    }
                }
                case SHUFFLE -> {
                    if (playlist.size() == 1) {
                        nextTrack = playlist.get(0);
                    } else if (playlist.size() > 1) {
                        int nextIndex = ThreadLocalRandom.current().nextInt(playlist.size() - 1);
                        if (nextIndex >= playlistIndex) {
                            nextIndex++;
                        }
                        playlistIndex = nextIndex;
                        nextTrack = playlist.get(nextIndex);
                    }
                }
                case SEQUENTIAL -> {
                    int nextIndex = playlistIndex + 1;
                    if (nextIndex >= 0 && nextIndex < playlist.size()) {
                        playlistIndex = nextIndex;
                        nextTrack = playlist.get(nextIndex);
                    }
                }
            }
        }

        if (nextTrack != null) {
            play(nextTrack);
        }
    }

    private boolean isCurrentPlayback(long id) {
        return id == playbackId;
    }

    private void closeLine(SourceDataLine audioLine) {
        if (audioLine == null) {
            return;
        }

        try {
            audioLine.stop();
        } catch (RuntimeException ignored) {
        }

        try {
            audioLine.flush();
        } catch (RuntimeException ignored) {
        }

        try {
            audioLine.close();
        } catch (RuntimeException ignored) {
        }
    }

    public synchronized void togglePause() {
        if (!playing) {
            return;
        }

        paused = !paused;
        SourceDataLine activeLine = line;

        if (activeLine != null) {
            try {
                if (paused) {
                    activeLine.stop();
                } else {
                    activeLine.start();
                }
            } catch (RuntimeException exception) {
                System.err.println("[Crystal Radio] Could not change pause state.");
                exception.printStackTrace(System.err);
            }
        }
    }

    public synchronized void stop() {
        playbackId++;

        paused = false;
        playing = false;
        currentTrack = null;

        Thread oldWorker = worker;
        worker = null;

        SourceDataLine activeLine = line;
        line = null;

        closeLine(activeLine);

        if (oldWorker != null && oldWorker != Thread.currentThread()) {
            oldWorker.interrupt();
        }
    }

    public synchronized void setPlaylist(List<Path> tracks) {
        playlist = tracks.stream()
            .filter(path -> path != null)
            .map(path -> path.toAbsolutePath().normalize())
            .toList();

        Path current = currentTrack;
        if (current != null) {
            playlistIndex = playlist.indexOf(current);
        } else if (playlistIndex >= playlist.size()) {
            playlistIndex = -1;
        }
    }

    public synchronized PlaybackMode getPlaybackMode() {
        return playbackMode;
    }

    public synchronized PlaybackMode cyclePlaybackMode() {
        PlaybackMode[] modes = PlaybackMode.values();
        playbackMode = modes[(playbackMode.ordinal() + 1) % modes.length];
        return playbackMode;
    }

    public synchronized String getPlaybackModeLabel() {
        return switch (playbackMode) {
            case SEQUENTIAL -> "Порядок";
            case REPEAT_ONE -> "Повтор 1";
            case SHUFFLE -> "Случайно";
        };
    }

    public void setVolume(float newVolume) {
        volume = Math.max(0.0f, Math.min(1.0f, newVolume));
    }

    public float getVolume() {
        return volume;
    }

    // The decoded stream is signed 16-bit little-endian PCM.
    // Applying gain to the samples works even when the audio device does not
    // expose a hardware MASTER_GAIN control.
    private static void applyVolume(byte[] samples, int length, float gain) {
        if (gain >= 0.999f) {
            return;
        }

        for (int i = 0; i + 1 < length; i += 2) {
            int sample = (short) ((samples[i] & 0xFF) | (samples[i + 1] << 8));
            int adjusted = Math.round(sample * gain);
            adjusted = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, adjusted));
            samples[i] = (byte) (adjusted & 0xFF);
            samples[i + 1] = (byte) ((adjusted >>> 8) & 0xFF);
        }
    }

    public boolean isPlaying() {
        return playing;
    }

    public boolean isPaused() {
        return paused;
    }

    public Path getCurrentTrack() {
        return currentTrack;
    }
}
