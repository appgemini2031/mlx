package com.tajasb.notif

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.R
import com.tajasb.livecomment.BannerIconMode
import com.tajasb.livecomment.BannerMode
import com.tajasb.livecomment.LiveCommentItem
import com.tajasb.livecomment.LiveCommentManager

class BuyerNotificationOverlayView(
    context: Context,
    private val windowManager: WindowManager,
    private val layoutParams: WindowManager.LayoutParams
) : FrameLayout(context) {

    private val manager = BuyerNotificationManager.getInstance(context)
    private val liveCommentManager = LiveCommentManager.getInstance(context)
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()

    private val pillCard: LinearLayout
    private val iconView: ImageView
    private val emojiIconView: TextView
    private val textView: TextView
    private val dragIndicatorText: TextView
    private val btnApplyLock: TextView

    private val interTypeface by lazy {
        try {
            androidx.core.content.res.ResourcesCompat.getFont(context, R.font.inter)
        } catch (_: Exception) {
            null
        } ?: Typeface.create("sans-serif", Typeface.BOLD)
    }

    private val interSemiBold by lazy {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            Typeface.create(interTypeface, 600, false)
        } else {
            Typeface.create(interTypeface, Typeface.BOLD)
        }
    }

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private var touchDownTime = 0L

    private var hideRunnable: Runnable? = null

    init {
        // Root container setup
        layoutParams.gravity = Gravity.TOP or Gravity.START
        layoutParams.x = manager.posX.value
        layoutParams.y = manager.posY.value

        val padH = manager.capsulePaddingH.value
        val padV = manager.capsulePaddingV.value
        val padTopBottom = dp(padV).coerceAtLeast(0)
        val negativeCompactionPx = if (padV < 0) (dp(padV) / 2).coerceAtLeast(dp(-14)) else 0

        pillCard = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(padH - 2).coerceAtLeast(dp(2)), padTopBottom, dp(padH), padTopBottom)
        }

        // 1. Drag Indicator (Visible only when unlocked)
        dragIndicatorText = TextView(context).apply {
            text = "⋮⋮ "
            setTextColor(Color.parseColor("#00E5FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setTypeface(null, Typeface.BOLD)
            visibility = if (manager.isPositionLocked.value) View.GONE else View.VISIBLE
        }
        pillCard.addView(dragIndicatorText)

        // 2. Vector Icon (Pin / Shopping Bag)
        iconView = ImageView(context).apply {
            val d = ContextCompat.getDrawable(context, R.drawable.ic_shopping_bag_filled)
            setImageDrawable(d)
            setColorFilter(Color.parseColor("#FFFFFF"))
            val iconPx = dp(manager.cartIconSizeDp.value.toInt().coerceAtLeast(6))
            layoutParams = LinearLayout.LayoutParams(iconPx, iconPx).apply {
                setMargins(0, 0, dp(4), 0)
            }
        }
        pillCard.addView(iconView)

        // 3. Emoji Icon (Prayer hands 🤲)
        emojiIconView = TextView(context).apply {
            text = "🤲 "
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            visibility = View.GONE
        }
        pillCard.addView(emojiIconView)

        // 4. Content Text (Inter Semi Bold #FFFFFF, No Text Shadow)
        textView = TextView(context).apply {
            setTextColor(Color.parseColor("#FFFFFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, manager.fontSizeSp.value)
            typeface = interSemiBold
            includeFontPadding = false
            setShadowLayer(0f, 0f, 0f, 0)
        }
        pillCard.addView(textView)

        // 5. Apply / Lock Button (Visible only when unlocked so user can quickly apply and hide)
        btnApplyLock = TextView(context).apply {
            text = " ✓ Simpan "
            setTextColor(Color.BLACK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            setTypeface(null, Typeface.BOLD)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#10B981"))
            }
            setPadding(dp(4), dp(2), dp(4), dp(2))
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(6)
            }
            layoutParams = lp
            visibility = if (manager.isPositionLocked.value) View.GONE else View.VISIBLE
            setOnClickListener {
                val wmParams = this@BuyerNotificationOverlayView.layoutParams
                manager.savePosition(wmParams.x, wmParams.y)
                manager.setPositionLocked(true)
            }
        }
        pillCard.addView(btnApplyLock)

        addView(pillCard)

        // Register custom precision capsule change listener
        manager.onCapsuleStyleChanged = {
            mainHandler.post {
                updateCapsulePaddingAndCorner()
            }
        }

        // Initial styling
        updateIcon()
        updateCapsulePaddingAndCorner()
        updatePillBackground(manager.isPositionLocked.value)

        // Initial sample text
        if (liveCommentManager.bannerMode.value == BannerMode.LIVE_DOA_COMMENT) {
            setDoaText("Penonton", "Semoga live hari ini berkah dan lancar", false)
        } else {
            setBuyerText(manager.currentBuyerName.value, manager.actionText.value)
        }

        setupTouchListener()

        visibility = if (!manager.isPositionLocked.value) View.VISIBLE else View.GONE
        alpha = if (!manager.isPositionLocked.value) 0.95f else 0f
    }

    fun updateIcon() {
        val mode = liveCommentManager.iconMode.value
        val bannerMode = liveCommentManager.bannerMode.value

        when (mode) {
            BannerIconMode.NONE -> {
                iconView.visibility = View.GONE
                emojiIconView.visibility = View.GONE
            }
            BannerIconMode.PIN -> {
                iconView.visibility = View.VISIBLE
                iconView.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_pin_filled))
                emojiIconView.visibility = View.GONE
            }
            BannerIconMode.PRAYER -> {
                iconView.visibility = View.GONE
                emojiIconView.visibility = View.VISIBLE
                emojiIconView.text = "🤲 "
            }
            BannerIconMode.SHOPPING -> {
                iconView.visibility = View.VISIBLE
                iconView.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_shopping_bag_filled))
                iconView.setColorFilter(Color.WHITE)
                emojiIconView.visibility = View.GONE
            }
        }
    }

    fun updatePosition(x: Int, y: Int) {
        layoutParams.x = x
        layoutParams.y = y
        try {
            windowManager.updateViewLayout(this, layoutParams)
        } catch (_: Throwable) {}
    }

    fun updateLockState(isLocked: Boolean) {
        dragIndicatorText.visibility = if (isLocked) View.GONE else View.VISIBLE
        btnApplyLock.visibility = if (isLocked) View.GONE else View.VISIBLE
        updatePillBackground(isLocked)

        if (!isLocked) {
            visibility = View.VISIBLE
            alpha = 1.0f
            translationX = 0f
            hideRunnable?.let { mainHandler.removeCallbacks(it) }
        } else {
            val isVisible = if (liveCommentManager.bannerMode.value == BannerMode.LIVE_DOA_COMMENT) {
                liveCommentManager.currentDisplayedItem.value != null
            } else {
                manager.isNotifVisible.value
            }
            if (!isVisible) {
                visibility = View.GONE
                alpha = 0f
            }
        }
    }

    fun updateCapsulePaddingAndCorner() {
        val padH = manager.capsulePaddingH.value
        val padV = manager.capsulePaddingV.value
        val padTopBottom = dp(padV).coerceAtLeast(0)
        val negativeCompactionPx = if (padV < 0) (dp(padV) / 2).coerceAtLeast(dp(-14)) else 0

        pillCard.setPadding(dp(padH - 2).coerceAtLeast(dp(2)), padTopBottom, dp(padH), padTopBottom)
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, manager.fontSizeSp.value)
        val iconPx = dp(manager.cartIconSizeDp.value.toInt().coerceAtLeast(6))
        (iconView.layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
            lp.width = iconPx
            lp.height = iconPx
            lp.topMargin = negativeCompactionPx
            lp.bottomMargin = negativeCompactionPx
            iconView.layoutParams = lp
        }
        (textView.layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
            lp.topMargin = negativeCompactionPx
            lp.bottomMargin = negativeCompactionPx
            textView.layoutParams = lp
        }
        (emojiIconView.layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
            lp.topMargin = negativeCompactionPx
            lp.bottomMargin = negativeCompactionPx
            emojiIconView.layoutParams = lp
        }
        (dragIndicatorText.layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
            lp.topMargin = negativeCompactionPx
            lp.bottomMargin = negativeCompactionPx
            dragIndicatorText.layoutParams = lp
        }
        (btnApplyLock.layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
            lp.topMargin = negativeCompactionPx
            lp.bottomMargin = negativeCompactionPx
            btnApplyLock.layoutParams = lp
        }
        updatePillBackground(manager.isPositionLocked.value)
    }

    fun updatePillBackground(isLocked: Boolean, isGiftHighlight: Boolean = false) {
        pillCard.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(manager.capsuleCornerRadius.value).toFloat()

            val isDoaMode = liveCommentManager.bannerMode.value == BannerMode.LIVE_DOA_COMMENT
            if (isGiftHighlight) {
                // Vibrant gold-orange badge for live gifts / roses
                setColor(Color.parseColor("#EA580C"))
                setStroke(dp(1), Color.parseColor("#FDE047"))
            } else if (isDoaMode) {
                // Sleek translucent badge with soft outline for prayers
                setColor(Color.parseColor("#D90F172A"))
                if (!isLocked) {
                    setStroke(dp(1.5f.toInt()), Color.parseColor("#00E5FF"))
                } else {
                    setStroke(dp(0.75f.toInt()), Color.parseColor("#40FFFFFF"))
                }
            } else {
                // Notif Jualan: Custom Background Color with Alpha & Outline Setting
                val baseCol = try {
                    Color.parseColor(manager.capsuleBgColorHex.value)
                } catch (_: Throwable) {
                    Color.parseColor("#D97724")
                }
                val alphaInt = (manager.capsuleAlpha.value * 255).toInt().coerceIn(0, 255)
                val finalBgColor = Color.argb(
                    alphaInt,
                    Color.red(baseCol),
                    Color.green(baseCol),
                    Color.blue(baseCol)
                )
                setColor(finalBgColor)

                val outlineW = manager.capsuleOutlineWidth.value
                val outlineCol = try {
                    Color.parseColor(manager.capsuleOutlineColor.value)
                } catch (_: Throwable) {
                    Color.WHITE
                }
                if (outlineW > 0) {
                    setStroke(dp(outlineW), outlineCol)
                } else {
                    setStroke(0, Color.TRANSPARENT)
                }
            }
        }
    }

    fun updateScale(scale: Float) {
        val sx = if (manager.isMirrored.value) -scale else scale
        pillCard.scaleX = sx
        pillCard.scaleY = scale
    }

    fun updateMirrorState(isMirrored: Boolean) {
        val scale = manager.scale.value
        val sx = if (isMirrored) -scale else scale
        pillCard.scaleX = sx
        pillCard.scaleY = scale
    }

    fun setBuyerText(buyerName: String, actionText: String) {
        val fullText = if (buyerName.isNotBlank()) "$buyerName $actionText" else actionText
        val spannable = SpannableStringBuilder(fullText)
        val nameLen = if (buyerName.isNotBlank()) buyerName.length.coerceAtMost(fullText.length) else 0

        // Inter semi bold #FFFFFF across entire notification, no text shadow
        if (nameLen > 0) {
            spannable.setSpan(
                StyleSpan(Typeface.BOLD),
                0,
                nameLen,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        spannable.setSpan(
            ForegroundColorSpan(Color.parseColor("#FFFFFF")),
            0,
            fullText.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        textView.typeface = interSemiBold
        textView.setTextColor(Color.parseColor("#FFFFFF"))
        textView.setShadowLayer(0f, 0f, 0f, 0)
        textView.text = spannable
        updateScale(manager.scale.value)
    }

    fun setDoaText(senderName: String, content: String, isGift: Boolean) {
        val prefix = if (isGift) "🎁 " else ""
        val fullText = if (isGift) "$prefix$senderName $content" else "$senderName: $content"
        val spannable = SpannableStringBuilder(fullText)

        val nameStart = if (isGift) 2 else 0
        val nameEnd = nameStart + senderName.length

        // Bold sender name
        if (nameEnd <= fullText.length) {
            spannable.setSpan(
                StyleSpan(Typeface.BOLD),
                nameStart,
                nameEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            val nameColor = if (isGift) Color.parseColor("#FDE047") else Color.parseColor("#38BDF8")
            spannable.setSpan(
                ForegroundColorSpan(nameColor),
                nameStart,
                nameEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        textView.text = spannable
    }

    fun showNotification(buyerName: String, actionText: String, durationMs: Long) {
        mainHandler.post {
            hideRunnable?.let { mainHandler.removeCallbacks(it) }

            updateIcon()
            updatePillBackground(manager.isPositionLocked.value, false)
            setBuyerText(buyerName, actionText)
            playEntryAnimation(durationMs)
        }
    }

    fun showLiveComment(item: LiveCommentItem, durationMs: Long) {
        mainHandler.post {
            hideRunnable?.let { mainHandler.removeCallbacks(it) }

            updateIcon()
            updatePillBackground(manager.isPositionLocked.value, item.isGift)
            setDoaText(item.senderName, item.content, item.isGift)
            playEntryAnimation(durationMs)
        }
    }

    private fun playEntryAnimation(durationMs: Long) {
        visibility = View.VISIBLE
        val dir = manager.entryDirection.value.uppercase()
        when (dir) {
            "LEFT" -> {
                translationX = -dp(280).toFloat()
                translationY = 0f
            }
            "RIGHT" -> {
                translationX = dp(280).toFloat()
                translationY = 0f
            }
            "TOP" -> {
                translationX = 0f
                translationY = -dp(180).toFloat()
            }
            "BOTTOM" -> {
                translationX = 0f
                translationY = dp(180).toFloat()
            }
            else -> { // "FADE"
                translationX = 0f
                translationY = 0f
            }
        }
        alpha = 0f

        animate()
            .translationX(0f)
            .translationY(0f)
            .alpha(1.0f)
            .setDuration(360L)
            .setInterpolator(DecelerateInterpolator(1.5f))
            .setListener(null)
            .start()

        val r = Runnable {
            hideNotificationSmooth()
        }
        hideRunnable = r
        mainHandler.postDelayed(r, durationMs)
    }

    fun hideNotificationSmooth() {
        mainHandler.post {
            if (!manager.isPositionLocked.value) {
                return@post
            }

            val dir = manager.entryDirection.value.uppercase()
            val exitAnim = animate()
                .alpha(0f)
                .setDuration(350L)
                .setInterpolator(DecelerateInterpolator(1.2f))
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        visibility = View.GONE
                        alpha = 0f
                        translationX = 0f
                        translationY = 0f
                        animate().setListener(null)
                    }
                })

            when (dir) {
                "LEFT" -> exitAnim.translationX(-dp(120).toFloat())
                "RIGHT" -> exitAnim.translationX(dp(120).toFloat())
                "TOP" -> exitAnim.translationY(-dp(80).toFloat())
                "BOTTOM" -> exitAnim.translationY(dp(80).toFloat())
                else -> {}
            }
            exitAnim.start()
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // Intercept all touches when in unlocked (drag) mode so child views don't steal the gesture
        if (!manager.isPositionLocked.value) {
            return true
        }
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (manager.isPositionLocked.value) {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchDownTime = System.currentTimeMillis()
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (System.currentTimeMillis() - touchDownTime < 320L) {
                        if (liveCommentManager.bannerMode.value == BannerMode.LIVE_DOA_COMMENT) {
                            liveCommentManager.skipToNext()
                        }
                    }
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = layoutParams.x
                initialY = layoutParams.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - initialTouchX).toInt()
                val dy = (event.rawY - initialTouchY).toInt()

                if (Math.abs(dx) > dp(1) || Math.abs(dy) > dp(1)) {
                    isDragging = true
                }

                layoutParams.x = initialX + dx
                layoutParams.y = initialY + dy

                try {
                    windowManager.updateViewLayout(this, layoutParams)
                } catch (_: Throwable) {}
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    manager.savePosition(layoutParams.x, layoutParams.y)
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun setupTouchListener() {
        isClickable = true
        isFocusable = false

        pillCard.isClickable = false
        pillCard.isFocusable = false
        textView.isClickable = false
        textView.isFocusable = false
        dragIndicatorText.isClickable = false
        dragIndicatorText.isFocusable = false
    }
}
