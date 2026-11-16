# PixelDL

A pixel-art video downloader for Android. No ads, no tracking, no account.

Paste a link from YouTube, Instagram, Facebook, TikTok, X or 1000+ other sites,
pick a quality (up to 1080p, or MP3 audio only), and the video saves straight to
your phone (Movies > PixelDL, audio to Music > PixelDL).

Built with Kotlin + Jetpack Compose, powered by the open-source
[yt-dlp](https://github.com/yt-dlp/yt-dlp) engine. The APK is built by GitHub
Actions on every push and published to the Releases page.

Made by Ali Nawaz.

## Development

PixelDL is an Android app written in **Kotlin** with **Jetpack Compose**, using a pixel-art UI theme (Press Start 2P and VT323 fonts).

- **Download engine:** yt-dlp running on-device through the youtubedl-boom-lite library (Python 3.12, bundled yt-dlp and FFmpeg). It lists the available formats for a link, downloads the quality the user picks, and can extract audio as MP3.
- **Saving:** finished files are saved through Android MediaStore to Movies/PixelDL (videos) and Music/PixelDL (audio), with a download history kept in the app.
- **Build and release:** GitHub Actions builds the APK on every push to `main` and publishes it to GitHub Releases. Builds are signed with a fixed keystore kept in the repo as `keystore.b64`, so every new release installs cleanly over the previous one.
- **Latest release:** v1.1 — replaced the download engine after v1.0's engine broke on newer yt-dlp versions.
