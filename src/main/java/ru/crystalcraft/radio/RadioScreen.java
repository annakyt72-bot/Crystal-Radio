package ru.crystalcraft.radio;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
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
    private static final int MAX_PAGES = 4;
    private static final String[] AUDIO_EXTENSIONS = {".mp3", ".ogg", ".wav"};

    private final Path musicFolder;
    private final List<Path> tracks = new ArrayList<>();

    private int page;
    private String message = "Положи музыку в папку radio";

    private Button previousPageButton;
    private Button nextPageButton;
    private static final int CRYSTAL_DARK = 0xE90B0820;
    private static final int CRYSTAL_PANEL = 0xD51B1238;
    private static final int CRYSTAL_PURPLE = 0xFF8B5CF6;
    private static final int CRYSTAL_CYAN = 0xFF55DDF5;
    private static final int CRYSTAL_TEXT = 0xFFF4EEFF;
    private boolean draggingVolume;

    public RadioScreen() {
        super(Component.literal("Crystal Radio"));
        this.musicFolder = Minecraft.getInstance().gameDirectory.toPath()
            .resolve("radio")
            .toAbsolutePath()
            .normalize();
    }

    @Override
    protected void init() {
        loadTracks();
        createWidgets();
    }

    private void createWidgets() {
        int centerX = this.width / 2;
        int listWidth = Math.max(120, Math.min(300, this.width - 120));
        int left = centerX - listWidth / 2;

        this.addRenderableWidget(
            Button.builder(Component.literal("Обновить"), button -> refreshTracks())
                .bounds(left, 28, 88, 22)
                .build()
        );

        this.addRenderableWidget(
            Button.builder(Component.literal("+"), button -> openMusicFolder())
                .bounds(left + listWidth - 28, 28, 28, 22)
                .build()
        );

        int firstTrack = page * PAGE_SIZE;
        int lastTrack = Math.min(firstTrack + PAGE_SIZE, Math.min(tracks.size(), PAGE_SIZE * MAX_PAGES));
        int rowY = 100;

        for (int index = firstTrack; index < lastTrack; index++) {
            Path track = tracks.get(index);
            String fileName = track.getFileName().toString();

            this.addRenderableWidget(
                Button.builder(Component.literal("▶  " + fileName), button -> playTrack(track))
                    .bounds(left, rowY, listWidth, 24)
                    .build()
            );

            rowY += 27;
        }

        this.addRenderableWidget(
            Button.builder(Component.literal("⏮"), button -> playRelative(-1))
                .bounds(centerX - 100, this.height - 62, 44, 20)
                .build()
        );

        this.addRenderableWidget(
            Button.builder(
                    Component.literal(MusicPlayer.getInstance().isPaused() ? "▶" : "Ⅱ"),
                    button -> {
                        MusicPlayer.getInstance().togglePause();
                        rebuildRadioWidgets();
                    }
                )
                .bounds(centerX - 52, this.height - 62, 44, 20)
                .build()
        );

        this.addRenderableWidget(
            Button.builder(Component.literal("■"), button -> {
                    MusicPlayer.getInstance().stop();
                    message = "Воспроизведение остановлено";
                    rebuildRadioWidgets();
                })
                .bounds(centerX - 4, this.height - 62, 44, 20)
                .build()
        );

        this.addRenderableWidget(
            Button.builder(Component.literal("⏭"), button -> playRelative(1))
                .bounds(centerX + 44, this.height - 62, 44, 20)
                .build()
        );

        this.previousPageButton = this.addRenderableWidget(
            Button.builder(Component.literal("Стр. <"), button -> changePage(-1))
                .bounds(centerX - 94, this.height - 34, 72, 20)
                .build()
        );

        this.nextPageButton = this.addRenderableWidget(
            Button.builder(Component.literal("Стр. >"), button -> changePage(1))
                .bounds(centerX + 22, this.height - 34, 72, 20)
                .build()
        );

        updatePageButtons();
    }

    private void changePage(int direction) {
        int pageCount = getPageCount();
        int newPage = page + direction;

        if (newPage >= 0 && newPage < pageCount) {
            page = newPage;
            rebuildRadioWidgets();
        }
    }

    private void playTrack(Path track) {
        if (track == null || !Files.isRegularFile(track)) {
            message = "Файл не найден. Нажми «Обновить».";
            return;
        }

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

        if (index < 0) {
            index = page * PAGE_SIZE;
        }

        int nextIndex = Math.floorMod(index + direction, tracks.size());
        page = nextIndex / PAGE_SIZE;
        playTrack(tracks.get(nextIndex));
        rebuildRadioWidgets();
    }

    private void rebuildRadioWidgets() {
        // Rebuild only the widgets. Do not call Screen.init() manually.
        clearWidgets();
        createWidgets();
    }

    private void updatePageButtons() {
        if (previousPageButton != null) {
            previousPageButton.active = page > 0;
        }

        if (nextPageButton != null) {
            nextPageButton.active = page + 1 < getPageCount();
        }
    }

    private int getPageCount() {
        int visibleTrackCount = Math.min(tracks.size(), PAGE_SIZE * MAX_PAGES);
        return Math.max(1, (visibleTrackCount + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private void refreshTracks() {
        loadTracks();
        rebuildRadioWidgets();
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
                    .limit(PAGE_SIZE * MAX_PAGES)
                    .forEach(tracks::add);
            }

            page = Math.min(page, getPageCount() - 1);
            message = tracks.isEmpty()
                ? "Папка radio пока пустая"
                : "Найдено файлов: " + tracks.size();
        } catch (IOException | SecurityException exception) {
            tracks.clear();
            page = 0;
            message = "Не удалось прочитать папку radio";
            System.err.println("[Crystal Radio] Cannot read music folder: " + musicFolder);
            exception.printStackTrace(System.err);
        }
    }

    private boolean isAudioFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);

        for (String extension : AUDIO_EXTENSIONS) {
            if (name.endsWith(extension)) {
                return true;
            }
        }

        return false;
    }

    private void openMusicFolder() {
        try {
            Files.createDirectories(musicFolder);

            if (!Desktop.isDesktopSupported()) {
                message = "Папка radio: " + musicFolder;
                return;
            }

            Desktop.getDesktop().open(musicFolder.toFile());
            message = "Добавь музыку и нажми «Обновить»";
        } catch (IOException | RuntimeException exception) {
            message = "Не удалось открыть папку radio";
            System.err.println("[Crystal Radio] Cannot open music folder: " + musicFolder);
            exception.printStackTrace(System.err);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        // Do not call Screen.renderBackground here: in this Minecraft build it
        // can request the blur effect more than once in a frame and crash with
        // "Can only blur once per frame". A simple translucent fill is safe.
        graphics.fill(0, 0, this.width, this.height, 0xE8050614);

        int centerX = this.width / 2;
        int listWidth = Math.max(120, Math.min(300, this.width - 120));
        int panelLeft = centerX - listWidth / 2 - 36;
        int panelRight = centerX + listWidth / 2 + 12;

        // CrystalCraft-inspired purple crystal panel with cyan accents.
        graphics.fill(panelLeft, 6, panelRight, this.height - 6, CRYSTAL_DARK);
        graphics.fill(panelLeft, 6, panelRight, 8, CRYSTAL_CYAN);
        graphics.fill(panelLeft, 8, panelLeft + 2, this.height - 8, CRYSTAL_PURPLE);
        graphics.fill(panelRight - 2, 8, panelRight, this.height - 8, CRYSTAL_PURPLE);
        graphics.fill(panelLeft + 2, 9, panelRight - 2, this.height - 9, CRYSTAL_PANEL);

        graphics.drawCenteredString(this.font, "✦ CRYSTAL RADIO ✦", centerX, 15, CRYSTAL_CYAN);
        graphics.drawCenteredString(this.font, "Моя музыка", centerX, 56, CRYSTAL_TEXT);
        graphics.drawString(
            this.font,
            "Форматы: MP3, OGG, WAV",
            centerX - listWidth / 2,
            80,
            CRYSTAL_CYAN
        );

        // Vertical volume control on the left: + at the top, - at the bottom.
        int volumeX = panelLeft + 18;
        int volumeTop = 132;
        int volumeHeight = Math.min(150, Math.max(90, this.height - 270));
        int volumeBottom = volumeTop + volumeHeight;
        float volume = MusicPlayer.getInstance().getVolume();
        int filledTop = volumeBottom - Math.round(volumeHeight * volume);

        graphics.drawCenteredString(this.font, "+", volumeX + 5, volumeTop - 25, CRYSTAL_CYAN);
        graphics.fill(volumeX + 2, volumeTop, volumeX + 8, volumeBottom, 0xFF392B58);
        graphics.fill(volumeX + 2, filledTop, volumeX + 8, volumeBottom, CRYSTAL_PURPLE);
        graphics.fill(volumeX, filledTop - 3, volumeX + 10, filledTop + 3, CRYSTAL_CYAN);
        graphics.drawCenteredString(this.font, "-", volumeX + 5, volumeBottom + 8, CRYSTAL_CYAN);
        graphics.drawCenteredString(this.font, Math.round(volume * 100) + "%", volumeX + 5, volumeBottom + 22, CRYSTAL_TEXT);

        // Accent strips behind the main controls.
        graphics.fill(centerX - 108, this.height - 66, centerX + 108, this.height - 40, 0xFF342253);
        graphics.fill(centerX - 96, this.height - 37, centerX + 96, this.height - 35, CRYSTAL_CYAN);

        super.render(graphics, mouseX, mouseY, delta);

        Path current = MusicPlayer.getInstance().getCurrentTrack();
        String nowPlaying;

        if (current == null) {
            nowPlaying = message;
        } else if (MusicPlayer.getInstance().isPaused()) {
            nowPlaying = "Пауза: " + current.getFileName();
        } else {
            nowPlaying = "Играет: " + current.getFileName();
        }

        graphics.drawCenteredString(this.font, nowPlaying, centerX, this.height - 45, CRYSTAL_TEXT);
        graphics.drawCenteredString(
            this.font,
            (page + 1) + " / " + getPageCount(),
            centerX,
            this.height - 12,
            CRYSTAL_CYAN
        );
    }

    private void setVolumeFromMouse(double mouseY) {
        int volumeTop = 132;
        int volumeHeight = Math.min(150, Math.max(90, this.height - 270));
        float value = 1.0f - (float) (mouseY - volumeTop) / volumeHeight;
        MusicPlayer.getInstance().setVolume(value);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubleClick) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        int panelLeft = this.width / 2 - Math.max(120, Math.min(300, this.width - 120)) / 2 - 36;
        int volumeX = panelLeft + 18;
        int volumeTop = 132;
        int volumeBottom = volumeTop + Math.min(150, Math.max(90, this.height - 270));

        if (button == 0 && mouseX >= volumeX - 8 && mouseX <= volumeX + 18
            && mouseY >= volumeTop && mouseY <= volumeBottom) {
            draggingVolume = true;
            setVolumeFromMouse(mouseY);
            return true;
        }

        if (button == 0 && mouseX >= volumeX - 12 && mouseX <= volumeX + 22
            && mouseY >= volumeTop - 30 && mouseY < volumeTop) {
            MusicPlayer.getInstance().setVolume(MusicPlayer.getInstance().getVolume() + 0.05f);
            return true;
        }

        if (button == 0 && mouseX >= volumeX - 12 && mouseX <= volumeX + 22
            && mouseY > volumeBottom && mouseY <= volumeBottom + 24) {
            MusicPlayer.getInstance().setVolume(MusicPlayer.getInstance().getVolume() - 0.05f);
            return true;
        }

        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double dragX, double dragY) {
        if (draggingVolume && click.button() == 0) {
            setVolumeFromMouse(click.y());
            return true;
        }

        return super.mouseDragged(click, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (click.button() == 0 && draggingVolume) {
            draggingVolume = false;
            return true;
        }

        return super.mouseReleased(click);
    }

    @Override
    public void onClose() {
        // Music continues after closing the screen.
        super.onClose();
    }
}
