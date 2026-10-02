#!/usr/bin/env python3
"""Synthesises the music loop and the sound effects into app/src/main/res/raw.

Needs numpy and ffmpeg. Deterministic: rerunning gives the same audio.

The loop is written to be easy on the ears over long sessions: slow 3/4 in D Dorian, soft plucked
strings with nothing above 5 kHz, a quiet recorder melody that rests for a third of the loop, no
percussion, and 80 seconds before anything repeats. Note tails and reverb wrap around the end, so
the loop point is seamless.
"""
import pathlib
import subprocess
import wave

import numpy as np

SR = 44100
OUT = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/res/raw"
BEAT = 60 / 72
BARS = 32
N = round(BARS * 3 * BEAT * SR)
rng = np.random.default_rng(7)


def hz(midi):
    return 440.0 * 2 ** ((midi - 69) / 12)


def pluck(midi, dur=2.6, vel=1.0):
    """Lute-like string: harmonics that die away faster the higher they are."""
    f = hz(midi)
    t = np.arange(int(dur * SR)) / SR
    out = np.zeros_like(t)
    k = 1
    while k * f < 5000:
        amp = np.sin(np.pi * k * 0.22) / k ** 1.3
        out += amp * np.exp(-t * (1.6 + 0.35 * k * k * f / 220)) * np.sin(2 * np.pi * k * f * t)
        k += 1
    return vel * out * np.minimum(1, t / 0.004) * np.minimum(1, (dur - t) / 0.05)


def recorder(midi, dur):
    """Soft flute: nearly a sine, slow attack, a little late vibrato."""
    f = hz(midi)
    t = np.arange(int(dur * SR)) / SR
    vibrato = 0.003 * np.clip((t - 0.25) / 0.4, 0, 1) * np.sin(2 * np.pi * 4.6 * t)
    phase = 2 * np.pi * f * np.cumsum(1 + vibrato) / SR
    tone = np.sin(phase) + 0.22 * np.sin(2 * phase) + 0.06 * np.sin(3 * phase)
    return tone * np.minimum(1, t / 0.07) * np.minimum(1, (dur - t) / 0.14)


def add(buf, at, sig):
    """Mixes sig into the loop at `at` seconds, wrapping past the end."""
    buf[(int(at * SR) + np.arange(len(sig))) % len(buf)] += sig


def reverb(x, delays, gain=0.72):
    """Parallel feedback combs. Run over two laps so the tail of the loop feeds its start."""
    x2 = np.concatenate([x, x])
    wet = np.zeros_like(x2)
    for d in delays:
        y = x2.copy()
        for i in range(d, len(y), d):
            y[i:i + d] += gain * y[i - d:i - d + len(y[i:i + d])]
        wet += y
    return wet[len(x):] / len(delays)


# Chords as (bass, fifth, octave, third) MIDI notes.
DM, C, F, AM, G = (50, 57, 62, 65), (48, 55, 60, 64), (53, 60, 65, 69), (45, 52, 57, 60), (43, 50, 55, 59)
A_CHORDS = [DM, DM, C, DM, F, C, DM, DM]
CHORDS = A_CHORDS + [DM, AM, C, DM, F, G, AM, DM] + [F, C, DM, AM, F, C, DM, AM] + A_CHORDS

# Melody as (MIDI note or None for a rest, beats); three beats per bar.
A_TUNE = [
    (62, 2), (65, 1), (69, 2), (67, 1), (64, 1), (67, 1), (64, 1), (62, 3),
    (65, 2), (69, 1), (67, 1), (64, 1), (60, 1), (62, 1), (64, 1), (65, 1), (62, 2), (None, 1),
]
TUNE = A_TUNE + [
    (69, 2), (74, 1), (72, 1), (71, 1), (69, 1), (67, 2), (64, 1), (65, 1), (64, 1), (62, 1),
    (65, 1), (67, 1), (69, 1), (71, 2), (67, 1), (69, 1), (67, 1), (64, 1), (62, 3),
] + [(None, 24)] + A_TUNE


def music():
    lute = np.zeros(N)
    flute = np.zeros(N)
    for bar, (bass, fifth, octave, third) in enumerate(CHORDS):
        melody_rests = 16 <= bar < 24
        # Eighth-note arpeggio; sparser while the melody rests so the middle section breathes.
        pattern = [(0, bass, 1.0), (2, fifth, 0.5), (3, octave, 0.6), (4, third, 0.55)] if melody_rests else \
            [(0, bass, 1.0), (1, fifth, 0.45), (2, octave, 0.55), (3, third, 0.5), (4, octave, 0.5), (5, fifth, 0.4)]
        for eighth, note, vel in pattern:
            at = (bar * 3 + eighth / 2) * BEAT + rng.normal(0, 0.006)
            add(lute, at, pluck(note, vel=vel * rng.uniform(0.85, 1.0)))
    at = 0.0
    for note, beats in TUNE:
        if note is not None:
            add(flute, at * BEAT, recorder(note, beats * BEAT * 0.96))
        at += beats
    assert at == BARS * 3, at

    # Quiet D-A drone, snapped to whole cycles per loop so it has no seam.
    t = np.arange(N) / SR
    loop = N / SR
    drone = sum(
        amp * np.sin(2 * np.pi * round(hz(m) * loop) / loop * t)
        for m, amp in ((38, 1.0), (45, 0.6), (50, 0.35))
    ) * (0.8 + 0.2 * np.sin(2 * np.pi * 4 / loop * t))

    dry = 0.55 * lute + 0.16 * flute + 0.05 * drone
    left = 0.75 * dry + 0.25 * reverb(dry, (1557, 1617, 1491, 1422))
    right = 0.75 * dry + 0.25 * reverb(dry, (1580, 1640, 1514, 1445))
    stereo = np.stack([left, right], axis=1)
    return stereo / np.abs(stereo).max() * 0.5


def click():
    """Short and dry, pitched where phone speakers are at ease."""
    t = np.arange(int(0.03 * SR)) / SR
    return np.sin(2 * np.pi * 1400 * t) * np.exp(-t * 220) * np.minimum(1, t / 0.0005)


def victory():
    """Rising D major arpeggio, then the full chord strummed."""
    out = np.zeros(int(3.4 * SR))
    notes = [(i * 0.12, note) for i, note in enumerate((62, 66, 69, 74, 78, 81))]
    notes += [(1.0 + i * 0.025, note) for i, note in enumerate((50, 62, 69, 74, 78, 86))]
    for at, note in notes:
        sig = pluck(note, dur=2.1)
        out[int(at * SR):][:len(sig)] += sig
    return out


def save(name, signal, peak):
    signal = np.asarray(signal)
    signal = signal / np.abs(signal).max() * peak
    pcm = (signal * 32767).astype("<i2")
    wav = OUT / f"{name}.wav"
    with wave.open(str(wav), "wb") as f:
        f.setnchannels(pcm.shape[1] if pcm.ndim == 2 else 1)
        f.setsampwidth(2)
        f.setframerate(SR)
        f.writeframes(pcm.tobytes())
    subprocess.run(
        ["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "3", OUT / f"{name}.ogg"],
        check=True,
    )
    wav.unlink()


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    save("music", music(), 0.5)
    save("sfx_click", click(), 0.4)
    save("sfx_victory", victory(), 0.8)
