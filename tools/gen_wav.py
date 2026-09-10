#!/usr/bin/env python3
"""生成轻快悦耳的提醒音（C大调上行琶音），输出到 res/raw/alert.wav"""
import math
import struct
import wave
import os

SR = 44100

# 音符频率：C5 E5 G5 C6（欢快上行）
NOTES = [523.25, 659.25, 783.99, 1046.50]
NOTE_DUR = 0.28          # 每个音时长（秒）
GAP = 0.03               # 音符间小间隙
AMPLITUDE = 0.62

samples = []
for f in NOTES:
    n = int(NOTE_DUR * SR)
    for i in range(n):
        t = i / SR
        # 指数衰减包络（钢琴/木琴感，attack 快、decay 柔和）
        env = math.exp(-t / (NOTE_DUR * 0.38))
        if t < 0.012:
            env *= t / 0.012
        # 基频 + 少量二次/三次谐波，明亮但不刺耳
        v = math.sin(2 * math.pi * f * t) \
            + 0.22 * math.sin(2 * math.pi * 2 * f * t) \
            + 0.08 * math.sin(2 * math.pi * 3 * f * t)
        samples.append(max(-1.0, min(1.0, v * env * AMPLITUDE)))
    # 音符间小间隙
    for _ in range(int(GAP * SR)):
        samples.append(0.0)

# 整体淡出防止结尾爆音
fade_n = int(0.08 * SR)
for i in range(fade_n):
    idx = len(samples) - 1 - i
    if idx >= 0:
        samples[idx] *= i / fade_n

out = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "raw", "alert.wav")
os.makedirs(os.path.dirname(out), exist_ok=True)
with wave.open(out, "wb") as w:
    w.setnchannels(1)
    w.setsampwidth(2)
    w.setframerate(SR)
    frames = b"".join(struct.pack("<h", int(s * 32767)) for s in samples)
    w.writeframes(frames)

print("written:", os.path.abspath(out), "size:", os.path.getsize(out), "bytes, duration: %.2fs" % (len(samples) / SR))
