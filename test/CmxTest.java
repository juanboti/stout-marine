import brewemu.audio.Cmx;
import brewemu.brew.Bar;
import java.io.*;
import java.nio.file.*;
public class CmxTest {
    public static void main(String[] a) throws Exception {
        Bar bar = new Bar(Files.readAllBytes(Paths.get(a[0])));
        new File(a[1]).mkdirs();
        int n = 0; long t0 = System.nanoTime();
        for (int id = 5001; id <= 5138; id++) {
            byte[] r = bar.get(6, id); if (r == null) continue;
            int h = r[0] & 0xff;
            Cmx.Clip c = Cmx.decode(r, h, r.length - h);
            if (c == null) continue;
            n++;
            if (c.midi != null) { Files.write(Paths.get(a[1], id + ".mid"), c.midi); System.out.println(id + " midi " + c.midi.length + " bytes " + c.durationMs + " ms"); }
            else {
                ByteArrayOutputStream o = new ByteArrayOutputStream();
                DataOutputStream w = new DataOutputStream(o);
                int len = c.pcm.length * 2;
                w.writeBytes("RIFF"); w.writeInt(Integer.reverseBytes(36 + len)); w.writeBytes("WAVEfmt ");
                w.writeInt(Integer.reverseBytes(16)); w.writeShort(Short.reverseBytes((short) 1)); w.writeShort(Short.reverseBytes((short) 1));
                w.writeInt(Integer.reverseBytes(8000)); w.writeInt(Integer.reverseBytes(16000)); w.writeShort(Short.reverseBytes((short) 2)); w.writeShort(Short.reverseBytes((short) 16));
                w.writeBytes("data"); w.writeInt(Integer.reverseBytes(len));
                for (short s : c.pcm) w.writeShort(Short.reverseBytes(s));
                Files.write(Paths.get(a[1], id + ".wav"), o.toByteArray());
            }
        }
        System.out.printf("%d clips in %.2fs%n", n, (System.nanoTime() - t0) / 1e9);
    }
}
