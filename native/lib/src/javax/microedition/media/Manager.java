package javax.microedition.media;

import java.io.IOException;
import java.io.InputStream;

public final class Manager {
    public static final String TONE_DEVICE_LOCATOR = "device://tone";
    public static final String MIDI_DEVICE_LOCATOR = "device://midi";

    private Manager() {
    }

    public static String[] getSupportedContentTypes(String protocol) {
        return new String[]{"audio/x-wav", "audio/wav", "audio/midi", "audio/mid"};
    }

    public static String[] getSupportedProtocols(String contentType) {
        return new String[]{};
    }

    public static Player createPlayer(InputStream in, String type) throws IOException, MediaException {
        if (in == null) {
            throw new IllegalArgumentException();
        }
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf, 0, buf.length)) > 0) {
            bo.write(buf, 0, n);
        }
        return new j2n.SoundPlayer(bo.toByteArray(), type);
    }

    public static Player createPlayer(String locator) throws IOException, MediaException {
        throw new MediaException("locator: " + locator);
    }

    public static void playTone(int note, int duration, int volume) throws MediaException {
    }
}
