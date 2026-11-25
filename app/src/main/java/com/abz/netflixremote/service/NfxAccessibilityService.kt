package com.abz.netflixremote.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Point
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Unico modo, senza root, per "toccare" l'interfaccia Netflix sul tablet.
 */
class NfxAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun screenSize(): Point {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.currentWindowMetrics.bounds
            Point(b.width(), b.height())
        } else {
            val p = Point()
            @Suppress("DEPRECATION") wm.defaultDisplay.getRealSize(p)
            p
        }
    }

    /** Serie di tocchi: ogni elemento e' (x, y, ritardo dall'inizio in ms). */
    fun taps(points: List<Triple<Float, Float, Long>>) {
        if (points.isEmpty()) return
        val max = try { GestureDescription.getMaxStrokeCount() } catch (e: Throwable) { 10 }
        val b = GestureDescription.Builder()
        points.take(max).forEach { (x, y, t) ->
            val path = Path().apply { moveTo(x, y) }
            b.addStroke(GestureDescription.StrokeDescription(path, t, 45))
        }
        dispatchGesture(b.build(), null, null)
    }

    /**
     * Cerca nell'albero di accessibilita' il comando del player il cui testo o descrizione
     * contiene una delle parole date, e lo preme. Molto piu' affidabile del tocco a coordinate
     * fisse: Netflix sposta i pulsanti fra una versione e l'altra e fra un layout e l'altro.
     *
     * Due accortezze imparate sul campo:
     *  - i contenitori vanno scartati per dimensione, altrimenti la radice "Video Player"
     *    (grande quanto lo schermo) intercetta la parola "play" e si finisce per toccare
     *    il centro dello schermo;
     *  - i controlli Netflix NON sono marcati clickable, quindi ACTION_CLICK fallisce e
     *    bisogna toccare il centro dei loro bounds.
     */
    fun clickNode(patterns: List<String>, times: Int = 1): Boolean {
        val root = rootInActiveWindow ?: return false
        val screen = screenSize()
        val maxArea = screen.x.toLong() * screen.y.toLong() / 4
        val node = findNode(root, patterns, maxArea, 0) ?: return false

        if (times == 1 && node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true
        }
        val r = Rect()
        node.getBoundsInScreen(r)
        if (r.width() <= 0 || r.height() <= 0) return false
        taps((0 until times).map { Triple(r.exactCenterX(), r.exactCenterY(), it * 180L) })
        return true
    }

    private fun findNode(
        node: AccessibilityNodeInfo?,
        patterns: List<String>,
        maxArea: Long,
        depth: Int
    ): AccessibilityNodeInfo? {
        if (node == null || depth > 25) return null
        val r = Rect()
        node.getBoundsInScreen(r)
        val area = r.width().toLong() * r.height().toLong()
        if (area in 1 until maxArea) {
            val label = ((node.text ?: "").toString() + " " + (node.contentDescription ?: "").toString())
            if (label.isNotBlank() && patterns.any { label.contains(it, ignoreCase = true) }) return node
        }
        for (i in 0 until node.childCount) {
            findNode(node.getChild(i), patterns, maxArea, depth + 1)?.let { return it }
        }
        return null
    }

    fun global(action: Int): Boolean = performGlobalAction(action)

    companion object {
        @Volatile
        var instance: NfxAccessibilityService? = null
            private set

        fun isEnabled(c: Context): Boolean {
            val enabled = Settings.Secure.getString(
                c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.contains(c.packageName + "/", ignoreCase = true)
        }
    }
}
