package java.util;

public class Vector {
    protected Object[] elementData;
    protected int elementCount;
    protected int capacityIncrement;

    public Vector(int cap, int inc) {
        if (cap < 0) {
            throw new IllegalArgumentException();
        }
        elementData = new Object[cap];
        capacityIncrement = inc;
    }

    public Vector(int cap) {
        this(cap, 0);
    }

    public Vector() {
        this(10);
    }

    public synchronized void copyInto(Object[] a) {
        System.arraycopy(elementData, 0, a, 0, elementCount);
    }

    public synchronized void trimToSize() {
        if (elementCount < elementData.length) {
            Object[] n = new Object[elementCount];
            System.arraycopy(elementData, 0, n, 0, elementCount);
            elementData = n;
        }
    }

    private void grow(int min) {
        int old = elementData.length;
        int n = capacityIncrement > 0 ? old + capacityIncrement : old * 2;
        if (n < min) {
            n = min;
        }
        Object[] a = new Object[n];
        System.arraycopy(elementData, 0, a, 0, elementCount);
        elementData = a;
    }

    public synchronized void ensureCapacity(int min) {
        if (min > elementData.length) {
            grow(min);
        }
    }

    public synchronized void setSize(int n) {
        if (n > elementData.length) {
            grow(n);
        }
        for (int i = n; i < elementCount; i++) {
            elementData[i] = null;
        }
        elementCount = n;
    }

    public int capacity() {
        return elementData.length;
    }

    public int size() {
        return elementCount;
    }

    public boolean isEmpty() {
        return elementCount == 0;
    }

    public synchronized Enumeration elements() {
        return new VectorEnum(this);
    }

    public boolean contains(Object o) {
        return indexOf(o, 0) >= 0;
    }

    public int indexOf(Object o) {
        return indexOf(o, 0);
    }

    public synchronized int indexOf(Object o, int from) {
        for (int i = from; i < elementCount; i++) {
            if (o == null ? elementData[i] == null : o.equals(elementData[i])) {
                return i;
            }
        }
        return -1;
    }

    public int lastIndexOf(Object o) {
        return lastIndexOf(o, elementCount - 1);
    }

    public synchronized int lastIndexOf(Object o, int from) {
        if (from >= elementCount) {
            throw new IndexOutOfBoundsException(from + " >= " + elementCount);
        }
        for (int i = from; i >= 0; i--) {
            if (o == null ? elementData[i] == null : o.equals(elementData[i])) {
                return i;
            }
        }
        return -1;
    }

    public synchronized Object elementAt(int i) {
        if (i >= elementCount) {
            throw new ArrayIndexOutOfBoundsException(i + " >= " + elementCount);
        }
        if (i < 0) {
            throw new ArrayIndexOutOfBoundsException(i);
        }
        return elementData[i];
    }

    public synchronized Object firstElement() {
        if (elementCount == 0) {
            throw new NoSuchElementException();
        }
        return elementData[0];
    }

    public synchronized Object lastElement() {
        if (elementCount == 0) {
            throw new NoSuchElementException();
        }
        return elementData[elementCount - 1];
    }

    public synchronized void setElementAt(Object o, int i) {
        if (i >= elementCount || i < 0) {
            throw new ArrayIndexOutOfBoundsException(i + " >= " + elementCount);
        }
        elementData[i] = o;
    }

    public synchronized void removeElementAt(int i) {
        if (i >= elementCount || i < 0) {
            throw new ArrayIndexOutOfBoundsException(i + " >= " + elementCount);
        }
        int j = elementCount - i - 1;
        if (j > 0) {
            System.arraycopy(elementData, i + 1, elementData, i, j);
        }
        elementCount--;
        elementData[elementCount] = null;
    }

    public synchronized void insertElementAt(Object o, int i) {
        if (i > elementCount || i < 0) {
            throw new ArrayIndexOutOfBoundsException(i + " > " + elementCount);
        }
        if (elementCount + 1 > elementData.length) {
            grow(elementCount + 1);
        }
        System.arraycopy(elementData, i, elementData, i + 1, elementCount - i);
        elementData[i] = o;
        elementCount++;
    }

    public synchronized void addElement(Object o) {
        if (elementCount + 1 > elementData.length) {
            grow(elementCount + 1);
        }
        elementData[elementCount++] = o;
    }

    public synchronized boolean removeElement(Object o) {
        int i = indexOf(o, 0);
        if (i >= 0) {
            removeElementAt(i);
            return true;
        }
        return false;
    }

    public synchronized void removeAllElements() {
        for (int i = 0; i < elementCount; i++) {
            elementData[i] = null;
        }
        elementCount = 0;
    }

    public synchronized String toString() {
        StringBuffer sb = new StringBuffer("[");
        for (int i = 0; i < elementCount; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(String.valueOf(elementData[i]));
        }
        return sb.append(']').toString();
    }
}

final class VectorEnum implements Enumeration {
    private final Vector v;
    private int i;

    VectorEnum(Vector v) {
        this.v = v;
    }

    public boolean hasMoreElements() {
        return i < v.elementCount;
    }

    public Object nextElement() {
        synchronized (v) {
            if (i < v.elementCount) {
                return v.elementData[i++];
            }
        }
        throw new NoSuchElementException("VectorEnumerator");
    }
}
