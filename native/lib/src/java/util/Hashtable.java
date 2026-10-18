package java.util;

public class Hashtable {
    private HtEntry[] table;
    private int count;
    private int threshold;

    public Hashtable(int cap) {
        if (cap < 0) {
            throw new IllegalArgumentException();
        }
        if (cap == 0) {
            cap = 1;
        }
        table = new HtEntry[cap];
        threshold = (cap * 3) / 4;
    }

    public Hashtable() {
        this(11);
    }

    public int size() {
        return count;
    }

    public boolean isEmpty() {
        return count == 0;
    }

    public synchronized Enumeration keys() {
        return new HtEnum(table, true);
    }

    public synchronized Enumeration elements() {
        return new HtEnum(table, false);
    }

    public synchronized boolean contains(Object v) {
        if (v == null) {
            throw new NullPointerException();
        }
        for (int i = table.length; i-- > 0;) {
            for (HtEntry e = table[i]; e != null; e = e.next) {
                if (e.value.equals(v)) {
                    return true;
                }
            }
        }
        return false;
    }

    public synchronized boolean containsKey(Object k) {
        int h = k.hashCode();
        int i = (h & 0x7FFFFFFF) % table.length;
        for (HtEntry e = table[i]; e != null; e = e.next) {
            if (e.hash == h && e.key.equals(k)) {
                return true;
            }
        }
        return false;
    }

    public synchronized Object get(Object k) {
        int h = k.hashCode();
        int i = (h & 0x7FFFFFFF) % table.length;
        for (HtEntry e = table[i]; e != null; e = e.next) {
            if (e.hash == h && e.key.equals(k)) {
                return e.value;
            }
        }
        return null;
    }

    protected void rehash() {
        HtEntry[] old = table;
        int nc = old.length * 2 + 1;
        HtEntry[] n = new HtEntry[nc];
        threshold = (nc * 3) / 4;
        table = n;
        for (int i = old.length; i-- > 0;) {
            for (HtEntry e = old[i]; e != null;) {
                HtEntry nx = e.next;
                int j = (e.hash & 0x7FFFFFFF) % nc;
                e.next = n[j];
                n[j] = e;
                e = nx;
            }
        }
    }

    public synchronized Object put(Object k, Object v) {
        if (v == null) {
            throw new NullPointerException();
        }
        int h = k.hashCode();
        int i = (h & 0x7FFFFFFF) % table.length;
        for (HtEntry e = table[i]; e != null; e = e.next) {
            if (e.hash == h && e.key.equals(k)) {
                Object o = e.value;
                e.value = v;
                return o;
            }
        }
        if (count >= threshold) {
            rehash();
            return put(k, v);
        }
        HtEntry e = new HtEntry();
        e.hash = h;
        e.key = k;
        e.value = v;
        e.next = table[i];
        table[i] = e;
        count++;
        return null;
    }

    public synchronized Object remove(Object k) {
        int h = k.hashCode();
        int i = (h & 0x7FFFFFFF) % table.length;
        HtEntry prev = null;
        for (HtEntry e = table[i]; e != null; prev = e, e = e.next) {
            if (e.hash == h && e.key.equals(k)) {
                if (prev != null) {
                    prev.next = e.next;
                } else {
                    table[i] = e.next;
                }
                count--;
                return e.value;
            }
        }
        return null;
    }

    public synchronized void clear() {
        for (int i = table.length; --i >= 0;) {
            table[i] = null;
        }
        count = 0;
    }

    public synchronized String toString() {
        StringBuffer sb = new StringBuffer("{");
        boolean first = true;
        for (int i = 0; i < table.length; i++) {
            for (HtEntry e = table[i]; e != null; e = e.next) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append(String.valueOf(e.key)).append('=').append(String.valueOf(e.value));
            }
        }
        return sb.append('}').toString();
    }
}

final class HtEntry {
    int hash;
    Object key;
    Object value;
    HtEntry next;
}

final class HtEnum implements Enumeration {
    private final HtEntry[] table;
    private final boolean keys;
    private int index;
    private HtEntry entry;

    HtEnum(HtEntry[] t, boolean keys) {
        table = t;
        this.keys = keys;
        index = t.length;
    }

    public boolean hasMoreElements() {
        if (entry != null) {
            return true;
        }
        while (index-- > 0) {
            if ((entry = table[index]) != null) {
                return true;
            }
        }
        return false;
    }

    public Object nextElement() {
        if (entry == null) {
            while (index-- > 0 && (entry = table[index]) == null) {
            }
        }
        if (entry != null) {
            HtEntry e = entry;
            entry = e.next;
            return keys ? e.key : e.value;
        }
        throw new NoSuchElementException("HashtableEnumerator");
    }
}
