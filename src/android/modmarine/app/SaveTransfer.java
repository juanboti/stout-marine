package modmarine.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Exports / imports the game's own save files as a .zip the user picks. */
final class SaveTransfer {
    static final int REQ_EXPORT = 7101, REQ_IMPORT = 7102;
    /** Folder name inside the .zip. */
    static final String PREFIX = "modmarine-saves/";

    /** A plain file name the game itself could have written (no folders, no tricks). */
    static boolean okName(String n) {
        if (n.length() == 0 || n.length() > 64 || n.startsWith(".")) return false;
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.')) return false;
        }
        return true;
    }

    static void startExport(Activity a, String fileName) {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/zip");
        i.putExtra(Intent.EXTRA_TITLE, fileName);
        a.startActivityForResult(i, REQ_EXPORT);
    }

    static void startImport(Activity a) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        a.startActivityForResult(i, REQ_IMPORT);
    }

    /** Writes every save file in saveDir into the zip at uri. Returns the number of files. */
    static int export(Activity a, File saveDir, Uri uri) throws Exception {
        File[] files = saveDir.listFiles();
        int n = 0;
        OutputStream os = a.getContentResolver().openOutputStream(uri, "w");
        if (os == null) throw new Exception("Couldn't write that file.");
        ZipOutputStream zo = new ZipOutputStream(os);
        try {
            if (files != null) for (int i = 0; i < files.length; i++) {
                File f = files[i];
                if (!f.isFile() || !okName(f.getName())) continue;
                zo.putNextEntry(new ZipEntry(PREFIX + f.getName()));
                FileInputStream in = new FileInputStream(f);
                try { copy(in, zo); } finally { in.close(); }
                zo.closeEntry();
                n++;
            }
        } finally { zo.close(); }
        return n;
    }

    /** Replaces the save files in saveDir with those in the zip. Returns the number of files. */
    static int importZip(Activity a, File saveDir, Uri uri) throws Exception {
        InputStream is = a.getContentResolver().openInputStream(uri);
        if (is == null) throw new Exception("Couldn't open that file.");
        File tmp = new File(saveDir.getParentFile(), "saves.import");
        deleteDir(tmp);
        tmp.mkdirs();
        int n = 0;
        ZipInputStream zi = new ZipInputStream(is);
        try {
            ZipEntry e;
            while ((e = zi.getNextEntry()) != null) {
                String name = e.getName();
                if (e.isDirectory() || !name.startsWith(PREFIX)) continue;
                name = name.substring(PREFIX.length());
                if (!okName(name)) continue;
                FileOutputStream out = new FileOutputStream(new File(tmp, name));
                try { copy(zi, out); } finally { out.close(); }
                n++;
            }
        } finally { zi.close(); }
        if (n == 0) { deleteDir(tmp); throw new Exception("No saved games found in that .zip."); }
        deleteDir(saveDir);
        if (!tmp.renameTo(saveDir)) throw new Exception("Couldn't replace the save files.");
        return n;
    }

    static void toast(Activity a, String m) { Toast.makeText(a, m, Toast.LENGTH_LONG).show(); }

    private static void copy(InputStream in, OutputStream out) throws java.io.IOException {
        byte[] b = new byte[16384]; int k;
        while ((k = in.read(b)) > 0) out.write(b, 0, k);
    }

    private static void deleteDir(File d) {
        File[] fs = d.listFiles();
        if (fs != null) for (int i = 0; i < fs.length; i++) fs[i].delete();
        d.delete();
    }
}
