package android.content.pm;
public abstract class PackageManager {
    public abstract PackageInfo getPackageInfo(String name, int flags) throws NameNotFoundException;
    public static class NameNotFoundException extends Exception { private static final long serialVersionUID = 1L; }
}
