package j2n;

import javax.microedition.media.Control;
import javax.microedition.media.MediaException;
import javax.microedition.media.Player;
import javax.microedition.media.PlayerListener;
import javax.microedition.media.control.VolumeControl;

public final class SoundPlayer implements Player, VolumeControl {
    private int h;
    private int state = UNREALIZED;
    private int loops = 1;
    private int level = 100;
    private boolean muted;
    private final String type;

    public SoundPlayer(byte[] data, String type) throws MediaException {
        this.type = type;
        h = create(data, type == null ? "" : type);
        if (h < 0) {
            throw new MediaException("khong doc duoc am thanh " + type);
        }
    }

    private static native int create(byte[] data, String type);

    private static native void start0(int h, int loops);

    private static native void stop0(int h);

    private static native void close0(int h);

    private static native void volume0(int h, int level);

    public void realize() {
        if (state < REALIZED) {
            state = REALIZED;
        }
    }

    public void prefetch() {
        if (state < PREFETCHED) {
            state = PREFETCHED;
        }
    }

    public void start() {
        if (state == CLOSED) {
            return;
        }
        state = STARTED;
        start0(h, loops);
    }

    public void stop() {
        if (state == STARTED) {
            state = PREFETCHED;
            stop0(h);
        }
    }

    public void deallocate() {
        stop();
        if (state > REALIZED) {
            state = REALIZED;
        }
    }

    public void close() {
        if (state != CLOSED) {
            stop0(h);
            close0(h);
            state = CLOSED;
        }
    }

    public long setMediaTime(long now) {
        return 0;
    }

    public long getMediaTime() {
        return 0;
    }

    public int getState() {
        return state;
    }

    public long getDuration() {
        return TIME_UNKNOWN;
    }

    public String getContentType() {
        return type;
    }

    public void setLoopCount(int count) {
        if (count == 0) {
            throw new IllegalArgumentException();
        }
        loops = count;
    }

    public void addPlayerListener(PlayerListener l) {
    }

    public void removePlayerListener(PlayerListener l) {
    }

    public Control[] getControls() {
        return new Control[]{this};
    }

    public Control getControl(String t) {
        if (t != null && (t.equals("VolumeControl") || t.endsWith(".VolumeControl"))) {
            return this;
        }
        return null;
    }

    public void setMute(boolean m) {
        muted = m;
        volume0(h, m ? 0 : level);
    }

    public boolean isMuted() {
        return muted;
    }

    public int setLevel(int l) {
        if (l < 0) {
            l = 0;
        }
        if (l > 100) {
            l = 100;
        }
        level = l;
        if (!muted) {
            volume0(h, l);
        }
        return l;
    }

    public int getLevel() {
        return level;
    }
}
