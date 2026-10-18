package javax.microedition.rms;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Vector;

/** moi kho = 1 tep (j2n.Rms): [nextId][so ban ghi][(id, dai, du lieu)...] */
public class RecordStore {
    public static final int AUTHMODE_PRIVATE = 0;
    public static final int AUTHMODE_ANY = 1;
    private static final Hashtable open = new Hashtable();

    private final String name;
    private int nextId = 1;
    private final Vector ids = new Vector();
    private final Vector datas = new Vector();
    private int openCount;
    private int version;
    private long modified;

    private RecordStore(String name) {
        this.name = name;
    }

    public static synchronized RecordStore openRecordStore(String name, boolean create) throws RecordStoreException {
        if (name == null || name.length() == 0 || name.length() > 32) {
            throw new IllegalArgumentException();
        }
        RecordStore rs = (RecordStore) open.get(name);
        if (rs == null) {
            byte[] b = j2n.Rms.load(name);
            if (b == null) {
                if (!create) {
                    throw new RecordStoreNotFoundException(name);
                }
                rs = new RecordStore(name);
                rs.save();
            } else {
                rs = new RecordStore(name);
                rs.parse(b);
            }
            open.put(name, rs);
        }
        rs.openCount++;
        return rs;
    }

    public static RecordStore openRecordStore(String name, boolean create, int auth, boolean writable)
            throws RecordStoreException {
        return openRecordStore(name, create);
    }

    public static RecordStore openRecordStore(String name, String vendor, String suite) throws RecordStoreException {
        return openRecordStore(name, false);
    }

    private void parse(byte[] b) throws RecordStoreException {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(b));
            nextId = in.readInt();
            int n = in.readInt();
            for (int i = 0; i < n; i++) {
                int id = in.readInt();
                int len = in.readInt();
                byte[] d = new byte[len];
                in.readFully(d);
                ids.addElement(new Integer(id));
                datas.addElement(d);
            }
            version = in.available() >= 4 ? in.readInt() : 0;
        } catch (IOException e) {
            throw new RecordStoreException("kho hong: " + name);
        }
    }

    private void save() throws RecordStoreException {
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bo);
            out.writeInt(nextId);
            out.writeInt(ids.size());
            for (int i = 0; i < ids.size(); i++) {
                byte[] d = (byte[]) datas.elementAt(i);
                out.writeInt(((Integer) ids.elementAt(i)).intValue());
                out.writeInt(d.length);
                out.write(d, 0, d.length);
            }
            out.writeInt(version);
            out.flush();
            if (!j2n.Rms.save(name, bo.toByteArray())) {
                throw new RecordStoreFullException(name);
            }
            modified = System.currentTimeMillis();
        } catch (IOException e) {
            throw new RecordStoreException(e.toString());
        }
    }

    private void check() throws RecordStoreNotOpenException {
        if (openCount <= 0) {
            throw new RecordStoreNotOpenException(name);
        }
    }

    private int index(int id) throws InvalidRecordIDException {
        for (int i = 0; i < ids.size(); i++) {
            if (((Integer) ids.elementAt(i)).intValue() == id) {
                return i;
            }
        }
        throw new InvalidRecordIDException(String.valueOf(id));
    }

    public void closeRecordStore() throws RecordStoreException {
        synchronized (RecordStore.class) {
            check();
            if (--openCount == 0) {
                open.remove(name);
            }
        }
    }

    public static void deleteRecordStore(String name) throws RecordStoreException {
        synchronized (RecordStore.class) {
            RecordStore rs = (RecordStore) open.get(name);
            if (rs != null && rs.openCount > 0) {
                throw new RecordStoreException("dang mo: " + name);
            }
            if (!j2n.Rms.delete(name)) {
                throw new RecordStoreNotFoundException(name);
            }
        }
    }

    public static String[] listRecordStores() {
        String[] s = j2n.Rms.list();
        return s == null || s.length == 0 ? null : s;
    }

    public String getName() throws RecordStoreNotOpenException {
        check();
        return name;
    }

    public synchronized int getVersion() throws RecordStoreNotOpenException {
        check();
        return version;
    }

    public synchronized int getNumRecords() throws RecordStoreNotOpenException {
        check();
        return ids.size();
    }

    public synchronized int getSize() throws RecordStoreNotOpenException {
        check();
        int n = 8;
        for (int i = 0; i < datas.size(); i++) {
            n += 8 + ((byte[]) datas.elementAt(i)).length;
        }
        return n;
    }

    public int getSizeAvailable() throws RecordStoreNotOpenException {
        check();
        return 4 * 1024 * 1024;
    }

    public synchronized long getLastModified() throws RecordStoreNotOpenException {
        check();
        return modified;
    }

    public synchronized int getNextRecordID() throws RecordStoreNotOpenException {
        check();
        return nextId;
    }

    public synchronized int addRecord(byte[] data, int off, int len) throws RecordStoreException {
        check();
        byte[] d = new byte[len];
        if (len > 0) {
            System.arraycopy(data, off, d, 0, len);
        }
        int id = nextId++;
        ids.addElement(new Integer(id));
        datas.addElement(d);
        version++;
        save();
        return id;
    }

    public synchronized void deleteRecord(int id) throws RecordStoreException {
        check();
        int i = index(id);
        ids.removeElementAt(i);
        datas.removeElementAt(i);
        version++;
        save();
    }

    public synchronized int getRecordSize(int id) throws RecordStoreException {
        check();
        return ((byte[]) datas.elementAt(index(id))).length;
    }

    public synchronized int getRecord(int id, byte[] buf, int off) throws RecordStoreException {
        check();
        byte[] d = (byte[]) datas.elementAt(index(id));
        System.arraycopy(d, 0, buf, off, d.length);
        return d.length;
    }

    public synchronized byte[] getRecord(int id) throws RecordStoreException {
        check();
        byte[] d = (byte[]) datas.elementAt(index(id));
        if (d.length == 0) {
            return null;
        }
        byte[] r = new byte[d.length];
        System.arraycopy(d, 0, r, 0, d.length);
        return r;
    }

    public synchronized void setRecord(int id, byte[] data, int off, int len) throws RecordStoreException {
        check();
        int i = index(id);
        byte[] d = new byte[len];
        if (len > 0) {
            System.arraycopy(data, off, d, 0, len);
        }
        datas.setElementAt(d, i);
        version++;
        save();
    }

    public RecordEnumeration enumerateRecords(RecordFilter f, RecordComparator c, boolean keepUpdated)
            throws RecordStoreNotOpenException {
        check();
        int[] a;
        synchronized (this) {
            a = new int[ids.size()];
            for (int i = 0; i < a.length; i++) {
                a[i] = ((Integer) ids.elementAt(i)).intValue();
            }
        }
        return new RecEnum(this, a);
    }

    public void addRecordListener(RecordListener l) {
    }

    public void removeRecordListener(RecordListener l) {
    }

    public void setMode(int auth, boolean writable) {
    }
}

final class RecEnum implements RecordEnumeration {
    private final RecordStore rs;
    private final int[] ids;
    private int i;

    RecEnum(RecordStore rs, int[] ids) {
        this.rs = rs;
        this.ids = ids;
    }

    public int numRecords() {
        return ids.length;
    }

    public byte[] nextRecord() throws RecordStoreException {
        return rs.getRecord(nextRecordId());
    }

    public int nextRecordId() throws InvalidRecordIDException {
        if (i >= ids.length) {
            throw new InvalidRecordIDException();
        }
        return ids[i++];
    }

    public byte[] previousRecord() throws RecordStoreException {
        return rs.getRecord(previousRecordId());
    }

    public int previousRecordId() throws InvalidRecordIDException {
        if (i <= 0) {
            throw new InvalidRecordIDException();
        }
        return ids[--i];
    }

    public boolean hasNextElement() {
        return i < ids.length;
    }

    public boolean hasPreviousElement() {
        return i > 0;
    }

    public void reset() {
        i = 0;
    }

    public void rebuild() {
    }

    public void keepUpdated(boolean k) {
    }

    public boolean isKeptUpdated() {
        return false;
    }

    public void destroy() {
    }
}
