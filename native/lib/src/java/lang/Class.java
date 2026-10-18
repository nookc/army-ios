package java.lang;

import java.io.InputStream;

public final class Class {
    /** con tro jclass* (do runtime dat) */
    long vmClass;
    private String name;

    private Class() {
    }

    private native String name0();

    public String getName() {
        if (name == null) {
            name = name0().replace('/', '.');
        }
        return name;
    }

    public String toString() {
        return (isInterface() ? "interface " : "class ") + getName();
    }

    public static native Class forName0(String className);

    public static Class forName(String className) throws ClassNotFoundException {
        if (className == null) {
            throw new NullPointerException();
        }
        Class c = forName0(className.replace('.', '/'));
        if (c == null) {
            throw new ClassNotFoundException(className);
        }
        return c;
    }

    private native Object newInstance0();

    public Object newInstance() throws InstantiationException, IllegalAccessException {
        Object o = newInstance0();
        if (o == null) {
            throw new InstantiationException(getName());
        }
        return o;
    }

    public native boolean isInstance(Object o);

    public native boolean isAssignableFrom(Class c);

    public native boolean isInterface();

    public native boolean isArray();

    public InputStream getResourceAsStream(String res) {
        if (res == null) {
            return null;
        }
        String r;
        if (res.startsWith("/")) {
            r = res.substring(1);
        } else {
            String n = getName();
            int i = n.lastIndexOf('.');
            r = i < 0 ? res : n.substring(0, i + 1).replace('.', '/') + res;
        }
        byte[] b = j2n.Res.read(r);
        if (b == null) {
            return null;
        }
        return new java.io.ByteArrayInputStream(b);
    }
}
