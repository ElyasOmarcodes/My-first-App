"""Drop likely misspellings from a bundled dictionary (word<TAB>count, xz).

Wikipedia counts carry common typos ("teh", "goverment", "کتبا") often
enough to pass the "seen in N articles" bar, and a typo in the dictionary
is a word the keyboard will never correct. A word is dropped when one slip
turns it into a word at least RATIO times more frequent. Only the slips that
are reliably typos count — two letters swapped ("teh", "کتبا") or a letter
missed inside the word ("goverment", "حکوت"). A different letter is too
often another real word ("dull" / "full"), and the end of the word is left
alone: in Pashto, Dari and Urdu that is where inflection lives.
usage: typo_filter.py in.txt.xz out.txt.xz [ratio]"""
import lzma, sys
from collections import defaultdict

src, out = sys.argv[1], sys.argv[2]
ratio = float(sys.argv[3]) if len(sys.argv) > 3 else 100.0
rows = []
for line in lzma.open(src, 'rt', encoding='utf-8'):
    w, _, c = line.rstrip('\n').partition('\t')
    if w: rows.append((w, int(c or 1)))
freq = {}
for w, c in rows: freq[w] = max(freq.get(w, 0), c)

subs = defaultdict(list)    # (pos, word minus pos) -> words, internal positions
for w in freq:
    for p in range(len(w) - 1):
        subs[(p, w[:p] + w[p + 1:])].append(w)

def neighbours(w):
    n = len(w)
    # one letter missed inside the word (not the first: prefixes such as
    # و / ال / را are words of their own there)
    for p in range(1, n):
        for v in subs.get((p, w), ()):
            if len(v) == n + 1: yield v
    for p in range(n - 1):                     # two letters swapped
        if w[p] != w[p + 1]:
            v = w[:p] + w[p + 1] + w[p] + w[p + 2:]
            if v in freq: yield v

drop = {}
for w, c in freq.items():
    # a typo is rare in itself: a word used more than a few times on the
    # dictionary's scale is a word ("alter" next to "later")
    if len(w) < 3 or c > 3 or not any(ch.isalpha() for ch in w): continue
    for v in neighbours(w):
        if freq[v] >= ratio * c and len(v) >= 3:
            drop[w] = v; break

kept = [(w, c) for w, c in rows if w not in drop]
data = ''.join('%s\t%d\n' % r for r in kept).encode()
dict_size = 1 << max(20, (len(data) - 1).bit_length())
lzma_filters = [{'id': lzma.FILTER_LZMA2, 'preset': 9 | lzma.PRESET_EXTREME, 'dict_size': dict_size}]
open(out, 'wb').write(lzma.compress(data, format=lzma.FORMAT_XZ, check=lzma.CHECK_CRC64, filters=lzma_filters))
print(src.rsplit('/', 1)[-1], 'words', len(freq), 'dropped', len(drop), 'kept', len(kept), file=sys.stderr)
for w in list(drop)[:0]: pass
if len(sys.argv) > 4:
    import random; random.seed(1)
    for w in random.sample(sorted(drop), min(int(sys.argv[4]), len(drop))):
        print('  %s (%d) -> %s (%d)' % (w, freq[w], drop[w], freq[drop[w]]), file=sys.stderr)
