package com.elyas.multiling

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The spatial half of a keyboard decoder.
 *
 * Standard keyboards (Gboard, Samsung) do not decide what you typed from the
 * letter alone — they keep the touch POINT and score every nearby key by how
 * far the finger landed from its centre, then let the language model pick the
 * word. Gboard calls this its "spatial model": a Gaussian around each key
 * centre, combined with a language score to rank candidates.
 *
 * We keep the same shape, minus the learned covariances:
 *
 *  * [Tap] records where the finger actually landed, not just which key won.
 *  * [alternativesFor] turns a tap back into "which characters could this
 *    plausibly have been, and how unlikely is each" — normalised by key size,
 *    so it behaves the same on a phone and on a tablet.
 *  * [TOUCH_BIAS_Y] corrects the systematic offset every touch keyboard has
 *    to deal with: fingers land BELOW the point the user believes they are
 *    pressing, because the contact patch is centred under the fingertip while
 *    the user aims with what they can see above it.
 *
 * Costs are in the same units the edit-distance matcher uses, where 1.0 is
 * "one whole wrong character".
 */
object SpatialModel {

    /** One key of the live layout: its centre and half-extents, in pixels. */
    class KeyBox(val ch: Char, val cx: Float, val cy: Float, val hw: Float, val hh: Float)

    /** Where a finger landed for one typed character. */
    class Tap(val ch: Char, val x: Float, val y: Float)

    /**
     * Fingers land low. The user aims at what they can see in front of the
     * fingertip, but the digitiser reports the centroid of the contact patch,
     * which sits lower — so the raw point drifts toward the row below. A
     * fraction of a key height upward is the correction; the same trick is
     * why other keyboards feel "surer" under a fast thumb.
     */
    const val TOUCH_BIAS_Y = 0.22f

    /** Beyond this many key-widths away a key is not a plausible slip. */
    private const val MAX_RADIUS = 1.9f

    /** Cost charged to the nearest alternative that is not the typed key. */
    private const val MIN_SLIP_COST = 0.28f

    /**
     * Characters this tap could have meant, cheapest first, including the
     * character actually committed (at cost 0).
     *
     * Distance is measured in key-widths so the model does not change
     * meaning with screen density or a resized keyboard, and it is measured
     * from the key EDGE rather than the centre — landing anywhere inside a
     * key is equally intentional, and only the distance past the edge counts
     * as a slip.
     */
    fun alternativesFor(tap: Tap, keys: List<KeyBox>): List<Pair<Char, Float>> {
        if (keys.isEmpty()) return listOf(tap.ch to 0f)
        val out = ArrayList<Pair<Char, Float>>(8)
        out.add(tap.ch to 0f)
        for (k in keys) {
            val ch = k.ch
            if (ch == tap.ch) continue
            // distance from the touch to this key's rectangle, in key-widths
            val dx = max(0f, abs(tap.x - k.cx) - k.hw) / max(1f, k.hw)
            val dy = max(0f, abs(tap.y - k.cy) - k.hh) / max(1f, k.hh)
            val d = kotlin.math.sqrt(dx * dx + dy * dy)
            if (d > MAX_RADIUS) continue
            // a key the finger nearly touched is cheap; one two keys over is
            // barely cheaper than any other character
            val cost = MIN_SLIP_COST + (d / MAX_RADIUS) * (1f - MIN_SLIP_COST)
            out.add(ch to min(1f, cost))
        }
        out.sortBy { it.second }
        return if (out.size > 8) out.subList(0, 8) else out
    }
}
