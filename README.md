# DJ SEBAS EDICIÓN CATCURTIS

Android video editor project inspired by modern mobile editors.

## Build

Open with Android Studio or run `./gradlew assembleDebug`.

The GitHub Actions workflow builds a debug APK and publishes it as an artifact.

## Current features (1.3.0)

- Dark editing-studio interface
- Import multiple videos, photos and audio files using Android's system picker
- Media bin and ordered timeline list with move-up/move-down controls
- Preview of the selected video
- Start/end trimming for the selected video
- Sequential export of video clips and still photos in timeline order
- Each photo is rendered for five seconds in the exported MP4
- The selected video's trim range is applied during project export
- The first imported audio file is mixed as a looping background soundtrack until the visual montage ends
- Export saved under `Movies/DJ SEBAS CATCURTIS`

## Still pending

- True multi-track timeline and drag-and-drop clip reordering
- Text, titles, stickers, transitions, filters and visual effects
- Undo/redo, speed controls, keyframes and advanced audio controls

Photos are currently exported as five-second still-image segments. The first imported audio file is used as a looping soundtrack during export; additional independent audio tracks and volume automation remain pending.

This is an independent application package: `com.djsebas.catcurtis`.
