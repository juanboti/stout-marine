package brewemu.cpu;

/**
 * ARMv5TE interpreter (ARM + Thumb), user mode only.
 * Written from the public ARM architecture description; no coprocessors, no exceptions/interrupts.
 *
 * Calls into host code: any branch to an address >= {@link #TRAP_BASE} (unsigned) stops normal
 * execution and invokes {@link TrapHandler#trap}; the handler sets r0/r1 and the CPU returns to LR.
 * {@link #call} runs a guest function from the host and returns its r0.
 */
public final class Cpu {
    public static final int TRAP_BASE = 0xF0000000;
    /** LR value used by {@link #call}; reaching it ends the call. */
    public static final int RETURN_MAGIC = 0xFFFFFFF0;

    public interface TrapHandler { void trap(Cpu cpu, int address); }

    public final Memory mem;
    public final int[] r = new int[16];
    public boolean n, z, c, v, q, thumb;
    /** Address of the instruction being executed. */
    private int pc;
    public TrapHandler traps;
    public long executed;
    private int depth;

    public Cpu(Memory m) { mem = m; }

    // ------------------------------------------------------------------ host API
    public int cpsr() {
        return (n ? 1 << 31 : 0) | (z ? 1 << 30 : 0) | (c ? 1 << 29 : 0) | (v ? 1 << 28 : 0) | (q ? 1 << 27 : 0)
                | (thumb ? 1 << 5 : 0) | 0x10;
    }

    public void setCpsrFlags(int x) { n = x < 0; z = (x & (1 << 30)) != 0; c = (x & (1 << 29)) != 0; v = (x & (1 << 28)) != 0; q = (x & (1 << 27)) != 0; }

    /** Calls guest function fn (bit 0 = Thumb) with up to 4 register args and extra stack args; returns r0. */
    public int call(int fn, int[] args, int[] stackArgs) {
        int[] saved = r.clone();
        boolean sn = n, sz = z, sc = c, sv = v, sq = q, st = thumb;
        int savedPc = pc;
        int sp = r[13];
        int extra = stackArgs == null ? 0 : stackArgs.length;
        sp -= 4 * extra; sp &= ~7;
        for (int i = 0; i < extra; i++) mem.write32(sp + 4 * i, stackArgs[i]);
        for (int i = 0; i < 4; i++) r[i] = args != null && i < args.length ? args[i] : 0;
        r[13] = sp;
        r[14] = RETURN_MAGIC;
        depth++;
        try {
            branchTo(fn);
            runUntilReturn();
            int ret = r[0];
            return ret;
        } finally {
            depth--;
            int r0 = r[0], r1 = r[1];
            System.arraycopy(saved, 0, r, 0, 16);
            r[0] = r0; r[1] = r1;     // caller may look at the result in r0/r1
            n = sn; z = sz; c = sc; v = sv; q = sq; thumb = st; pc = savedPc;
        }
    }

    public int call(int fn, int... args) { return call(fn, args, null); }

    /** Sets PC with interworking (bit 0 selects Thumb). */
    public void branchTo(int addr) {
        if ((addr & 1) != 0) { thumb = true; pc = addr & ~1; }
        else { thumb = false; pc = addr & ~3; if ((addr ^ 0x80000000) >= (TRAP_BASE ^ 0x80000000)) pc = addr; }
    }

    private void runUntilReturn() {
        while (true) {
            if ((pc ^ 0x80000000) >= (TRAP_BASE ^ 0x80000000)) {
                if (pc == RETURN_MAGIC || pc == (RETURN_MAGIC & ~1)) return;
                int t = pc;
                traps.trap(this, t);
                branchTo(r[14]);   // return from the host function
                continue;
            }
            step();
        }
    }

    public int pc() { return pc; }

    // ------------------------------------------------------------------ execution
    public void step() {
        executed++;
        if (thumb) stepThumb(); else stepArm();
    }

    private boolean cond(int cc) {
        switch (cc) {
            case 0: return z;
            case 1: return !z;
            case 2: return c;
            case 3: return !c;
            case 4: return n;
            case 5: return !n;
            case 6: return v;
            case 7: return !v;
            case 8: return c && !z;
            case 9: return !c || z;
            case 10: return n == v;
            case 11: return n != v;
            case 12: return !z && n == v;
            case 13: return z || n != v;
            case 14: return true;
            default: return true;   // 0xF handled by caller
        }
    }

    /** Register read as seen by an ARM instruction (PC reads as current + 8). */
    private int ra(int i) { return i == 15 ? pc + 8 : r[i]; }

    private void setNZ(int x) { n = x < 0; z = x == 0; }

    private int add(int a, int b, int carryIn, boolean s) {
        long ua = a & 0xffffffffL, ub = b & 0xffffffffL;
        long res = ua + ub + carryIn;
        int x = (int) res;
        if (s) {
            setNZ(x);
            c = (res >>> 32) != 0;
            v = ((a ^ x) & (b ^ x)) < 0;
        }
        return x;
    }

    /** a - b - (1 - carry). */
    private int sub(int a, int b, int carry, boolean s) { return add(a, ~b, carry, s); }

    private boolean shC;   // shifter carry out

    /** Immediate-shift operand (shift amount from instruction). */
    private int shiftImm(int val, int type, int amt) {
        switch (type) {
            case 0: // LSL
                if (amt == 0) { shC = c; return val; }
                shC = ((val >>> (32 - amt)) & 1) != 0; return val << amt;
            case 1: // LSR (0 means 32)
                if (amt == 0) { shC = val < 0; return 0; }
                shC = ((val >>> (amt - 1)) & 1) != 0; return val >>> amt;
            case 2: // ASR (0 means 32)
                if (amt == 0) { shC = val < 0; return val >> 31; }
                shC = ((val >> (amt - 1)) & 1) != 0; return val >> amt;
            default: // ROR, 0 = RRX
                if (amt == 0) { boolean o = (val & 1) != 0; int x = (val >>> 1) | (c ? 0x80000000 : 0); shC = o; return x; }
                shC = ((val >>> (amt - 1)) & 1) != 0; return Integer.rotateRight(val, amt);
        }
    }

    /** Register-specified shift (amount = bottom byte of a register). */
    private int shiftReg(int val, int type, int amt) {
        amt &= 0xff;
        if (amt == 0) { shC = c; return val; }
        switch (type) {
            case 0:
                if (amt < 32) { shC = ((val >>> (32 - amt)) & 1) != 0; return val << amt; }
                shC = amt == 32 && (val & 1) != 0; return 0;
            case 1:
                if (amt < 32) { shC = ((val >>> (amt - 1)) & 1) != 0; return val >>> amt; }
                shC = amt == 32 && val < 0; return 0;
            case 2:
                if (amt < 32) { shC = ((val >> (amt - 1)) & 1) != 0; return val >> amt; }
                shC = val < 0; return val >> 31;
            default:
                int s = amt & 31;
                if (s == 0) { shC = val < 0; return val; }
                shC = ((val >>> (s - 1)) & 1) != 0; return Integer.rotateRight(val, s);
        }
    }

    private void stepArm() {
        int at = pc;
        int ins = mem.read32(at);
        int cc = ins >>> 28;
        if (cc == 0xF) { pc = at + 4; armUnconditional(ins, at); return; }
        if (cc != 14 && !cond(cc)) { pc = at + 4; return; }
        execArm(ins);   // runs with pc == at, so ra(15) == at + 8
    }

    /** Writes a register; writing PC branches (ARM: with interworking only where noted by callers). */
    private void wr(int rd, int val) {
        if (rd == 15) { pc = val & ~3; thumb = false; branched = true; }
        else r[rd] = val;
    }

    private boolean branched;

    private void wrPcInterwork(int val) { branched = true; branchTo(val); }

    private void execArm(int ins) {
        branched = false;
        int at = pc;
        int op = (ins >>> 25) & 7;
        switch (op) {
            case 0:
            case 1:
                execArmGroup0(ins, at);
                break;
            case 2:
            case 3:
                if (op == 3 && (ins & 0x10) != 0) throw undefined(ins, at);
                loadStore(ins, at);
                break;
            case 4:
                blockTransfer(ins, at);
                break;
            case 5: {
                int off = (ins << 8) >> 6;
                if ((ins & (1 << 24)) != 0) r[14] = at + 4;
                pc = at + 8 + off; branched = true;
                break;
            }
            default:
                throw undefined(ins, at);
        }
        if (!branched) pc = at + 4;
    }

    private void armUnconditional(int ins, int at) {
        if ((ins & 0x0e000000) == 0x0a000000) {     // BLX immediate
            int off = (ins << 8) >> 6;
            int h = (ins >> 24) & 1;
            r[14] = at + 4;
            thumb = true;
            pc = at + 8 + off + (h << 1);
            return;
        }
        if ((ins & 0x0d70f000) == 0x0550f000) return;   // PLD: no-op
        throw undefined(ins, at);
    }

    private RuntimeException undefined(int ins, int at) {
        return new IllegalStateException(String.format("undefined %s instruction %08x at %08x", thumb ? "Thumb" : "ARM", ins, at));
    }

    private void execArmGroup0(int ins, int at) {
        boolean imm = (ins & (1 << 25)) != 0;
        if (!imm) {
            // multiplies, extra load/stores, misc
            if ((ins & 0x90) == 0x90) {
                if ((ins & 0x60) == 0) {
                    if ((ins & 0x0f800000) == 0x00000000) { multiply(ins); return; }
                    if ((ins & 0x0f800000) == 0x00800000) { multiplyLong(ins); return; }
                    if ((ins & 0x0fb00ff0) == 0x01000090) { swap(ins); return; }
                    throw undefined(ins, at);
                }
                extraLoadStore(ins, at);
                return;
            }
            if ((ins & 0x01900000) == 0x01000000) { misc(ins, at); return; }
        } else if ((ins & 0x01b00000) == 0x01200000) {     // MSR immediate
            int rot = ((ins >> 8) & 15) * 2;
            int val = Integer.rotateRight(ins & 0xff, rot);
            if ((ins & (1 << 22)) == 0 && (ins & (1 << 19)) != 0) setCpsrFlags(val);
            return;
        } else if ((ins & 0x01b00000) == 0x01000000) throw undefined(ins, at);
        dataProcessing(ins, imm);
    }

    private void dataProcessing(int ins, boolean imm) {
        int opc = (ins >> 21) & 15;
        boolean s = (ins & (1 << 20)) != 0;
        int rn = (ins >> 16) & 15, rd = (ins >> 12) & 15;
        int op2;
        if (imm) {
            int rot = ((ins >> 8) & 15) * 2;
            op2 = Integer.rotateRight(ins & 0xff, rot);
            shC = rot == 0 ? c : op2 < 0;
        } else {
            int rm = ins & 15, type = (ins >> 5) & 3;
            if ((ins & 0x10) == 0) op2 = shiftImm(ra(rm), type, (ins >> 7) & 31);
            else {
                int rmv = rm == 15 ? pc + 12 : r[rm];
                int rnv0 = r[(ins >> 8) & 15];
                op2 = shiftReg(rmv, type, rnv0);
            }
        }
        int a = (imm || (ins & 0x10) == 0) ? ra(rn) : (rn == 15 ? pc + 12 : r[rn]);
        int res;
        switch (opc) {
            case 0: res = a & op2; if (s) { setNZ(res); c = shC; } wr(rd, res); break;
            case 1: res = a ^ op2; if (s) { setNZ(res); c = shC; } wr(rd, res); break;
            case 2: wr(rd, sub(a, op2, 1, s)); break;
            case 3: wr(rd, sub(op2, a, 1, s)); break;
            case 4: wr(rd, add(a, op2, 0, s)); break;
            case 5: wr(rd, add(a, op2, c ? 1 : 0, s)); break;
            case 6: wr(rd, sub(a, op2, c ? 1 : 0, s)); break;
            case 7: wr(rd, sub(op2, a, c ? 1 : 0, s)); break;
            case 8: res = a & op2; setNZ(res); c = shC; break;
            case 9: res = a ^ op2; setNZ(res); c = shC; break;
            case 10: sub(a, op2, 1, true); break;
            case 11: add(a, op2, 0, true); break;
            case 12: res = a | op2; if (s) { setNZ(res); c = shC; } wr(rd, res); break;
            case 13: res = op2; if (s) { setNZ(res); c = shC; } wr(rd, res); break;
            case 14: res = a & ~op2; if (s) { setNZ(res); c = shC; } wr(rd, res); break;
            default: res = ~op2; if (s) { setNZ(res); c = shC; } wr(rd, res); break;
        }
    }

    private void multiply(int ins) {
        int rd = (ins >> 16) & 15, rn = (ins >> 12) & 15, rs = (ins >> 8) & 15, rm = ins & 15;
        int res = r[rm] * r[rs];
        if ((ins & (1 << 21)) != 0) res += r[rn];
        r[rd] = res;
        if ((ins & (1 << 20)) != 0) setNZ(res);
    }

    private void multiplyLong(int ins) {
        int hi = (ins >> 16) & 15, lo = (ins >> 12) & 15, rs = (ins >> 8) & 15, rm = ins & 15;
        boolean signed = (ins & (1 << 22)) != 0, acc = (ins & (1 << 21)) != 0;
        long res = signed ? (long) r[rm] * (long) r[rs] : (r[rm] & 0xffffffffL) * (r[rs] & 0xffffffffL);
        if (acc) res += ((long) r[hi] << 32) | (r[lo] & 0xffffffffL);
        r[lo] = (int) res; r[hi] = (int) (res >>> 32);
        if ((ins & (1 << 20)) != 0) { n = res < 0; z = res == 0; }
    }

    private void swap(int ins) {
        int rn = (ins >> 16) & 15, rd = (ins >> 12) & 15, rm = ins & 15;
        int addr = r[rn];
        if ((ins & (1 << 22)) != 0) { int t = mem.read8(addr); mem.write8(addr, r[rm]); r[rd] = t; }
        else { int t = Integer.rotateRight(mem.read32(addr), (addr & 3) * 8); mem.write32(addr, r[rm]); r[rd] = t; }
    }

    private void misc(int ins, int at) {
        int op = (ins >> 4) & 15;
        int op2 = (ins >> 21) & 3;
        if (op == 0) {
            if ((ins & 0x00200000) == 0) {           // MRS
                r[(ins >> 12) & 15] = cpsr();
            } else {                                  // MSR register
                if ((ins & (1 << 22)) == 0 && (ins & (1 << 19)) != 0) setCpsrFlags(r[ins & 15]);
            }
            return;
        }
        if (op == 1 && op2 == 1) { wrPcInterwork(r[ins & 15]); return; }                 // BX
        if (op == 1 && op2 == 3) {                                                          // CLZ
            r[(ins >> 12) & 15] = Integer.numberOfLeadingZeros(r[ins & 15]); return;
        }
        if (op == 3 && op2 == 1) { int t = r[ins & 15]; r[14] = at + 4; wrPcInterwork(t); return; }  // BLX reg
        if (op == 5) {                                                                      // QADD/QSUB/QDADD/QDSUB
            int rd = (ins >> 12) & 15, rn = (ins >> 16) & 15, rm = ins & 15;
            long b = r[rn];
            if ((op2 & 2) != 0) b = sat(b * 2);
            long res = (op2 & 1) == 0 ? (long) r[rm] + b : (long) r[rm] - b;
            r[rd] = (int) sat(res);
            return;
        }
        if ((op & 9) == 8) {                                                                // signed 16-bit multiplies
            int rd = (ins >> 16) & 15, rn = (ins >> 12) & 15, rs = (ins >> 8) & 15, rm = ins & 15;
            boolean x = (op & 2) != 0, y = (op & 4) != 0;
            int a = x ? r[rm] >> 16 : (short) r[rm];
            int b = y ? r[rs] >> 16 : (short) r[rs];
            switch (op2) {
                case 0: { long res = (long) a * b + r[rn]; if (res != (int) res) q = true; r[rd] = (int) res; return; }   // SMLAxy
                case 1: {                                                                    // SMLAWy / SMULWy
                    long res = ((long) r[rm] * b) >> 16;
                    if (!x) { long s2 = res + r[rn]; if (s2 != (int) s2) q = true; r[rd] = (int) s2; }
                    else r[rd] = (int) res;
                    return;
                }
                case 2: {                                                                    // SMLALxy
                    long acc = ((long) r[rd] << 32) | (r[rn] & 0xffffffffL);
                    acc += (long) a * b;
                    r[rn] = (int) acc; r[rd] = (int) (acc >>> 32);
                    return;
                }
                default: r[rd] = a * b; return;                                              // SMULxy
            }
        }
        if (op == 7) throw new IllegalStateException(String.format("BKPT at %08x", at));
        throw undefined(ins, at);
    }

    private long sat(long x) {
        if (x > Integer.MAX_VALUE) { q = true; return Integer.MAX_VALUE; }
        if (x < Integer.MIN_VALUE) { q = true; return Integer.MIN_VALUE; }
        return x;
    }

    private void extraLoadStore(int ins, int at) {
        boolean p = (ins & (1 << 24)) != 0, u = (ins & (1 << 23)) != 0, immOff = (ins & (1 << 22)) != 0,
                w = (ins & (1 << 21)) != 0, l = (ins & (1 << 20)) != 0;
        int rn = (ins >> 16) & 15, rd = (ins >> 12) & 15, sh = (ins >> 5) & 3;
        int off = immOff ? ((ins >> 4) & 0xf0) | (ins & 15) : r[ins & 15];
        int base = ra(rn);
        int addr = p ? (u ? base + off : base - off) : base;
        int wb = u ? base + off : base - off;
        if (l) {
            int val;
            switch (sh) {
                case 1: val = mem.read16(addr); break;
                case 2: val = (byte) mem.read8(addr); break;
                default: val = (short) mem.read16(addr); break;
            }
            if (!p || w) r[rn] = wb;
            wr(rd, val);
        } else {
            switch (sh) {
                case 1: mem.write16(addr, ra(rd)); break;
                case 2: // LDRD
                    if (!p || w) r[rn] = wb;
                    r[rd] = mem.read32(addr); r[rd + 1] = mem.read32(addr + 4);
                    return;
                default: // STRD
                    mem.write32(addr, r[rd]); mem.write32(addr + 4, r[rd + 1]);
                    break;
            }
            if (!p || w) r[rn] = wb;
        }
    }

    private void loadStore(int ins, int at) {
        boolean regOff = (ins & (1 << 25)) != 0, p = (ins & (1 << 24)) != 0, u = (ins & (1 << 23)) != 0,
                b = (ins & (1 << 22)) != 0, w = (ins & (1 << 21)) != 0, l = (ins & (1 << 20)) != 0;
        int rn = (ins >> 16) & 15, rd = (ins >> 12) & 15;
        int off;
        if (regOff) {
            boolean savedC = c;
            off = shiftImm(r[ins & 15], (ins >> 5) & 3, (ins >> 7) & 31);
            c = savedC;
        } else off = ins & 0xfff;
        int base = ra(rn);
        int addr = p ? (u ? base + off : base - off) : base;
        int wb = u ? base + off : base - off;
        if (l) {
            int val = b ? mem.read8(addr) : Integer.rotateRight(mem.read32(addr), (addr & 3) * 8);
            if (!p || w) r[rn] = wb;
            if (rd == 15) wrPcInterwork(val); else r[rd] = val;
        } else {
            int val = rd == 15 ? at + 12 : r[rd];
            if (b) mem.write8(addr, val); else mem.write32(addr, val);
            if (!p || w) r[rn] = wb;
        }
    }

    private void blockTransfer(int ins, int at) {
        boolean p = (ins & (1 << 24)) != 0, u = (ins & (1 << 23)) != 0, w = (ins & (1 << 21)) != 0, l = (ins & (1 << 20)) != 0;
        int rn = (ins >> 16) & 15;
        int list = ins & 0xffff;
        int count = Integer.bitCount(list);
        int base = r[rn];
        int start;
        if (u) start = p ? base + 4 : base;
        else start = p ? base - 4 * count : base - 4 * count + 4;
        int newBase = u ? base + 4 * count : base - 4 * count;
        int addr = start;
        if (l) {
            if (w) r[rn] = newBase;
            for (int i = 0; i < 16; i++) {
                if ((list & (1 << i)) == 0) continue;
                int val = mem.read32(addr); addr += 4;
                if (i == 15) wrPcInterwork(val); else r[i] = val;
            }
        } else {
            for (int i = 0; i < 16; i++) {      // a listed base register stores its original value
                if ((list & (1 << i)) == 0) continue;
                mem.write32(addr, i == 15 ? at + 12 : r[i]); addr += 4;
            }
            if (w) r[rn] = newBase;
        }
    }

    // ------------------------------------------------------------------ Thumb
    private void stepThumb() {
        int at = pc;
        int ins = mem.read16(at);
        pc = at + 2;
        int pcv = at + 4;   // value of PC as an operand
        switch (ins >>> 11) {
            case 0: case 1: case 2: {        // shift by immediate
                int rd = ins & 7, rs = (ins >> 3) & 7, amt = (ins >> 6) & 31;
                int res = shiftImm(r[rs], ins >>> 11, amt);
                r[rd] = res; setNZ(res); c = shC;
                return;
            }
            case 3: {                        // add/sub register or 3-bit immediate
                int rd = ins & 7, rs = (ins >> 3) & 7;
                int operand = (ins & (1 << 10)) != 0 ? (ins >> 6) & 7 : r[(ins >> 6) & 7];
                r[rd] = (ins & (1 << 9)) != 0 ? sub(r[rs], operand, 1, true) : add(r[rs], operand, 0, true);
                return;
            }
            case 4: { int rd = (ins >> 8) & 7, im = ins & 0xff; r[rd] = im; setNZ(im); return; }   // MOV
            case 5: { sub(r[(ins >> 8) & 7], ins & 0xff, 1, true); return; }                         // CMP
            case 6: { int rd = (ins >> 8) & 7; r[rd] = add(r[rd], ins & 0xff, 0, true); return; }   // ADD
            case 7: { int rd = (ins >> 8) & 7; r[rd] = sub(r[rd], ins & 0xff, 1, true); return; }   // SUB
            case 8:
                if ((ins & 0x400) == 0) thumbAlu(ins); else thumbHiReg(ins, at, pcv);
                return;
            case 9: { int rd = (ins >> 8) & 7; r[rd] = mem.read32((pcv & ~3) + (ins & 0xff) * 4); return; }   // LDR pc-rel
            case 10: case 11: {              // load/store register offset
                int rd = ins & 7, rb = (ins >> 3) & 7, ro = (ins >> 6) & 7;
                int addr = r[rb] + r[ro];
                switch ((ins >> 9) & 7) {
                    case 0: mem.write32(addr, r[rd]); break;
                    case 1: mem.write16(addr, r[rd]); break;
                    case 2: mem.write8(addr, r[rd]); break;
                    case 3: r[rd] = (byte) mem.read8(addr); break;
                    case 4: r[rd] = Integer.rotateRight(mem.read32(addr), (addr & 3) * 8); break;
                    case 5: r[rd] = mem.read16(addr); break;
                    case 6: r[rd] = mem.read8(addr); break;
                    default: r[rd] = (short) mem.read16(addr); break;
                }
                return;
            }
            case 12: { int rd = ins & 7, rb = (ins >> 3) & 7; mem.write32(r[rb] + ((ins >> 6) & 31) * 4, r[rd]); return; }
            case 13: { int rd = ins & 7, rb = (ins >> 3) & 7; int a = r[rb] + ((ins >> 6) & 31) * 4; r[rd] = Integer.rotateRight(mem.read32(a), (a & 3) * 8); return; }
            case 14: { int rd = ins & 7, rb = (ins >> 3) & 7; mem.write8(r[rb] + ((ins >> 6) & 31), r[rd]); return; }
            case 15: { int rd = ins & 7, rb = (ins >> 3) & 7; r[rd] = mem.read8(r[rb] + ((ins >> 6) & 31)); return; }
            case 16: { int rd = ins & 7, rb = (ins >> 3) & 7; mem.write16(r[rb] + ((ins >> 6) & 31) * 2, r[rd]); return; }
            case 17: { int rd = ins & 7, rb = (ins >> 3) & 7; r[rd] = mem.read16(r[rb] + ((ins >> 6) & 31) * 2); return; }
            case 18: { int rd = (ins >> 8) & 7; mem.write32(r[13] + (ins & 0xff) * 4, r[rd]); return; }
            case 19: { int rd = (ins >> 8) & 7; r[rd] = mem.read32(r[13] + (ins & 0xff) * 4); return; }
            case 20: { int rd = (ins >> 8) & 7; r[rd] = (pcv & ~3) + (ins & 0xff) * 4; return; }     // ADD rd, PC, #
            case 21: { int rd = (ins >> 8) & 7; r[rd] = r[13] + (ins & 0xff) * 4; return; }          // ADD rd, SP, #
            case 22: case 23: thumbMisc(ins, at); return;
            case 24: {                       // STMIA
                int rb = (ins >> 8) & 7, a = r[rb];
                for (int i = 0; i < 8; i++) if ((ins & (1 << i)) != 0) { mem.write32(a, r[i]); a += 4; }
                r[rb] = a;
                return;
            }
            case 25: {                       // LDMIA
                int rb = (ins >> 8) & 7, a = r[rb];
                for (int i = 0; i < 8; i++) if ((ins & (1 << i)) != 0) { r[i] = mem.read32(a); a += 4; }
                if ((ins & (1 << rb)) == 0) r[rb] = a;
                return;
            }
            case 26: case 27: {              // conditional branch / SWI
                int cc = (ins >> 8) & 15;
                if (cc == 15) throw new IllegalStateException(String.format("SWI at %08x", at));
                if (cc == 14) throw undefined(ins, at);
                if (cond(cc)) pc = pcv + (((byte) ins) << 1);
                return;
            }
            case 28: pc = pcv + ((ins << 21) >> 20); return;                         // B
            case 29: {                       // BLX suffix (to ARM)
                int target = (r[14] + ((ins & 0x7ff) << 1)) & ~3;
                r[14] = (at + 2) | 1;
                thumb = false; pc = target;
                return;
            }
            case 30: r[14] = pcv + ((ins << 21) >> 9); return;                        // BL prefix
            default: {                       // BL suffix
                int target = r[14] + ((ins & 0x7ff) << 1);
                r[14] = (at + 2) | 1;
                pc = target & ~1;
                return;
            }
        }
    }

    private void thumbAlu(int ins) {
        int rd = ins & 7, rs = (ins >> 3) & 7;
        int a = r[rd], b = r[rs], res;
        switch ((ins >> 6) & 15) {
            case 0: res = a & b; r[rd] = res; setNZ(res); return;
            case 1: res = a ^ b; r[rd] = res; setNZ(res); return;
            case 2: res = shiftReg(a, 0, b); r[rd] = res; setNZ(res); c = shC; return;
            case 3: res = shiftReg(a, 1, b); r[rd] = res; setNZ(res); c = shC; return;
            case 4: res = shiftReg(a, 2, b); r[rd] = res; setNZ(res); c = shC; return;
            case 5: r[rd] = add(a, b, c ? 1 : 0, true); return;
            case 6: r[rd] = sub(a, b, c ? 1 : 0, true); return;
            case 7: res = shiftReg(a, 3, b); r[rd] = res; setNZ(res); c = shC; return;
            case 8: setNZ(a & b); return;
            case 9: r[rd] = sub(0, b, 1, true); return;
            case 10: sub(a, b, 1, true); return;
            case 11: add(a, b, 0, true); return;
            case 12: res = a | b; r[rd] = res; setNZ(res); return;
            case 13: res = a * b; r[rd] = res; setNZ(res); return;
            case 14: res = a & ~b; r[rd] = res; setNZ(res); return;
            default: res = ~b; r[rd] = res; setNZ(res); return;
        }
    }

    private void thumbHiReg(int ins, int at, int pcv) {
        int op = (ins >> 8) & 3;
        int rd = (ins & 7) | ((ins >> 4) & 8), rs = (ins >> 3) & 15;
        int sv = rs == 15 ? pcv : r[rs];
        switch (op) {
            case 0: {
                int dv = rd == 15 ? pcv : r[rd];
                int res = dv + sv;
                if (rd == 15) pc = res & ~1; else r[rd] = res;
                return;
            }
            case 1: sub(rd == 15 ? pcv : r[rd], sv, 1, true); return;
            case 2:
                if (rd == 15) pc = sv & ~1; else r[rd] = sv;
                return;
            default:
                if ((ins & 0x80) != 0) r[14] = (at + 2) | 1;   // BLX
                branchTo(sv);
                return;
        }
    }

    private void thumbMisc(int ins, int at) {
        if ((ins & 0xff00) == 0xb000) {           // ADD/SUB SP, #imm
            int im = (ins & 0x7f) * 4;
            r[13] += (ins & 0x80) != 0 ? -im : im;
            return;
        }
        if ((ins & 0xf600) == 0xb400) {           // PUSH / POP
            boolean pop = (ins & 0x800) != 0, extra = (ins & 0x100) != 0;
            int list = ins & 0xff;
            if (!pop) {
                int cnt = Integer.bitCount(list) + (extra ? 1 : 0);
                int a = r[13] - 4 * cnt;
                r[13] = a;
                for (int i = 0; i < 8; i++) if ((list & (1 << i)) != 0) { mem.write32(a, r[i]); a += 4; }
                if (extra) mem.write32(a, r[14]);
            } else {
                int a = r[13];
                for (int i = 0; i < 8; i++) if ((list & (1 << i)) != 0) { r[i] = mem.read32(a); a += 4; }
                if (extra) { int val = mem.read32(a); a += 4; r[13] = a; branchTo(val); return; }
                r[13] = a;
            }
            return;
        }
        if ((ins & 0xff00) == 0xbe00) throw new IllegalStateException(String.format("BKPT at %08x", at));
        throw undefined(ins, at);
    }
}
