package android.view;
/** Test fixture for framework state, not a copy of adapter selection logic. */
public class View {
    private int id;
    public boolean shown = true, enabled = true, clickable = true, listener = true;
    public void setId(int value) { id = value; }
    public View findViewById(int value) { return id == value ? this : null; }
    public boolean isShown() { return shown; }
    public boolean isEnabled() { return enabled; }
    public boolean isClickable() { return clickable; }
    public boolean hasOnClickListeners() { return listener; }
}
