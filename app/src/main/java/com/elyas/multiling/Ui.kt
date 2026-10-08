package com.elyas.multiling

import androidx.appcompat.app.AppCompatActivity
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Keep activity content clear of the status bar, navigation bar and the
 * on-screen keyboard (edge-to-edge is enforced on Android 15+).
 *
 * Blunt but safe, and still used by screens that are not built from [Ui.page]
 * — that one handles its own insets per region instead, so the top bar can sit
 * under the status bar while the list clears the navigation bar.
 */
fun AppCompatActivity.applyEdgePadding() {
    val content = findViewById<View>(android.R.id.content) ?: return
    ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
        )
        v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
        WindowInsetsCompat.CONSUMED
    }
}

/**
 * The app's shared UI vocabulary.
 *
 * Most screens here are built in Kotlin rather than XML, so "consistent"
 * has to mean one set of builders rather than one set of layout files.
 * Every page is assembled from these, which is what keeps spacing, radii,
 * colour and motion identical from screen to screen — and means a change to
 * the look is a change in one file.
 *
 * Everything reads its values from the design tokens (colors/dimens), never
 * from literals.
 */
object Ui {

    // ------------------------------------------------------------ metrics
    fun dp(c: Context, v: Float): Int = (v * c.resources.displayMetrics.density).toInt()

    fun color(c: Context, res: Int): Int =
        androidx.core.content.ContextCompat.getColor(c, res)

    fun icon(c: Context, res: Int, tint: Int, sizeDp: Float = 22f): Drawable? {
        val d = try {
            AppCompatResources.getDrawable(c, res)?.mutate()
        } catch (_: Exception) { null } ?: return null
        DrawableCompat.setTint(d, tint)
        val s = dp(c, sizeDp)
        d.setBounds(0, 0, s, s)
        return d
    }

    /**
     * Let the gradient run behind the system bars, and keep their icons
     * light — every surface in this app is dark, so dark icons would
     * disappear. Each page then pads the regions that need it.
     */
    fun edgeToEdge(a: AppCompatActivity) {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(a.window, false)
        androidx.core.view.WindowCompat
            .getInsetsController(a.window, a.window.decorView).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
    }

    // --------------------------------------------------------------- page
    /**
     * The standard page: gradient background, three colour orbs bled behind
     * the content, a top bar with a back button, and a scrolling column.
     *
     * Returns the column to put content into. It already carries the page
     * gutter, so callers add children without padding of their own.
     */
    fun page(a: AppCompatActivity, title: String, onBack: (() -> Unit)? = null): LinearLayout {
        edgeToEdge(a)
        val root = FrameLayout(a)
        root.setBackgroundResource(R.drawable.ds_bg_app)

        // orbs: placed well outside the frame so only their soft edge shows
        orb(a, root, R.drawable.ds_glow_a, 420f, -140f, -170f, Gravity.TOP or Gravity.END)
        orb(a, root, R.drawable.ds_glow_b, 380f, -150f, 180f, Gravity.TOP or Gravity.START)
        orb(a, root, R.drawable.ds_glow_c, 400f, -130f, -120f, Gravity.BOTTOM or Gravity.END)

        val column = LinearLayout(a)
        column.orientation = LinearLayout.VERTICAL

        val bar = topBar(a, title) { onBack?.invoke() ?: a.onBackPressedDispatcher.onBackPressed() }
        column.addView(bar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(a, 60f)))

        val scroll = ScrollView(a)
        scroll.isVerticalScrollBarEnabled = false
        scroll.clipToPadding = false
        // overscroll glow fights the glass look; the orbs are the depth cue
        scroll.overScrollMode = View.OVER_SCROLL_NEVER

        val content = LinearLayout(a)
        content.orientation = LinearLayout.VERTICAL
        val g = a.resources.getDimensionPixelSize(R.dimen.gutter)
        content.setPadding(g, 0, g, dp(a, 28f))
        scroll.addView(content, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))

        column.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(column, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        // edge to edge: the bar clears the status bar, the list clears the
        // navigation bar, and nothing else has to think about insets
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            bar.setPadding(0, sys.top, 0, 0)
            bar.layoutParams.height = dp(a, 60f) + sys.top
            bar.requestLayout()
            scroll.setPadding(0, 0, 0, sys.bottom)
            insets
        }
        a.setContentView(root)
        return content
    }

    private fun orb(
        c: Context, parent: FrameLayout, res: Int,
        sizeDp: Float, xDp: Float, yDp: Float, gravity: Int
    ) {
        val v = View(c)
        v.setBackgroundResource(res)
        val s = dp(c, sizeDp)
        val lp = FrameLayout.LayoutParams(s, s, gravity)
        lp.leftMargin = dp(c, xDp)
        lp.topMargin = dp(c, yDp)
        lp.rightMargin = dp(c, xDp)
        lp.bottomMargin = dp(c, yDp)
        parent.addView(v, lp)
    }

    /** Back button plus title. Icon-only button, so the icon is centred. */
    fun topBar(c: Context, title: String, onBack: () -> Unit): LinearLayout {
        val bar = LinearLayout(c)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        val g = c.resources.getDimensionPixelSize(R.dimen.gutter)
        bar.setPadding(g - dp(c, 6f), 0, g, 0)

        val back = ImageView(c)
        back.setImageDrawable(icon(c, R.drawable.ds_ic_back, color(c, R.color.text_primary), 22f))
        back.scaleType = ImageView.ScaleType.FIT_CENTER
        back.background = AppCompatResources.getDrawable(c, R.drawable.ds_btn_ghost)
        back.setOnClickListener { onBack() }
        bar.addView(back, LinearLayout.LayoutParams(dp(c, 42f), dp(c, 42f)))

        val tv = TextView(c)
        tv.text = title
        tv.setTextColor(color(c, R.color.text_primary))
        tv.textSize = 19f
        tv.setTypeface(tv.typeface, Typeface.BOLD)
        tv.maxLines = 1
        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        lp.marginStart = dp(c, 12f)
        bar.addView(tv, lp)
        return bar
    }

    // --------------------------------------------------------------- card
    /** A glass panel. Children are added by the caller. */
    fun card(c: Context, tappable: Boolean = false): LinearLayout {
        val v = LinearLayout(c)
        v.orientation = LinearLayout.VERTICAL
        v.setBackgroundResource(
            if (tappable) R.drawable.ds_card_tappable else R.drawable.ds_card
        )
        v.clipToOutline = true
        ViewCompat.setElevation(v, c.resources.getDimension(R.dimen.elev_card))
        val p = c.resources.getDimensionPixelSize(R.dimen.sp_2)
        v.setPadding(p, p, p, p)
        return v
    }

    /** Adds a card to [parent] with the standard gap above it. */
    fun addCard(parent: LinearLayout, card: View, topDp: Float = 14f) {
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(parent.context, topDp)
        parent.addView(card, lp)
    }

    // ------------------------------------------------------------- pieces
    fun sectionHeader(c: Context, text: String): TextView {
        val tv = TextView(c)
        tv.text = text
        tv.setTextColor(color(c, R.color.text_tertiary))
        tv.textSize = 13f
        tv.setTypeface(tv.typeface, Typeface.BOLD)
        tv.isAllCaps = true
        tv.letterSpacing = 0.12f
        tv.setPadding(dp(c, 6f), dp(c, 18f), dp(c, 6f), dp(c, 8f))
        return tv
    }

    fun body(c: Context, text: CharSequence, colorRes: Int = R.color.text_secondary): TextView {
        val tv = TextView(c)
        tv.text = text
        tv.setTextColor(color(c, colorRes))
        tv.textSize = 14.5f
        tv.setLineSpacing(dp(c, 5f).toFloat(), 1f)
        return tv
    }

    /**
     * A row inside a card: coloured icon badge, title, optional summary, and
     * a chevron when it leads somewhere. This is the shape the whole app
     * uses for anything tappable in a list.
     */
    fun row(
        c: Context,
        iconRes: Int,
        accent: Int,
        title: String,
        summary: String? = null,
        chevron: Boolean = true,
        onClick: (() -> Unit)? = null
    ): LinearLayout {
        val row = LinearLayout(c)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.minimumHeight = c.resources.getDimensionPixelSize(R.dimen.row_min)
        val h = dp(c, 10f)
        val v = dp(c, 10f)
        row.setPadding(h, v, h, v)
        if (onClick != null) {
            row.setBackgroundResource(R.drawable.ds_row)
            row.isClickable = true
            row.setOnClickListener { onClick() }
        }

        row.addView(badge(c, iconRes, accent), LinearLayout.LayoutParams(
            c.resources.getDimensionPixelSize(R.dimen.badge),
            c.resources.getDimensionPixelSize(R.dimen.badge)
        ).also { it.marginEnd = dp(c, 12f) })

        val texts = LinearLayout(c)
        texts.orientation = LinearLayout.VERTICAL
        val t = TextView(c)
        t.text = title
        t.setTextColor(color(c, R.color.text_primary))
        t.textSize = 16f
        texts.addView(t)
        if (!summary.isNullOrEmpty()) {
            val s = TextView(c)
            s.text = summary
            s.setTextColor(color(c, R.color.text_tertiary))
            s.textSize = 12.5f
            s.setPadding(0, dp(c, 2f), 0, 0)
            texts.addView(s)
        }
        row.addView(texts, LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        if (chevron) {
            val ch = ImageView(c)
            ch.setImageDrawable(icon(c, R.drawable.ds_ic_chevron,
                color(c, R.color.text_tertiary), 18f))
            row.addView(ch, LinearLayout.LayoutParams(dp(c, 18f), dp(c, 18f)))
        }
        return row
    }

    /**
     * The round icon badge. The accent is applied as a translucent fill with
     * a full-strength icon, so each area reads as its own colour without the
     * page turning loud.
     */
    fun badge(c: Context, iconRes: Int, accent: Int): FrameLayout {
        val holder = FrameLayout(c)
        val bg = android.graphics.drawable.GradientDrawable()
        bg.shape = android.graphics.drawable.GradientDrawable.OVAL
        bg.setColor((accent and 0x00FFFFFF) or 0x33000000)
        bg.setStroke(dp(c, 1f), (accent and 0x00FFFFFF) or 0x40000000)
        holder.background = bg
        val iv = ImageView(c)
        iv.setImageDrawable(icon(c, iconRes, accent, 21f))
        iv.scaleType = ImageView.ScaleType.FIT_CENTER
        holder.addView(iv, FrameLayout.LayoutParams(
            dp(c, 21f), dp(c, 21f), Gravity.CENTER))
        return holder
    }

    /** Thin separator between rows of the same card. */
    fun divider(c: Context): View {
        val v = View(c)
        v.setBackgroundColor(color(c, R.color.divider))
        return v
    }

    fun addDivider(card: LinearLayout) {
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, kotlin.math.max(1, dp(card.context, 0.7f)))
        lp.marginStart = dp(card.context, 66f)
        lp.marginEnd = dp(card.context, 10f)
        card.addView(divider(card.context), lp)
    }

    // ------------------------------------------------------------ buttons
    fun primaryButton(c: Context, text: String, onClick: () -> Unit): TextView =
        button(c, text, R.drawable.ds_btn_primary, R.color.text_on_accent, onClick)

    fun ghostButton(c: Context, text: String, onClick: () -> Unit): TextView =
        button(c, text, R.drawable.ds_btn_ghost, R.color.text_primary, onClick)

    private fun button(
        c: Context, text: String, bg: Int, textColor: Int, onClick: () -> Unit
    ): TextView {
        val b = TextView(c)
        b.text = text
        b.gravity = Gravity.CENTER
        b.textSize = 15.5f
        b.setTypeface(b.typeface, Typeface.BOLD)
        b.setTextColor(color(c, textColor))
        b.setBackgroundResource(bg)
        b.setPadding(dp(c, 20f), dp(c, 14f), dp(c, 20f), dp(c, 14f))
        b.isClickable = true
        b.setOnClickListener { onClick() }
        return b
    }
}
