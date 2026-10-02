"""Generates single-instruction CPU test cases by running them on unicorn (ARM926EJ-S, ARMv5TE).
Output: one line per case:  mode ins r0..r15 cpsr | r0..r15 cpsr | memwrites(addr:val,...)"""
import random, struct, sys
from unicorn import *
from unicorn.arm_const import *
from capstone import *

random.seed(int(sys.argv[2]) if len(sys.argv) > 2 else 1)
N = int(sys.argv[1]) if len(sys.argv) > 1 else 20000
CODE = 0x10000
DATA, DSIZE = 0x40000, 0x10000
REGS = [UC_ARM_REG_R0 + i for i in range(13)] + [UC_ARM_REG_SP, UC_ARM_REG_LR, UC_ARM_REG_PC]

cs_arm = Cs(CS_ARCH_ARM, CS_MODE_ARM)
cs_thumb = Cs(CS_ARCH_ARM, CS_MODE_THUMB)
cs_arm.detail = True; cs_thumb.detail = True

BAD = ('cdp', 'mcr', 'mrc', 'ldc', 'stc', 'svc', 'swi', 'bkpt', 'udf', 'ldrex', 'strex', 'msr', 'mrs', 'swp', 'swpb',
       'mcrr', 'mrrc', 'pld', 'blx', 'bxj', 'srs', 'rfe', 'cps', 'setend', 'ldrt', 'strt', 'ldrbt', 'strbt',
       'smc', 'clrex', 'dmb', 'dsb', 'isb', 'nop', 'yield', 'wfe', 'wfi', 'sev', 'hint', 'dbg', 'it', 'cbz', 'cbnz')

def ok_arm(w):
    l = list(cs_arm.disasm(struct.pack('<I', w), CODE))
    if not l: return False
    i = l[0]
    m = i.mnemonic.split('.')[0]
    base = m.rstrip('s') if m not in ('mrs', 'bls', 'bhs', 'blx') else m
    for b in BAD:
        if m.startswith(b): return False
    # v6+ media instructions not on ARMv5
    for v6 in ('sxt', 'uxt', 'rev', 'sel', 'pkh', 'ssat', 'usat', 'sad', 'usad', 'smuad', 'smusd', 'smlad', 'smlsd',
               'smmul', 'smmla', 'smmls', 'umaal', 'qadd8', 'qadd16', 'qsub8', 'qsub16', 'uadd', 'usub', 'sadd', 'ssub',
               'shadd', 'shsub', 'uhadd', 'uhsub', 'uqadd', 'uqsub', 'qasx', 'qsax', 'sasx', 'ssax', 'uasx', 'usax',
               'shasx', 'shsax', 'uhasx', 'uhsax', 'uqasx', 'uqsax', 'ldrh', 'strh', 'movw', 'movt', 'bfc', 'bfi',
               'ubfx', 'sbfx', 'rbit', 'mls', 'ldrd', 'strd', 'smlald', 'smlsld', 'smuadx'):
        if m.startswith(v6) and v6 not in ('ldrh', 'strh', 'ldrd', 'strd'): return False
    s = i.op_str
    if (w >> 28) == 0xF: return False
    # pc only in plain branches and pc-relative loads
    if 'pc' in s:
        if m in ('b', 'bl', 'bx') or (m.startswith('b') and len(m) <= 4 and m not in ('bic', 'bics')):
            pass
        elif m.startswith('ldr') and '[pc, #' in s and not s.startswith('pc'):
            pass
        else:
            return False
    if '!' in s and m.startswith(('ldm', 'stm', 'pop', 'push')):
        rn = s.split('!')[0].split(',')[0].strip()
        if rn in s.split('{')[1]: return False
    if m.startswith(('ldm', 'stm', 'pop', 'push')) and '^' in s: return False
    if m.startswith(('ldr', 'str')) and ('!' in s or '], ' in s):
        rt = s.split(',')[0].strip(); rn = s.split('[')[1].split(',')[0].split(']')[0].strip()
        if rt == rn: return False
    if m.startswith(('ldrd', 'strd')):
        rt = int(s.split(',')[0].strip()[1:]) if s.split(',')[0].strip()[1:].isdigit() else 99
        if rt % 2 or rt >= 14: return False
    if m.startswith(('mul', 'mla', 'umull', 'umlal', 'smull', 'smlal')):
        regs = [x.strip() for x in s.split(',')]
        if m.startswith(('umull', 'umlal', 'smull', 'smlal')) and regs[0] == regs[1]: return False
    if m.startswith(('ldrh', 'ldrsh', 'ldrsb', 'strh', 'ldrd', 'strd')) and (w & 0x0e000090) == 0x00000090 and (w & (1 << 22)) == 0 and (w & 15) == 15:
        return False
    return True

def ok_thumb(h):
    l = list(cs_thumb.disasm(struct.pack('<H', h), CODE))
    if not l or l[0].size != 2: return False
    m = l[0].mnemonic.split('.')[0]
    for b in BAD + ('bl', 'blx'):
        if m == b or m.startswith(('svc', 'bkpt', 'udf', 'cps', 'setend', 'cbz', 'cbnz', 'it', 'rev', 'sxt', 'uxt', 'nop', 'yield', 'wfe', 'wfi', 'sev')):
            return False
    if (h >> 11) in (29, 30, 31): return False
    if (h & 0xff00) == 0xdf00 or (h & 0xff00) == 0xde00: return False
    s = l[0].op_str
    # hi-register forms with pc as destination / mov pc
    if (h >> 10) == 0x11 and ((h & 0x87) == 0x87 or ((h >> 3) & 15) == 15) and ((h >> 8) & 3) != 3: return False
    if (h >> 10) == 0x11 and ((h >> 8) & 3) == 3: return False     # bx/blx (covered by branch tests)
    if (h >> 12) == 0xb and (h & 0x0600) == 0x0400 and (h & 0x800) and (h & 0x100): return False   # pop {pc}
    if (h >> 11) == 25:   # ldmia with rb in list: fine; stmia with rb in list not lowest: unpredictable
        pass
    if (h >> 11) == 24:
        rb = (h >> 8) & 7
        if (h & (1 << rb)) and (h & ((1 << rb) - 1)): return False
    if (h >> 12) == 0xb and ((h >> 8) & 0xf) not in (0x0, 0x4, 0x5, 0xc, 0xd): return False
    return True

def setup_regs(mem_like):
    regs = []
    for i in range(15):
        if mem_like and random.random() < 0.85:
            regs.append(DATA + 0x4000 + random.randrange(0, 0x400) * 4)
        else:
            regs.append(random.choice([0, 1, 2, 31, 32, 33, -1 & 0xffffffff, 0x80000000, 0x7fffffff, random.getrandbits(32), random.getrandbits(8), random.getrandbits(16)]))
    regs[13] = DATA + 0x8000 + random.randrange(0, 0x100) * 4
    return regs

PATTERN = bytes(((i * 37 + 11) ^ (i >> 8)) & 255 for i in range(DSIZE))   # same formula in the Java test
out = open(sys.argv[3] if len(sys.argv) > 3 else 'cpu_tests.txt', 'w')
made = 0
attempts = 0
while made < N and attempts < N * 400:
    attempts += 1
    thumb = random.random() < 0.4
    if thumb:
        h = random.getrandbits(16)
        if not ok_thumb(h): continue
        code = struct.pack('<H', h) + b'\x00\xbf'; ins = h
    else:
        w = (random.choice([0xE, 0xE, 0xE, random.randrange(0, 15)]) << 28) | random.getrandbits(28)
        if not ok_arm(w): continue
        code = struct.pack('<I', w); ins = w
    mu = Uc(UC_ARCH_ARM, UC_MODE_THUMB if thumb else UC_MODE_ARM)
    try: mu.ctl_set_cpu_model(UC_CPU_ARM_926)
    except Exception: pass
    mu.mem_map(0, 0x100000)
    mu.mem_write(CODE, code)
    pattern = PATTERN
    mu.mem_write(DATA, pattern)
    dis = list((cs_thumb if thumb else cs_arm).disasm(code[:2] if thumb else code, CODE))[0]
    mn = dis.mnemonic
    is_mem = mn.startswith(('ldr', 'str', 'ldm', 'stm', 'push', 'pop'))
    if is_mem:
        size = 1 if ('b' in mn[3:5] and not mn.startswith(('ldm', 'stm'))) else (2 if 'h' in mn[3:6] else 4)
        disp = 0
        for op in dis.operands:
            if op.type == 3: disp = op.mem.disp   # ARM_OP_MEM
        if 'pc' in dis.op_str: continue
        if disp % size: continue
        if '], #' in dis.op_str:
            imm = int(dis.op_str.split('#')[-1].replace('!', ''), 0)
            if imm % size: continue
    regs = setup_regs(True)
    if is_mem:
        regs = [DATA + 0x4000 + random.randrange(0, 0x400) * 4 for _ in range(15)]
        regs[13] = DATA + 0x8000 + random.randrange(0, 0x100) * 4
    flags = random.getrandbits(4) << 28
    cpsr = flags | 0x10 | (0x20 if thumb else 0)
    mu.reg_write(UC_ARM_REG_CPSR, cpsr)          # switch to user mode first (banks SP/LR)
    for rid, val in zip(REGS[:15], regs): mu.reg_write(rid, val)
    try:
        mu.emu_start(CODE | (1 if thumb else 0), 0xffffff, count=1)
    except UcError:
        continue
    after = [mu.reg_read(r) for r in REGS]
    acpsr = mu.reg_read(UC_ARM_REG_CPSR)
    newmem = bytes(mu.mem_read(DATA, DSIZE))
    writes = []
    for a in range(0, DSIZE, 4):
        if newmem[a:a + 4] != pattern[a:a + 4]:
            writes.append(f"{DATA + a:x}:{struct.unpack_from('<I', newmem, a)[0]:x}")
    if len(writes) > 40: continue
    pc_after = after[15]
    out.write(f"{'T' if thumb else 'A'} {ins:x} {' '.join('%x' % (v & 0xffffffff) for v in regs)} {cpsr:x} | "
              f"{' '.join('%x' % v for v in after[:15])} {pc_after:x} {acpsr & 0xf8000020:x} | {','.join(writes)}\n")
    made += 1
    if made % 2000 == 0: print(made, 'cases', file=sys.stderr)
print('cases', made, 'attempts', attempts, file=sys.stderr)
