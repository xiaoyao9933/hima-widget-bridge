package android.view;
import java.util.ArrayList;
import java.util.List;
public class ViewGroup extends View {
    private final List<View> children = new ArrayList<>();
    public void addView(View child) { children.add(child); }
    public int getChildCount() { return children.size(); }
    public View getChildAt(int index) { return children.get(index); }
    @Override public View findViewById(int value) {
        View own = super.findViewById(value); if (own != null) return own;
        for (View child : children) { View found = child.findViewById(value); if (found != null) return found; }
        return null;
    }
}
