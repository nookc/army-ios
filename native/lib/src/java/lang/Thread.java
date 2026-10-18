package java.lang;

public class Thread implements Runnable {
    public static final int MIN_PRIORITY = 1;
    public static final int NORM_PRIORITY = 5;
    public static final int MAX_PRIORITY = 10;
    private static int seq;

    private Runnable target;
    private String name;
    private int priority = NORM_PRIORITY;
    boolean alive;
    boolean started;

    public Thread() {
        this(null, null);
    }

    public Thread(Runnable target) {
        this(target, null);
    }

    public Thread(String name) {
        this(null, name);
    }

    public Thread(Runnable target, String name) {
        this.target = target;
        synchronized (Thread.class) {
            seq++;
            this.name = name != null ? name : "Thread-" + seq;
        }
    }

    public static native Thread currentThread();

    public static native void yield();

    public static native void sleep0(long millis);

    public static void sleep(long millis) throws InterruptedException {
        if (millis < 0) {
            throw new IllegalArgumentException("timeout value is negative");
        }
        sleep0(millis);
    }

    private native void start0();

    public synchronized void start() {
        if (started) {
            throw new IllegalThreadStateException();
        }
        started = true;
        alive = true;
        start0();
    }

    public void run() {
        if (target != null) {
            target.run();
        }
    }

    public final boolean isAlive() {
        return alive;
    }

    public final void setPriority(int p) {
        if (p < MIN_PRIORITY || p > MAX_PRIORITY) {
            throw new IllegalArgumentException();
        }
        priority = p;
    }

    public final int getPriority() {
        return priority;
    }

    public final String getName() {
        return name;
    }

    public void interrupt() {
    }

    public static int activeCount() {
        return 1;
    }

    public final void join() throws InterruptedException {
        synchronized (this) {
            while (alive) {
                wait(0);
            }
        }
    }

    public String toString() {
        return "Thread[" + name + "," + priority + "]";
    }

    /** runtime goi trong luong moi (rt_thread_run_hook) */
    public static void entry(Thread t) {
        try {
            t.run();
        } catch (Throwable e) {
            System.err.println("Uncaught exception in " + t.name + ": " + e);
        } finally {
            synchronized (t) {
                t.alive = false;
                t.notifyAll();
            }
        }
    }
}
