package javax.microedition.media;

public interface PlayerListener {
    String STARTED = "started";
    String STOPPED = "stopped";
    String END_OF_MEDIA = "endOfMedia";
    String CLOSED = "closed";
    String ERROR = "error";
    String VOLUME_CHANGED = "volumeChanged";

    void playerUpdate(Player p, String event, Object data);
}
