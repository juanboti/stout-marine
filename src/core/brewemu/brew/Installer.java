package brewemu.brew;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Installs the user's own game files: a BREW applet module (.mod), its resource file (.bar) and,
 * optionally, its .mif. They can come inside a .zip (also one zip nested in another) or as loose files.
 */
public final class Installer {
    public static final String MOD = "game.mod", BAR = "game.bar", INFO = "game.properties";
    /** Doom RPG's applet class id; used when no .mif is supplied. */
    public static final int DEFAULT_CLSID = 0x01035893;
    private static final int MAX_FILE = 16 << 20;

    public static final class Game {
        public File mod, bar;
        public String barName;
        public int clsid;
    }

    /** Collects candidate files by lower-case name. */
    public static final class Files {
        final Map<String, byte[]> byName = new LinkedHashMap<String, byte[]>();

        /** Adds a loose file or, if it is a zip, every file inside it. */
        public void add(String name, InputStream in) throws IOException {
            byte[] data = readAll(in);
            if (isZip(data)) addZip(data, 0);
            else byName.put(base(name), data);
        }

        private void addZip(byte[] data, int depth) throws IOException {
            ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(data));
            try {
                ZipEntry e;
                while ((e = z.getNextEntry()) != null) {
                    if (e.isDirectory()) continue;
                    String n = base(e.getName());
                    if (!(n.endsWith(".mod") || n.endsWith(".bar") || n.endsWith(".mif") || n.endsWith(".zip"))) continue;
                    byte[] d = readAll(z);
                    if (n.endsWith(".zip")) { if (depth < 1 && isZip(d)) addZip(d, depth + 1); }
                    else if (!byName.containsKey(n)) byName.put(n, d);
                }
            } finally { z.close(); }
        }
    }

    static String base(String name) {
        String n = name == null ? "" : name.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1);
        return n.toLowerCase(Locale.US);
    }

    static boolean isZip(byte[] d) { return d.length > 4 && d[0] == 'P' && d[1] == 'K' && d[2] == 3 && d[3] == 4; }

    static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] b = new byte[65536];
        int k;
        while ((k = in.read(b)) > 0) {
            o.write(b, 0, k);
            if (o.size() > MAX_FILE * 4) throw new IOException("That file is too large to be a phone game.");
        }
        return o.toByteArray();
    }

    /** Checks the collected files and writes the game into dir. Throws with a user-readable message on problems. */
    public static Game install(Files files, File dir) throws IOException {
        String modName = null, barName = null, mifName = null;
        for (String n : files.byName.keySet()) {
            if (n.endsWith(".mod") && modName == null) modName = n;
            if (n.endsWith(".mif") && mifName == null) mifName = n;
        }
        if (modName == null) throw new IOException("No .mod file found. Choose the BREW game's .zip (it holds a .mod and a .bar file).");
        String stem = modName.substring(0, modName.length() - 4);
        if (files.byName.containsKey(stem + ".bar")) barName = stem + ".bar";
        else for (String n : files.byName.keySet()) if (n.endsWith(".bar")) { barName = n; break; }
        if (barName == null) throw new IOException("No .bar resource file found next to " + modName + ".");
        byte[] mod = files.byName.get(modName), bar = files.byName.get(barName);
        if (mod.length < 1024 || mod.length > Brew.MOD_LIMIT - Brew.MOD_BASE) throw new IOException(modName + " doesn't look like a BREW module.");
        Bar parsed;
        try { parsed = new Bar(bar); } catch (RuntimeException e) { throw new IOException(barName + " isn't a BREW resource file."); }
        if (parsed.count() == 0) throw new IOException(barName + " has no resources.");
        int clsid = DEFAULT_CLSID;
        byte[] mif = mifName != null ? files.byName.get(mifName) : null;
        if (mif != null) { int c = clsidFrom(mif, mod); if (c != 0) clsid = c; }

        dir.mkdirs();
        write(new File(dir, MOD), mod);
        write(new File(dir, BAR), bar);
        String info = "bar=" + barName + "\nmod=" + modName + "\nclsid=" + Integer.toHexString(clsid) + "\n";
        write(new File(dir, INFO), info.getBytes("UTF-8"));
        return load(dir);
    }

    /** An applet class id named in the .mif that the module itself also refers to. */
    static int clsidFrom(byte[] mif, byte[] mod) {
        for (int i = 0; i + 4 <= mif.length; i++) {
            int v = (mif[i] & 0xff) | (mif[i + 1] & 0xff) << 8 | (mif[i + 2] & 0xff) << 16 | (mif[i + 3] & 0xff) << 24;
            if (v < 0x01010000 || v > 0x0fffffff) continue;          // system classes are below 0x01010000
            for (int j = 0; j + 4 <= mod.length; j += 4) {
                int w = (mod[j] & 0xff) | (mod[j + 1] & 0xff) << 8 | (mod[j + 2] & 0xff) << 16 | (mod[j + 3] & 0xff) << 24;
                if (w == v) return v;
            }
        }
        return 0;
    }

    public static boolean isInstalled(File dir) {
        return new File(dir, MOD).isFile() && new File(dir, BAR).isFile() && new File(dir, INFO).isFile();
    }

    public static Game load(File dir) throws IOException {
        java.util.Properties p = new java.util.Properties();
        InputStream in = new FileInputStream(new File(dir, INFO));
        try { p.load(in); } finally { in.close(); }
        Game g = new Game();
        g.mod = new File(dir, MOD); g.bar = new File(dir, BAR);
        g.barName = p.getProperty("bar", "doomrpg.bar");
        g.clsid = (int) Long.parseLong(p.getProperty("clsid", Integer.toHexString(DEFAULT_CLSID)), 16);
        return g;
    }

    public static void uninstall(File dir) {
        new File(dir, MOD).delete(); new File(dir, BAR).delete(); new File(dir, INFO).delete();
    }

    public static byte[] read(File f) throws IOException {
        InputStream in = new FileInputStream(f);
        try { return readAll(in); } finally { in.close(); }
    }

    private static void write(File f, byte[] d) throws IOException {
        File tmp = new File(f.getPath() + ".tmp");
        OutputStream o = new FileOutputStream(tmp);
        try { o.write(d); } finally { o.close(); }
        if (f.exists() && !f.delete()) throw new IOException("Couldn't replace " + f.getName());
        if (!tmp.renameTo(f)) throw new IOException("Couldn't write " + f.getName());
    }
}
