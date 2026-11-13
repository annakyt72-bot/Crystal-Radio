# Crystal Radio

Client-side Fabric mod for Minecraft 1.21.11.

## First milestone
- Open the library with `/radio`.
- The `+` button opens the game's `radio` folder.
- Supported file extensions: MP3, OGG, WAV, FLAC and M4A.
- Press **Обновить** after copying files into the folder.
- The library has page navigation and lists six tracks per page.

The folder is created inside the Minecraft game directory: `radio/`.

This first milestone implements the library UI and file discovery. Audio playback is not implemented yet.

## Build
GitHub Actions builds the mod on every push to `main`. Start it manually from the Actions tab with **Build Crystal Radio**. The compiled JAR is uploaded as an Actions artifact.
