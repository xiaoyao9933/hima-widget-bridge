package android.widget;
import android.view.View;
public class TextView extends View {
    private CharSequence text = "";
    public void setText(CharSequence value) { text = value; }
    public CharSequence getText() { return text; }
}
