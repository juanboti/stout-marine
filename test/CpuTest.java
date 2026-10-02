import brewemu.cpu.*;
import java.io.*;
import java.util.*;

/** Replays the unicorn single-instruction cases and reports mismatches. */
public class CpuTest {
    static final int CODE = 0x10000, DATA = 0x40000, DSIZE = 0x10000;

    public static void main(String[] a) throws Exception {
        BufferedReader in = new BufferedReader(new FileReader(a[0]));
        byte[] pattern = new byte[DSIZE];
        for (int i = 0; i < DSIZE; i++) pattern[i] = (byte) (((i * 37 + 11) ^ (i >> 8)) & 255);
        String line; int total = 0, bad = 0, unal = 0;
        Map<String, Integer> badByKind = new TreeMap<String, Integer>();
        while ((line = in.readLine()) != null) {
            String[] parts = line.split("\\|", -1);
            String[] pre = parts[0].trim().split(" ");
            String[] post = parts[1].trim().split(" ");
            String writes = parts[2].trim();
            boolean thumb = pre[0].equals("T");
            int ins = (int) Long.parseLong(pre[1], 16);
            Memory m = new Memory(0x100000);
            System.arraycopy(pattern, 0, m.ram, DATA, DSIZE);
            if (thumb) { m.write16(CODE, ins); m.write16(CODE + 2, 0xbf00); } else m.write32(CODE, ins);
            Cpu c = new Cpu(m);
            for (int i = 0; i < 15; i++) c.r[i] = (int) Long.parseLong(pre[2 + i], 16);
            int cpsr = (int) Long.parseLong(pre[17], 16);
            c.setCpsrFlags(cpsr);
            c.thumb = thumb;
            c.branchTo(thumb ? CODE | 1 : CODE);
            String err = null;
            try { c.step(); } catch (Throwable t) { err = "EXC " + t; }
            StringBuilder diff = new StringBuilder();
            if (err != null) diff.append(err);
            else {
                for (int i = 0; i < 15; i++) {
                    int exp = (int) Long.parseLong(post[i], 16);
                    if (c.r[i] != exp) diff.append(String.format(" r%d=%08x exp %08x", i, c.r[i], exp));
                }
                int expPc = (int) Long.parseLong(post[15], 16);
                if (c.pc() != expPc) diff.append(String.format(" pc=%08x exp %08x", c.pc(), expPc));
                int expF = (int) Long.parseLong(post[16], 16);
                int gotF = c.cpsr() & 0xf8000020;
                if (gotF != expF) diff.append(String.format(" cpsr=%08x exp %08x", gotF, expF));
                StringBuilder got = new StringBuilder();
                for (int off = 0; off < DSIZE; off += 4) {
                    boolean ch = false;
                    for (int k = 0; k < 4; k++) if (m.ram[DATA + off + k] != pattern[off + k]) ch = true;
                    if (ch) {
                        if (got.length() > 0) got.append(',');
                        got.append(Integer.toHexString(DATA + off)).append(':').append(Integer.toHexString(m.read32(DATA + off)));
                    }
                }
                if (!got.toString().equals(writes)) diff.append(" mem=" + got + " exp " + writes);
            }
            total++;
            if (diff.length() > 0 && m.unaligned > 0) { unal++; continue; }
            if (diff.length() > 0) {
                bad++;
                String kind = (thumb ? "T" : "A") + (thumb ? Integer.toHexString(ins >>> 11) : Integer.toHexString((ins >>> 20) & 0xff));
                Integer k = badByKind.get(kind);
                badByKind.put(kind, k == null ? 1 : k + 1);
                if (bad <= 40) System.out.println(String.format("%s %08x:%s", thumb ? "T" : "A", ins, diff));
            }
        }
        System.out.println("total " + total + " mismatches " + bad + " (unaligned-access cases skipped: " + unal + ")");
        System.out.println(badByKind);
    }
}
