package modmarine.app;
import java.awt.image.BufferedImage; import javax.imageio.ImageIO; import java.io.*;
/** Renders the real deck for the help images and writes button positions (screen px) as JSON. */
public class HelpRender {
    static void out(int w, int h, float dp, boolean keypad, boolean pulse, String name) throws Exception { out(w, h, dp, keypad, pulse, name, Deck.MODE_TOUCH); }
    static void out(int w, int h, float dp, boolean keypad, boolean pulse, String name, int mode) throws Exception {
        Deck d = new Deck(dp, mode == Deck.MODE_TOUCH ? 176 : 240, mode == Deck.MODE_TOUCH ? 208 : 320); d.mode = mode; d.keypadOn = keypad; d.layout(w, h); d.keypadOn = keypad; d.pulse = pulse;
        d.wpnCur = 2; d.wpnPrev = 1; d.wpnNext = 3;   // an example: pistol in hand, extinguisher / shotgun beside the arrows
        d.render();
        BufferedImage a = new BufferedImage(d.artW, d.artH, BufferedImage.TYPE_INT_ARGB);
        a.setRGB(0, 0, d.artW, d.artH, d.art.px, 0, d.artW);
        ImageIO.write(a, "png", new File(name + "_art.png"));
        StringBuilder sb = new StringBuilder("{\"artPx\":" + d.artPx + ",\"game\":[" + d.game[0] + "," + d.game[1] + "," + d.game[2] + "," + d.game[3] + "],\"buttons\":[");
        boolean first = true;
        for (Deck.Btn b : d.buttons) {
            if (!first) sb.append(","); first = false;
            sb.append("{\"key\":" + b.key + ",\"kind\":" + b.kind + ",\"cx\":" + (b.centerX() + 0.5) * d.artPx + ",\"cy\":" + (b.centerY() + 0.5) * d.artPx
                + ",\"x0\":" + b.x0 * d.artPx + ",\"y0\":" + b.y0 * d.artPx + ",\"x1\":" + (b.x1 + 1) * d.artPx + ",\"y1\":" + (b.y1 + 1) * d.artPx + ",\"r\":" + b.r * d.artPx + "}");
        }
        sb.append("],\"keypad\":[");
        first = true;
        for (Deck.Btn b : d.keypad) {
            if (!first) sb.append(","); first = false;
            sb.append("{\"key\":" + b.key + ",\"x0\":" + b.x0 * d.artPx + ",\"y0\":" + b.y0 * d.artPx + ",\"x1\":" + (b.x1 + 1) * d.artPx + ",\"y1\":" + (b.y1 + 1) * d.artPx + "}");
        }
        sb.append("]}");
        try (Writer wr = new FileWriter(name + ".json")) { wr.write(sb.toString()); }
    }
    public static void main(String[] x) throws Exception {
        out(1080, 2400, 2.75f, false, false, "p");
        out(1080, 2400, 2.75f, true, true, "pk");
        out(2400, 1080, 2.75f, true, true, "lk");
        out(720, 720, 2f, false, false, "hh", Deck.MODE_HANDHELD);
        out(720, 720, 2f, true, false, "hhk", Deck.MODE_HANDHELD);
    }
}
