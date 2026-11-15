# Easy TV: a simple IPTV player for Chromecast with Google TV

A clean, remote-first IPTV app. It has no ads, no accounts and no clutter.

## What it does
- **Setup from your phone:** on first launch the TV shows an address such as `http://192.168.1.20:8642`. Open it on your phone, paste your M3U link and tap *Add to TV*. You don't have to type with the remote.
- **Category sidebar:** moving onto a category shows its channels straight away.
- **Big channel tiles** with logos (or initials when a channel has no logo) and a search bar.
- **Favourites:** hold OK on a channel to add it. Favourites are always at the top of the sidebar.
- **Continue watching:** one button takes you back to the last channel you watched.
- **Player:** ▲/▼ (or ◀/▶, CH+/CH−) changes channel instantly and wraps around the list. OK shows channel info. BACK returns to the list.
- Plays M3U/M3U8 playlists, HLS (`.m3u8`) and MPEG-TS streams. You can add several playlists and they are merged. Playlists are cached for 1 hour so the app starts fast.

**Xtream Codes login?** Build the M3U link like this:
`http://SERVER:PORT/get.php?username=USER&password=PASS&type=m3u_plus&output=ts`

## Get the APK

**Option A: GitHub (no install needed)**
1. Create a private GitHub repo and push this folder to it.
2. Go to **Actions → Build APK**. It runs on every push.
3. When it finishes, download **EasyTV-apk** from the run page.

**Option B: Android Studio**
Open the folder, then choose **Build → Build APK(s)**. The APK is saved as `app/build/outputs/apk/debug/app-debug.apk`.

## Install on the Chromecast
1. On the Chromecast, go to **Settings → System → About** and click **Android TV OS build** 7 times. This turns on Developer options.
2. Go to **Settings → System → Developer options** and turn on **USB debugging** / **Network debugging**.
3. Install the APK in one of two ways:
   - **Easiest:** install **Downloader** (by AFTVnews) from the Play Store, upload the APK to Google Drive or Dropbox, and open the link in Downloader. Allow "Install unknown apps" for Downloader when asked.
   - **From a PC:** run `adb connect <chromecast-ip>:5555` and then `adb install app-debug.apk`.
4. **Easy TV** now appears in your Apps row.

## Project layout
| File | Purpose |
|---|---|
| `data/M3uParser.kt` | Parses `#EXTINF` (name, group-title, tvg-logo, tvg-id) |
| `data/PlaylistRepository.kt` | Playlists, download and cache, favourites, last watched |
| `ui/BrowseScreen.kt` | Sidebar, channel grid and search |
| `ui/SettingsScreen.kt` | Add or remove playlists (remote or phone) |
| `util/PhoneSetupServer.kt` | Tiny local web page for setup from your phone |
| `PlayerActivity.kt` | ExoPlayer full-screen player with fast-zap buffering |
