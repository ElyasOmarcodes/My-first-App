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
 * Full emoji panel (Samsung/Gboard style): recents + category tabs +
 * scrollable grid + bottom bar with back-to-letters, search and delete.
 */
class EmojiPanel(
    context: Context,
    private val theme: KeyboardView.Theme,
    private val onEmoji: (String) -> Unit,
    private val onBack: () -> Unit,
    private val onSearch: () -> Unit,
    private val onDelete: () -> Unit
) : LinearLayout(context) {

    private val density = resources.displayMetrics.density
    private val gridHolder = ScrollView(context)
    private val tabViews = ArrayList<TextView>()
    private var currentCat = -1 // -1 = recents

    init {
        orientation = VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_LTR
        setBackgroundColor(theme.background)

        // category tabs
        val tabs = LinearLayout(context)
        tabs.orientation = HORIZONTAL
        val tabScroll = HorizontalScrollView(context)
        tabScroll.isHorizontalScrollBarEnabled = false
        tabScroll.addView(tabs)
        addView(tabScroll, LayoutParams(LayoutParams.MATCH_PARENT, (40 * density).toInt()))

        fun addTab(label: String, iconRes: Int, cat: Int) {
            val tv = TextView(context)
            tv.text = label
            tv.gravity = Gravity.CENTER
            tv.textSize = 20f
            tv.setPadding((12 * density).toInt(), 0, (12 * density).toInt(), 0)
            if (iconRes != 0) {
                val d = try {
                    androidx.appcompat.content.res.AppCompatResources
                        .getDrawable(context, iconRes)?.mutate()
                } catch (_: Exception) { null }
                if (d != null) {
                    androidx.core.graphics.drawable.DrawableCompat.setTint(d, theme.hint)
                    val sz = (22 * density).toInt()
                    d.setBounds(0, 0, sz, sz)
                    tv.setCompoundDrawables(null, d, null, null)
                }
            }
            tv.setOnClickListener { showCategory(cat) }
            tabs.addView(tv, LayoutParams((44 * density).toInt(), LayoutParams.MATCH_PARENT))
            tabViews.add(tv)
        }
        addTab("", R.drawable.ic_key_recent, -1)
        for ((i, c) in EmojiData.CATEGORIES.withIndex()) addTab(c.first, 0, i)

        // emoji grid
        addView(gridHolder, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        // bottom bar
        val bottom = LinearLayout(context)
        bottom.orientation = HORIZONTAL
        fun addBtn(label: String, iconRes: Int, w: Float, click: () -> Unit) {
            val tv = TextView(context)
            tv.text = label
            tv.gravity = Gravity.CENTER
            tv.textSize = 16f
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
                    tv.compoundDrawablePadding = (4 * density).toInt()
                }
            }
            val bg = GradientDrawable()
            bg.setColor(theme.specialFill)
            bg.cornerRadius = 8 * density
            tv.background = bg
            tv.setOnClickListener { click() }
            val lp = LayoutParams(0, LayoutParams.MATCH_PARENT, w)
            lp.setMargins(
                (2 * density).toInt(), (2 * density).toInt(),
                (2 * density).toInt(), (2 * density).toInt()
            )
            bottom.addView(tv, lp)
        }
        addBtn("ابت", 0, 1.2f) { onBack() }
        addBtn("لټون", R.drawable.ic_key_search, 1.6f) { onSearch() }
        addBtn("", R.drawable.ic_key_backspace, 1.2f) { onDelete() }
        addView(bottom, LayoutParams(LayoutParams.MATCH_PARENT, (46 * density).toInt()))

        showCategory(if (loadRecents().isEmpty()) 0 else -1)
    }

    private fun showCategory(cat: Int) {
        currentCat = cat
        for ((i, tv) in tabViews.withIndex()) {
            val selected = (i == cat + 1)
            tv.setBackgroundColor(if (selected) theme.keyPressed else 0)
        }
        val emojis = if (cat == -1) loadRecents() else EmojiData.CATEGORIES[cat].second
        gridHolder.removeAllViews()
        val grid = GridLayout(context)
        grid.columnCount = 8
        grid.layoutDirection = View.LAYOUT_DIRECTION_LTR
        val cell = (resources.displayMetrics.widthPixels / 8f).toInt()
        for (e in emojis) {
            val tv = TextView(context)
            tv.text = e
            tv.gravity = Gravity.CENTER
            tv.textSize = 26f
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
        (prefs().getString("emoji_recents", "") ?: "")
            .split(',').filter { it.isNotEmpty() }

    private fun addRecent(e: String) {
        val list = ArrayList(loadRecents())
        list.remove(e)
        list.add(0, e)
        while (list.size > 40) list.removeAt(list.size - 1)
        prefs().edit().putString("emoji_recents", list.joinToString(",")).apply()
    }
}
