import re, sys, json
src = open(sys.argv[1], encoding='utf-8').read()
out = {}
for lang in ['ps','fa','ar','ur','en']:
    m = re.search(r'private val %sRows = listOf\((.*?)\n    \)\n' % lang, src, re.S)
    chars = set()
    for args in re.findall(r'\bk\(([^)]*)\)', m.group(1)):
        for s in re.findall(r'"([^"]*)"', args):
            for ch in s:
                if ch.isalpha(): chars.add(ch)
    out[lang] = ''.join(sorted(chars))
json.dump(out, open(sys.argv[2],'w'), ensure_ascii=False)
for k,v in out.items(): print(k, v)
