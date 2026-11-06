# Dictionary build

The bundled lists in `app/src/main/assets/dict/` combine the earlier
Leipzig-based lists with Wikipedia word counts.

1. `charsets.py Layouts.kt charsets.json` – the letters on each layout. A word
   is kept only if it can be typed.
2. Pashto and Urdu: `wikifreq.py <xx>wiki-latest-pages-articles.xml.bz2 <xx> charsets.json <xx>.wiki.tsv`
   (dumps from dumps.wikimedia.org). Persian, Arabic and English use the
   pre-counted lists from github.com/IlyaSemenov/wikipedia-word-frequency.
3. `merge.py old.txt.xz wiki.txt tsv|sp <xx> charsets.json <cap> out.txt.xz`
   averages both per million, keeps every old word, adds Wikipedia words up
   to `<cap>`, and writes XZ with a window sized to the file.
