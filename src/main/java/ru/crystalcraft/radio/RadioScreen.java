package ru.crystalcraft.radio;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class RadioScreen extends Screen {
    private static final int PAGE_SIZE = 6;
    private static final String[] AUDIO_EXTENSIONS = {".mp3", ".ogg", ".wav"};

    private final Path musicFolder;
    private final List<Path> tracks = new ArrayList<>();
    private int page = 0;
    private String message = "Положи музыку в папку radio";
    private Button previousPageButton;
    private Button nextPageButton;

    public RadioScreen() {
        super(Component.literal("Crystal Radio"));
        this.musicFolder = Minecraft.getInstance().gameDirectory.toPath().resolve("radio");
    }

    @Override
    protected void init() {
        loadTracks();
        int centerX = this.width / 2;
        int listWidth = Math.min(320, this.width - 40);
        int left = centerX - listWidth / 2;

        this.addRenderableWidget(Button.builder(Component.literal("+"), button -> openMusicFolder())
            .bounds(centerX + listWidth / 2 - 28, 28, 24, 22).build());
        this.addRenderableWidget(Button.builder(Component.literal("Обновить"), button -> refreshTracks())
            .bounds(left, 28, 82, 22).build());

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, tracks.size());
        int rowY = 98;
        for (int i = start; i < end; i++) {
            Path track = tracks.get(i);
            String fileName = track.getFileName().toString();
            this.addRenderableWidget(Button.builder(Component.literal("▶  " + fileName), button -> playTrack(track))
                .bounds(left, rowY, listWidth, 24).build());
            rowY += 29;
        }

        this.addRenderableWidget(Button.builder(Component.literal("⏮"), button -> playRelative(-1))
            .bounds(centerX - 100, this.height - 62, 44, 20).build());
        this.addRenderableWidget(Button.builder(
                Component.literal(MusicPlayer.getInstance().isPaused() ? "▶" : "Ⅱ"),
                button -> {
                    MusicPlayer.getInstance().togglePause();
                    rebuildWidgets();
                })
            .bounds(centerX - 52, this.height - 62, 44, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("■"), button -> {
                MusicPlayer.getInstance().stop();
                message = "Воспроизведение остановлено";
                rebuildWidgets();
            })
            .bounds(centerX - 4, this.height - 62, 44, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("⏭"), button -> playRelative(1))
            .bounds(centerX + 44, this.height - 62, 44, 20).build());

        this.previousPageButton = this.addRenderableWidget(Button.builder(Component.literal("Стр. <"), button -> {
            if (page > 0) {
                page--;
                rebuildWidgets();
            }
        }).bounds(centerX - 94, this.height - 34, 72, 20).build());
        this.nextPageButton = this.addRenderableWidget(Button.builder(Component.literal("Стр. >"), button -> {
            if (page < 3) {
                page++;
                rebuildWidgets();
            }
        }).bounds(centerX + 22, this.height - 34, 72, 20).build());
        updatePageButtons();
    }

    private void playTrack(Path track) {
        MusicPlayer.getInstance().play(track);
        message = "Запуск: " + track.getFileName();
    }

    private void playRelative(int direction) {
        if (tracks.isEmpty()) {
            message = "Сначала добавь музыку";
            return;
        }
        Path current = MusicPlayer.getInstance().getCurrentTrack();
        int index = current == null ? page * PAGE_SIZE : tracks.indexOf(current);
        if (index < 0) index = page * PAGE_SIZE;
        int next = Math.floorMod(index + direction, tracks.size());
        page = next / PAGE_SIZE;
        playTrack(tracks.get(next));
        rebuildWidgets();
    }

    private void rebuildWidgets() {
        clearWidgets();
        init();
    }

    private void updatePageButtons() {
        if (previousPageButton != null) previousPageButton.active = page > 0;
        if (nextPageButton != null) nextPageButton.active = page < 3;
    }

    private void refreshTracks() {
        loadTracks();
        rebuildWidgets();
    }

    private void loadTracks() {
        try {
            Files.createDirectories(musicFolder);
            tracks.clear();
            try (var files = Files.list(musicFolder)) {
                files.filter(Files::isRegularFile)
                    .filter(this::isAudioFile)
                    .sorted(Comparator.comparing(path ->
                        path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .forEach(tracks::add);
            }
            page = Math.min(page, 3);
            message = tracks.isEmpty() ? "Папка radio пока пустая"
                : "Найдено музыкальных файлов: " + tracks.size();
        } catch (IOException exception) {
            message = "Не удалось прочитать папку radio";
        }
    }

    private boolean isAudioFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        for (String extension : AUDIO_EXTENSIONS) {
            if (name.endsWith(extension)) return true;
        }
        return false;
    }

    private void openMusicFolder() {
        try {
            Files.createDirectories(musicFolder);
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(musicFolder.toFile());
                message = "Добавь музыку в папку и нажми «Обновить»";
            } else {
                message = "Папка: " + musicFolder.toAbsolutePath();
            }
        } catch (IOException | UnsupportedOperationException exception) {
            message = "Не удалось открыть папку: " + musicFolder.toAbsolutePath();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics, mouseX, mouseY, delta);
        int centerX = this.width / 2;
        int listWidth = Math.min(320, this.width - 40);
        graphics.drawCenteredString(this.font, "CRYSTAL RADIO", centerX, 12, 0xFFFFFF);
        graphics.drawCenteredString(this.font, "Моя музыка", centerX, 48, 0xFFFFFF);
        graphics.drawString(this.font, "Форматы: MP3, OGG, WAV",
            centerX - listWidth / 2, 76, 0xAAAAAA);
        super.render(graphics, mouseX, mouseY, delta);

        Path current = MusicPlayer.getInstance().getCurrentTrack();
        String nowPlaying = current == null ? message
            : (MusicPlayer.getInstance().isPaused() ? "Пауза: " : "Играет: ") + current.getFileName();
        graphics.drawCenteredString(this.font, nowPlaying, centerX, this.height - 45, 0xDDDDDD);
        graphics.drawCenteredString(this.font, (page + 1) + " / 4",
            centerX, this.height - 12, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        // Music intentionally continues after closing the radio screen.
        if (this.minecraft != null) this.minecraft.setScreen(null);
    }
}
