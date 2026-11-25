# Asset map

`image_3.png` is mapped to the user-selected `icon512_rounded.png`; `image_2.png` to the maskable icon; `image_1.png` retained as the basic icon. `image_4.jpg` becomes the default `hintergrund.jpg`; `image_6.png` becomes the sleep note.

The 832×1248 `image_5.png` is retained in `design/source-assets/background_sleep_original.png`. Android runtime uses a WebP quality-90 derivative in `drawable-nodpi/background_sleep.webp`: 135,686 bytes versus 1,753,676 bytes (about 92% smaller, same dimensions). Original appearance is preserved as a source asset.

The uploaded MP3 bytes were copied unchanged to `assets/limit.mp3` and `assets/gutenacht.mp3` plus Android `res/raw` resources; both were recognized by ffprobe as 24 kHz mono MP3.
