# DJ SEBAS EDICIÓN CATCURTIS

Android video editor project inspired by modern mobile editors.

## Build

Open with Android Studio or run `./gradlew assembleDebug`.

The GitHub Actions workflow builds a debug APK and publishes it as an artifact.

## Current features (1.1.0)

- Dark editing-studio interface
- Import multiple videos, photos and audio files using Android's system picker
- Media bin and ordered timeline list
- Preview of the selected video
- Start/end trimming for the selected video
- Sequential export of all imported video clips into one MP4, in their timeline/import order
- The selected video's trim range is applied during project export
- Export saved under `Movies/DJ SEBAS CATCURTIS`

## Still pending

- Drag-and-drop clip reordering and a true multi-track timeline
- Adding imported music as a separate soundtrack in the exported composition
- Photo clips in the exported timeline
- Text, titles, stickers, transitions, filters and visual effects
- Undo/redo, speed controls, keyframes and advanced audio controls

This is an independent application package: `com.djsebas.catcurtis`.
