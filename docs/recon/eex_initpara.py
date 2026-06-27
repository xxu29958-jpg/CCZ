#!/usr/bin/env python3
"""Extract each Script op's initPara payload-advance from libMyGame.so (ELF32 ARM/Thumb).

Each EEX command class has `<class>::initPara(char* payload, int)` that reads the record's
fields and RETURNS the advanced payload pointer (payload + record_length). For fixed-length
ops that return is `payload + const`; for string ops it scans a NUL-terminated string.
We disassemble each initPara (Thumb) and recover the constant advance where it is fixed.
"""
import struct
import sys
import re
from capstone import Cs, CS_ARCH_ARM, CS_MODE_THUMB, CS_MODE_LITTLE_ENDIAN

SO = r"C:\Users\Xy172\AppData\Local\Temp\ccz_apk_probe\libMyGame.so"

def read_elf(path):
    d = open(path, "rb").read()
    assert d[:4] == b"\x7fELF", "not ELF"
    assert d[4] == 1, "not ELF32"
    e_shoff = struct.unpack_from("<I", d, 0x20)[0]
    e_shentsize = struct.unpack_from("<H", d, 0x2e)[0]
    e_shnum = struct.unpack_from("<H", d, 0x30)[0]
    e_phoff = struct.unpack_from("<I", d, 0x1c)[0]
    e_phentsize = struct.unpack_from("<H", d, 0x2a)[0]
    e_phnum = struct.unpack_from("<H", d, 0x2c)[0]
    secs = []
    for i in range(e_shnum):
        off = e_shoff + i * e_shentsize
        name, typ, flags, addr, offset, size, link, info, align, entsize = struct.unpack_from("<10I", d, off)
        secs.append(dict(name=name, type=typ, addr=addr, offset=offset, size=size, link=link, entsize=entsize))
    phs = []
    for i in range(e_phnum):
        off = e_phoff + i * e_phentsize
        p_type, p_offset, p_vaddr, p_paddr, p_filesz, p_memsz, p_flags, p_align = struct.unpack_from("<8I", d, off)
        phs.append(dict(type=p_type, offset=p_offset, vaddr=p_vaddr, filesz=p_filesz))
    return d, secs, phs

def symbols(d, secs):
    out = {}
    for s in secs:
        if s["type"] in (2, 11) and s["entsize"]:  # SYMTAB / DYNSYM
            strtab = secs[s["link"]]
            stroff = strtab["offset"]
            n = s["size"] // s["entsize"]
            for i in range(n):
                base = s["offset"] + i * s["entsize"]
                st_name, st_value, st_size, st_info, st_other, st_shndx = struct.unpack_from("<IIIBBH", d, base)
                if st_value == 0:
                    continue
                end = d.find(b"\x00", stroff + st_name)
                nm = d[stroff + st_name:end].decode("latin1")
                if nm:
                    out.setdefault(nm, (st_value, st_size))
    return out

def vaddr_to_off(phs, vaddr):
    for p in phs:
        if p["type"] == 1 and p["vaddr"] <= vaddr < p["vaddr"] + p["filesz"]:
            return p["offset"] + (vaddr - p["vaddr"])
    return None

# demangle just the class name from _ZN<len><name>8initParaEPci
def class_of(sym):
    m = re.match(r"_ZN(\d+)(Script[A-Za-z0-9]+)8initParaEPci", sym)
    return m.group(2) if m else None

def analyze_initpara(d, phs, vaddr, size, md):
    """Forward symbolic track of r0..r7 as ('base',k)=r2+k / ('pay',k)=r1+k / ('imm',v) / ('unk',).
    initPara returns r0 = offset_base(r2) + record_length, so a return with r0=('base',k) means a
    fixed-length record of k bytes; a return with r0=('unk',) (r0 derived from a scanned string) is
    a variable-length (string-bearing) record the walker must read field-by-field."""
    addr = vaddr & ~1
    off = vaddr_to_off(phs, addr)
    if off is None:
        return ("?", "no-offset", [])
    insns = list(md.disasm(d[off:off + (size if size else 400)], addr))
    reg = {"r%d" % i: ("unk",) for i in range(13)}
    reg["r1"] = ("pay", 0)
    reg["r2"] = ("base", 0)
    rets = []

    def val(tok):
        return reg.get(tok, ("unk",))

    for ins in insns:
        m, ops = ins.mnemonic, ins.op_str
        if (m == "pop" and "pc" in ops) or (m == "bx" and ops.strip() == "lr") or m == "bxns":
            rets.append(val("r0"))
            # keep scanning: there can be multiple return paths
            continue
        a = re.match(r"(r\d+), (r\d+), #(0x[0-9a-f]+|\d+)$", ops)   # add(s) rD, rN, #imm
        b = re.match(r"(r\d+), #(0x[0-9a-f]+|\d+)$", ops)           # add(s)/movs rD, #imm  OR subs
        c = re.match(r"(r\d+), (r\d+)$", ops)                       # mov(s)/adds rD, rN
        if m in ("add", "adds") and a:
            base_v = val(a.group(2)); k = int(a.group(3), 0)
            reg[a.group(1)] = (base_v[0], base_v[1] + k) if base_v[0] in ("base", "pay", "imm") else ("unk",)
        elif m in ("add", "adds") and b:
            v = val(b.group(1)); k = int(b.group(2), 0)
            reg[b.group(1)] = (v[0], v[1] + k) if v[0] in ("base", "pay", "imm") else ("unk",)
        elif m in ("mov", "movs") and b:
            reg[b.group(1)] = ("imm", int(b.group(2), 0))
        elif m in ("mov", "movs") and c:
            reg[c.group(1)] = val(c.group(2))
        elif m == "adds" and c:  # adds rD, rN  (== rD = rD + rN) -> data unless one is imm
            reg[c.group(1)] = ("unk",)
        elif m in ("ldr", "ldrh", "ldrb", "ldrsh", "ldrsb"):
            dr = ops.split(",")[0].strip()
            reg[dr] = ("unk",)
        elif m in ("bl", "blx"):
            for r in ("r0", "r1", "r2", "r3"):
                reg[r] = ("unk",)   # AAPCS: calls clobber r0-r3; return (read short) in r0 = data
        else:
            dr = ops.split(",")[0].strip()
            if re.match(r"r\d+$", dr):
                reg[dr] = ("unk",)
    base_rets = [v[1] for v in rets if v[0] == "base"]
    if base_rets:
        return ("fixed", max(base_rets), rets)
    if any(v[0] == "unk" for v in rets):
        return ("var", "string/data-dependent", rets)
    if rets:
        return ("?", "ret=%s" % rets[0][0], rets)
    return ("?", "no-return", rets)

def main():
    d, secs, phs = read_elf(SO)
    syms = symbols(d, secs)
    md = Cs(CS_ARCH_ARM, CS_MODE_THUMB | CS_MODE_LITTLE_ENDIAN)
    md.detail = False
    ips = {class_of(k): (v[0], v[1]) for k, v in syms.items() if class_of(k)}
    print("initPara symbols found:", len(ips))
    if len(sys.argv) > 1:
        # validate one class
        want = sys.argv[1]
        for cls, (va, sz) in ips.items():
            if want in cls:
                kind, val, _ = analyze_initpara(d, phs, va, sz, md)
                print("%s @0x%x size=%d -> %s %s" % (cls, va & ~1, sz, kind, val))
        return
    # batch: print all
    results = {}
    for cls, (va, sz) in sorted(ips.items()):
        kind, val, _ = analyze_initpara(d, phs, va, sz, md)
        results[cls] = (kind, val)
        v = ("0x%x" % val) if kind == "fixed" else str(val)
        print("%-30s @0x%08x size=%-4d %-6s %s" % (cls, va & ~1, sz, kind, v))
    fixed = sum(1 for k, v in results.values() if k == "fixed")
    var = sum(1 for k, v in results.values() if k == "var")
    print("\nfixed-length: %d  variable(string): %d  unresolved: %d" %
          (fixed, var, len(results) - fixed - var))

if __name__ == "__main__":
    main()
