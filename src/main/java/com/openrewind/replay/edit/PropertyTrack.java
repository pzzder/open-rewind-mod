package com.openrewind.replay.edit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * A keyframed scalar property (brightness, saturation, zoom, volume, FOV, …),
 * the equivalent of Lunar Rewind's per-property keyframes. Values are
 * interpolated over time so an effect can fade in/out or ramp smoothly.
 *
 * <p>If the track has no keyframes, {@link #valueAt} returns {@link #defaultValue}
 * — i.e. the property behaves as a constant.</p>
 */
public class PropertyTrack {

    public static final class Key {
        public long   timeMs;
        public double value;
        /** 0 = linear, 1 = smooth (ease). */
        public int    interp = 1;
        public Key() { }
        public Key(long t, double v) { this.timeMs = t; this.value = v; }
    }

    public String name;
    public double defaultValue;
    public List<Key> keys = new ArrayList<Key>();

    public PropertyTrack() { }
    public PropertyTrack(String name, double defaultValue) {
        this.name = name; this.defaultValue = defaultValue;
    }

    public void add(long timeMs, double value) {
        keys.add(new Key(timeMs, value));
        Collections.sort(keys, new Comparator<Key>() {
            public int compare(Key a, Key b) { return Long.compare(a.timeMs, b.timeMs); }
        });
    }

    public void clear() { keys.clear(); }
    public boolean isEmpty() { return keys.isEmpty(); }

    /** Interpolated value at {@code timeMs}. */
    public double valueAt(long timeMs) {
        if (keys.isEmpty()) return defaultValue;
        if (timeMs <= keys.get(0).timeMs) return keys.get(0).value;
        Key last = keys.get(keys.size() - 1);
        if (timeMs >= last.timeMs) return last.value;
        for (int i = 0; i < keys.size() - 1; i++) {
            Key a = keys.get(i), b = keys.get(i + 1);
            if (timeMs >= a.timeMs && timeMs <= b.timeMs) {
                double raw = (double) (timeMs - a.timeMs) / Math.max(1, b.timeMs - a.timeMs);
                double t = a.interp == 0 ? raw : raw * raw * (3 - 2 * raw);
                return a.value + (b.value - a.value) * t;
            }
        }
        return defaultValue;
    }
}
