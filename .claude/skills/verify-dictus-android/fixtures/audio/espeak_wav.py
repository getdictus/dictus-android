# Synthesize text to a 16-bit mono WAV through libespeak-ng (ctypes, stdlib only).
import ctypes, sys, wave
lib = ctypes.CDLL("libespeak-ng.so.1")
text, voice, out, rate = sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4]) if len(sys.argv) > 4 else 150
SYNCH = 0x0002  # AUDIO_OUTPUT_SYNCHRONOUS
sr = lib.espeak_Initialize(SYNCH, 0, None, 0)
pcm = bytearray()
CB = ctypes.CFUNCTYPE(ctypes.c_int, ctypes.POINTER(ctypes.c_short), ctypes.c_int, ctypes.c_void_p)
def cb(wav, n, events):
    if wav and n > 0: pcm.extend(ctypes.string_at(wav, n * 2))
    return 0
cbf = CB(cb); lib.espeak_SetSynthCallback(cbf)
assert lib.espeak_SetVoiceByName(voice.encode()) == 0, "voice"
lib.espeak_SetParameter(1, rate, 0)  # espeakRATE
b = text.encode("utf-8")
lib.espeak_Synth(b, len(b) + 1, 0, 0, 0, 0x01, None, None)  # espeakCHARS_UTF8
lib.espeak_Synchronize()
with wave.open(out, "wb") as w:
    w.setnchannels(1); w.setsampwidth(2); w.setframerate(sr); w.writeframes(bytes(pcm))
print(out, sr, len(pcm) // 2)
