import brewemu.video.Hqx;
import brewemu.video.Xbr;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;

/**
 * Checks the Java hqx / xBR scalers against FFmpeg's filters, pixel by pixel.
 * args: image.png outDir   (needs ffmpeg on the PATH; writes the Java results as PNG too)
 */
public class UpscaleTest {
    public static void main(String[] a) throws Exception {
        BufferedImage im = ImageIO.read(new File(a[0]));
        File out = new File(a[1]); out.mkdirs();
        int w = im.getWidth(), h = im.getHeight();
        int[] src = im.getRGB(0, 0, w, h, null, 0, w);
        int bad = 0;
        for (String f : new String[]{"hqx", "xbr"}) for (int n = 2; n <= 4; n++) {
            int[] dst = new int[w * n * h * n], yuv = new int[w * h];
            long t0 = System.nanoTime();
            for (int rep = 0; rep < 30; rep++) {
                if (f.equals("hqx")) Hqx.scale(src, w, h, n, dst, yuv); else Xbr.scale(src, w, h, n, dst, yuv);
            }
            long ms = (System.nanoTime() - t0) / 30 / 1000000;
            File raw = new File(out, f + n + ".rgb");
            Process p = new ProcessBuilder("ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", a[0],
                    "-vf", f + "=n=" + n, "-f", "rawvideo", "-pix_fmt", "rgb24", raw.getPath()).inheritIO().start();
            if (p.waitFor() != 0) throw new IOException("ffmpeg failed");
            byte[] ref = java.nio.file.Files.readAllBytes(raw.toPath());
            int diff = 0, first = -1;
            for (int i = 0; i < dst.length; i++) {
                int r = ((ref[i * 3] & 255) << 16) | ((ref[i * 3 + 1] & 255) << 8) | (ref[i * 3 + 2] & 255);
                if ((dst[i] & 0xffffff) != r) { diff++; if (first < 0) first = i; }
            }
            BufferedImage o = new BufferedImage(w * n, h * n, BufferedImage.TYPE_INT_RGB);
            o.setRGB(0, 0, w * n, h * n, dst, 0, w * n);
            ImageIO.write(o, "png", new File(out, f + n + ".png"));
            System.out.println(f + " " + n + "x: " + ms + " ms per frame, pixels different from FFmpeg: " + diff
                    + (first >= 0 ? " (first at " + (first % (w * n)) + "," + (first / (w * n)) + ")" : ""));
            if (diff != 0) bad++;
        }
        System.out.println(bad == 0 ? "OK" : "MISMATCH");
    }
}
