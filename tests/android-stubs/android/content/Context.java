package android.content;
import android.content.pm.PackageManager;
/** Minimal host fixture; production builds use the real Android SDK instead. */
public abstract class Context {
    public abstract PackageManager getPackageManager();
}
