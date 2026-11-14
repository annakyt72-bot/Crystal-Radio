package ru.crystalcraft.radio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import java.nio.file.Files;
import java.nio.file.Path;

public final class MusicPlayer {
    private static final MusicPlayer INSTANCE = new MusicPlayer();

    private volatile SourceDataLine line;
    private volatile Thread worker;
    private volatile long playbackId;
    private volatile boolean paused;
    private volatile boolean playing;
    private volatile Path currentTrack;
    private volatile float volume = 0.70f;

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

            synchronized (this) {
                if (thisPlayback == playbackId) {
                    line = null;
                    playing = false;
                    paused = false;
                    currentTrack = null;
                    worker = null;
                }
            }
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
