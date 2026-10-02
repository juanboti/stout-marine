package brewemu.brew;

import brewemu.cpu.Memory;

/** C printf formatting for guest format strings (%d %i %u %x %X %o %c %s %p %%, flags, width, precision). */
final class Fmt {
    interface Args { int next(); }

    static String format(Memory mem, String fmt, Args a) {
        StringBuilder out = new StringBuilder();
        int i = 0, n = fmt.length();
        while (i < n) {
            char ch = fmt.charAt(i);
            if (ch != '%') { out.append(ch); i++; continue; }
            int j = i + 1;
            boolean left = false, zero = false, plus = false, space = false, alt = false;
            for (; j < n; j++) {
                char f = fmt.charAt(j);
                if (f == '-') left = true; else if (f == '0') zero = true; else if (f == '+') plus = true;
                else if (f == ' ') space = true; else if (f == '#') alt = true; else break;
            }
            int width = -1, prec = -1;
            if (j < n && fmt.charAt(j) == '*') { width = a.next(); if (width < 0) { left = true; width = -width; } j++; }
            else { while (j < n && Character.isDigit(fmt.charAt(j))) { width = Math.max(width, 0) * 10 + (fmt.charAt(j) - '0'); j++; } }
            if (j < n && fmt.charAt(j) == '.') {
                j++; prec = 0;
                if (j < n && fmt.charAt(j) == '*') { prec = a.next(); j++; }
                else while (j < n && Character.isDigit(fmt.charAt(j))) { prec = prec * 10 + (fmt.charAt(j) - '0'); j++; }
            }
            while (j < n && "hlLqjzt".indexOf(fmt.charAt(j)) >= 0) j++;
            if (j >= n) { out.append(fmt, i, n); break; }
            char conv = fmt.charAt(j);
            String body; boolean numeric = true; String sign = "";
            switch (conv) {
                case 'd': case 'i': {
                    long v = a.next();
                    if (v < 0) { sign = "-"; v = -v; } else if (plus) sign = "+"; else if (space) sign = " ";
                    body = Long.toString(v); break;
                }
                case 'u': body = Long.toString(a.next() & 0xffffffffL); break;
                case 'x': body = Long.toHexString(a.next() & 0xffffffffL); if (alt && !body.equals("0")) sign = "0x"; break;
                case 'X': body = Long.toHexString(a.next() & 0xffffffffL).toUpperCase(); if (alt && !body.equals("0")) sign = "0X"; break;
                case 'o': body = Long.toOctalString(a.next() & 0xffffffffL); break;
                case 'p': body = Long.toHexString(a.next() & 0xffffffffL); sign = "0x"; break;
                case 'c': body = String.valueOf((char) (a.next() & 0xff)); numeric = false; break;
                case 's': {
                    int p = a.next();
                    String s = p == 0 ? "(null)" : mem.cstr(p);
                    if (prec >= 0 && s.length() > prec) s = s.substring(0, prec);
                    body = s; numeric = false; break;
                }
                case '%': out.append('%'); i = j + 1; continue;
                default: out.append(fmt, i, j + 1); i = j + 1; continue;
            }
            if (numeric && prec >= 0) {
                while (body.length() < prec) body = "0" + body;
                if (prec == 0 && body.equals("0")) body = "";
                zero = false;
            }
            int len = sign.length() + body.length();
            if (width > len) {
                StringBuilder pad = new StringBuilder();
                for (int k = len; k < width; k++) pad.append(zero && numeric && !left ? '0' : ' ');
                if (left) body = sign + body + pad;
                else if (zero && numeric) body = sign + pad + body;
                else body = pad + sign + body;
            } else body = sign + body;
            out.append(body);
            i = j + 1;
        }
        return out.toString();
    }
}
