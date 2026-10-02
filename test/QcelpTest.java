import brewemu.audio.Qcelp;
import java.io.*;
import java.nio.file.*;
/** Decodes concatenated QCELP packets (raw file) to little-endian float32 for comparison with ffmpeg. */
public class QcelpTest {
    public static void main(String[] a) throws Exception {
        byte[] d = Files.readAllBytes(Paths.get(a[0]));
        Qcelp q = new Qcelp();
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(a[1])));
        float[] s = new float[160];
        int p = 0, n = 0;
        while (p < d.length) {
            int sz = Qcelp.packetSize(d[p] & 0xff);
            if (sz < 0) break;
            q.decode(d, p, sz, s); p += sz; n++;
            for (float f : s) out.writeInt(Integer.reverseBytes(Float.floatToIntBits(f)));
        }
        out.close();
        System.out.println("packets " + n);
    }
}
