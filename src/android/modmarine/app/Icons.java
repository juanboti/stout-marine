package modmarine.app;

/** Hand-made pixel icons and a small pixel font for the control deck. */
final class Icons {
    static final String[] UP = {
        "....#....",
        "...###...",
        "..#####..",
        ".#######.",
        "...###...",
        "...###...",
        "...###...",
        "...###...",
    };
    static final String[] DOWN = vflip(UP);
    static final String[] TURN_L = {
        "...#####...",
        "..#######..",
        ".##.....##.",
        "##.......##",
        "##.......##",
        "######.....",
        ".####......",
        "..##.......",
    };
    static final String[] TURN_R = hflip(TURN_L);
    static final String[] STEP_L = {
        "...#.....#",
        "..##.....#",
        ".#######.#",
        "########.#",
        ".#######.#",
        "..##.....#",
        "...#.....#",
    };
    static final String[] STEP_R = hflip(STEP_L);
    static final String[] CROSS = {
        "......#......",
        "......#......",
        "...#######...",
        "..##..#..##..",
        ".##.......##.",
        ".#.........#.",
        "####..#..####",
        ".#.........#.",
        ".##.......##.",
        "..##..#..##..",
        "...#######...",
        "......#......",
        "......#......",
    };
    static final String[] HOUR = {
        "#######",
        ".#...#.",
        "..#.#..",
        "...#...",
        "..#.#..",
        ".#.#.#.",
        "#######",
    };
    static final String[] GEAR = {
        "...#.#...",
        ".#######.",
        ".##...##.",
        "###...###",
        ".##...##.",
        ".#######.",
        "...#.#...",
    };
    /** The game's menu (three bars). */
    static final String[] MENU = {
        "#######",
        ".......",
        "#######",
        ".......",
        "#######",
    };
    static final String[] MAP = {
        "###......",
        "#.####...",
        "#.#..####",
        "#.#..#..#",
        "#.#..#..#",
        "####.#..#",
        "...####.#",
        "......###",
    };
    static final String[] KEYS = {
        "#.#.#",
        ".....",
        "#.#.#",
        ".....",
        "#.#.#",
    };
    static final String[] GUN = {
        "...########..",
        ".############",
        ".##########..",
        ".####.#......",
        "####.........",
        "###..........",
    };
    /** Simple weapon icons (Stout Marine's own drawings) by weapon number: 0 axe ... 8 BFG, 9-11 dogs. */
    static final String[] PAW = {".#.#.", "#.#.#", ".....", ".###.", ".###."};
    static final String[][] WEAPON = {
        {"..........##.", ".........####", "#############", ".........####", "..........##."},
        {"..##.........", ".####.###....", ".####...#....", ".####....#...", ".####....##..", ".####........", ".####........"},
        GUN,
        {"#############", "####.####....", "###..........", "##..........."},
        {"..###########", ".############", "..###########", ".####.#......", "####........."},
        {"##########...", "##########...", "###.#........", "##..........."},
        {"..#########..", ".###########.", ".##.#.#.####.", ".###########.", "####........."},
        {"#############", "#############", "...##.##.....", "...#........."},
        {".##########..", "############.", "############.", ".##########..", "..##...##...."},
        PAW, PAW, PAW,
    };
    /** Tiny versions for beside the weapon arrows. */
    static final String[] PAW_S = {"#.#", ".#.", "###"};
    static final String[][] WEAPON_S = {
        {"....##.", "#######", "....##."},
        {".##.##", ".##..#", ".##..."},
        {".#####", "###...", "##...."},
        {"#######", "##.....", "#......"},
        {".######", "#######", "##....."},
        {"######.", "######.", "##....."},
        {".#####.", "#.#.##.", "##....."},
        {"#######", "#######", "..#...."},
        {"######.", "#######", "######."},
        PAW_S, PAW_S, PAW_S,
    };
    static final String[] X = {"#...#", ".#.#.", "..#..", ".#.#.", "#...#"};
    static final String[] CHEV_L = {"..#", ".#.", "#..", ".#.", "..#"};
    static final String[] CHEV_R = {"#..", ".#.", "..#", ".#.", "#.."};

    static String[] hflip(String[] b) {
        String[] o = new String[b.length];
        for (int i = 0; i < b.length; i++) o[i] = new StringBuilder(b[i]).reverse().toString();
        return o;
    }
    static String[] vflip(String[] b) {
        String[] o = new String[b.length];
        for (int i = 0; i < b.length; i++) o[i] = b[b.length - 1 - i];
        return o;
    }

    // ---------------------------------------------------------------- font (5 rows high)
    private static final String[] SPACE = {"..", "..", "..", "..", ".."};
    static String[] glyph(char c) {
        switch (Character.toUpperCase(c)) {
            case 'M': return new String[]{"#...#", "##.##", "#.#.#", "#...#", "#...#"};
            case 'N': return new String[]{"#..#", "##.#", "#.##", "#..#", "#..#"};
            case 'I': return new String[]{"###", ".#.", ".#.", ".#.", "###"};
            case 'D': return new String[]{"##.", "#.#", "#.#", "#.#", "##."};
            case 'L': return new String[]{"#..", "#..", "#..", "#..", "###"};
            case 'E': return new String[]{"###", "#..", "##.", "#..", "###"};
            case 'T': return new String[]{"###", ".#.", ".#.", ".#.", ".#."};
            case 'A': return new String[]{".#.", "#.#", "###", "#.#", "#.#"};
            case 'R': return new String[]{"##.", "#.#", "##.", "#.#", "#.#"};
            case 'C': return new String[]{".##", "#..", "#..", "#..", ".##"};
            case 'O': return new String[]{".#.", "#.#", "#.#", "#.#", ".#."};
            case 'S': return new String[]{".##", "#..", ".#.", "..#", "##."};
            case 'B': return new String[]{"##.", "#.#", "##.", "#.#", "##."};
            case 'F': return new String[]{"###", "#..", "##.", "#..", "#.."};
            case 'G': return new String[]{".##", "#..", "#.#", "#.#", ".##"};
            case 'H': return new String[]{"#.#", "#.#", "###", "#.#", "#.#"};
            case 'J': return new String[]{"..#", "..#", "..#", "#.#", ".#."};
            case 'K': return new String[]{"#.#", "#.#", "##.", "#.#", "#.#"};
            case 'P': return new String[]{"##.", "#.#", "##.", "#..", "#.."};
            case 'Q': return new String[]{".#.", "#.#", "#.#", "##.", ".##"};
            case 'U': return new String[]{"#.#", "#.#", "#.#", "#.#", "###"};
            case 'V': return new String[]{"#.#", "#.#", "#.#", "#.#", ".#."};
            case 'W': return new String[]{"#...#", "#...#", "#.#.#", "#.#.#", ".#.#."};
            case 'X': return new String[]{"#.#", "#.#", ".#.", "#.#", "#.#"};
            case 'Y': return new String[]{"#.#", "#.#", ".#.", ".#.", ".#."};
            case 'Z': return new String[]{"###", "..#", ".#.", "#..", "###"};
            case '/': return new String[]{"..#", "..#", ".#.", "#..", "#.."};
            case '+': return new String[]{"...", ".#.", "###", ".#.", "..."};
            case ':': return new String[]{".", "#", ".", "#", "."};
            case '0': return new String[]{"###", "#.#", "#.#", "#.#", "###"};
            case '1': return new String[]{".#.", "##.", ".#.", ".#.", "###"};
            case '2': return new String[]{"###", "..#", "###", "#..", "###"};
            case '3': return new String[]{"###", "..#", ".##", "..#", "###"};
            case '4': return new String[]{"#.#", "#.#", "###", "..#", "..#"};
            case '5': return new String[]{"###", "#..", "###", "..#", "###"};
            case '6': return new String[]{"###", "#..", "###", "#.#", "###"};
            case '7': return new String[]{"###", "..#", ".#.", ".#.", ".#."};
            case '8': return new String[]{"###", "#.#", "###", "#.#", "###"};
            case '9': return new String[]{"###", "#.#", "###", "..#", "###"};
            case '*': return new String[]{"...", "#.#", ".#.", "#.#", "..."};
            case '#': return new String[]{".#.#.", "#####", ".#.#.", "#####", ".#.#."};
            default: return SPACE;
        }
    }
}
