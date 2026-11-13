package ru.crystalcraft.radio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import java.nio.file.Path;

public final class MusicPlayer {
    private static final MusicPlayer INSTANCE = new MusicPlayer();

    private volatile SourceDataLine line;
    private volatile Thread worker;
    private volatile long playbackId;
    private volatile boolean stopRequested;
    private volatile boolean paused;
    private volatile boolean playing;
    private volatile Path currentTrack;

    private MusicPlayer() {}

    public static MusicPlayer getInstance() {
        return INSTANCE;
    }

    public synchronized void play(Path file) {
        stop();
        stopRequested = false;
        paused = false;
        playing = true;
        currentTrack = file;
        long thisPlayback = ++playbackId;

        worker = new Thread(() -> playFile(file, thisPlayback), "CrystalRadio-Audio");
        worker.setDaemon(true);
        worker.start();
    }

    private void playFile(Path file, long thisPlayback) {
        SourceDataLine output = null;
        try (AudioInputStream source = AudioSystem.getAudioInputStream(file.toFile())) {
            AudioFormat sourceFormat = source.getFormat();
            AudioFormat pcmFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                sourceFormat.getSampleRate(),
                16,
                sourceFormat.getChannels(),
                sourceFormat.getChannels() * 2,
                sourceFormat.getSampleRate(),
                false
            );

            try (AudioInputStream decoded = AudioSystem.getAudioInputStream(pcmFormat, source)) {
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, pcmFormat);
                output = (SourceDataLine) AudioSystem.getLine(info);
                if (thisPlayback != playbackId || stopRequested) return;
                line = output;
                output.open(pcmFormat);
                output.start();

                byte[] buffer = new byte[8192];
                int count;
                while (!stopRequested && thisPlayback == playbackId
                        && (count = decoded.read(buffer, 0, buffer.length)) != -1) {
                    while (paused && !stopRequested && thisPlayback == playbackId) {
                        Thread.sleep(50);
                    }
                    if (!stopRequested && thisPlayback == playbackId) {
                        output.write(buffer, 0, count);
                    }
                }

                if (!stopRequested && thisPlayback == playbackId) {
                    output.drain();
                }
            }
        } catch (Exception exception) {
            if (thisPlayback == playbackId && !stopRequested) {
                System.err.println("[Crystal Radio] Could not play " + file + ": " + exception.getMessage());
            }
        } finally {
            if (output != null) {
                output.stop();
                output.close();
            }
            if (thisPlayback == playbackId) {
                line = null;
                playing = false;
                paused = false;
                currentTrack = null;
                worker = null;
            }
        }
    }

    public synchronized void togglePause() {
        if (!playing) return;
        paused = !paused;
        SourceDataLine activeLine = line;
        if (activeLine != null) {
            if (paused) activeLine.stop();
            else activeLine.start();
        }
    }

    public synchronized void stop() {
        playbackId++;
        stopRequested = true;
        paused = false;
        SourceDataLine activeLine = line;
        line = null;
        if (activeLine != null) {
            activeLine.stop();
            activeLine.flush();
            activeLine.close();
        }
        playing = false;
        currentTrack = null;
        worker = null;
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
