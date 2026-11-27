import socket, sys, time
s = socket.socket()
s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
s.bind(("127.0.0.1", 19129))
s.listen(8)
log = open(sys.argv[1], "a")
while True:
    c, a = s.accept()
    c.settimeout(6)
    data = b""
    try:
        while len(data) < 64:
            d = c.recv(64)
            if not d:
                break
            data += d
    except Exception:
        pass
    log.write("ket noi tu %s:%d nhan %d byte: %s\n" % (a[0], a[1], len(data), data[:24].hex()))
    log.flush()
    time.sleep(4)
    c.close()
