package brewemu.brew;

import java.io.ByteArrayOutputStream;
import java.util.zip.Inflater;

/** IUnzipAStream: inflates a gzip (or raw deflate) source stream. Decompression is done in one step. */
final class Unzip extends BObj {
    private byte[] data = new byte[0];
    private int pos;
    private BObj source;

    Unzip(Brew b) { super(b, Brew.K_UNZIP, 16); }

    @Override public int invoke(int slot) {
        switch (slot) {
            case 2: return 0;
            case 3: {   // Read(buf, n)
                int n = Math.min(arg(2), data.length - pos);
                if (n <= 0) return 0;
                b.mem.write(arg(1), data, pos, n); pos += n;
                return n;
            }
            case 4: return 0;
            case 5: {   // SetStream(IAStream*)
                BObj s = b.object(arg(1));
                if (source != null) source.release();
                source = s;
                if (s != null) s.refs++;
                byte[] in = s instanceof MemStream ? ((MemStream) s).remaining() : new byte[0];
                data = inflate(in); pos = 0;
                return 0;
            }
            default: return super.invoke(slot);
        }
    }

    @Override protected void destroy() { if (source != null) source.release(); source = null; }

    static byte[] inflate(byte[] in) {
        int off = 0;
        if (in.length > 10 && (in[0] & 0xff) == 0x1f && (in[1] & 0xff) == 0x8b) {
            int flg = in[3] & 0xff;
            off = 10;
            if ((flg & 4) != 0) off += 2 + ((in[off] & 0xff) | (in[off + 1] & 0xff) << 8);
            if ((flg & 8) != 0) { while (off < in.length && in[off] != 0) off++; off++; }
            if ((flg & 16) != 0) { while (off < in.length && in[off] != 0) off++; off++; }
            if ((flg & 2) != 0) off += 2;
        }
        Inflater inf = new Inflater(true);
        inf.setInput(in, off, Math.max(0, in.length - off));
        ByteArrayOutputStream out = new ByteArrayOutputStream(in.length * 3);
        byte[] buf = new byte[16384];
        try {
            while (!inf.finished()) {
                int n = inf.inflate(buf);
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break;
                out.write(buf, 0, n);
            }
        } catch (java.util.zip.DataFormatException e) {
            // truncated/corrupt data: return what was decoded
        } finally { inf.end(); }
        return out.toByteArray();
    }
}
