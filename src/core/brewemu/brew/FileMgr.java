package brewemu.brew;

import brewemu.cpu.Memory;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/** IFileMgr over the host's private data directory. Paths are confined to that directory. */
final class FileMgr extends BObj {
    static final int OFM_READ = 1, OFM_READWRITE = 2, OFM_CREATE = 4, OFM_APPEND = 8;
    static final int EFILEEXISTS = 0x101, EFILENOEXISTS = 0x102, EBADFILENAME = 0x104;
    private int lastError;
    private File[] enumList;
    private int enumPos;

    FileMgr(Brew b) { super(b, Brew.K_FILEMGR, 16); }

    /** Maps a BREW path to a file under the data directory, or null if it is not acceptable. */
    File map(String path) {
        if (path == null) return null;
        String p = path.replace('\\', '/');
        if (p.startsWith("fs:/")) p = p.substring(4);
        while (p.startsWith("~/") || p.startsWith("/")) p = p.substring(p.indexOf('/') + 1);
        if (p.startsWith("~")) p = p.substring(1);
        File f = b.host.dataDir();
        for (String part : p.split("/")) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..") || part.indexOf(':') >= 0) return null;
            f = new File(f, part);
        }
        return f;
    }

    private void fileInfo(int p, File f) {
        Memory m = b.mem;
        m.write8(p, f.isDirectory() ? 0x10 : 0);
        m.write32(p + 4, (int) (f.lastModified() / 1000L - 315964800L));
        m.write32(p + 8, (int) f.length());
        b.putCstr(p + 12, f.getName(), 64);
    }

    @Override public int invoke(int slot) {
        Memory m = b.mem;
        switch (slot) {
            case 2: {  // OpenFile(name, mode) -> IFile*
                String name = m.cstr(arg(1)); int mode = arg(2);
                File f = map(name);
                if (f == null) { lastError = EBADFILENAME; return 0; }
                try {
                    if ((mode & OFM_CREATE) != 0) {
                        if (f.exists()) { lastError = EFILEEXISTS; return 0; }
                        File parent = f.getParentFile();
                        if (parent != null) parent.mkdirs();
                        return new BFile(b, new RandomAccessFile(f, "rw"), f).addr;
                    }
                    if (!f.isFile()) { lastError = EFILENOEXISTS; return 0; }
                    boolean write = (mode & (OFM_READWRITE | OFM_APPEND)) != 0;
                    BFile bf = new BFile(b, new RandomAccessFile(f, write ? "rw" : "r"), f);
                    if ((mode & OFM_APPEND) != 0) bf.raf.seek(bf.raf.length());
                    return bf.addr;
                } catch (IOException e) {
                    lastError = Brew.EFAILED; return 0;
                }
            }
            case 3: {  // GetInfo(name, FileInfo*)
                File f = map(m.cstr(arg(1)));
                if (f == null || !f.exists()) { lastError = EFILENOEXISTS; return Brew.EFAILED; }
                if (arg(2) != 0) fileInfo(arg(2), f);
                return Brew.SUCCESS;
            }
            case 4: {  // Remove(name)
                File f = map(m.cstr(arg(1)));
                if (f == null || !f.isFile()) { lastError = EFILENOEXISTS; return Brew.EFAILED; }
                return f.delete() ? Brew.SUCCESS : Brew.EFAILED;
            }
            case 5: {  // MkDir
                File f = map(m.cstr(arg(1)));
                return f != null && (f.isDirectory() || f.mkdirs()) ? Brew.SUCCESS : Brew.EFAILED;
            }
            case 6: {  // RmDir
                File f = map(m.cstr(arg(1)));
                return f != null && f.isDirectory() && f.delete() ? Brew.SUCCESS : Brew.EFAILED;
            }
            case 7: {  // Test(name)
                File f = map(m.cstr(arg(1)));
                if (f != null && f.exists()) return Brew.SUCCESS;
                lastError = EFILENOEXISTS;
                return Brew.EFAILED;
            }
            case 8: {  // GetFreeSpace(pdwTotal)
                if (arg(1) != 0) m.write32(arg(1), 16 << 20);
                return 8 << 20;
            }
            case 9: return lastError;
            case 10: {  // EnumInit(dir, bDirs)
                File d = map(m.cstr(arg(1)));
                final boolean dirs = arg(2) != 0;
                enumList = d == null ? null : d.listFiles(new java.io.FileFilter() {
                    public boolean accept(File f) { return f.isDirectory() == dirs; }
                });
                enumPos = 0;
                return enumList != null ? Brew.SUCCESS : Brew.EFAILED;
            }
            case 11: {  // EnumNext(FileInfo*)
                if (enumList == null || enumPos >= enumList.length) return 0;
                fileInfo(arg(1), enumList[enumPos++]);
                return 1;
            }
            case 12: {  // Rename(src, dst)
                File s = map(m.cstr(arg(1))), d = map(m.cstr(arg(2)));
                return s != null && d != null && s.renameTo(d) ? Brew.SUCCESS : Brew.EFAILED;
            }
            default: return super.invoke(slot);
        }
    }
}
