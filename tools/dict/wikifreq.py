"""Word frequencies from a MediaWiki pages-articles .xml.bz2 dump.
Only main-namespace, non-redirect pages; markup stripped roughly.
Output: word<TAB>count<TAB>docs, for words seen in >= 3 articles."""
import bz2, re, sys, json
from collections import Counter
from xml.etree.ElementTree import iterparse

dump, lang, charsets, out = sys.argv[1:5]
letters = json.load(open(charsets, encoding='utf-8'))[lang]
ZWNJ = '‌'
allowed = set(letters) | {ZWNJ}
TASHKEEL = re.compile('[ً-ْٰـ]')
word_re = re.compile('[%s]+(?:%s[%s]+)*' % (re.escape(letters), ZWNJ, re.escape(letters)))

def strip(t):
    t = re.sub(r'<!--.*?-->', ' ', t, flags=re.S)
    t = re.sub(r'<ref[^>]*/>', ' ', t)
    t = re.sub(r'<ref.*?</ref>', ' ', t, flags=re.S)
    # templates / tables (nested): repeat innermost removal
    for _ in range(6):
        n = re.sub(r'\{\{[^{}]*\}\}', ' ', t)
        n = re.sub(r'\{\|[^{}]*?\|\}', ' ', n, flags=re.S)
        if n == t: break
        t = n
    t = re.sub(r'\[\[(?:[^\]|]*:)[^\]]*\]\]', ' ', t)          # files, categories
    t = re.sub(r'\[\[[^\]|]*\|([^\]]*)\]\]', r'\1', t)
    t = re.sub(r'\[\[([^\]]*)\]\]', r'\1', t)
    t = re.sub(r'\[https?://\S+ ?([^\]]*)\]', r'\1', t)
    t = re.sub(r'<[^>]+>', ' ', t)
    return t

uses, docs = Counter(), Counter()
ns_tag = None
pages = 0
title = ns = None; redirect = False
for ev, el in iterparse(bz2.open(dump), events=('end',)):
    tag = el.tag.rsplit('}', 1)[-1]
    if tag == 'ns': ns = el.text
    elif tag == 'redirect': redirect = True
    elif tag == 'text':
        if ns == '0' and not redirect and el.text and not el.text.lstrip().lower().startswith('#'):
            t = TASHKEEL.sub('', strip(el.text))
            if lang == 'fa': t = t.replace('ك', 'ک').replace('ي', 'ی')
            ws = word_re.findall(t)
            c = Counter(w.strip(ZWNJ) for w in ws)
            uses.update(c); docs.update(c.keys())
            pages += 1
            if pages % 20000 == 0: print(lang, pages, len(uses), file=sys.stderr, flush=True)
    elif tag == 'page':
        ns = None; redirect = False
        el.clear()
with open(out, 'w', encoding='utf-8') as f:
    for w, n in uses.most_common():
        if w and docs[w] >= 2 and len(w) <= 30:
            f.write('%s\t%d\t%d\n' % (w, n, docs[w]))
print(lang, 'pages', pages, 'unique', len(uses), file=sys.stderr)
