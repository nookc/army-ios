package vn.army250.thuan;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

/** Am thanh: WAV PCM 8/16 bit (nhac MIDI da duoc runtime tong hop san thanh WAV) -> AudioTrack tinh */
final class Sound {
    private Sound() {
    }

    private static final int MAX = 64;
    private static final AudioTrack[] tracks = new AudioTrack[MAX];
    private static final int[] frames = new int[MAX];

    private static int le16(byte[] d, int p) {
        return (d[p] & 0xff) | ((d[p + 1] & 0xff) << 8);
    }

    private static int le32(byte[] d, int p) {
        return le16(d, p) | (le16(d, p + 2) << 16);
    }

    static synchronized int create(byte[] d, String type) {
        try {
            if (d == null || d.length < 44 || d[0] != 'R' || d[1] != 'I' || d[2] != 'F' || d[3] != 'F') {
                Log.w("Army250", "[am thanh] khong phai WAV: " + type);
                return -1;
            }
            int fmt = 0, ch = 1, rate = 8000, bits = 8, off = -1, len = 0;
            int p = 12;
            while (p + 8 <= d.length) {
                int cl = le32(d, p + 4);
                if (d[p] == 'f' && d[p + 1] == 'm' && d[p + 2] == 't' && d[p + 3] == ' ') {
                    fmt = le16(d, p + 8);
                    ch = le16(d, p + 10);
                    rate = le32(d, p + 12);
                    bits = le16(d, p + 22);
                } else if (d[p] == 'd' && d[p + 1] == 'a' && d[p + 2] == 't' && d[p + 3] == 'a') {
                    off = p + 8;
                    len = Math.min(cl, d.length - off);
                }
                if (cl < 0) {
                    break;
                }
                p += 8 + cl + (cl & 1);
            }
            if (fmt != 1 || off < 0 || len <= 0 || (bits != 8 && bits != 16) || (ch != 1 && ch != 2)) {
                Log.w("Army250", "[am thanh] WAV khong ho tro: fmt " + fmt + " bit " + bits + " kenh " + ch);
                return -1;
            }
            int frameBytes = ch * bits / 8;
            len -= len % frameBytes;
            int h = -1;
            for (int i = 0; i < MAX; i++) {
                if (tracks[i] == null) {
                    h = i;
                    break;
                }
            }
            if (h < 0) {
                return -1;
            }
            AudioTrack t = new AudioTrack(AudioManager.STREAM_MUSIC, rate,
                    ch == 2 ? AudioFormat.CHANNEL_OUT_STEREO : AudioFormat.CHANNEL_OUT_MONO,
                    bits == 8 ? AudioFormat.ENCODING_PCM_8BIT : AudioFormat.ENCODING_PCM_16BIT,
                    len, AudioTrack.MODE_STATIC);
            if (t.getState() == AudioTrack.STATE_UNINITIALIZED) {
                t.release();
                return -1;
            }
            t.write(d, off, len);
            tracks[h] = t;
            frames[h] = len / frameBytes;
            Log.i("Army250", "[am thanh] " + type + " " + d.length + " byte: ok");
            return h;
        } catch (Throwable e) {
            Log.w("Army250", "[am thanh] loi " + e);
            return -1;
        }
    }

    static synchronized void start(int h, int loops) {
        AudioTrack t = get(h);
        if (t == null) {
            return;
        }
        try {
            if (t.getPlayState() != AudioTrack.PLAYSTATE_STOPPED) {
                t.stop();
            }
            t.reloadStaticData();
            if (loops != 1 && frames[h] > 0) {
                t.setLoopPoints(0, frames[h], loops < 0 ? -1 : loops - 1);
            } else {
                t.setLoopPoints(0, 0, 0);
            }
            t.play();
        } catch (Throwable e) {
            Log.w("Army250", "[am thanh] phat loi " + e);
        }
    }

    static synchronized void stop(int h) {
        AudioTrack t = get(h);
        if (t == null) {
            return;
        }
        try {
            t.stop();
        } catch (Throwable e) {
        }
    }

    static synchronized void close(int h) {
        AudioTrack t = get(h);
        if (t == null) {
            return;
        }
        try {
            t.stop();
        } catch (Throwable e) {
        }
        t.release();
        tracks[h] = null;
    }

    static synchronized void volume(int h, int level) {
        AudioTrack t = get(h);
        if (t == null) {
            return;
        }
        float v = Math.max(0, Math.min(100, level)) / 100f;
        try {
            t.setVolume(v);
        } catch (Throwable e) {
        }
    }

    /** dung het (khi app ra nen) / phat lai khong can: game tu quan ly */
    static synchronized void pauseAll(boolean pause) {
        for (int i = 0; i < MAX; i++) {
            AudioTrack t = tracks[i];
            if (t == null) {
                continue;
            }
            try {
                if (pause && t.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                    t.pause();
                } else if (!pause && t.getPlayState() == AudioTrack.PLAYSTATE_PAUSED) {
                    t.play();
                }
            } catch (Throwable e) {
            }
        }
    }

    private static AudioTrack get(int h) {
        return h >= 0 && h < MAX ? tracks[h] : null;
    }
}
