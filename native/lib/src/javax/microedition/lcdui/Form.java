package javax.microedition.lcdui;

import java.util.Vector;

/** Avatar: Form toi gian. Hien bang hop nhap chu cua he dieu hanh cho TextField dau tien (j2n.Ui). */
public class Form extends Screen {
    final Vector items = new Vector();

    public Form(String title) {
        this.title = title;
    }

    public int append(Item item) {
        if (item == null) {
            throw new NullPointerException();
        }
        items.addElement(item);
        return items.size() - 1;
    }

    public int append(String s) {
        if (s == null) {
            throw new NullPointerException();
        }
        items.addElement(s);
        return items.size() - 1;
    }

    public int size() {
        return items.size();
    }

    public Item get(int i) {
        Object o = items.elementAt(i);
        return o instanceof Item ? (Item) o : null;
    }

    public void delete(int i) {
        items.removeElementAt(i);
    }

    public void deleteAll() {
        items.removeAllElements();
    }

    /** j2n: TextField dau tien (null neu khong co) */
    public TextField firstTextField() {
        for (int i = 0; i < items.size(); i++) {
            Object o = items.elementAt(i);
            if (o instanceof TextField) {
                return (TextField) o;
            }
        }
        return null;
    }

    /** j2n: cac dong chu (append(String)) noi lai, dung lam loi nhac tren hop nhap chu */
    public String plainText() {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < items.size(); i++) {
            Object o = items.elementAt(i);
            if (o instanceof String) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append((String) o);
            }
        }
        return sb.toString();
    }
}
