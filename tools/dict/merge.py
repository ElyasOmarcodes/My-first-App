"""Merge the bundled dictionary with a Wikipedia frequency list.
Both are turned into per-million frequencies and averaged, then scaled back
to the bundled list's own scale so learned-word weights keep their balance."""
import lzma, re, sys, json
old_xz, wiki, fmt, lang, charsets, cap, out = sys.argv[1:8]
cap = int(cap)
letters = set(json.load(open(charsets, encoding='utf-8'))[lang]) | {'‌'}

old = {}
for line in lzma.open(old_xz, 'rt', encoding='utf-8'):
    w, _, c = line.rstrip('\n').partition('\t')
    if w: old[w] = old.get(w, 0) + int(c or 1)
old_total = sum(old.values())

wk = {}
for line in open(wiki, encoding='utf-8'):
    parts = line.rstrip('\n').split('\t' if fmt == 'tsv' else ' ')
    if len(parts) < 2: continue
    w, c = parts[0], int(parts[1])
    if fmt == 'tsv' and len(parts) > 2 and int(parts[2]) < (2 if lang == 'ps' else 3): continue
    if lang == 'fa': w = w.replace('ك', 'ک').replace('ي', 'ی')
    w = w.strip('‌')
    if not w or any(ch not in letters for ch in w): continue
    wk[w] = wk.get(w, 0) + c
wk_total = sum(wk.values())

# Pashto writers often glue the preposition د to the next word; drop those
# when the rest is itself a word (د + کتاب -> دکتاب).
if lang == 'ps':
    for w in [w for w in wk if w.startswith('د') and len(w) > 3 and w not in old]:
        if w[1:] in wk or w[1:] in old: del wk[w]
# single letters only when the old list already had them
for w in [w for w in wk if len(w) == 1 and w not in old]: del wk[w]

lower_old = {w.lower(): w for w in old} if lang == 'en' else {}
pm = {}
for w, c in old.items(): pm[w] = c / old_total * 1e6 / 2
for w, c in wk.items():
    key = lower_old.get(w, w) if lang == 'en' else w
    pm[key] = pm.get(key, 0) + c / wk_total * 1e6 / 2

ranked = sorted(pm.items(), key=lambda kv: -kv[1])
keep = [kv for kv in ranked if kv[0] in old]
extra = [kv for kv in ranked if kv[0] not in old][:max(0, cap - len(keep))]
final = sorted(keep + extra, key=lambda kv: -kv[1])
data = ''.join('%s\t%d\n' % (w, max(1, round(v * old_total / 1e6))) for w, v in final).encode()
# the dictionary window only needs to cover the file: the decoder on the
# phone allocates the whole window, and preset 9's is 64 MiB
dict_size = 1 << max(20, (len(data) - 1).bit_length())
filters = [{'id': lzma.FILTER_LZMA2, 'preset': 9 | lzma.PRESET_EXTREME, 'dict_size': dict_size}]
open(out, 'wb').write(lzma.compress(data, format=lzma.FORMAT_XZ, check=lzma.CHECK_CRC64, filters=filters))
print(lang, 'bytes', len(data), 'dict', dict_size >> 20, 'MiB')
print(lang, 'old', len(old), 'wiki', len(wk), 'final', len(final), 'new', len(extra))
