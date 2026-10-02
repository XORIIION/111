package ru.platesgame

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.*
import androidx.core.animation.doOnEnd
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.ColorUtils
import androidx.core.widget.doAfterTextChanged
import kotlin.random.Random

fun rep(s: String) = s.groupingBy { it }.eachCount().values.max()

data class Plate(val num: String, val region: String, val gold: Boolean = false) {
    private val letters get() = "" + num[0] + num[4] + num[5]
    private val digits get() = num.substring(1, 4)
    val dRep get() = rep(digits)
    val lRep get() = rep(letters)
    // Цена зависит только от одинаковых цифр и букв
    val price: Int get() {
        var p = 100.0
        p *= when (dRep) { 3 -> 25.0; 2 -> 4.0; else -> 1.0 }
        p *= when (lRep) { 3 -> 15.0; 2 -> 3.0; else -> 1.0 }
        if (dRep < 3 && digits[0] == digits[2]) p *= 2 // зеркальные цифры (121)
        if (gold) p *= 1.5 // золотой номер — x1.5 к стоимости
        return p.toInt()
    }
}

class MainActivity : Activity() {
    private val cyr = "АВЕКМНОРСТУХ"
    private val lat = "ABEKMHOPCTYX"
    private val regions = ("01 02 16 21 23 24 36 47 50 52 54 61 63 66 72 73 74 77 78 86 90 93 96 97 98 99 " +
        "102 116 123 150 154 161 163 174 177 178 186 190 193 196 197 198 199 716 750 763 777 797 799 977").split(" ")

    private val plates = mutableListOf<Plate>()
    private var balance = 0
    private var last: Plate? = null
    private val prefs by lazy { getSharedPreferences("plates", MODE_PRIVATE) }

    private lateinit var tvBal: TextView
    private lateinit var tvCars: TextView
    private lateinit var balChip: View
    private lateinit var tvPrice: TextView
    private lateinit var tvRarity: TextView
    private lateinit var slot: FrameLayout
    private lateinit var glow: View
    private lateinit var placeholder: TextView
    private lateinit var tapRing: View
    private lateinit var tvHint: TextView
    private lateinit var bonusBtn: Button
    private lateinit var sellBtn: Button
    private lateinit var spinScreen: View
    private lateinit var listScreen: View
    private lateinit var list: LinearLayout
    private lateinit var fRegion: EditText
    private lateinit var fLetter: EditText
    private lateinit var fType: Spinner
    private lateinit var fSort: Spinner
    private lateinit var navSpin: TextView
    private lateinit var navList: TextView
    private lateinit var navUpg: TextView
    private lateinit var upgradeScreen: View
    private lateinit var upgCard: View
    private lateinit var tvGoldLevel: TextView
    private lateinit var tvGoldChance: TextView
    private lateinit var tvGoldNext: TextView
    private lateinit var goldFill: View
    private lateinit var goldRest: View
    private lateinit var goldBar: LinearLayout
    private lateinit var upgBtn: Button

    private var currentView: PlateView? = null
    private var plateW = 0
    private var plateH = 0
    private var shownBal = 0
    private var balAnim: ValueAnimator? = null
    private var priceAnim: ValueAnimator? = null
    private var curScreen = -1
    private var lastBonus = 0L
    private var bonusWasReady: Boolean? = null
    private val loops = mutableListOf<ValueAnimator>()

    private val spinCost = 300
    private var goldLevel = 0                 // каждый уровень = +1% шанса золотого номера
    private val maxGold = 100
    private val goldBaseCost = 3000.0         // первое улучшение
    private val goldCostMul = 1.5             // каждое следующее дороже в 1.5 раза
    private val bonusAmount = 5000
    private val bonusCooldownMs = 10 * 60 * 1000L

    private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val bold = Typeface.create("sans-serif-medium", Typeface.BOLD)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun c(s: String) = Color.parseColor(s)
    private fun alpha(color: Int, a: Int) = ColorUtils.setAlphaComponent(color, a)

    // ───────────────────────── Жизненный цикл ─────────────────────────

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = c("#14092E")
        window.navigationBarColor = c("#1B0D3F")

        balance = prefs.getInt("bal", 0)
        shownBal = balance
        lastBonus = prefs.getLong("bonus", 0L)
        goldLevel = prefs.getInt("gold", 0).coerceIn(0, maxGold)
        prefs.getString("plates", "")!!.split(";").filter { it.contains("|") }
            .forEach { val s = it.split("|"); plates.add(Plate(s[0], s[1], s.getOrNull(2) == "g")) }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(c("#14092E"), c("#2B1262"), c("#180A38"))
            )
        }

        tvBal = TextView(this).apply { textSize = 20f; setTextColor(Color.WHITE); typeface = bold }
        tvCars = TextView(this).apply { textSize = 20f; setTextColor(Color.WHITE); typeface = bold }
        balChip = chip(IconView(this, IconView.COIN), 30, 30, tvBal)
        val carChip = chip(IconView(this, IconView.PLATE), 40, 24, tvCars)
        val top = LinearLayout(this).apply { setPadding(dp(16), dp(40), dp(16), dp(8)) }
        top.addView(balChip, LinearLayout.LayoutParams(0, -2, 1.4f).apply { rightMargin = dp(10) })
        top.addView(carChip, LinearLayout.LayoutParams(0, -2, 1f))

        val content = FrameLayout(this).apply { clipChildren = false }
        spinScreen = buildSpin(); listScreen = buildList(); upgradeScreen = buildUpgrades()
        content.addView(spinScreen); content.addView(listScreen); content.addView(upgradeScreen)

        root.addView(top)
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(buildNav())
        setContentView(root)

        show(0); update()
        startLoops()
        root.post { createHomeShortcut() }
    }

    /** При первом запуске предлагает добавить ярлык игры на главный экран. */
    private fun createHomeShortcut() {
        if (prefs.getBoolean("shortcut_asked", false)) return
        prefs.edit().putBoolean("shortcut_asked", true).apply()
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(this)) return
        val launch = Intent(this, MainActivity::class.java).setAction(Intent.ACTION_MAIN)
        val info = ShortcutInfoCompat.Builder(this, "plates_game")
            .setShortLabel("Автономера")
            .setLongLabel("Автономера")
            .setIcon(IconCompat.createWithResource(this, R.mipmap.ic_launcher))
            .setIntent(launch)
            .build()
        ShortcutManagerCompat.requestPinShortcut(this, info, null)
    }

    private val bonusTicker = object : Runnable {
        override fun run() {
            updateBonusButton()
            bonusBtn.postDelayed(this, 1000)
        }
    }

    override fun onPause() {
        super.onPause()
        loops.forEach { it.pause() }
        bonusBtn.removeCallbacks(bonusTicker)
    }

    override fun onResume() {
        super.onResume()
        loops.forEach { it.resume() }
        bonusBtn.removeCallbacks(bonusTicker)
        bonusTicker.run()
    }

    override fun onDestroy() {
        super.onDestroy()
        loops.forEach { it.cancel() }
        bonusBtn.removeCallbacks(bonusTicker)
    }

    // ───────────────────────── Стили и помощники ─────────────────────────

    private fun gradBg(
        colors: IntArray, radiusDp: Int,
        orientation: GradientDrawable.Orientation = GradientDrawable.Orientation.LEFT_RIGHT,
        strokeDp: Int = 0, strokeColor: Int = 0
    ) = GradientDrawable(orientation, colors).apply {
        cornerRadius = dp(radiusDp).toFloat()
        if (strokeDp > 0) setStroke(dp(strokeDp), strokeColor)
    }

    private fun ripple(content: GradientDrawable, radiusDp: Int): RippleDrawable {
        val mask = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(radiusDp).toFloat() }
        return RippleDrawable(ColorStateList.valueOf(0x55FFFFFF), content, mask)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun View.pressAnim(scale: Float = 0.94f) {
        setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN ->
                    v.animate().scaleX(scale).scaleY(scale).setDuration(90).setInterpolator(DecelerateInterpolator()).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.animate().scaleX(1f).scaleY(1f).setDuration(240).setInterpolator(OvershootInterpolator(3f)).start()
            }
            false
        }
    }

    private fun styledButton(label: String, colors: IntArray, textSp: Float = 16f, radiusDp: Int = 16): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            textSize = textSp
            typeface = bold
            setTextColor(Color.WHITE)
            stateListAnimator = null
            background = ripple(gradBg(colors, radiusDp), radiusDp)
            setPadding(dp(18), dp(10), dp(18), dp(10))
            pressAnim()
        }

    private fun chip(icon: View, iconWdp: Int, iconHdp: Int, tv: TextView): LinearLayout = LinearLayout(this).apply {
        gravity = Gravity.CENTER
        setPadding(dp(12), dp(8), dp(14), dp(8))
        background = gradBg(
            intArrayOf(c("#3A1F73"), c("#2A1659")), 18,
            GradientDrawable.Orientation.TOP_BOTTOM, 1, c("#6B44C0")
        )
        addView(icon, LinearLayout.LayoutParams(dp(iconWdp), dp(iconHdp)).apply { rightMargin = dp(8) })
        addView(tv)
    }

    private fun priceColor(p: Int) = when {
        p >= 10000 -> "#FFC107"; p >= 1000 -> "#E879F9"; p >= 400 -> "#4FC3F7"; else -> "#CDB8F0"
    }.let { Color.parseColor(it) }

    private fun rarityName(p: Int) = when {
        p >= 10000 -> "ЛЕГЕНДАРНЫЙ"; p >= 1000 -> "ЭПИЧЕСКИЙ"; p >= 400 -> "РЕДКИЙ"; else -> "ОБЫЧНЫЙ"
    }

    private fun plateColor(p: Plate) = if (p.gold) Color.parseColor("#FFC107") else priceColor(p.price)

    private fun rarityLabel(p: Plate) = if (p.gold) "★ ЗОЛОТОЙ · ${rarityName(p.price)}" else rarityName(p.price)

    private fun glowDrawable(color: Int, size: Int) = GradientDrawable().apply {
        gradientType = GradientDrawable.RADIAL_GRADIENT
        gradientRadius = size / 2f
        colors = intArrayOf(alpha(color, 170), alpha(color, 0))
    }

    private fun loop(a: ValueAnimator, durationMs: Long, restart: Boolean = false) {
        a.duration = durationMs
        a.repeatCount = ValueAnimator.INFINITE
        a.repeatMode = if (restart) ValueAnimator.RESTART else ValueAnimator.REVERSE
        a.start()
        loops.add(a)
    }

    private fun startLoops() {
        // Лёгкое «парение» номера
        loop(ObjectAnimator.ofFloat(slot, View.TRANSLATION_Y, -dp(5).toFloat(), dp(5).toFloat())
            .apply { interpolator = AccelerateDecelerateInterpolator() }, 1800)
        // «Дыхание» свечения за номером
        loop(ObjectAnimator.ofPropertyValuesHolder(
            glow,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.1f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.5f, 0.56f)
        ), 1400)
        // Пульсирующее кольцо вокруг кнопки ТАП
        loop(ObjectAnimator.ofPropertyValuesHolder(
            tapRing,
            PropertyValuesHolder.ofFloat(View.ALPHA, 0.55f, 0f),
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.08f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.35f)
        ).apply { interpolator = DecelerateInterpolator() }, 1500, restart = true)
    }

    private fun shake(v: View) {
        val d = dp(10).toFloat()
        ObjectAnimator.ofFloat(v, View.TRANSLATION_X, 0f, -d, d, -d * 0.6f, d * 0.6f, 0f)
            .setDuration(420).start()
    }

    // ───────────────────────── Навигация ─────────────────────────

    private fun buildNav(): View {
        val r = dp(22).toFloat()
        val nav = LinearLayout(this).apply {
            setPadding(dp(12), dp(10), dp(12), dp(14))
            background = GradientDrawable().apply {
                setColor(c("#1B0D3F"))
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
        }
        navSpin = navItem("🎰 Крутить") { show(0) }
        navList = navItem("🗂 Коллекция") { show(1) }
        navUpg = navItem("✨ Улучшения") { show(2) }
        nav.addView(navSpin, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(4) })
        nav.addView(navList, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(4); rightMargin = dp(4) })
        nav.addView(navUpg, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(4) })
        return nav
    }

    private fun navItem(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 14f
        typeface = medium
        gravity = Gravity.CENTER
        maxLines = 1
        setPadding(dp(4), dp(14), dp(4), dp(14))
        setOnClickListener { onClick() }
        pressAnim()
    }

    private fun styleNav(sel: Int) {
        listOf(navSpin, navList, navUpg).forEachIndexed { i, t ->
            if (i == sel) {
                t.background = gradBg(intArrayOf(c("#7C3AED"), c("#A855F7")), 16)
                t.setTextColor(Color.WHITE)
            } else {
                t.background = gradBg(intArrayOf(c("#2A1659"), c("#2A1659")), 16)
                t.setTextColor(c("#B9A4E0"))
            }
        }
    }

    private fun show(i: Int) {
        val screens = listOf(spinScreen, listScreen, upgradeScreen)
        val inV = screens[i]
        if (curScreen == -1) {
            screens.forEachIndexed { idx, s -> s.visibility = if (idx == i) View.VISIBLE else View.GONE }
        } else if (curScreen != i) {
            val prev = curScreen
            val outV = screens[prev]
            val dir = if (i > prev) 1 else -1
            screens.forEachIndexed { idx, s ->
                if (idx != i && idx != prev) {
                    s.animate().cancel(); s.visibility = View.GONE; s.alpha = 1f; s.translationX = 0f
                }
            }
            outV.animate().cancel(); inV.animate().cancel()
            outV.animate().alpha(0f).translationX(-dir * dp(40).toFloat()).setDuration(180)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction {
                    if (curScreen != prev) outV.visibility = View.GONE
                    outV.alpha = 1f; outV.translationX = 0f
                }.start()
            inV.alpha = 0f
            inV.translationX = dir * dp(40).toFloat()
            inV.visibility = View.VISIBLE
            inV.animate().alpha(1f).translationX(0f).setDuration(280)
                .setInterpolator(DecelerateInterpolator()).start()
            val navItem = listOf(navSpin, navList, navUpg)[i]
            navItem.scaleX = 0.9f; navItem.scaleY = 0.9f
            navItem.animate().scaleX(1f).scaleY(1f).setDuration(280)
                .setInterpolator(OvershootInterpolator(3f)).start()
        }
        curScreen = i
        styleNav(i)
        if (i == 1) refreshList(true)
        if (i == 2) refreshUpgrade()
    }

    // ───────────────────────── Экран «Крутить» ─────────────────────────

    private fun roll(): Plate {
        val l = { cyr[Random.nextInt(cyr.length)] }
        val num = "${l()}${"%03d".format(Random.nextInt(1, 1000))}${l()}${l()}"
        val gold = Random.nextInt(100) < goldLevel // шанс золота = уровень улучшения в %
        return Plate(num, regions.random(), gold)
    }

    private fun buildSpin(): View {
        val l = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            clipChildren = false
            clipToPadding = false
            setPadding(dp(16), dp(8), dp(16), dp(16))
        }
        plateW = minOf(resources.displayMetrics.widthPixels - dp(40), dp(420))
        plateH = PlateView.heightFor(plateW)

        slot = FrameLayout(this).apply { clipChildren = false }
        val glowSize = plateW + dp(80)
        glow = View(this).apply { alpha = 0f; scaleY = 0.5f }
        placeholder = TextView(this).apply {
            text = "ТАП · $spinCost ₽"
            typeface = bold
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(c("#7B5BC4"))
            background = GradientDrawable().apply {
                setColor(alpha(Color.WHITE, 14))
                cornerRadius = dp(14).toFloat()
                setStroke(dp(2), c("#7B5BC4"), dp(8).toFloat(), dp(6).toFloat())
            }
        }
        tapRing = View(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = dp(14).toFloat()
                setStroke(dp(2), c("#C084FC"))
            }
        }
        slot.addView(glow, FrameLayout.LayoutParams(glowSize, glowSize, Gravity.CENTER))
        slot.addView(tapRing, FrameLayout.LayoutParams(plateW, plateH, Gravity.CENTER))
        slot.addView(placeholder, FrameLayout.LayoutParams(plateW, plateH, Gravity.CENTER))
        // Сам номер — кнопка «Крутить»
        slot.setOnClickListener { spin() }
        slot.pressAnim(0.96f)
        l.addView(slot, LinearLayout.LayoutParams(plateW, plateH))

        tvPrice = TextView(this).apply {
            textSize = 22f
            gravity = Gravity.CENTER
            typeface = bold
            setTextColor(c("#E9D8FF"))
            text = "Выбей свой номер!"
        }
        tvRarity = TextView(this).apply {
            textSize = 12f
            typeface = bold
            letterSpacing = 0.15f
            setPadding(dp(14), dp(4), dp(14), dp(4))
            visibility = View.INVISIBLE
        }
        l.addView(tvPrice, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20) })
        l.addView(tvRarity, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(8) })
        tvHint = TextView(this).apply {
            text = "👆 Тапни по номеру — прокрут стоит $spinCost ₽"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(c("#B9A4E0"))
        }
        l.addView(tvHint, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })

        sellBtn = styledButton("Продать этот номер", intArrayOf(c("#C026D3"), c("#7C3AED"))).apply {
            isEnabled = false
            alpha = 0.45f
            setOnClickListener { sellCurrent() }
        }
        l.addView(sellBtn, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(20); leftMargin = dp(8); rightMargin = dp(8)
        })

        bonusBtn = styledButton("🎁 Получить +$bonusAmount ₽", intArrayOf(c("#F5A524"), c("#E8590C")), 13f, 12).apply {
            minHeight = 0; minimumHeight = 0; minWidth = 0; minimumWidth = 0
            setPadding(dp(16), dp(8), dp(16), dp(8))
            setOnClickListener { claimBonus() }
        }
        l.addView(bonusBtn, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(18) })
        return l
    }

    private fun spin() {
        if (balance < spinCost) {
            shake(slot)
            Toast.makeText(this, "Не хватает денег: прокрут стоит $spinCost ₽", Toast.LENGTH_SHORT).show()
            return
        }
        balance -= spinCost
        val p = roll(); plates.add(p); last = p
        showPlate(p)
        update()
    }

    private fun bonusRemaining(): Long =
        (lastBonus + bonusCooldownMs - System.currentTimeMillis()).coerceAtMost(bonusCooldownMs)

    private fun updateBonusButton() {
        val left = bonusRemaining()
        val ready = left <= 0
        if (ready) {
            bonusBtn.text = "🎁 Получить +$bonusAmount ₽"
        } else {
            val sec = (left + 999) / 1000
            bonusBtn.text = "⏳ Бонус через %02d:%02d".format(sec / 60, sec % 60)
        }
        bonusBtn.isEnabled = ready
        bonusBtn.alpha = if (ready) 1f else 0.5f
        if (ready && bonusWasReady == false) {
            bonusBtn.scaleX = 0.8f; bonusBtn.scaleY = 0.8f
            bonusBtn.animate().scaleX(1f).scaleY(1f).setDuration(400)
                .setInterpolator(OvershootInterpolator(3f)).start()
        }
        bonusWasReady = ready
    }

    private fun claimBonus() {
        if (bonusRemaining() > 0) return
        lastBonus = System.currentTimeMillis()
        prefs.edit().putLong("bonus", lastBonus).apply()
        balance += bonusAmount
        update()
        updateBonusButton()
    }

    private fun setSellEnabled(on: Boolean) {
        sellBtn.isEnabled = on
        sellBtn.animate().alpha(if (on) 1f else 0.45f).setDuration(200).start()
    }

    private fun showPlate(p: Plate) {
        currentView?.let { it.animate().cancel(); slot.removeView(it) }
        placeholder.animate().cancel()
        placeholder.visibility = View.GONE

        val col = plateColor(p)
        glow.background = glowDrawable(col, plateW + dp(80))
        glow.animate().cancel()
        glow.alpha = 0f
        glow.animate().alpha(1f).setDuration(500).start()

        val pv = PlateView(this).apply {
            plate = p
            cameraDistance = 14000f * resources.displayMetrics.density
            alpha = 0f; scaleX = 0.55f; scaleY = 0.55f
            rotationX = -80f; translationY = -dp(60).toFloat()
        }
        slot.addView(pv, FrameLayout.LayoutParams(plateW, plateH, Gravity.CENTER))
        currentView = pv
        pv.animate().alpha(1f).scaleX(1f).scaleY(1f).rotationX(0f).translationY(0f)
            .setDuration(520).setInterpolator(OvershootInterpolator(1.4f)).start()

        // Блик пробегает по номеру
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 900
            startDelay = 350
            addUpdateListener { pv.shine = it.animatedValue as Float }
            doOnEnd { pv.shine = -1f }
            start()
        }

        // Цена «набегает», редкость проявляется
        priceAnim?.cancel()
        tvPrice.setTextColor(col)
        tvPrice.scaleX = 0.85f; tvPrice.scaleY = 0.85f
        tvPrice.animate().scaleX(1f).scaleY(1f).setDuration(400).setInterpolator(OvershootInterpolator(3f)).start()
        priceAnim = ValueAnimator.ofInt(0, p.price).apply {
            duration = 650
            interpolator = DecelerateInterpolator()
            addUpdateListener { tvPrice.text = "Цена: ${it.animatedValue} ₽" }
            start()
        }
        tvRarity.text = rarityLabel(p)
        tvRarity.setTextColor(col)
        tvRarity.background = gradBg(
            intArrayOf(alpha(col, 40), alpha(col, 40)), 12, strokeDp = 1, strokeColor = alpha(col, 160)
        )
        tvRarity.visibility = View.VISIBLE
        tvRarity.alpha = 0f
        tvRarity.animate().alpha(1f).setStartDelay(250).setDuration(300).start()

        setSellEnabled(true)
        if (p.price >= 10000 || p.gold) shake(slot)
    }

    private fun flyAway() {
        val pv = currentView ?: return
        currentView = null
        glow.animate().cancel()
        glow.animate().alpha(0f).setDuration(350).start()
        pv.animate().cancel()
        pv.animate().translationY(-dp(200).toFloat()).rotation(-10f).scaleX(0.7f).scaleY(0.7f).alpha(0f)
            .setDuration(420).setInterpolator(AccelerateInterpolator(1.4f))
            .withEndAction {
                slot.removeView(pv)
                if (currentView == null) {
                    placeholder.visibility = View.VISIBLE
                    placeholder.alpha = 0f
                    placeholder.animate().alpha(1f).setDuration(250).start()
                }
            }.start()
    }

    private fun sellCurrent() {
        val p = last ?: return
        if (!plates.remove(p)) return
        balance += p.price
        last = null
        priceAnim?.cancel()
        tvPrice.setTextColor(c("#E9D8FF"))
        tvPrice.text = "Продано за ${p.price} ₽"
        tvRarity.visibility = View.INVISIBLE
        setSellEnabled(false)
        flyAway()
        update()
    }

    // ───────────────────────── Экран «Коллекция» ─────────────────────────

    private fun themedAdapter(items: Array<out String>) =
        object : ArrayAdapter<String>(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, items.toList()) {
            override fun getView(pos: Int, cv: View?, parent: ViewGroup): View =
                (super.getView(pos, cv, parent) as TextView).apply {
                    setTextColor(Color.WHITE); textSize = 15f
                    setPadding(dp(14), dp(12), dp(34), dp(12))
                }

            override fun getDropDownView(pos: Int, cv: View?, parent: ViewGroup): View =
                (super.getDropDownView(pos, cv, parent) as TextView).apply {
                    setTextColor(Color.WHITE); textSize = 15f
                    setBackgroundColor(Color.TRANSPARENT)
                    setPadding(dp(16), dp(14), dp(16), dp(14))
                }
        }

    private fun spBox(s: Spinner): View = FrameLayout(this).apply {
        background = gradBg(intArrayOf(c("#24134D"), c("#24134D")), 14, strokeDp = 1, strokeColor = c("#5B3BA8"))
        addView(s, FrameLayout.LayoutParams(-1, -2))
        addView(
            TextView(this@MainActivity).apply { text = "▾"; textSize = 18f; setTextColor(c("#B9A4E0")) },
            FrameLayout.LayoutParams(-2, -2, Gravity.END or Gravity.CENTER_VERTICAL).apply { rightMargin = dp(14) }
        )
    }

    private fun buildList(): View {
        val l = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(4), dp(14), 0)
        }
        val sel = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(a: AdapterView<*>?, v: View?, pos: Int, id: Long) = refreshList()
            override fun onNothingSelected(a: AdapterView<*>?) {}
        }
        fun sp(vararg items: String) = Spinner(this).apply {
            adapter = themedAdapter(items)
            onItemSelectedListener = sel
            background = null
            setPopupBackgroundDrawable(gradBg(intArrayOf(c("#2A1659"), c("#2A1659")), 12, strokeDp = 1, strokeColor = c("#6B44C0")))
        }
        fun et(h: String) = EditText(this).apply {
            hint = h
            textSize = 15f
            setSingleLine()
            setTextColor(Color.WHITE)
            setHintTextColor(c("#8F7BC0"))
            background = gradBg(intArrayOf(c("#24134D"), c("#24134D")), 14, strokeDp = 1, strokeColor = c("#5B3BA8"))
            setPadding(dp(14), dp(12), dp(14), dp(12))
            doAfterTextChanged { refreshList() }
        }
        fRegion = et("Регион"); fLetter = et("Буква/цифры")
        fType = sp("Все", "Пара цифр", "Тройка цифр", "Пара букв", "Тройка букв", "Дорогие (от 1000)", "Золотые")
        fSort = sp("Дороже сначала", "Дешевле сначала")
        val row = LinearLayout(this)
        row.addView(fRegion, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(8) })
        row.addView(fLetter, LinearLayout.LayoutParams(0, -2, 1f))
        val sellAll = styledButton("Продать все найденные", intArrayOf(c("#4C1D95"), c("#6D28D9")), 15f, 14).apply {
            setOnClickListener { sellFilteredAnimated() }
        }
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(12)) }
        l.addView(row)
        l.addView(spBox(fType), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        l.addView(spBox(fSort), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        l.addView(sellAll, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        l.addView(
            ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(list) },
            LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(8) }
        )
        return l
    }

    private fun filtered(): List<Plate> {
        val reg = fRegion.text.toString().trim()
        val q = fLetter.text.toString().trim().uppercase().map { c -> lat.indexOf(c).let { if (it >= 0) cyr[it] else c } }.joinToString("")
        val res = plates.filter { p ->
            (reg.isEmpty() || p.region == reg) && (q.isEmpty() || p.num.contains(q)) && when (fType.selectedItemPosition) {
                1 -> p.dRep == 2; 2 -> p.dRep == 3; 3 -> p.lRep == 2; 4 -> p.lRep == 3; 5 -> p.price >= 1000; 6 -> p.gold; else -> true
            }
        }
        return if (fSort.selectedItemPosition == 0) res.sortedByDescending { it.price } else res.sortedBy { it.price }
    }

    private fun refreshList(animate: Boolean = false) {
        list.removeAllViews()
        val f = filtered()
        list.addView(TextView(this).apply {
            text = "Найдено: ${f.size}"
            setTextColor(c("#B9A4E0"))
            textSize = 13f
            setPadding(dp(4), 0, 0, dp(6))
        })
        if (f.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "Здесь пока пусто.\nКрути номера на вкладке «Крутить» 🎰"
                gravity = Gravity.CENTER
                textSize = 15f
                setTextColor(c("#8F7BC0"))
                setPadding(0, dp(40), 0, 0)
            })
            return
        }
        for ((i, p) in f.take(100).withIndex()) {
            val col = plateColor(p)
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(10), dp(10), dp(10))
                background = gradBg(
                    intArrayOf(c("#2C1760"), c("#22114A")), 16,
                    strokeDp = 1, strokeColor = alpha(col, 90)
                )
            }
            row.addView(PlateView(this).apply { plate = p },
                LinearLayout.LayoutParams(dp(140), PlateView.heightFor(dp(140))))
            val info = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), 0, dp(6), 0)
            }
            info.addView(TextView(this).apply {
                text = "${p.price} ₽"; textSize = 16f; typeface = bold; setTextColor(col)
            })
            info.addView(TextView(this).apply {
                text = rarityLabel(p); textSize = 10f; letterSpacing = 0.05f
                setTextColor(alpha(col, 200))
            })
            row.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
            val sell = styledButton("Продать", intArrayOf(c("#C026D3"), c("#7C3AED")), 13f, 12).apply {
                minHeight = 0; minimumHeight = 0; minWidth = 0; minimumWidth = 0
                setPadding(dp(12), dp(8), dp(12), dp(8))
            }
            sell.setOnClickListener {
                sell.isEnabled = false
                removeRow(row) { if (plates.remove(p)) { balance += p.price; update() } }
            }
            row.addView(sell)
            list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })

            if (animate && i < 12) {
                row.alpha = 0f
                row.translationY = dp(24).toFloat()
                row.animate().alpha(1f).translationY(0f).setStartDelay(i * 45L).setDuration(300)
                    .setInterpolator(DecelerateInterpolator()).start()
            }
        }
    }

    private fun removeRow(row: View, after: () -> Unit) {
        row.animate().cancel()
        row.animate().setStartDelay(0).translationX(row.width * 0.6f).alpha(0f).setDuration(220)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                ValueAnimator.ofInt(row.height, 0).apply {
                    duration = 160
                    addUpdateListener {
                        row.layoutParams.height = it.animatedValue as Int
                        row.requestLayout()
                    }
                    doOnEnd { after() }
                    start()
                }
            }.start()
    }

    private fun sellFilteredAnimated() {
        if (filtered().isEmpty()) return
        list.animate().cancel()
        list.animate().alpha(0f).translationY(dp(24).toFloat()).setDuration(200)
            .withEndAction {
                sellFiltered()
                list.translationY = 0f
                list.alpha = 0f
                list.animate().alpha(1f).setDuration(250).start()
            }.start()
    }

    private fun sellFiltered() {
        val f = filtered()
        balance += f.sumOf { it.price }
        plates.removeAll(f.toSet())
        Toast.makeText(this, "Продано: ${f.size}", Toast.LENGTH_SHORT).show()
        update()
    }

    // ───────────────────────── Экран «Улучшения» ─────────────────────────

    private fun upgradeCost(level: Int): Double =
        Math.rint(goldBaseCost * Math.pow(goldCostMul, level.toDouble()))

    private fun fmt(d: Double): String = when {
        d < 1e9 -> Math.round(d).toString().reversed().chunked(3).joinToString(" ").reversed()
        d < 1e12 -> "%.1f млрд".format(d / 1e9)
        d < 1e15 -> "%.1f трлн".format(d / 1e12)
        else -> "%.2e".format(d)
    }

    private fun buildUpgrades(): View {
        val sc = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        val l = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(16))
        }
        sc.addView(l)

        l.addView(TextView(this).apply {
            text = "Улучшения"; textSize = 22f; typeface = bold; setTextColor(Color.WHITE)
        })
        l.addView(TextView(this).apply {
            text = "Вкладывай деньги — выбивай больше золота"
            textSize = 13f; setTextColor(c("#B9A4E0"))
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2); bottomMargin = dp(12) })

        upgCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = gradBg(
                intArrayOf(c("#3A1F73"), c("#22114A")), 22,
                GradientDrawable.Orientation.TOP_BOTTOM, 2, alpha(c("#FFC107"), 150)
            )
        } as LinearLayout
        val card = upgCard as LinearLayout

        // Заголовок: звезда + название + уровень
        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(IconView(this, IconView.STAR), LinearLayout.LayoutParams(dp(30), dp(30)).apply { rightMargin = dp(10) })
        head.addView(TextView(this).apply {
            text = "Шанс золота"; textSize = 20f; typeface = bold; setTextColor(c("#FFD54F"))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        tvGoldLevel = TextView(this).apply {
            textSize = 12f; typeface = bold; setTextColor(c("#FFD54F"))
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = gradBg(intArrayOf(alpha(c("#FFC107"), 40), alpha(c("#FFC107"), 40)), 12, strokeDp = 1, strokeColor = alpha(c("#FFC107"), 150))
        }
        head.addView(tvGoldLevel)
        card.addView(head)

        // Пример золотого номера
        val sw = minOf(resources.displayMetrics.widthPixels - dp(28 + 32), dp(320))
        card.addView(PlateView(this).apply { plate = Plate("О777ОО", "77", true) },
            LinearLayout.LayoutParams(sw, PlateView.heightFor(sw)).apply { topMargin = dp(14); gravity = Gravity.CENTER_HORIZONTAL })

        card.addView(TextView(this).apply {
            text = "Каждый уровень даёт +1% шанса, что выпавший номер станет золотым. Золотой номер стоит в 1.5 раза дороже."
            textSize = 13f; gravity = Gravity.CENTER; setTextColor(c("#CDB8F0"))
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })

        tvGoldChance = TextView(this).apply {
            textSize = 40f; typeface = bold; gravity = Gravity.CENTER; setTextColor(c("#FFC107"))
        }
        card.addView(tvGoldChance, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        goldBar = LinearLayout(this).apply {
            background = gradBg(intArrayOf(c("#1A0C3D"), c("#1A0C3D")), 6, strokeDp = 1, strokeColor = c("#4B2E91"))
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }
        goldFill = View(this).apply { background = gradBg(intArrayOf(c("#FFE27A"), c("#F5A524")), 5) }
        goldRest = View(this)
        goldBar.addView(goldFill, LinearLayout.LayoutParams(0, -1, 0f))
        goldBar.addView(goldRest, LinearLayout.LayoutParams(0, -1, 100f))
        card.addView(goldBar, LinearLayout.LayoutParams(-1, dp(12)).apply { topMargin = dp(4) })

        tvGoldNext = TextView(this).apply { textSize = 13f; gravity = Gravity.CENTER; setTextColor(c("#B9A4E0")) }
        card.addView(tvGoldNext, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        upgBtn = styledButton("", intArrayOf(c("#FFC107"), c("#F57C00")), 17f, 16).apply {
            setTextColor(c("#3B1F00"))
            setOnClickListener { buyGold() }
        }
        card.addView(upgBtn, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })

        l.addView(card, LinearLayout.LayoutParams(-1, -2))
        return sc
    }

    private fun refreshUpgrade() {
        val maxed = goldLevel >= maxGold
        tvGoldLevel.text = "Ур. $goldLevel / $maxGold"
        tvGoldChance.text = "$goldLevel%"
        tvGoldNext.text = if (maxed) "Достигнут максимум — все номера золотые!" else "Следующий уровень: ${goldLevel + 1}%"
        goldFill.layoutParams = LinearLayout.LayoutParams(0, -1, goldLevel.toFloat())
        goldRest.layoutParams = LinearLayout.LayoutParams(0, -1, (maxGold - goldLevel).toFloat())
        goldBar.requestLayout()
        if (maxed) {
            upgBtn.text = "Максимальный уровень"
            upgBtn.isEnabled = false
            upgBtn.alpha = 0.45f
        } else {
            val cost = upgradeCost(goldLevel)
            upgBtn.text = "Улучшить  ·  ${fmt(cost)} ₽"
            upgBtn.isEnabled = true
            upgBtn.alpha = if (cost <= balance) 1f else 0.6f
        }
    }

    private fun buyGold() {
        if (goldLevel >= maxGold) return
        val cost = upgradeCost(goldLevel)
        if (cost > balance) {
            shake(upgCard)
            Toast.makeText(this, "Не хватает денег: нужно ${fmt(cost)} ₽", Toast.LENGTH_SHORT).show()
            return
        }
        balance -= cost.toInt()
        goldLevel++
        prefs.edit().putInt("gold", goldLevel).apply()
        update()
        tvGoldChance.scaleX = 0.7f; tvGoldChance.scaleY = 0.7f
        tvGoldChance.animate().scaleX(1f).scaleY(1f).setDuration(400)
            .setInterpolator(OvershootInterpolator(3f)).start()
    }

    // ───────────────────────── Баланс и сохранение ─────────────────────────

    private fun animateBalance() {
        balAnim?.cancel()
        balAnim = ValueAnimator.ofInt(shownBal, balance).apply {
            duration = 600
            interpolator = DecelerateInterpolator()
            addUpdateListener { shownBal = it.animatedValue as Int; tvBal.text = "$shownBal" }
            start()
        }
        balChip.animate().cancel()
        balChip.animate().scaleX(1.07f).scaleY(1.07f).setDuration(120)
            .withEndAction {
                balChip.animate().scaleX(1f).scaleY(1f).setDuration(260)
                    .setInterpolator(OvershootInterpolator(3f)).start()
            }.start()
    }

    private fun update() {
        tvCars.text = "${plates.size}"
        if (shownBal != balance) animateBalance() else tvBal.text = "$balance"
        prefs.edit().putInt("bal", balance).putString("plates", plates.joinToString(";") { it.num + "|" + it.region + (if (it.gold) "|g" else "") }).apply()
        if (listScreen.visibility == View.VISIBLE) refreshList()
        refreshUpgrade()
    }
}
