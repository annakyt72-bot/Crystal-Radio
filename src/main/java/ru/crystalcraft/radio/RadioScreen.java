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
    private static final String[] AUDIO_EXTENSIONS = {".mp3", ".ogg", ".wav", ".flac", ".m4a"};

    private final Path musicFolder;
    private final List<Path> tracks = new ArrayList<>();
    private int page = 0;
    private String message = "Положи музыку в папку radio";
    private Button previousButton;
    private Button nextButton;

    public RadioScreen() {
        super(Component.literal("Crystal Radio"));
        this.musicFolder = Minecraft.getInstance().gameDirectory.toPath().resolve("radio");
    }

    @Override
    protected void init() {
        refreshTracks();

        int centerX = this.width / 2;
        int listWidth = Math.min(320, this.width - 40);
        int left = centerX - listWidth / 2;

        this.addRenderableWidget(Button.builder(Component.literal("+"), button -> openMusicFolder())
            .bounds(centerX + listWidth / 2 - 28, 28, 24, 22).build());

        this.addRenderableWidget(Button.builder(Component.literal("Обновить"), button -> refreshTracks())
            .bounds(left, this.height - 34, 90, 20).build());

        this.previousButton = this.addRenderableWidget(Button.builder(Component.literal("<"), button -> {
            if (page > 0) {
                page--;
                rebuildWidgets();
            }
        }).bounds(centerX - 55, this.height - 34, 24, 20).build());

        this.nextButton = this.addRenderableWidget(Button.builder(Component.literal(">"), button -> {
            if ((page + 1) * PAGE_SIZE < tracks.size()) {
                page++;
                rebuildWidgets();
            }
        }).bounds(centerX + 31, this.height - 34, 24, 20).build());

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, tracks.size());
        int rowY = 98;

        for (int i = start; i < end; i++) {
            Path track = tracks.get(i);
            String fileName = track.getFileName().toString();
            this.addRenderableWidget(Button.builder(Component.literal(fileName), button -> {
                message = "Выбрано: " + fileName;
            }).bounds(left, rowY, listWidth, 24).build());
            rowY += 30;
        }

        updatePageButtons();
    }

    private void rebuildWidgets() {
        clearWidgets();
        init();
    }

    private void updatePageButtons() {
        if (previousButton != null) previousButton.active = page > 0;
        if (nextButton != null) nextButton.active = (page + 1) * PAGE_SIZE < tracks.size();
    }

    private void refreshTracks() {
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
            int maxPage = Math.max(0, (tracks.size() - 1) / PAGE_SIZE);
            page = Math.min(page, maxPage);
            message = tracks.isEmpty() ? "Папка radio пока пустая"
                : "Найдено музыкальных файлов: " + tracks.size();
        } catch (IOException exception) {
            message = "Не удалось прочитать папку radio";
        }
        if (this.minecraft != null) rebuildWidgets();
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
        graphics.drawString(this.font, "Форматы: MP3, OGG, WAV, FLAC, M4A",
            centerX - listWidth / 2, 76, 0xAAAAAA);

        super.render(graphics, mouseX, mouseY, delta);
        graphics.drawCenteredString(this.font, message, centerX, this.height - 58, 0xDDDDDD);

        int totalPages = Math.max(1, (tracks.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        graphics.drawCenteredString(this.font, (page + 1) + " / " + totalPages,
            centerX, this.height - 12, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(null);
    }
}
