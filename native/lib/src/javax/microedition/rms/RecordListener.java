package javax.microedition.rms;

public interface RecordListener {
    void recordAdded(RecordStore rs, int id);

    void recordChanged(RecordStore rs, int id);

    void recordDeleted(RecordStore rs, int id);
}
