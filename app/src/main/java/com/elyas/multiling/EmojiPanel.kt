package com.elyas.multiling

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.preference.PreferenceManager

/** Emoji sets grouped Samsung/Gboard-style. */
object EmojiData {
    val CATEGORIES: List<Pair<String, List<String>>> = listOf(
        "😀" to listOf(
            "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂", "🙂", "🙃",
            "😉", "😊", "😇", "🥰", "😍", "🤩", "😘", "😗", "😚", "😙",
            "😋", "😛", "😜", "🤪", "😝", "🤑", "🤗", "🤭", "🤫", "🤔",
            "🤐", "🤨", "😐", "😑", "😶", "😏", "😒", "🙄", "😬", "🤥",
            "😌", "😔", "😪", "🤤", "😴", "😷", "🤒", "🤕", "🤢", "🤮",
            "🤧", "🥵", "🥶", "🥴", "😵", "🤯", "🤠", "🥳", "😎", "🤓",
            "🧐", "😕", "😟", "🙁", "😮", "😯", "😲", "😳", "🥺", "😦",
            "😧", "😨", "😰", "😥", "😢", "😭", "😱", "😖", "😣", "😞",
            "😓", "😩", "😫", "🥱", "😤", "😡", "😠", "🤬", "😈", "👿",
            "💀", "👻", "👽", "🤖", "💩", "😺", "😸", "😹", "😻", "😽"
        ),
        "👍" to listOf(
            "👍", "👎", "👌", "🤌", "✌️", "🤞", "🤟", "🤘", "🤙", "👈",
            "👉", "👆", "👇", "☝️", "✋", "🤚", "🖐️", "🖖", "👋", "🤝",
            "👏", "🙌", "👐", "🤲", "🙏", "✍️", "💪", "🦾", "🖕", "✊",
            "👊", "🤛", "🤜", "💅", "🤳", "👂", "👃", "👀", "👁️", "👅",
            "👄", "🧠", "🦷", "👶", "🧒", "👦", "👧", "🧑", "👨", "👩",
            "🧔", "👴", "👵", "👨‍⚕️", "👨‍🏫", "👨‍🌾", "👨‍🍳", "👨‍🔧", "👮", "💂",
            "🕵️", "👷", "🤴", "👸", "👳", "🧕", "🤵", "👰", "🤰", "🤱",
            "🚶", "🏃", "💃", "🕺", "🧎", "🧍", "👫", "👬", "👭", "💏",
            "💑", "👪", "🗣️", "👤", "👥", "🫂", "👣", "🤷", "🤦", "💁"
        ),
        "🐻" to listOf(
            "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯",
            "🦁", "🐮", "🐷", "🐸", "🐵", "🙈", "🙉", "🙊", "🐒", "🐔",
            "🐧", "🐦", "🐤", "🦆", "🦅", "🦉", "🦇", "🐺", "🐗", "🐴",
            "🦄", "🐝", "🐛", "🦋", "🐌", "🐞", "🐜", "🦟", "🦗", "🕷️",
            "🦂", "🐢", "🐍", "🦎", "🦖", "🐙", "🦑", "🦐", "🦀", "🐡",
            "🐠", "🐟", "🐬", "🐳", "🐋", "🦈", "🐊", "🐅", "🐆", "🦓",
            "🦍", "🐘", "🦛", "🦏", "🐪", "🐫", "🦒", "🦘", "🐃", "🐂",
            "🐄", "🐎", "🐖", "🐏", "🐑", "🦙", "🐐", "🦌", "🐕", "🐩",
            "🐈", "🐓", "🦃", "🕊️", "🐇", "🦝", "🦨", "🦥", "🌵", "🌲",
            "🌳", "🌴", "🌱", "🌿", "☘️", "🍀", "🍁", "🍂", "🌸", "🌺",
            "🌻", "🌹", "🥀", "🌷", "🌼", "💐", "🌾", "🌍", "🌙", "⭐",
            "🌟", "✨", "⚡", "🔥", "🌈", "☀️", "⛅", "☁️", "🌧️", "⛈️",
            "❄️", "☃️", "🌬️", "💧", "💦", "🌊"
        ),
        "🍔" to listOf(
            "🍏", "🍎", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓", "🫐",
            "🍈", "🍒", "🍑", "🥭", "🍍", "🥥", "🥝", "🍅", "🍆", "🥑",
            "🥦", "🥬", "🥒", "🌶️", "🌽", "🥕", "🧄", "🧅", "🥔", "🍠",
            "🥐", "🍞", "🥖", "🥨", "🧀", "🥚", "🍳", "🧈", "🥞", "🧇",
            "🥓", "🥩", "🍗", "🍖", "🌭", "🍔", "🍟", "🍕", "🥪", "🌮",
            "🌯", "🥙", "🧆", "🥘", "🍲", "🥣", "🥗", "🍿", "🧂", "🥫",
            "🍱", "🍚", "🍜", "🍝", "🍢", "🍣", "🍤", "🍙", "🍘", "🍥",
            "🥮", "🍡", "🥟", "🍦", "🍧", "🍨", "🍩", "🍪", "🎂", "🍰",
            "🧁", "🥧", "🍫", "🍬", "🍭", "🍮", "🍯", "🍼", "🥛", "☕",
            "🍵", "🧃", "🥤", "🧉", "🥢", "🍽️", "🍴", "🥄", "🫖", "🧊"
        ),
        "⚽" to listOf(
            "⚽", "🏀", "🏈", "⚾", "🥎", "🎾", "🏐", "🏉", "🥏", "🎱",
            "🪀", "🏓", "🏸", "🏒", "🏑", "🥍", "🏏", "🥅", "⛳", "🪁",
            "🏹", "🎣", "🤿", "🥊", "🥋", "🎽", "🛹", "🛷", "⛸️", "🥌",
            "🎿", "⛷️", "🏂", "🏋️", "🤼", "🤸", "⛹️", "🤺", "🤾", "🏌️",
            "🏇", "🧘", "🏄", "🏊", "🤽", "🚣", "🧗", "🚵", "🚴", "🏆",
            "🥇", "🥈", "🥉", "🏅", "🎖️", "🎗️", "🎫", "🎟️", "🎪", "🤹",
            "🎭", "🩰", "🎨", "🎬", "🎤", "🎧", "🎼", "🎹", "🥁", "🎷",
            "🎺", "🎸", "🪕", "🎻", "🎲", "♟️", "🎯", "🎳", "🎮", "🎰", "🧩"
        ),
        "🚗" to listOf(
            "🚗", "🚕", "🚙", "🚌", "🚎", "🏎️", "🚓", "🚑", "🚒", "🚐",
            "🛻", "🚚", "🚛", "🚜", "🛵", "🏍️", "🛺", "🚲", "🛴", "🚨",
            "🚔", "🚍", "🚘", "🚖", "🚡", "🚠", "🚟", "🚃", "🚋", "🚞",
            "🚝", "🚄", "🚅", "🚈", "🚂", "🚆", "🚇", "🚊", "🚉", "✈️",
            "🛫", "🛬", "🛩️", "💺", "🛰️", "🚀", "🛸", "🚁", "🛶", "⛵",
            "🚤", "🛥️", "🛳️", "⛴️", "🚢", "⚓", "⛽", "🚧", "🚦", "🚥",
            "🗺️", "🗿", "🗽", "🗼", "🏰", "🏯", "🏟️", "🎡", "🎢", "🎠",
            "⛲", "⛱️", "🏖️", "🏝️", "🏜️", "🌋", "⛰️", "🏔️", "🗻", "🏕️",
            "⛺", "🏠", "🏡", "🏘️", "🏚️", "🏗️", "🏭", "🏢", "🏬", "🏣",
            "🏤", "🏥", "🏦", "🏨", "🏪", "🏫", "🏩", "💒", "🏛️", "⛪",
            "🕌", "🕍", "🛕", "🕋", "⛩️", "🌁", "🌃", "🏙️", "🌄", "🌅"
        ),
        "💡" to listOf(
            "⌚", "📱", "📲", "💻", "⌨️", "🖥️", "🖨️", "🖱️", "🖲️", "🕹️",
            "🗜️", "💽", "💾", "💿", "📀", "📼", "📷", "📸", "📹", "🎥",
            "📽️", "🎞️", "📞", "☎️", "📟", "📠", "📺", "📻", "🎙️", "🎚️",
            "🎛️", "🧭", "⏱️", "⏲️", "⏰", "🕰️", "⌛", "⏳", "📡", "🔋",
            "🔌", "💡", "🔦", "🕯️", "🧯", "🛢️", "💸", "💵", "💴", "💶",
            "💷", "💰", "💳", "💎", "⚖️", "🧰", "🔧", "🔨", "⚒️", "🛠️",
            "⛏️", "🔩", "⚙️", "🧱", "⛓️", "🧲", "🔫", "💣", "🧨", "🪓",
            "🔪", "🗡️", "⚔️", "🛡️", "🚬", "⚰️", "⚱️", "🏺", "🔮", "📿",
            "🧿", "💈", "⚗️", "🔭", "🔬", "🕳️", "💊", "💉", "🩸", "🩹",
            "🩺", "🌡️", "🧹", "🧺", "🧻", "🚽", "🚰", "🚿", "🛁", "🛀",
            "🧼", "🪒", "🧽", "🧴", "🛎️", "🔑", "🗝️", "🚪", "🪑", "🛋️",
            "🛏️", "🛌", "🧸", "🖼️", "🛍️", "🛒", "🎁", "🎈", "🎏", "🎀",
            "🎊", "🎉", "🎎", "🏮", "🎐", "✉️", "📩", "📨", "📧", "💌",
            "📮", "📪", "📫", "📬", "📭", "📦", "🏷️", "📜", "📃", "📄",
            "📑", "🧾", "📊", "📈", "📉", "🗒️", "🗓️", "📆", "📅", "🗑️",
            "📇", "🗃️", "🗳️", "🗄️", "📋", "📁", "📂", "🗂️", "🗞️", "📰",
            "📓", "📔", "📒", "📕", "📗", "📘", "📙", "📚", "📖", "🔖",
            "🧷", "🔗", "📎", "🖇️", "📐", "📏", "🧮", "📌", "📍", "✂️",
            "🖊️", "🖋️", "✒️", "🖌️", "🖍️", "📝", "✏️", "🔍", "🔎", "🔏",
            "🔐", "🔒", "🔓"
        ),
        "❤" to listOf(
            "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔",
            "❣️", "💕", "💞", "💓", "💗", "💖", "💘", "💝", "💟", "☮️",
            "✝️", "☪️", "🕉️", "☸️", "✡️", "🔯", "🕎", "☯️", "☦️", "🛐",
            "⛎", "♈", "♉", "♊", "♋", "♌", "♍", "♎", "♏", "♐",
            "♑", "♒", "♓", "🆔", "⚛️", "🉑", "☢️", "☣️", "📴", "📳",
            "🈶", "🈚", "🈸", "🈺", "🈷️", "✴️", "🆚", "💮", "🉐", "㊙️",
            "㊗️", "🈴", "🈵", "🈹", "🈲", "🅰️", "🅱️", "🆎", "🆑", "🅾️",
            "🆘", "❌", "⭕", "🛑", "⛔", "📛", "🚫", "💯", "💢", "♨️",
            "🚷", "🚯", "🚳", "🚱", "🔞", "📵", "🚭", "❗", "❕", "❓",
            "❔", "‼️", "⁉️", "🔅", "🔆", "〽️", "⚠️", "🚸", "🔱", "⚜️",
            "🔰", "♻️", "✅", "🈯", "💹", "❇️", "✳️", "❎", "🌐", "💠",
            "Ⓜ️", "🌀", "💤", "🏧", "🚾", "♿", "🅿️", "🈳", "🈂️", "🛂",
            "🛃", "🛄", "🛅", "🚹", "🚺", "🚼", "🚻", "🚮", "🎦", "📶",
            "🈁", "🔣", "ℹ️", "🔤", "🔡", "🔠", "🆖", "🆗", "🆙", "🆒",
            "🆕", "🆓", "0️⃣", "1️⃣", "2️⃣", "3️⃣", "4️⃣", "5️⃣", "6️⃣", "7️⃣",
            "8️⃣", "9️⃣", "🔟", "🔢", "#️⃣", "*️⃣", "⏏️", "▶️", "⏸️", "⏯️",
            "⏹️", "⏺️", "⏭️", "⏮️", "⏩", "⏪", "⏫", "⏬", "◀️", "🔼",
            "🔽", "➡️", "⬅️", "⬆️", "⬇️", "↗️", "↘️", "↙️", "↖️", "↕️",
            "↔️", "↪️", "↩️", "⤴️", "⤵️", "🔀", "🔁", "🔂", "🔄", "🔃"
        ),
        "🏳" to listOf(
            "🏳️", "🏴", "🏁", "🚩", "🏳️‍🌈", "🇦🇫", "🇵🇰", "🇮🇷", "🇮🇳", "🇸🇦",
            "🇦🇪", "🇶🇦", "🇰🇼", "🇧🇭", "🇴🇲", "🇾🇪", "🇮🇶", "🇸🇾", "🇯🇴", "🇱🇧",
            "🇵🇸", "🇪🇬", "🇹🇷", "🇹🇯", "🇺🇿", "🇹🇲", "🇰🇿", "🇰🇬", "🇨🇳", "🇯🇵",
            "🇰🇷", "🇮🇩", "🇲🇾", "🇧🇩", "🇱🇰", "🇳🇵", "🇷🇺", "🇺🇦", "🇩🇪", "🇫🇷",
            "🇬🇧", "🇮🇹", "🇪🇸", "🇳🇱", "🇧🇪", "🇨🇭", "🇸🇪", "🇳🇴", "🇩🇰", "🇦🇹",
            "🇬🇷", "🇵🇱", "🇺🇸", "🇨🇦", "🇲🇽", "🇧🇷", "🇦🇷", "🇦🇺", "🇳🇿", "🇿🇦"
        )
    )
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
    private val categories: List<Pair<String, List<String>>> = EmojiData.CATEGORIES,
    private val recentsKey: String = "emoji_recents",
    /** kaomoji may contain commas, so their recents use a control char */
    private val recentsSep: Char = ',',
    private val columns: Int = 8,
    private val itemTextSize: Float = 26f,
    private val tabWidthDp: Int = 42,
    private val tabTextSize: Float = 19f
) : LinearLayout(context) {

    private val density = resources.displayMetrics.density
    private val gridHolder = ScrollView(context)
    private val tabViews = ArrayList<TextView>()
    private var currentCat = -1 // -1 = recents

    init {
        orientation = VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_LTR
        setBackgroundColor(bgColor)

        // -------- top bar: back | search | category pill | delete
        val top = LinearLayout(context)
        top.orientation = HORIZONTAL

        fun addBtn(label: String, iconRes: Int, click: () -> Unit) {
            val tv = TextView(context)
            tv.text = label
            tv.gravity = Gravity.CENTER
            tv.textSize = 15f
            tv.setTextColor(theme.text)
            if (iconRes != 0) {
                val d = try {
                    androidx.appcompat.content.res.AppCompatResources
                        .getDrawable(context, iconRes)?.mutate()
                } catch (_: Exception) { null }
                if (d != null) {
                    androidx.core.graphics.drawable.DrawableCompat.setTint(d, theme.text)
                    val sz = (20 * density).toInt()
                    d.setBounds(0, 0, sz, sz)
                    tv.setCompoundDrawables(d, null, null, null)
                }
            }
            val bg = GradientDrawable()
            bg.setColor(specialColor)
            bg.cornerRadius = 10 * density
            tv.background = bg
            tv.setOnClickListener { click() }
            val lp = LayoutParams((48 * density).toInt(), LayoutParams.MATCH_PARENT)
            lp.setMargins(
                (3 * density).toInt(), (4 * density).toInt(),
                (3 * density).toInt(), (4 * density).toInt()
            )
            top.addView(tv, lp)
        }

        addBtn("ابت", 0) { onBack() }
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

        // emoji grid fills everything below the top bar
        addView(gridHolder, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        showCategory(if (loadRecents().isEmpty()) 0 else -1)
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
            tv.background = if (selected) activeCircle() else null
        }
        val emojis = if (cat == -1) loadRecents() else categories[cat].second
        gridHolder.removeAllViews()
        val grid = GridLayout(context)
        grid.columnCount = columns
        grid.layoutDirection = View.LAYOUT_DIRECTION_LTR
        val cell = (resources.displayMetrics.widthPixels / columns.toFloat()).toInt()
        for (e in emojis) {
            val tv = TextView(context)
            tv.text = e
            tv.maxLines = 1
            tv.gravity = Gravity.CENTER
            tv.textSize = itemTextSize
            tv.width = cell
            tv.height = (46 * density).toInt()
            tv.setOnClickListener {
                onEmoji(e)
                addRecent(e)
            }
            grid.addView(tv)
        }
        gridHolder.addView(grid)
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
