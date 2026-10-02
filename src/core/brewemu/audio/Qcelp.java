/*
 * QCELP (TIA/EIA/IS-733, "13K") speech decoder.
 *
 * Java port of FFmpeg's libavcodec/qcelpdec.c and qcelpdata.h (plus the small CELP helper routines it uses).
 * Original C code: Copyright (c) 2007 Reynaldo H. Verdejo Pinochet, and the FFmpeg developers.
 *
 * This file is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This file is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser
 * General Public License (LICENSE-LGPL-2.1.txt) for more details.
 */
package brewemu.audio;

/** Decodes QCELP-13K packets (rate byte + payload) into 160 samples of 8 kHz audio per packet. */
public final class Qcelp {
    static final int I_F_Q = -1, SILENCE = 0, RATE_OCTAVE = 1, RATE_QUARTER = 2, RATE_HALF = 3, RATE_FULL = 4;
    // unpacked frame layout (byte offsets as in the C struct)
    static final int CBSIGN = 0, CBGAIN = 16, CINDEX = 32, PLAG = 48, PFRAC = 52, PGAIN = 56, LSPV = 60, RESERVED = 70;

    static final float[] HAMMSINC = {-0.006822f, 0.041249f, -0.143459f, 0.588863f};
    static final double[] RND_FIR = {-1.344519e-1, 1.735384e-2, -6.905826e-2, 2.434368e-2, -8.210701e-2, 3.041388e-2,
            -9.251384e-2, 3.501983e-2, -9.918777e-2, 3.749518e-2, 8.985137e-1};
    static final float FULL_RATIO = .01f, HALF_RATIO = 0.5f;
    static final double SQRT1887 = 1.373681186;
    static final float SPREAD = 0.02f;
    static final double OCT_PRED = 29.0 / 32;
    static final double BW_EXP = 0.9883;

    static final int[] BITMAP_FULL = {
        62, 0, 3,  61, 0, 7,  60, 0, 6,  64, 0, 6,  63, 0, 6,  62, 3, 4,
        0, 0, 1,  16, 0, 4,  52, 0, 1,  48, 0, 7,  56, 0, 3,  33, 0, 4,
        1, 0, 1,  17, 0, 4,  32, 0, 7,  19, 0, 1,  34, 0, 7,  2, 0, 1,
        18, 0, 4,  33, 4, 3,  49, 0, 3,  57, 0, 3,  35, 0, 7,  3, 0, 1,
        19, 1, 2,  36, 0, 6,  4, 0, 1,  20, 0, 4,  53, 0, 1,  49, 3, 4,
        22, 0, 3,  37, 0, 7,  5, 0, 1,  21, 0, 4,  36, 6, 1,  39, 0, 3,
        7, 0, 1,  23, 0, 3,  38, 0, 7,  6, 0, 1,  22, 3, 1,  24, 0, 1,
        54, 0, 1,  50, 0, 7,  58, 0, 3,  39, 3, 4,  9, 0, 1,  25, 0, 4,
        40, 0, 7,  8, 0, 1,  24, 1, 3,  42, 0, 4,  10, 0, 1,  26, 0, 4,
        41, 0, 7,  59, 0, 2,  43, 0, 7,  11, 0, 1,  27, 0, 3,  42, 4, 3,
        44, 0, 2,  12, 0, 1,  28, 0, 4,  55, 0, 1,  51, 0, 7,  59, 2, 1,
        45, 0, 6,  13, 0, 1,  29, 0, 4,  44, 2, 5,  31, 0, 3,  46, 0, 7,
        14, 0, 1,  30, 0, 4,  45, 6, 1,  70, 0, 2,  47, 0, 7,  15, 0, 1};
    static final int[] BITMAP_HALF = {
        62, 0, 3,  61, 0, 7,  60, 0, 6,  64, 0, 6,  63, 0, 6,  62, 3, 4,
        0, 0, 1,  16, 0, 4,  52, 0, 1,  48, 0, 7,  56, 0, 3,  49, 0, 6,
        57, 0, 3,  32, 0, 7,  58, 0, 2,  33, 0, 7,  1, 0, 1,  17, 0, 4,
        53, 0, 1,  49, 6, 1,  34, 0, 2,  2, 0, 1,  18, 0, 4,  54, 0, 1,
        50, 0, 7,  58, 2, 1,  55, 0, 1,  51, 0, 7,  59, 0, 3,  34, 2, 5,
        35, 0, 7,  3, 0, 1,  19, 0, 4};
    static final int[] BITMAP_QUARTER = {
        62, 0, 3,  61, 0, 7,  60, 0, 6,  64, 0, 6,  63, 0, 6,  62, 3, 4,
        19, 0, 4,  18, 0, 4,  17, 0, 4,  16, 0, 4,  70, 0, 2,  20, 0, 4};
    static final int[] BITMAP_OCTAVE = {
        15, 3, 1,  60, 0, 1,  61, 0, 1,  62, 0, 1,  15, 2, 1,  63, 0, 1,
        64, 0, 1,  65, 0, 1,  15, 1, 1,  66, 0, 1,  67, 0, 1,  68, 0, 1,
        15, 0, 1,  69, 0, 1,  16, 0, 2,  70, 0, 4};
    static final short[] LSPVQ1 = {327, 118, 919, 111, 427, 440, 1327, 185, 469, 50, 1272, 91, 892, 59, 1771, 193, 222, 158, 1100, 127, 827, 55, 978, 791, 665, 47, 700, 1401, 670, 859, 1913, 1048, 471, 215, 1046, 125, 645, 298, 1599, 160, 593, 39, 1187, 462, 749, 341, 1520, 511, 290, 792, 909, 362, 753, 81, 1111, 1058, 519, 253, 828, 839, 685, 541, 1421, 1258, 386, 130, 962, 119, 542, 387, 1431, 185, 526, 51, 1175, 260, 831, 167, 1728, 510, 273, 437, 1172, 113, 771, 144, 1122, 751, 619, 119, 492, 1276, 658, 695, 1882, 615, 415, 200, 1018, 88, 681, 339, 1436, 325, 555, 122, 1042, 485, 826, 345, 1374, 743, 383, 1018, 1005, 358, 704, 86, 1301, 586, 597, 241, 832, 621, 555, 573, 1504, 839};
    static final short[] LSPVQ2 = {255, 293, 904, 219, 151, 1211, 1447, 498, 470, 253, 1559, 177, 1547, 994, 2394, 242, 91, 813, 857, 590, 934, 1326, 1889, 282, 813, 472, 1057, 1494, 450, 3315, 2163, 1895, 538, 532, 1399, 218, 146, 1552, 1755, 626, 822, 202, 1299, 663, 706, 1732, 2656, 401, 418, 745, 762, 1038, 583, 1748, 1746, 1285, 527, 1169, 1314, 830, 556, 2116, 1073, 2321, 297, 570, 981, 403, 468, 1103, 1740, 243, 725, 179, 1255, 474, 1374, 1362, 1922, 912, 285, 947, 930, 700, 593, 1372, 1909, 576, 588, 916, 1110, 1116, 224, 2719, 1633, 2220, 402, 520, 1061, 448, 402, 1352, 1499, 775, 664, 589, 1081, 727, 801, 2206, 2165, 1157, 566, 802, 911, 1116, 306, 1703, 1792, 836, 655, 999, 1061, 1038, 298, 2089, 1110, 1753, 361, 311, 970, 239, 265, 1231, 1495, 573, 566, 262, 1569, 293, 1341, 1144, 2271, 544, 214, 877, 847, 719, 794, 1384, 2067, 274, 703, 688, 1099, 1306, 391, 2947, 2024, 1670, 471, 525, 1245, 290, 264, 1557, 1568, 807, 718, 399, 1193, 685, 883, 1594, 2729, 764, 500, 754, 809, 1108, 541, 1648, 1523, 1385, 614, 1196, 1209, 847, 345, 2242, 1442, 1747, 199, 560, 1092, 194, 349, 1253, 1653, 507, 625, 354, 1376, 431, 1187, 1465, 2164, 872, 360, 974, 1008, 698, 704, 1346, 2114, 452, 720, 816, 1240, 1089, 439, 2475, 1498, 2040, 336, 718, 1213, 187, 451, 1450, 1368, 885, 592, 578, 1131, 531, 861, 1855, 1764, 1500, 444, 970, 935, 903, 424, 1687, 1633, 1102, 793, 897, 1060, 897, 185, 2011, 1205, 1855};
    static final short[] LSPVQ3 = {225, 283, 1296, 355, 543, 343, 2073, 274, 204, 1099, 1562, 523, 1388, 161, 2784, 274, 112, 849, 1870, 175, 1189, 160, 1490, 1088, 969, 1115, 659, 3322, 1158, 1073, 3183, 1363, 517, 223, 1740, 223, 704, 387, 2637, 234, 692, 1005, 1287, 1610, 952, 532, 2393, 646, 490, 552, 1619, 657, 845, 670, 1784, 2280, 191, 1775, 272, 2868, 942, 952, 2628, 1479, 278, 579, 1565, 218, 814, 180, 2379, 187, 276, 1444, 1199, 1223, 1200, 349, 3009, 307, 312, 844, 1898, 306, 863, 470, 1685, 1241, 513, 1727, 711, 2233, 1085, 864, 3398, 527, 414, 440, 1356, 612, 964, 147, 2173, 738, 465, 1292, 877, 1749, 1104, 689, 2105, 1311, 580, 864, 1895, 752, 652, 609, 1485, 1699, 514, 1400, 386, 2131, 933, 798, 2473, 986, 334, 360, 1375, 398, 621, 276, 2183, 280, 311, 1114, 1382, 807, 1284, 175, 2605, 636, 230, 816, 1739, 408, 1074, 176, 1619, 1120, 784, 1371, 448, 3050, 1189, 880, 3039, 1165, 424, 241, 1672, 186, 815, 333, 2432, 324, 584, 1029, 1137, 1546, 1015, 585, 2198, 995, 574, 581, 1746, 647, 733, 740, 1938, 1737, 347, 1710, 373, 2429, 787, 1061, 2439, 1438, 185, 536, 1489, 178, 703, 216, 2178, 487, 154, 1421, 1414, 994, 1103, 352, 3072, 473, 408, 819, 2055, 168, 998, 354, 1917, 1140, 665, 1799, 993, 2213, 1234, 631, 3003, 762, 373, 620, 1518, 425, 913, 300, 1966, 836, 402, 1185, 948, 1385, 1121, 555, 1802, 1509, 474, 886, 1888, 610, 739, 585, 1231, 2379, 661, 1335, 205, 2211, 823, 822, 2480, 1179};
    static final short[] LSPVQ4 = {348, 311, 812, 1145, 552, 461, 1826, 263, 601, 675, 1730, 172, 1523, 193, 2449, 277, 334, 668, 805, 1441, 1319, 207, 1684, 910, 582, 1318, 1403, 1098, 979, 832, 2700, 1359, 624, 228, 1292, 979, 800, 195, 2226, 285, 730, 862, 1537, 601, 1115, 509, 2720, 354, 218, 1167, 1212, 1538, 1074, 247, 1674, 1710, 322, 2142, 1263, 777, 981, 556, 2119, 1710, 193, 596, 1035, 957, 694, 397, 1997, 253, 743, 603, 1584, 321, 1346, 346, 2221, 708, 451, 732, 1040, 1415, 1184, 230, 1853, 919, 310, 1661, 1625, 706, 856, 843, 2902, 702, 467, 348, 1108, 1048, 859, 306, 1964, 463, 560, 1013, 1425, 533, 1142, 634, 2391, 879, 397, 1084, 1345, 1700, 976, 248, 1887, 1189, 644, 2087, 1262, 603, 877, 550, 2203, 1307};
    static final short[] LSPVQ5 = {360, 222, 820, 1097, 601, 319, 1656, 198, 604, 513, 1552, 141, 1391, 155, 2474, 261, 269, 785, 1463, 646, 1123, 191, 2015, 223, 785, 844, 1202, 1011, 980, 807, 3014, 793, 570, 180, 1135, 1382, 778, 256, 1901, 179, 807, 622, 1461, 458, 1231, 178, 2028, 821, 387, 927, 1496, 1004, 888, 392, 2246, 341, 295, 1462, 1156, 694, 1022, 473, 2226, 1364, 210, 478, 1029, 1020, 722, 181, 1730, 251, 730, 488, 1465, 293, 1303, 326, 2595, 387, 458, 584, 1569, 742, 1029, 173, 1910, 495, 605, 1159, 1268, 719, 973, 646, 2872, 428, 443, 334, 835, 1465, 912, 138, 1716, 442, 620, 778, 1316, 450, 1186, 335, 1446, 1665, 486, 1050, 1675, 1019, 880, 278, 2214, 202, 539, 1564, 1142, 533, 984, 391, 2130, 1089};
    static final float[] G12GA = {1.000f / 8192f, 1.125f / 8192f, 1.250f / 8192f, 1.375f / 8192f, 1.625f / 8192f, 1.750f / 8192f, 2.000f / 8192f, 2.250f / 8192f, 2.500f / 8192f, 2.875f / 8192f, 3.125f / 8192f, 3.500f / 8192f, 4.000f / 8192f, 4.500f / 8192f, 5.000f / 8192f, 5.625f / 8192f, 6.250f / 8192f, 7.125f / 8192f, 8.000f / 8192f, 8.875f / 8192f, 10.000f / 8192f, 11.250f / 8192f, 12.625f / 8192f, 14.125f / 8192f, 15.875f / 8192f, 17.750f / 8192f, 20.000f / 8192f, 22.375f / 8192f, 25.125f / 8192f, 28.125f / 8192f, 31.625f / 8192f, 35.500f / 8192f, 39.750f / 8192f, 44.625f / 8192f, 50.125f / 8192f, 56.250f / 8192f, 63.125f / 8192f, 70.750f / 8192f, 79.375f / 8192f, 89.125f / 8192f, 100.000f / 8192f, 112.250f / 8192f, 125.875f / 8192f, 141.250f / 8192f, 158.500f / 8192f, 177.875f / 8192f, 199.500f / 8192f, 223.875f / 8192f, 251.250f / 8192f, 281.875f / 8192f, 316.250f / 8192f, 354.875f / 8192f, 398.125f / 8192f, 446.625f / 8192f, 501.125f / 8192f, 562.375f / 8192f, 631.000f / 8192f, 708.000f / 8192f, 794.375f / 8192f, 891.250f / 8192f, 1000.000f / 8192f};
    static final short[] FULL_CODEBOOK = {10, -65, -59, 12, 110, 34, -134, 157, 104, -84, -34, -115, 23, -101, 3, 45, -101, -16, -59, 28, -45, 134, -67, 22, 61, -29, 226, -26, -55, -179, 157, -51, -220, -93, -37, 60, 118, 74, -48, -95, -181, 111, 36, -52, -215, 78, -112, 39, -17, -47, -223, 19, 12, -98, -142, 130, 54, -127, 21, -12, 39, -48, 12, 128, 6, -167, 82, -102, -79, 55, -44, 48, -20, -53, 8, -61, 11, -70, -157, -168, 20, -56, -74, 78, 33, -63, -173, -2, -75, -53, -146, 77, 66, -29, 9, -75, 65, 119, -43, 76, 233, 98, 125, -156, -27, 78, -9, 170, 176, 143, -148, -7, 27, -136, 5, 27, 18, 139, 204, 7, -184, -197, 52, -3, 78, -189, 8, -65};
    static final short[] HALF_CODEBOOK = {0, -4, 0, -3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -3, -2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 5, 0, 0, 0, 0, 0, 0, 4, 0, 0, 3, 2, 0, 3, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 0, 0, -3, 3, 0, 0, -2, 0, 3, 0, 0, 0, 0, 0, 0, 0, -5, 0, 0, 0, 0, 3, 0, 0, 0, 3, 0, 0, 0, 0, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 6, -3, -4, 0, -3, -3, 3, -3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

    static final short[][] LSPVQ = {LSPVQ1, LSPVQ2, LSPVQ3, LSPVQ4, LSPVQ5};
    static final int[][] BITMAPS = {null, BITMAP_OCTAVE, BITMAP_QUARTER, BITMAP_HALF, BITMAP_FULL};

    // ---- decoder state
    private int bitrate;
    private final int[] frame = new int[71];
    private int erasureCount, octaveCount;
    private final float[] prevLspf = new float[10], predictorLspf = new float[10];
    private final float[] pitchSynthMem = new float[303], pitchPreMem = new float[303];
    private final float[] rndFirMem = new float[180];
    private final float[] formantMem = new float[170];
    private float lastCodebookGain;
    private final int[] prevG1 = new int[2];
    private int prevBitrate;
    private final float[] pitchGain = new float[4];
    private final int[] pitchLag = new int[4];
    private int first16bits;
    private final float[] postSynthMem = new float[10];
    private float postAgcMem, postTiltMem;

    public Qcelp() { for (int i = 0; i < 10; i++) prevLspf[i] = (i + 1) / 11.0f; }

    /** Packet size in bytes (including the rate byte) for a rate byte value, or -1. */
    public static int packetSize(int rate) {
        switch (rate) { case 4: return 35; case 3: return 17; case 2: return 8; case 1: return 4; case 0: return 1; default: return -1; }
    }

    private static float clampf(float x, float lo, float hi) { return x < lo ? lo : x > hi ? hi : x; }
    private static int clip(int x, int lo, int hi) { return x < lo ? lo : x > hi ? hi : x; }

    private static void weightedSum(float[] out, int oo, float[] a, int ao, float[] b, int bo, float wa, float wb, int n) {
        for (int i = 0; i < n; i++) out[oo + i] = wa * a[ao + i] + wb * b[bo + i];
    }

    private static float dot(float[] a, int ao, float[] b, int bo, int n) {
        float s = 0; for (int i = 0; i < n; i++) s += a[ao + i] * b[bo + i]; return s;
    }

    private int decodeLspf(float[] lspf) {
        if (bitrate == RATE_OCTAVE || bitrate == I_F_Q) {
            float[] predictors = prevBitrate != RATE_OCTAVE && prevBitrate != I_F_Q ? prevLspf : predictorLspf;
            float smooth;
            if (bitrate == RATE_OCTAVE) {
                octaveCount++;
                for (int i = 0; i < 10; i++) {
                    lspf[i] = (float) ((frame[LSPV + i] != 0 ? SPREAD : -SPREAD) + predictors[i] * OCT_PRED
                            + (i + 1) * ((1 - OCT_PRED) / 11));
                }
                System.arraycopy(lspf, 0, predictorLspf, 0, 10);
                smooth = octaveCount < 10 ? .875f : 0.1f;
            } else {
                double erasureCoeff = OCT_PRED;
                if (erasureCount > 1) erasureCoeff *= erasureCount < 4 ? 0.9 : 0.7;
                for (int i = 0; i < 10; i++) lspf[i] = (float) ((i + 1) * (1 - erasureCoeff) / 11 + erasureCoeff * predictors[i]);
                System.arraycopy(lspf, 0, predictorLspf, 0, 10);
                smooth = 0.125f;
            }
            lspf[0] = Math.max(lspf[0], SPREAD);
            for (int i = 1; i < 10; i++) lspf[i] = Math.max(lspf[i], lspf[i - 1] + SPREAD);
            lspf[9] = Math.min(lspf[9], 1.0f - SPREAD);
            for (int i = 9; i > 0; i--) lspf[i - 1] = Math.min(lspf[i - 1], lspf[i] - SPREAD);
            weightedSum(lspf, 0, lspf, 0, prevLspf, 0, smooth, 1.0f - smooth, 10);
        } else {
            octaveCount = 0;
            float tmp = 0;
            for (int i = 0; i < 5; i++) {
                int v = frame[LSPV + i];
                lspf[2 * i] = tmp += LSPVQ[i][2 * v] * 0.0001f;
                lspf[2 * i + 1] = tmp += LSPVQ[i][2 * v + 1] * 0.0001f;
            }
            if (bitrate == RATE_QUARTER) {
                if (lspf[9] <= .70f || lspf[9] >= .97f) return -1;
                for (int i = 3; i < 10; i++) if (Math.abs(lspf[i] - lspf[i - 2]) < .08f) return -1;
            } else {
                if (lspf[9] <= .66f || lspf[9] >= .985f) return -1;
                for (int i = 4; i < 10; i++) if (Math.abs(lspf[i] - lspf[i - 4]) < .0931f) return -1;
            }
        }
        return 0;
    }

    private void decodeGainAndIndex(float[] gain) {
        int[] g1 = new int[16];
        int i;
        if (bitrate >= RATE_QUARTER) {
            int n = bitrate == RATE_FULL ? 16 : bitrate == RATE_HALF ? 4 : 5;
            for (i = 0; i < n; i++) {
                g1[i] = 4 * frame[CBGAIN + i];
                if (bitrate == RATE_FULL && ((i + 1) & 3) == 0) g1[i] += clip((g1[i - 1] + g1[i - 2] + g1[i - 3]) / 3 - 6, 0, 32);
                gain[i] = G12GA[g1[i]];
                if (frame[CBSIGN + i] != 0) {
                    gain[i] = -gain[i];
                    frame[CINDEX + i] = (frame[CINDEX + i] - 89) & 127;
                }
            }
            prevG1[0] = g1[i - 2];
            prevG1[1] = g1[i - 1];
            lastCodebookGain = G12GA[g1[i - 1]];
            if (bitrate == RATE_QUARTER) {
                gain[7] = gain[4];
                gain[6] = 0.4f * gain[3] + 0.6f * gain[4];
                gain[5] = gain[3];
                gain[4] = 0.8f * gain[2] + 0.2f * gain[3];
                gain[3] = 0.2f * gain[1] + 0.8f * gain[2];
                gain[2] = gain[1];
                gain[1] = 0.6f * gain[0] + 0.4f * gain[1];
            }
        } else if (bitrate != SILENCE) {
            int n;
            if (bitrate == RATE_OCTAVE) {
                g1[0] = 2 * frame[CBGAIN] + clip((prevG1[0] + prevG1[1]) / 2 - 5, 0, 54);
                n = 8;
            } else {
                g1[0] = prevG1[1];
                switch (erasureCount) { case 1: break; case 2: g1[0] -= 1; break; case 3: g1[0] -= 2; break; default: g1[0] -= 6; }
                if (g1[0] < 0) g1[0] = 0;
                n = 4;
            }
            float slope = 0.5f * (G12GA[g1[0]] - lastCodebookGain) / n;
            for (i = 1; i <= n; i++) gain[i - 1] = lastCodebookGain + slope * i;
            lastCodebookGain = gain[i - 2];
            prevG1[0] = prevG1[1];
            prevG1[1] = g1[0];
        }
    }

    private boolean quarterGainBad() {
        int prevDiff = 0;
        for (int i = 1; i < 5; i++) {
            int diff = frame[CBGAIN + i] - frame[CBGAIN + i - 1];
            if (Math.abs(diff) > 10) return true;
            if (Math.abs(diff - prevDiff) > 12) return true;
            prevDiff = diff;
        }
        return false;
    }

    private void computeSvector(float[] gain, float[] cdn) {
        int o = 0;
        switch (bitrate) {
            case RATE_FULL:
                for (int i = 0; i < 16; i++) {
                    float g = gain[i] * FULL_RATIO;
                    int ci = (-frame[CINDEX + i]) & 0xffff;
                    for (int j = 0; j < 10; j++) cdn[o++] = g * FULL_CODEBOOK[ci++ & 127];
                }
                break;
            case RATE_HALF:
                for (int i = 0; i < 4; i++) {
                    float g = gain[i] * HALF_RATIO;
                    int ci = (-frame[CINDEX + i]) & 0xffff;
                    for (int j = 0; j < 40; j++) cdn[o++] = g * HALF_CODEBOOK[ci++ & 127];
                }
                break;
            case RATE_QUARTER: {
                int seed = ((0x0003 & frame[LSPV + 4]) << 14 | (0x003F & frame[LSPV + 3]) << 8 | (0x0060 & frame[LSPV + 2]) << 1
                        | (0x0007 & frame[LSPV + 1]) << 3 | (0x0038 & frame[LSPV]) >> 3) & 0xffff;
                int rnd = 20;
                for (int i = 0; i < 8; i++) {
                    float g = (float) (gain[i] * (SQRT1887 / 32768.0));
                    for (int k = 0; k < 20; k++) {
                        seed = (521 * seed + 259) & 0xffff;
                        rndFirMem[rnd] = (short) seed;
                        float f = 0;
                        for (int j = 0; j < 10; j++) f += RND_FIR[j] * (rndFirMem[rnd - j] + rndFirMem[rnd - 20 + j]);
                        f += RND_FIR[10] * rndFirMem[rnd - 10];
                        cdn[o++] = g * f;
                        rnd++;
                    }
                }
                System.arraycopy(rndFirMem, 160, rndFirMem, 0, 20);
                break;
            }
            case RATE_OCTAVE: {
                int seed = first16bits;
                for (int i = 0; i < 8; i++) {
                    float g = (float) (gain[i] * (SQRT1887 / 32768.0));
                    for (int j = 0; j < 20; j++) { seed = (521 * seed + 259) & 0xffff; cdn[o++] = g * (short) seed; }
                }
                break;
            }
            case I_F_Q: {
                int seed = -44;
                for (int i = 0; i < 4; i++) {
                    float g = gain[i] * FULL_RATIO;
                    for (int j = 0; j < 40; j++) cdn[o++] = g * FULL_CODEBOOK[seed++ & 127];
                }
                break;
            }
            default:
                java.util.Arrays.fill(cdn, 0, 160, 0f);
        }
    }

    private static void scaleToSumOfSquares(float[] out, int oo, float[] in, int io, float sumSq, int n) {
        float sf = dot(in, io, in, io, n);
        if (sf != 0) sf = (float) Math.sqrt(sumSq / sf);
        for (int i = 0; i < n; i++) out[oo + i] = in[io + i] * sf;
    }

    /** Pitch filter over 4 subframes; result is memory[143..302]; returns a copy of it. */
    private static float[] pitchFilter(float[] mem, float[] vin, float[] gain, int[] lag, int[] pfrac) {
        int out = 143, in = 0;
        for (int i = 0; i < 4; i++) {
            if (gain[i] != 0) {
                int vl = 143 + 40 * i - lag[i];
                for (int k = 0; k < 40; k++, in++, vl++, out++) {
                    float v;
                    if (pfrac[i] != 0) {
                        v = 0;
                        for (int j = 0; j < 4; j++) v += HAMMSINC[j] * (mem[vl + j - 4] + mem[vl + 3 - j]);
                    } else v = mem[vl];
                    mem[out] = vin[in] + gain[i] * v;
                }
            } else {
                System.arraycopy(vin, in, mem, out, 40);
                in += 40; out += 40;
            }
        }
        float[] res = new float[160];
        System.arraycopy(mem, 143, res, 0, 160);
        System.arraycopy(mem, 160, mem, 0, 143);
        return res;
    }

    private void applyPitchFilters(float[] cdn) {
        if (bitrate >= RATE_HALF || bitrate == SILENCE || (bitrate == I_F_Q && prevBitrate >= RATE_HALF)) {
            int[] pfrac = new int[4];
            if (bitrate >= RATE_HALF) {
                for (int i = 0; i < 4; i++) {
                    pitchGain[i] = frame[PLAG + i] != 0 ? (frame[PGAIN + i] + 1) * 0.25f : 0.0f;
                    pitchLag[i] = frame[PLAG + i] + 16;
                    pfrac[i] = frame[PFRAC + i];
                }
            } else {
                float maxGain;
                if (bitrate == I_F_Q) maxGain = erasureCount < 3 ? 0.9f - 0.3f * (erasureCount - 1) : 0.0f;
                else maxGain = 1.0f;
                for (int i = 0; i < 4; i++) pitchGain[i] = Math.min(pitchGain[i], maxGain);
                for (int i = 0; i < 4; i++) frame[PFRAC + i] = 0;
            }
            float[] synth = pitchFilter(pitchSynthMem, cdn, pitchGain, pitchLag, pfrac);
            for (int i = 0; i < 4; i++) pitchGain[i] = 0.5f * Math.min(pitchGain[i], 1.0f);
            float[] pre = pitchFilter(pitchPreMem, synth, pitchGain, pitchLag, pfrac);
            for (int i = 0; i < 160; i += 40) scaleToSumOfSquares(cdn, i, pre, i, dot(synth, i, synth, i, 40), 40);
        } else {
            System.arraycopy(cdn, 17, pitchSynthMem, 0, 143);
            System.arraycopy(cdn, 17, pitchPreMem, 0, 143);
            java.util.Arrays.fill(pitchGain, 0);
            java.util.Arrays.fill(pitchLag, 0);
        }
    }

    private static void lsp2poly(double[] lsp, int off, double[] f, int half) {
        f[0] = 1.0;
        f[1] = -2 * lsp[off];
        for (int i = 2; i <= half; i++) {
            double val = -2 * lsp[off + 2 * i - 2];
            f[i] = val * f[i - 1] + 2 * f[i - 2];
            for (int j = i - 1; j > 1; j--) f[j] += f[j - 1] * val + f[j - 2];
            f[1] += val;
        }
    }

    private static void lspf2lpc(float[] lspf, float[] lpc) {
        double[] lsp = new double[10];
        for (int i = 0; i < 10; i++) lsp[i] = Math.cos(Math.PI * lspf[i]);
        double[] pa = new double[6], qa = new double[6];
        lsp2poly(lsp, 0, pa, 5);
        lsp2poly(lsp, 1, qa, 5);
        for (int k = 4; k >= 0; k--) {
            double paf = pa[k + 1] + pa[k], qaf = qa[k + 1] - qa[k];
            lpc[k] = (float) (0.5 * (paf + qaf));
            lpc[9 - k] = (float) (0.5 * (paf - qaf));
        }
        double c = BW_EXP;
        for (int i = 0; i < 10; i++) { lpc[i] *= c; c *= BW_EXP; }
    }

    private void interpolateLpc(float[] cur, float[] lpc, int sub) {
        float w;
        if (bitrate >= RATE_QUARTER) w = 0.25f * (sub + 1);
        else if (bitrate == RATE_OCTAVE && sub == 0) w = 0.625f;
        else w = 1.0f;
        if (w != 1.0f) {
            float[] t = new float[10];
            weightedSum(t, 0, cur, 0, prevLspf, 0, w, 1.0f - w, 10);
            lspf2lpc(t, lpc);
        } else if (bitrate >= RATE_QUARTER || (bitrate == I_F_Q && sub == 0)) lspf2lpc(cur, lpc);
        else if (bitrate == SILENCE && sub == 0) lspf2lpc(prevLspf, lpc);
    }

    /** All-pole synthesis: out[oo+n] = in[n] - sum c[i-1]*out[oo+n-i]; out must hold 10 history values before oo. */
    private static void lpSynthesis(float[] out, int oo, float[] c, float[] in, int io, int len) {
        for (int n = 0; n < len; n++) {
            float v = in[io + n];
            for (int i = 1; i <= 10; i++) v -= c[i - 1] * out[oo + n - i];
            out[oo + n] = v;
        }
    }

    private void postfilter(float[] samples, float[] lpc) {
        final float[] p775 = {0.775000f, 0.600625f, 0.465484f, 0.360750f, 0.279582f, 0.216676f, 0.167924f, 0.130141f, 0.100859f, 0.078166f};
        final float[] p625 = {0.625000f, 0.390625f, 0.244141f, 0.152588f, 0.095367f, 0.059605f, 0.037253f, 0.023283f, 0.014552f, 0.009095f};
        float[] s = new float[10], p = new float[10], pole = new float[170], zero = new float[160];
        for (int n = 0; n < 10; n++) { s[n] = lpc[n] * p625[n]; p[n] = lpc[n] * p775[n]; }
        for (int n = 0; n < 160; n++) {          // zero synthesis over formantMem[10..]
            float v = formantMem[10 + n];
            for (int i = 1; i <= 10; i++) v += s[i - 1] * formantMem[10 + n - i];
            zero[n] = v;
        }
        System.arraycopy(postSynthMem, 0, pole, 0, 10);
        lpSynthesis(pole, 10, p, zero, 0, 160);
        System.arraycopy(pole, 160, postSynthMem, 0, 10);
        // tilt compensation
        float newTilt = pole[10 + 159];
        for (int i = 159; i > 0; i--) pole[10 + i] -= 0.3f * pole[10 + i - 1];
        pole[10] -= 0.3f * postTiltMem;
        postTiltMem = newTilt;
        // adaptive gain control
        float speech = dot(formantMem, 10, formantMem, 10, 160);
        float post = dot(pole, 10, pole, 10, 160);
        float sf = 1.0f;
        if (post != 0) sf = (float) Math.sqrt(speech / post);
        sf *= 1.0f - 0.9375f;
        float mem = postAgcMem;
        for (int i = 0; i < 160; i++) { mem = 0.9375f * mem + sf; samples[i] = pole[10 + i] * mem; }
        postAgcMem = mem;
    }

    private boolean unpack(byte[] buf, int off, int len) {
        int[] bm = BITMAPS[bitrate];
        java.util.Arrays.fill(frame, 0);
        long bitPos = 0;
        for (int k = 0; k < bm.length; k += 3) {
            int idx = bm[k], pos = bm[k + 1], n = bm[k + 2], v = 0;
            for (int b = 0; b < n; b++, bitPos++) {
                int byteI = (int) (bitPos >> 3);
                int bit = byteI < len ? (buf[off + byteI] >> (7 - (bitPos & 7))) & 1 : 0;
                v = v << 1 | bit;
            }
            frame[idx] = (frame[idx] | v << pos) & 0xff;
        }
        return true;
    }

    /**
     * Decodes one packet (first byte = rate) into 160 float samples (nominal range about +-1).
     * A null or malformed packet is treated as an erasure.
     */
    public void decode(byte[] pkt, int off, int len, float[] out) {
        float[] lspf = new float[10], lpc = new float[10], gain = new float[16];
        boolean erasure = false;
        int rate = pkt == null || len < 1 ? -1 : pkt[off] & 0xff;
        if (rate < 0 || rate > 4 || packetSize(rate) > len) erasure = true;
        else {
            bitrate = rate;
            int p = off + 1, plen = len - 1;
            if (bitrate == RATE_OCTAVE) {
                first16bits = plen >= 2 ? ((pkt[p] & 0xff) << 8 | (pkt[p + 1] & 0xff)) : 0;
                if (first16bits == 0xFFFF) erasure = true;
            }
            if (!erasure && bitrate > SILENCE) {
                unpack(pkt, p, plen);
                if (frame[RESERVED] != 0) erasure = true;
                else if (bitrate == RATE_QUARTER && quarterGainBad()) erasure = true;
                else if (bitrate >= RATE_HALF) {
                    for (int i = 0; i < 4; i++) if (frame[PFRAC + i] != 0 && frame[PLAG + i] >= 124) erasure = true;
                }
            }
            if (!erasure) {
                decodeGainAndIndex(gain);
                computeSvector(gain, out);
                if (decodeLspf(lspf) < 0) erasure = true;
                else applyPitchFilters(out);
            }
        }
        if (erasure) {
            bitrate = I_F_Q;
            erasureCount++;
            decodeGainAndIndex(gain);
            computeSvector(gain, out);
            decodeLspf(lspf);
            applyPitchFilters(out);
        } else erasureCount = 0;

        for (int i = 0; i < 4; i++) {
            interpolateLpc(lspf, lpc, i);
            lpSynthesis(formantMem, 10 + 40 * i, lpc, out, i * 40, 40);
        }
        postfilter(out, lpc);
        System.arraycopy(formantMem, 160, formantMem, 0, 10);
        System.arraycopy(lspf, 0, prevLspf, 0, 10);
        prevBitrate = bitrate;
    }
}
