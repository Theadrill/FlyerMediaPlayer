# FlyerMediaPlayer - Digital Signage for Android TV

FlyerMediaPlayer is a robust digital signage solution designed for Android TV (TV Boxes). It automates the playback of promotional flyers and entertainment content (like music videos or football matches) directly from a USB drive.

## Features

- **Intelligent Playback Cycle**: Automatically alternates between 1 house flyer and 2 random entertainment videos.
- **8-Minute Limit**: Entertainment videos are automatically limited to 8 minutes each to ensure promotional flyers appear frequently, while flyers always play to completion.
- **Smart Shuffling**: Implements a non-repeating shuffle queue for both flyers and videos. No content is repeated until the entire playlist has been seen.
- **Automatic Boot Start**: The app automatically launches when the Android TV device is powered on.
- **USB Plug-and-Play**:
  - Place your flyers containing "MARIA" in the name at the **root** of the USB drive.
  - Place all other entertainment videos inside a folder named `VIDEOS` (or `videos`).
- **Always-On Display**: Prevents the screen from dimming or sleeping during playback.

## How it Works

1. **Scan**: On startup, the app scans the connected USB drive.
2. **Flyers**: It identifies files with "MARIA" in their name on the USB root.
3. **Videos**: It identifies all video files inside the `/VIDEOS` folder.
4. **Logic**: 
   - Play 1 Flyer (Full length).
   - Play 2 Random Videos (Max 8 minutes each).
   - Repeat.

## Technical Details

- Built with **Kotlin** and **Android Media3 (ExoPlayer)**.
- Target SDK: **35**.
- Requires `READ_EXTERNAL_STORAGE` or `READ_MEDIA_VIDEO` permissions.

---
*Developed for MARIA Digital Signage.*
