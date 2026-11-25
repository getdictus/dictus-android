# Voice note test audio (#204)

`tone-440hz-1500ms.ogg`: 1.5 s of a 440 Hz sine at ffmpeg's default amplitude (1/8), mono, Opus in Ogg at 16 kb/s, 48 kHz (the container and codec of a WhatsApp voice note). Synthesized, no recording involved:

```bash
ffmpeg -f lavfi -i "sine=frequency=440:sample_rate=48000:duration=1.5" -ac 1 -c:a libopus -b:a 16k \
  -application audio -map_metadata -1 -fflags +bitexact -flags:a +bitexact tone-440hz-1500ms.ogg
```

Made with ffmpeg 6.1.1 (libopus). Read by the JVM tests (`src/test`, as a resource) and by the instrumented decoder test (`src/androidTest`, as an asset).
