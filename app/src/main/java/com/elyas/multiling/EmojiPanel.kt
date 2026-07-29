package com.elyas.multiling

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.preference.PreferenceManager
import org.tukaani.xz.XZInputStream

/**
 * The complete emoji catalogue (every emoji in the Unicode standard, ~3500
 * counting skin tones) lives in `assets/emoji.txt.xz` — LZMA2-compressed so
 * it costs the APK ~43 KB instead of ~190 KB. It is decoded once, lazily, the
 * first time the emoji panel or emoji search is opened.
 *
 * Line format inside the archive:
 *     `<0x01><tabEmoji>`              starts a category
 *     `<emoji>\t<tags>`                an entry
 *     `<emoji>\t<tags>\t<v1 v2 …>`     an entry whose long-press offers the
 *                                      five skin-tone variants
 * Tags mix English, Farsi and Arabic words so search works in any of the
 * languages our users type in.
 *
 * The category marker has to be a control character: the keycap emoji `#️⃣`
 * starts with a literal '#', so a '#' marker ate it as a header.
 */
object EmojiData {

    /** starts a category line — see the note on '#' above */
    private const val CAT_MARK = '\u0001'

    /** tab emoji -> the emoji shown in that tab, in official Unicode order */
    private var cats: List<Pair<String, List<String>>>? = null

    /** base emoji -> its skin-tone variants (long-press menu) */
    private var variantMap: Map<String, List<String>> = emptyMap()

    /** base emoji -> its space-separated search tags */
    private var tagMap: Map<String, String> = emptyMap()

    @Synchronized
    private fun ensureLoaded(context: Context) {
        if (cats != null) return
        val out = ArrayList<Pair<String, ArrayList<String>>>()
        val variants = HashMap<String, List<String>>()
        val tags = LinkedHashMap<String, String>()
        try {
            XZInputStream(context.assets.open("emoji.txt.xz"))
                .bufferedReader().forEachLine { line ->
                    if (line.isEmpty()) return@forEachLine
                    if (line[0] == CAT_MARK) {
                        out.add(line.substring(1) to ArrayList<String>())
                        return@forEachLine
                    }
                    val parts = line.split('\t')
                    if (parts.size < 2 || out.isEmpty()) return@forEachLine
                    val e = parts[0]
                    out[out.size - 1].second.add(e)
                    tags[e] = parts[1]
                    if (parts.size >= 3 && parts[2].isNotEmpty()) {
                        variants[e] = parts[2].split(' ').filter { it.isNotEmpty() }
                    }
                }
        } catch (_: Exception) {
        }
        variantMap = variants
        tagMap = tags
        cats = if (out.isEmpty()) FALLBACK
        else out.map { (tab, list) -> tab to list.toList() }
    }

    fun categories(context: Context): List<Pair<String, List<String>>> {
        ensureLoaded(context)
        return cats ?: FALLBACK
    }

    /** the five skin-tone forms of [emoji], or empty when it has none */
    fun variantsOf(context: Context, emoji: String): List<String> {
        ensureLoaded(context)
        return variantMap[emoji] ?: emptyList()
    }

    /** every emoji with its search tags, in catalogue order */
    fun searchIndex(context: Context): Map<String, String> {
        ensureLoaded(context)
        return tagMap
    }

    /** tiny built-in set, used only if the asset is missing or corrupt */
    private val FALLBACK: List<Pair<String, List<String>>> = listOf(
        "😀" to listOf(
            "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🙂", "😉", "😊",
            "😍", "😘", "😗", "😋", "😛", "🤔", "😐", "😒", "🙄", "😪",
            "😴", "😷", "🤒", "😎", "😕", "😟", "😢", "😭", "😡", "😠"
        ),
        "👍" to listOf(
            "👍", "👎", "👌", "✌️", "🤝", "👏", "🙌", "🤲", "🙏", "💪",
            "👀", "👶", "🧒", "👨", "👩", "🧓", "🧕", "👪", "🚶", "🏃"
        ),
        "🐻" to listOf(
            "🐶", "🐱", "🐭", "🐰", "🐻", "🐯", "🦁", "🐸", "🐝", "🦋",
            "🌲", "🌿", "🌷", "🌹", "🌞", "🌙", "⭐", "✨", "🔥", "🌊"
        ),
        "🍔" to listOf(
            "🍎", "🍌", "🍇", "🍉", "🍅", "🍞", "🍔", "🍕", "🍛", "🍜",
            "🍩", "🍰", "☕", "🍵", "🥛", "🥂"
        ),
        "⚽" to listOf(
            "⚽", "🏀", "🏏", "🏐", "🏆", "🥇", "🎯", "🎮", "🎵", "🎨"
        ),
        "🚗" to listOf(
            "🚗", "🚕", "🚌", "🚓", "🚑", "🚲", "✈️", "🚀", "⛵", "🏠",
            "🏥", "🏫", "🕌", "🕋", "⛰️", "🌅"
        ),
        "💡" to listOf(
            "⌚", "📱", "💻", "📷", "☎️", "💡", "🔑", "🔒", "💰", "💳",
            "📚", "✏️", "📝", "✂️", "🎁", "💊"
        ),
        "❤" to listOf(
            "❤️", "🧡", "💛", "💚", "💙", "💜", "💔", "💕", "☪️", "✅",
            "❌", "❗", "❓", "⚠️", "💯", "🔝"
        ),
        "🏳" to listOf(
            "🏳️", "🏁", "🚩", "🇦🇫", "🇵🇰", "🇮🇷", "🇸🇦", "🇹🇷", "🇺🇸", "🇬🇧"
        )
    )
}

/**
 * One emoji in the grid. Draws a small triangle in the bottom-right corner
 * when the emoji has skin-tone forms behind a long-press — the same hint
 * Gboard and Samsung use for "there is more under this key".
 */
class EmojiCell(context: Context) : TextView(context) {
    var hasMore: Boolean = false
        set(value) {
            if (field != value) { field = value; invalidate() }
        }
    var markColor: Int = 0x99FFFFFF.toInt()

    private val markPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private val markPath = android.graphics.Path()

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        if (!hasMore) return
        val d = resources.displayMetrics.density
        val size = 4.5f * d
        val pad = 2.5f * d
        val r = width - pad
        val b = height - pad
        markPath.reset()
        markPath.moveTo(r, b - size)
        markPath.lineTo(r, b)
        markPath.lineTo(r - size, b)
        markPath.close()
        markPaint.color = markColor
        canvas.drawPath(markPath, markPaint)
    }
}

/**
 * Full emoji panel (Samsung/Gboard style). Single top bar holding the
 * back-to-letters, search and delete buttons plus a pill-shaped category
 * strip (recents + one emoji per family). The pill takes the normal key
 * colour; the active family is marked with a circle in the special-key
 * colour. The rest of the panel is the scrollable emoji grid.
 */
class EmojiPanel(
    context: Context,
    private val theme: KeyboardView.Theme,
    private val bgColor: Int,
    private val keyColor: Int,
    private val specialColor: Int,
    private val onEmoji: (String) -> Unit,
    private val onBack: () -> Unit,
    private val onSearch: (() -> Unit)?,
    private val onDelete: () -> Unit,
    /** the same panel also serves kaomoji: wider cells, no search */
    private val categories: List<Pair<String, List<String>>> = EmojiData.categories(context),
    private val recentsKey: String = "emoji_recents",
    /** kaomoji may contain commas, so their recents use a control char */
    private val recentsSep: Char = ',',
    private val columns: Int = 8,
    private val itemTextSize: Float = 26f,
    private val tabWidthDp: Int = 42,
    private val tabTextSize: Float = 19f,
    /** kaomoji have no skin tones — don't decode the emoji catalogue for them */
    private val skinTones: Boolean = true
) : LinearLayout(context) {

    private val density = resources.displayMetrics.density
    private val grid = GridView(context)
    private val tabViews = ArrayList<TextView>()
    private var currentCat = -1 // -1 = recents
    private var items: List<String> = emptyList()
    private var tonePopup: PopupWindow? = null

    // declared before init(): the init block hands it to the GridView
    private val adapter = object : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val tv = (convertView as? EmojiCell) ?: EmojiCell(context).apply {
                maxLines = 1
                gravity = Gravity.CENTER
                textSize = itemTextSize
                height = (46 * density).toInt()
                markColor = theme.hint
            }
            val e = items[position]
            tv.text = e
            tv.setTextColor(theme.text)
            tv.setOnClickListener { pick(e) }
            val tones =
                if (skinTones) EmojiData.variantsOf(context, e) else emptyList()
            // the corner mark tells the user this emoji has skin-tone forms
            tv.hasMore = tones.isNotEmpty()
            tv.isLongClickable = tones.isNotEmpty()
            tv.setOnLongClickListener(
                if (tones.isEmpty()) null
                else View.OnLongClickListener { showTones(tv, tones); true }
            )
            return tv
        }
    }

    init {
        orientation = VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_LTR
        setBackgroundColor(bgColor)

        // -------- top bar: back | search | category pill | delete
        val top = LinearLayout(context)
        top.orientation = HORIZONTAL

        fun addBtn(label: String, iconRes: Int, click: () -> Unit) {
            val bg = GradientDrawable()
            bg.setColor(specialColor)
            bg.cornerRadius = 10 * density
            // icon-only buttons use an ImageView so the icon is CENTERED —
            // a compound drawable on a TextView sticks to the start edge
            val v: View = if (label.isEmpty() && iconRes != 0) {
                val iv = android.widget.ImageView(context)
                val d = try {
                    androidx.appcompat.content.res.AppCompatResources
                        .getDrawable(context, iconRes)?.mutate()
                } catch (_: Exception) { null }
                if (d != null) {
                    androidx.core.graphics.drawable.DrawableCompat.setTint(d, theme.text)
                }
                iv.setImageDrawable(d)
                iv.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                val pad = (13 * density).toInt()
                iv.setPadding(pad, pad, pad, pad)
                iv
            } else {
                val tv = TextView(context)
                tv.text = label
                tv.gravity = Gravity.CENTER
                tv.textSize = 15f
                tv.setTextColor(theme.text)
                tv
            }
            v.background = bg
            v.setOnClickListener { click() }
            val lp = LayoutParams((48 * density).toInt(), LayoutParams.MATCH_PARENT)
            lp.setMargins(
                (3 * density).toInt(), (4 * density).toInt(),
                (3 * density).toInt(), (4 * density).toInt()
            )
            top.addView(v, lp)
        }

        addBtn("اب‌ت", 0) { onBack() }
        onSearch?.let { s -> addBtn("", R.drawable.ic_key_search) { s() } }

        // category pill (rounded rect, normal-key colour, no stroke)
        val tabs = LinearLayout(context)
        tabs.orientation = HORIZONTAL
        val tabScroll = HorizontalScrollView(context)
        tabScroll.isHorizontalScrollBarEnabled = false
        tabScroll.addView(tabs)
        val pill = GradientDrawable()
        pill.setColor(keyColor)
        pill.cornerRadius = 20 * density
        tabScroll.background = pill
        tabScroll.clipToOutline = true
        val pillLp = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
        pillLp.setMargins(
            (3 * density).toInt(), (4 * density).toInt(),
            (3 * density).toInt(), (4 * density).toInt()
        )
        top.addView(tabScroll, pillLp)

        addBtn("", R.drawable.ic_key_backspace) { onDelete() }
        addView(top, LayoutParams(LayoutParams.MATCH_PARENT, (48 * density).toInt()))

        fun addTab(label: String, iconRes: Int, cat: Int) {
            val tv = TextView(context)
            tv.text = label
            tv.maxLines = 1
            tv.gravity = Gravity.CENTER
            tv.textSize = tabTextSize
            if (iconRes != 0) {
                val d = try {
                    androidx.appcompat.content.res.AppCompatResources
                        .getDrawable(context, iconRes)?.mutate()
                } catch (_: Exception) { null }
                if (d != null) {
                    androidx.core.graphics.drawable.DrawableCompat.setTint(d, theme.hint)
                    val sz = (20 * density).toInt()
                    d.setBounds(0, 0, sz, sz)
                    tv.setCompoundDrawables(null, d, null, null)
                    tv.setPadding(0, (10 * density).toInt(), 0, 0)
                }
            }
            tv.setOnClickListener { showCategory(cat) }
            tabs.addView(tv, LayoutParams((tabWidthDp * density).toInt(), LayoutParams.MATCH_PARENT))
            tabViews.add(tv)
        }
        addTab("", R.drawable.ic_key_recent, -1)
        for ((i, c) in categories.withIndex()) addTab(c.first, 0, i)

        // emoji grid fills everything below the top bar. A GridView (rather
        // than a GridLayout in a ScrollView) recycles its cells, so a family
        // of 385 emoji costs the same to show as one of 20.
        grid.numColumns = columns
        grid.layoutDirection = View.LAYOUT_DIRECTION_LTR
        grid.isVerticalScrollBarEnabled = false
        grid.selector = android.graphics.drawable.ColorDrawable(0)
        grid.adapter = adapter
        addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        showCategory(if (loadRecents().isEmpty()) 0 else -1)
    }

    private fun pick(e: String) {
        onEmoji(e)
        addRecent(e)
    }

    /**
     * Long-press on an emoji that has skin tones opens the five variants in a
     * small row above the cell, the way Gboard and WhatsApp do it.
     */
    private fun showTones(anchor: View, tones: List<String>) {
        tonePopup?.dismiss()
        val row = LinearLayout(context)
        row.orientation = HORIZONTAL
        row.layoutDirection = View.LAYOUT_DIRECTION_LTR
        val bg = GradientDrawable()
        bg.setColor(keyColor)
        bg.cornerRadius = 12 * density
        bg.setStroke((1 * density).toInt(), specialColor)
        row.background = bg
        val cell = (44 * density).toInt()
        for (t in tones) {
            val tv = TextView(context)
            tv.text = t
            tv.maxLines = 1
            tv.gravity = Gravity.CENTER
            tv.textSize = itemTextSize
            tv.setOnClickListener {
                tonePopup?.dismiss()
                tonePopup = null
                pick(t)
            }
            row.addView(tv, LayoutParams(cell, cell))
        }
        val width = cell * tones.size
        val pop = PopupWindow(row, width, cell, true)
        pop.isOutsideTouchable = true
        pop.isClippingEnabled = false
        pop.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(0))
        val loc = IntArray(2)
        anchor.getLocationInWindow(loc)
        // keep the row on screen when the pressed cell is near either edge
        val maxX = maxOf(0, resources.displayMetrics.widthPixels - width)
        val x = (loc[0] + anchor.width / 2 - width / 2).coerceIn(0, maxX)
        // above the cell, except on the top row where there is no space
        val y = if (loc[1] >= cell) loc[1] - cell else loc[1] + anchor.height
        try {
            pop.showAtLocation(this, Gravity.NO_GRAVITY, x, y)
            tonePopup = pop
        } catch (_: Exception) {
        }
    }

    /** Circle marking the active family, in the special-key colour. */
    private fun activeCircle(): android.graphics.drawable.Drawable {
        val oval = GradientDrawable()
        oval.shape = GradientDrawable.OVAL
        oval.setColor(specialColor)
        val h = (3 * density).toInt()
        val v = (3 * density).toInt()
        return android.graphics.drawable.InsetDrawable(oval, h, v, h, v)
    }

    private fun showCategory(cat: Int) {
        currentCat = cat
        for ((i, tv) in tabViews.withIndex()) {
            val selected = (i == cat + 1)
            // setBackground() overwrites the view's padding with the drawable's
            // own padding (InsetDrawable reports its insets), which shifted the
            // recents icon and never restored it — preserve padding explicitly
            val pl = tv.paddingLeft; val pt = tv.paddingTop
            val pr = tv.paddingRight; val pb = tv.paddingBottom
            tv.background = if (selected) activeCircle() else null
            tv.setPadding(pl, pt, pr, pb)
        }
        tonePopup?.dismiss()
        tonePopup = null
        items = if (cat == -1) loadRecents() else categories[cat].second
        adapter.notifyDataSetChanged()
        grid.setSelection(0)
    }

    // -------------------------------------------------------------- recents
    private fun prefs() = PreferenceManager.getDefaultSharedPreferences(context)

    private fun loadRecents(): List<String> =
        (prefs().getString(recentsKey, "") ?: "")
            .split(recentsSep).filter { it.isNotEmpty() }

    private fun addRecent(e: String) {
        val list = ArrayList(loadRecents())
        list.remove(e)
        list.add(0, e)
        while (list.size > 40) list.removeAt(list.size - 1)
        prefs().edit().putString(recentsKey, list.joinToString(recentsSep.toString())).apply()
    }
}
