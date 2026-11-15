package javax.microedition.lcdui;

/** Avatar: Item toi gian (nhan) cho Form */
public abstract class Item {
    String label;

    Item() {
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String l) {
        label = l;
    }
}
