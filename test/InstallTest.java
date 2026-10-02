import brewemu.brew.*;
import java.io.*;
/** Installs a game .zip (or loose files) into a folder the same way the app does. */
public class InstallTest {
    public static void main(String[] a) throws Exception {
        Installer.Files f = new Installer.Files();
        for (int i = 1; i < a.length; i++) { FileInputStream in = new FileInputStream(a[i]); try { f.add(new File(a[i]).getName(), in); } finally { in.close(); } }
        Installer.Game g = Installer.install(f, new File(a[0]));
        System.out.println("bar=" + g.barName + " clsid=" + Integer.toHexString(g.clsid) + " mod=" + g.mod.length() + " bar=" + g.bar.length());
    }
}
