<p align="center">
  <img src="logo.svg" width="128" alt="PreGlide logo">
</p>

<h1 align="center">PreGlide</h1>

<p align="center">An Android keyboard that learns how you type.</p>

<p align="center">
  Russian & English · Glide typing · On-device predictions · Android 11+
</p>

This repository holds the source of the free version and its releases. Premium's sync is closed source.

## Download

Get the APK from [Releases](https://github.com/AlexWayfer/PreGlide/releases).

Open PreGlide after installing it and follow the setup steps to enable the keyboard.

The app checks GitHub Releases for new versions and can download and install an update with your confirmation.
You can also check manually in **Updates**. Allow notifications to hear about new releases in the background.

## Features

### Typing

- Russian and English layouts, with a swipe on the space bar to switch languages
- Glide typing: slide across the letters without lifting your finger
- Autocorrect, word suggestions and a personal dictionary that learns from your typing
- Neural next-word predictions that run entirely on your device
- Cursor control with space bar gestures and word deletion with a backspace swipe
- Digits, punctuation and extra letters through long presses
- Optional system spell checker for other apps

### Emoji & clipboard

- Emoji search in Russian and English, skin tones and recently used emoji
- Clipboard history for text and images, kept for an hour
- Fresh screenshots offered for pasting, with optional photo access

### Make it yours

- Light, dark and system themes
- Colors from your wallpaper or a palette of your choice
- Key height, key backgrounds, fonts and vibration settings
- Adjustable gestures, suggestions and animations

## Privacy

Typing, autocorrect and predictions work offline. Dictionaries and prediction models are included; your typing
stays on your device. The free version uses the Internet to check for and download updates from GitHub.
Learned words are excluded from Android cloud backups; Android 12 and newer can carry them directly to a new
device during a device transfer.

If the app crashes or freezes, it offers to share a diagnostic report with the developer. Sending it is your
choice; reports do not contain what you typed.

## Premium

Premium syncs learned words between your Android devices and [PreType](https://pretype.alexwayfer.name) on Windows
through your Google Drive. Words are encrypted on your device; only devices with your sync key can read them.
The same purchase unlocks Premium in both apps. Open **Sync** in PreGlide's settings for the purchase links.

## Building

Open the project in Android Studio, select `freeDebug` and run it. No API keys or account setup are needed.
The Gradle daemon uses JDK 25; Gradle's toolchain resolver can download it.

Install [Git LFS](https://git-lfs.com/) before cloning, or run `git lfs pull` afterward to fetch the prediction
models. An archive that contains only LFS pointers is not enough to run predictions.

With the Android SDK configured and a suitable JDK available:

```sh
./gradlew assembleFreeDebug
./gradlew :engine:test :app:testFreeDebugUnitTest
```

The `engine` module contains the keyboard logic without Android. `dictionary-builder` holds the tools that
generate the bundled dictionaries and emoji data. Models and dictionaries are already included in the assets;
building the app does not train a model or download a corpus.

## Contributing

Issues with bugs and ideas are welcome. Pull requests are not accepted: the same code goes into the closed
Premium build.

## License

The free version's source is available under [GPL-3.0](LICENSE). Bundled libraries, fonts, icons and dictionary
data keep their own licenses; the app's **Licenses** page lists them and their notices.

The dictionaries and prediction models use [FineWeb](https://huggingface.co/datasets/HuggingFaceFW/fineweb),
[FineWeb-2](https://huggingface.co/datasets/HuggingFaceFW/fineweb-2) and [Common Crawl](https://commoncrawl.org/).
Russian also uses [OpenSubtitles.org](https://www.opensubtitles.org/) through the OPUS OpenSubtitles corpus:
P. Lison and J. Tiedemann, 2016, *OpenSubtitles2016: Extracting Large Parallel Corpora from Movie and TV Subtitles*,
LREC 2016. See [dictionary sources and licenses](dictionary-builder/README.md#sources-and-license) for details.
