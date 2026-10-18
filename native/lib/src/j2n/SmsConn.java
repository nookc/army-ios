package j2n;

import java.io.IOException;
import java.util.Date;
import javax.wireless.messaging.Message;
import javax.wireless.messaging.MessageConnection;
import javax.wireless.messaging.TextMessage;

public final class SmsConn implements MessageConnection {
    private final String address;

    public SmsConn(String address) {
        this.address = address;
    }

    public Message newMessage(String type) {
        return newMessage(type, "sms://" + address);
    }

    public Message newMessage(String type, String addr) {
        SmsText m = new SmsText();
        m.setAddress(addr);
        return m;
    }

    public void send(Message msg) throws IOException {
        String a = msg.getAddress();
        if (a != null && a.startsWith("sms://")) {
            a = a.substring(6);
        }
        String text = msg instanceof TextMessage ? ((TextMessage) msg).getPayloadText() : "";
        if (!sms(a == null ? address : a, text == null ? "" : text)) {
            throw new IOException("khong gui duoc tin nhan");
        }
    }

    public Message receive() throws IOException {
        throw new IOException("khong ho tro");
    }

    public int numberOfSegments(Message msg) {
        return 1;
    }

    public void close() throws IOException {
    }

    private static native boolean sms(String number, String text);
}

final class SmsText implements TextMessage {
    private String address;
    private String text;

    public String getAddress() {
        return address;
    }

    public void setAddress(String a) {
        address = a;
    }

    public Date getTimestamp() {
        return null;
    }

    public String getPayloadText() {
        return text;
    }

    public void setPayloadText(String t) {
        text = t;
    }
}
