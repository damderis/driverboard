// Inspect actual GSUB substitutions; no external font dependencies required.
const fs = require('fs');
const b = fs.readFileSync('app/src/main/res/font/atkinson_next.ttf');
const u16 = o => b.readUInt16BE(o);
const u32 = o => b.readUInt32BE(o);
const tag = o => b.toString('ascii', o, o + 4);
const tables = {};
for (let i = 0; i < u16(4); i++) {
    const o = 12 + i * 16;
    tables[tag(o)] = u32(o + 8);
}
const cmap = tables.cmap;
let zero;
for (let i = 0; i < u16(cmap + 2); i++) {
    const s = cmap + u32(cmap + 4 + i * 8 + 4);
    if (u16(s) !== 4) continue;
    const n = u16(s + 6) / 2;
    const ends = s + 14;
    const starts = ends + n * 2 + 2;
    const deltas = starts + n * 2;
    const ranges = deltas + n * 2;
    for (let j = 0; j < n; j++) {
        if (u16(starts + j * 2) <= 48 && u16(ends + j * 2) >= 48) {
            const r = ranges + j * 2;
            const g = u16(r) ? u16(r + u16(r) + 2 * (48 - u16(starts + j * 2))) : 48;
            zero = (g + u16(deltas + j * 2)) & 65535;
        }
    }
}
function coverage(o) {
    if (u16(o) === 1) return Array.from({length: u16(o + 2)}, (_, i) => u16(o + 4 + i * 2));
    const result = [];
    for (let i = 0; i < u16(o + 2); i++) {
        const r = o + 4 + i * 6;
        for (let g = u16(r); g <= u16(r + 2); g++) result[u16(r + 4) + g - u16(r)] = g;
    }
    return result;
}
const gsub = tables.GSUB;
const features = gsub + u16(gsub + 6);
const lookups = gsub + u16(gsub + 8);
const results = [];
const available = [];
for (let i = 0; i < u16(features); i++) {
    const r = features + 2 + i * 6;
    const name = tag(r);
    available.push(name);
    if (!/^(aalt|zero|ss\d\d|cv\d\d)$/.test(name)) continue;
    const f = features + u16(r + 4);
    for (let j = 0; j < u16(f + 2); j++) {
        const l = lookups + u16(lookups + 2 + 2 * u16(f + 4 + j * 2));
        for (let k = 0; k < u16(l + 4); k++) {
            let type = u16(l);
            let s = l + u16(l + 6 + k * 2);
            if (type === 7) { type = u16(s + 2); s += u32(s + 4); }
            const glyphs = coverage(s + u16(s + 2));
            const index = glyphs.indexOf(zero);
            if (index < 0) continue;
            let targets = [];
            if (type === 1) targets = [u16(s) === 1 ? (zero + u16(s + 4)) & 65535 : u16(s + 6 + index * 2)];
            if (type === 3) {
                const a = s + u16(s + 6 + index * 2);
                targets = Array.from({length: u16(a)}, (_, n) => u16(a + 2 + n * 2));
            }
            results.push({feature: name, lookupType: type, targets});
        }
    }
}
function outline(glyph) {
    const longOffsets = b.readInt16BE(tables.head + 50) === 1;
    const offset = g => longOffsets ? u32(tables.loca + g * 4) : u16(tables.loca + g * 2) * 2;
    const start = tables.glyf + offset(glyph);
    const contours = b.readInt16BE(start);
    if (contours < 0) {
        const flags = u16(start + 10);
        // A single translated component preserves its source outline.
        if (!(flags & (32 | 8 | 64 | 128))) return outline(u16(start + 12));
        return {composite: true};
    }
    const count = u16(start + 10 + (contours - 1) * 2) + 1;
    let o = start + 10 + contours * 2;
    o += 2 + u16(o);
    const flags = [];
    while (flags.length < count) {
        const flag = b[o++];
        flags.push(flag);
        if (flag & 8) {
            const repeat = b[o++];
            for (let i = 0; i < repeat; i++) flags.push(flag);
        }
    }
    const axes = [];
    for (const [short, same] of [[2, 16], [4, 32]]) {
        let value = 0;
        const axis = [];
        for (const flag of flags) {
            if (flag & short) value += b[o++] * (flag & same ? 1 : -1);
            else if (!(flag & same)) { value += b.readInt16BE(o); o += 2; }
            axis.push(value);
        }
        axes.push(axis.map(v => v - axis[0]));
    }
    return {contours, axes};
}
const comparisons = results.flatMap(r => r.targets.map(glyph => ({
    glyph,
    sameOutlineAsDefault: JSON.stringify(outline(zero)) === JSON.stringify(outline(glyph))
})));
console.log(JSON.stringify({zeroGlyph: zero, features: [...new Set(available)],
    zeroAlternates: results, comparisons}, null, 2));
