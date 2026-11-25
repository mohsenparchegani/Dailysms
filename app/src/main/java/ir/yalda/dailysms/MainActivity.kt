package ir.yalda.dailysms

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.telephony.SmsManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar

/**
 * برنامه ارسال پیامک روزانه املاک یلدا
 * - طراحی مدرن، عمق‌دار و سه‌بعدی (3D Card & Floating Elevation)
 * - تقسیم هوشمند اپراتوری پیامک‌ها (همراه اول / ایرانسل / رایتل) با بالانس ۵۰/۵۰ جهت جلوگیری از مسدودی
 * - بانک ۱۴۶۰ پیامک آماده در ۴ دسته‌بندی با انتخاب ۱ لمسی
 * - نمایش تاریخ ثابت شمسی امروز
 */
class MainActivity : Activity() {

    data class Contact(val name: String, val phone: String, val md: Int)
    enum class Operator { MCI, IRANCELL, RIGHTEL, OTHER }

    data class SimSlot(val subId: Int, val slotIndex: Int, val name: String, val op: Operator)
    data class SendTask(val phone: String, val subId: Int, val simLabel: String, val note: String)

    private val ui = Handler(Looper.getMainLooper())
    private lateinit var prefs: SharedPreferences

    private var contacts: List<Contact> = emptyList()
    private var sending = false

    private lateinit var todayBadgeText: TextView
    private lateinit var targetDateView: TextView
    private lateinit var totalCountBadge: TextView
    private lateinit var pendingCountBadge: TextView
    private lateinit var simSummaryText: TextView
    private lateinit var msgEdit: EditText
    private lateinit var charCounterText: TextView
    private lateinit var sendBtn: Button
    private lateinit var resetMsgBtn: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var totalContactsReadView: TextView

    private val optionViews = ArrayList<LinearLayout>()
    private val optionRadioViews = ArrayList<TextView>()
    private val optionTextViews = ArrayList<TextView>()
    private var selectedOptionIndex = 0
    private var currentDailyOptions = listOf<String>()

    private var currentMonth: Int = 1
    private var currentDay: Int = 1
    private var todayKey: String = ""

    private val requiredPerms = arrayOf(
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_PHONE_STATE
    )

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    /**
     * ایجاد Drawable با گوشه‌های گرد و استروک دلخواه
     */
    private fun roundRect(bgColor: Int, radiusDp: Int, strokeWidthDp: Int = 0, strokeColor: Int = 0): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(bgColor)
            if (strokeWidthDp > 0) {
                setStroke(dp(strokeWidthDp), strokeColor)
            }
        }
    }

    /**
     * ایجاد کارت سه‌بعدی و شناور (Elevated 3D Card) با عمق و سایه طبیعی
     */
    private fun create3DCard(
        bgColor: Int = Color.WHITE,
        radiusDp: Int = 16,
        elevationDp: Int = 6,
        strokeWidthDp: Int = 1,
        strokeColor: Int = Color.parseColor("#E2E8F0")
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundRect(bgColor, radiusDp, strokeWidthDp, strokeColor)
            elevation = dp(elevationDp).toFloat()
            translationZ = dp(elevationDp / 3).toFloat()
            clipToOutline = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("dailysms", Context.MODE_PRIVATE)

        // پس‌زمینه کلی ملایم و مدرن Slate جهت برجسته‌تر دیده شدن کارت‌های سه‌بعدی سفید
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#F1F5F9"))
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(16), dp(18), dp(16), dp(32))
        }

        // --- ۱. هدر شکیل سه‌بعدی با گرادیان سرمه‌ای-آبی عمیق ---
        val headerCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#1D4ED8"), Color.parseColor("#2563EB"), Color.parseColor("#3B82F6"))
            ).apply { cornerRadius = dp(20).toFloat() }
            setPadding(dp(18), dp(18), dp(18), dp(18))
            elevation = dp(10).toFloat()
            translationZ = dp(3).toFloat()
            clipToOutline = true
        }

        val headerTopRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            layoutParams = lp
        }

        val appTitle = TextView(this).apply {
            text = "✨ پیامک هوشمند املاک یلدا"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = lp
        }
        headerTopRow.addView(appTitle)

        val versionBadge = TextView(this).apply {
            text = "نسخه ۱.۵ سه‌بعدی"
            textSize = 11f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            background = roundRect(Color.parseColor("#35FFFFFF"), 10, 1, Color.parseColor("#70FFFFFF"))
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        headerTopRow.addView(versionBadge)
        headerCard.addView(headerTopRow)

        todayBadgeText = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#DBEAFE"))
            setPadding(0, dp(6), 0, 0)
        }
        headerCard.addView(todayBadgeText)
        root.addView(headerCard)

        // --- ۲. کارت سه‌بعدی تاریخ ارسال امروز (ثابت و غیرقابل تغییر) ---
        val dateCard = create3DCard(Color.WHITE, 16, 5).apply {
            setPadding(dp(16), dp(14), dp(16), dp(14))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(14)
            layoutParams = lp
        }

        val dateLabel = TextView(this).apply {
            text = "📅 تاریخ ارسال امروز (هماهنگ با تقویم ملی و مناسبت‌ها)"
            textSize = 13f
            setTextColor(Color.parseColor("#64748B"))
            setTypeface(null, Typeface.BOLD)
        }
        dateCard.addView(dateLabel)

        targetDateView = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.parseColor("#0F172A"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(4), 0, 0)
        }
        dateCard.addView(targetDateView)
        root.addView(dateCard)

        // --- ۳. ردیف آمار سه‌بعدی مخاطبین (دو کپسول برجسته مدرن) ---
        val statsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(12)
            layoutParams = lp
        }

        totalCountBadge = TextView(this).apply {
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1E40AF"))
            background = roundRect(Color.parseColor("#EFF6FF"), 14, 1, Color.parseColor("#BFDBFE"))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            gravity = Gravity.CENTER
            elevation = dp(4).toFloat()
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lp.marginEnd = dp(6)
            layoutParams = lp
            text = "👥 مخاطبین امروز: ۰ نفر"
        }
        statsRow.addView(totalCountBadge)

        pendingCountBadge = TextView(this).apply {
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#065F46"))
            background = roundRect(Color.parseColor("#ECFDF5"), 14, 1, Color.parseColor("#A7F3D0"))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            gravity = Gravity.CENTER
            elevation = dp(4).toFloat()
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lp.marginStart = dp(6)
            layoutParams = lp
            text = "⏳ مانده برای ارسال: ۰ نفر"
        }
        statsRow.addView(pendingCountBadge)
        root.addView(statsRow)

        // --- ۴. کارت سه‌بعدی وضعیت سیم‌کارت‌ها و تقسیم هوشمند اپراتوری ---
        val simCard = create3DCard(Color.WHITE, 16, 5).apply {
            setPadding(dp(16), dp(14), dp(16), dp(14))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(14)
            layoutParams = lp
        }

        val simHeader = TextView(this).apply {
            text = "📶 تقسیم هوشمند اپراتوری سیم‌کارت‌ها (ضد مسدودی)"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1E293B"))
        }
        simCard.addView(simHeader)

        simSummaryText = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#475569"))
            setPadding(0, dp(6), 0, 0)
            text = "در حال شناسایی اپراتور سیم‌کارت‌های فعال..."
        }
        simCard.addView(simSummaryText)
        root.addView(simCard)

        // --- ۵. کارت سه‌بعدی ۴ گزینه متن پیشنهادی روز (کاشی‌های لمسی برجسته) ---
        val optionsCard = create3DCard(Color.WHITE, 18, 6).apply {
            setPadding(dp(16), dp(16), dp(16), dp(16))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(14)
            layoutParams = lp
        }

        val optionsHeader = TextView(this).apply {
            text = "🎯 انتخاب متن روز از بین ۴ گزینه (یکی را لمس کنید):"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1E293B"))
            setPadding(0, 0, 0, dp(6))
        }
        optionsCard.addView(optionsHeader)

        val optionTitles = listOf(
            "📅 گزینه ۱: مناسبت و تقویم (شمسی / قمری / میلادی)",
            "🏢 گزینه ۲: خرید و فروش ملک (۲۰ سال سابقه و اکازیون)",
            "⚖️ گزینه ۳: کد رهگیری رسمی و نظارت وکیل دادگستری",
            "🔑 گزینه ۴: رهن، اجاره، سرمایه‌گذاری و مشاوره تخصصی"
        )

        for (i in 0 until 4) {
            val optItem = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = roundRect(Color.parseColor("#F8FAFC"), 12, 1, Color.parseColor("#E2E8F0"))
                setPadding(dp(14), dp(12), dp(14), dp(12))
                elevation = dp(3).toFloat()
                clipToOutline = true
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.topMargin = dp(8)
                layoutParams = lp
            }

            val topRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                layoutParams = lp
            }

            val titleView = TextView(this).apply {
                text = optionTitles[i]
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#2563EB"))
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                layoutParams = lp
            }
            topRow.addView(titleView)

            val radioView = TextView(this).apply {
                text = if (i == 0) "✓ انتخابی" else "انتخاب"
                textSize = 11f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (i == 0) Color.parseColor("#1D4ED8") else Color.parseColor("#64748B"))
                background = roundRect(
                    if (i == 0) Color.parseColor("#DBEAFE") else Color.parseColor("#E2E8F0"),
                    8
                )
                setPadding(dp(8), dp(3), dp(8), dp(3))
            }
            topRow.addView(radioView)
            optItem.addView(topRow)

            val bodyView = TextView(this).apply {
                textSize = 13.5f
                setTextColor(Color.parseColor("#334155"))
                setPadding(0, dp(6), 0, 0)
                text = "در حال بارگذاری..."
            }
            optItem.addView(bodyView)

            val optIndex = i
            optItem.setOnClickListener {
                selectOption(optIndex)
            }

            optionViews.add(optItem)
            optionRadioViews.add(radioView)
            optionTextViews.add(bodyView)
            optionsCard.addView(optItem)
        }

        root.addView(optionsCard)

        // --- ۶. کادر سه‌بعدی متن پیامک (نمایش متن روز + قابلیت ویرایش) ---
        val msgCard = create3DCard(Color.WHITE, 18, 6).apply {
            setPadding(dp(16), dp(16), dp(16), dp(16))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(14)
            layoutParams = lp
        }

        val msgHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            layoutParams = lp
        }

        val msgLabel = TextView(this).apply {
            text = "✉️ متن نهایی پیامک امروز (قابل ویرایش):"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1E293B"))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = lp
        }
        msgHeaderRow.addView(msgLabel)

        resetMsgBtn = Button(this).apply {
            text = "🔄 بازنشانی متن"
            textSize = 11f
            setTextColor(Color.parseColor("#1D4ED8"))
            background = roundRect(Color.parseColor("#EFF6FF"), 10, 1, Color.parseColor("#BFDBFE"))
            elevation = dp(2).toFloat()
            setPadding(dp(10), dp(4), dp(10), dp(4))
            setOnClickListener {
                val defaultOpt = if (selectedOptionIndex in currentDailyOptions.indices) {
                    currentDailyOptions[selectedOptionIndex]
                } else {
                    DailySmsBank.getMessage(currentMonth, currentDay)
                }
                msgEdit.setText(defaultOpt)
                prefs.edit().putString("custom_msg_$todayKey", defaultOpt).apply()
                Toast.makeText(this@MainActivity, "متن گزینه انتخابی بازنشانی شد", Toast.LENGTH_SHORT).show()
            }
        }
        msgHeaderRow.addView(resetMsgBtn)
        msgCard.addView(msgHeaderRow)

        msgEdit = EditText(this).apply {
            minLines = 4
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            background = roundRect(Color.parseColor("#F8FAFC"), 12, 1, Color.parseColor("#CBD5E1"))
            setPadding(dp(14), dp(14), dp(14), dp(14))
            hint = "متن پیامک امروز..."
            textSize = 15f
            setTextColor(Color.parseColor("#0F172A"))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(10)
            layoutParams = lp
        }

        charCounterText = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#64748B"))
            gravity = Gravity.START
            setPadding(0, dp(6), 0, 0)
            text = "تعداد کاراکتر: ۰ / ۷۰ (۱ پیامک)"
        }

        msgEdit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val str = s?.toString() ?: ""
                prefs.edit().putString("custom_msg_$todayKey", str).apply()
                val len = str.length
                val parts = if (len <= 70) 1 else ((len - 70 + 66) / 67 + 1)
                charCounterText.text = "تعداد کاراکتر: $len / ۷۰  ($parts پیامک فارسی)"
                if (len > 70) {
                    charCounterText.setTextColor(Color.parseColor("#DC2626"))
                } else {
                    charCounterText.setTextColor(Color.parseColor("#64748B"))
                }
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        msgCard.addView(msgEdit)
        msgCard.addView(charCounterText)
        root.addView(msgCard)

        // --- ۷. دکمه سه‌بعدی و مدرن ارسال هوشمند پیامک‌ها ---
        sendBtn = Button(this).apply {
            text = "🚀 ارسال هوشمند پیامک‌های امروز"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#2563EB"), Color.parseColor("#1D4ED8"))
            ).apply { cornerRadius = dp(16).toFloat() }
            elevation = dp(10).toFloat()
            translationZ = dp(4).toFloat()
            setOnClickListener { onSendClicked() }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56))
            lp.topMargin = dp(18)
            layoutParams = lp
        }
        root.addView(sendBtn)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            visibility = View.GONE
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(10))
            lp.topMargin = dp(14)
            layoutParams = lp
        }
        root.addView(progressBar)

        statusText = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#475569"))
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(4))
        }
        root.addView(statusText)

        totalContactsReadView = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(8))
        }
        root.addView(totalContactsReadView)

        scroll.addView(root)
        setContentView(scroll)

        setupToday()
        loadOptions()
        updateSimInfo()

        if (hasPerms()) {
            loadContacts()
        } else {
            requestPermissions(requiredPerms, 1)
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasPerms() && !sending && contacts.isEmpty()) {
            loadContacts()
        }
        updateSimInfo()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        updateSimInfo()
        if (hasPerms()) {
            loadContacts()
        } else {
            Toast.makeText(this, "دسترسی‌های مخاطبین، پیامک و تلفن برای کارکرد خودکار الزامی است", Toast.LENGTH_LONG).show()
        }
    }

    private fun hasPerms(): Boolean {
        for (p in requiredPerms) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) return false
        }
        return true
    }

    // ---------------------------------------------------------------- تاریخ شمسی

    private fun setupToday() {
        val cal = Calendar.getInstance()
        val gy = cal.get(Calendar.YEAR)
        val gm = cal.get(Calendar.MONTH) + 1
        val gd = cal.get(Calendar.DAY_OF_MONTH)

        val j = gregorianToJalali(gy, gm, gd)
        val jy = j[0]
        currentMonth = j[1]
        currentDay = j[2]
        todayKey = String.format("%04d-%02d-%02d", jy, currentMonth, currentDay)

        val monthNames = arrayOf(
            "", "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
            "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
        )
        val mName = if (currentMonth in 1..12) monthNames[currentMonth] else "$currentMonth"
        val dayOfWeek = when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SATURDAY -> "شنبه"
            Calendar.SUNDAY -> "یک‌شنبه"
            Calendar.MONDAY -> "دوشنبه"
            Calendar.TUESDAY -> "سه‌شنبه"
            Calendar.WEDNESDAY -> "چهارشنبه"
            Calendar.THURSDAY -> "پنج‌شنبه"
            Calendar.FRIDAY -> "جمعه"
            else -> ""
        }

        todayBadgeText.text = "امروز: $dayOfWeek $currentDay $mName $jy"
        targetDateView.text = "ارسال به مخاطبین روز $currentDay  ماه $mName  (همه سال‌ها)"
    }

    private fun loadOptions() {
        currentDailyOptions = DailySmsBank.getMessages(currentMonth, currentDay)
        selectedOptionIndex = prefs.getInt("selected_opt_$todayKey", 0)
        if (selectedOptionIndex !in 0..3) selectedOptionIndex = 0

        for (i in 0 until 4) {
            val text = if (i in currentDailyOptions.indices) currentDailyOptions[i] else ""
            if (i < optionTextViews.size) {
                optionTextViews[i].text = text
            }
        }
        updateOptionStyles()

        val savedMsg = prefs.getString("custom_msg_$todayKey", null)
        val initialText = if (!savedMsg.isNullOrEmpty()) {
            savedMsg
        } else if (selectedOptionIndex in currentDailyOptions.indices) {
            currentDailyOptions[selectedOptionIndex]
        } else {
            DailySmsBank.getMessage(currentMonth, currentDay)
        }
        msgEdit.setText(initialText)
    }

    private fun selectOption(index: Int) {
        if (index !in 0..3) return
        selectedOptionIndex = index
        prefs.edit().putInt("selected_opt_$todayKey", index).apply()
        updateOptionStyles()

        val chosenText = if (index in currentDailyOptions.indices) currentDailyOptions[index] else ""
        msgEdit.setText(chosenText)
        prefs.edit().putString("custom_msg_$todayKey", chosenText).apply()
        Toast.makeText(this, "گزینه ${index + 1} انتخاب و در کادر قرار گرفت", Toast.LENGTH_SHORT).show()
    }

    private fun updateOptionStyles() {
        for (i in 0 until 4) {
            val isSel = (i == selectedOptionIndex)
            if (i < optionViews.size) {
                if (isSel) {
                    // کاشی فعال: هایلایت سه‌بعدی برجسته با رنگ آبی روشن و حاشیه ۲ پیکسلی
                    optionViews[i].background = roundRect(
                        Color.parseColor("#EFF6FF"),
                        12,
                        2,
                        Color.parseColor("#2563EB")
                    )
                    optionViews[i].elevation = dp(7).toFloat()
                    optionViews[i].translationZ = dp(2).toFloat()
                } else {
                    optionViews[i].background = roundRect(
                        Color.WHITE,
                        12,
                        1,
                        Color.parseColor("#E2E8F0")
                    )
                    optionViews[i].elevation = dp(3).toFloat()
                    optionViews[i].translationZ = 0f
                }
            }
            if (i < optionRadioViews.size) {
                val rv = optionRadioViews[i]
                rv.text = if (isSel) "✓ انتخابی" else "انتخاب"
                rv.setTextColor(if (isSel) Color.parseColor("#1D4ED8") else Color.parseColor("#64748B"))
                rv.background = roundRect(
                    if (isSel) Color.parseColor("#DBEAFE") else Color.parseColor("#F1F5F9"),
                    8
                )
            }
        }
    }

    private fun gregorianToJalali(gy: Int, gm: Int, gd: Int): IntArray {
        val gDm = intArrayOf(0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var jy = if (gy <= 1600) 0 else 979
        val gy2 = if (gy <= 1600) gy - 621 else gy - 1600
        val leapG = (gy % 4 == 0 && gy % 100 != 0) || (gy % 400 == 0)
        if (leapG) gDm[2] = 29
        var gDayNo = 0
        for (i in 1 until gm) gDayNo += gDm[i]
        gDayNo += gd
        var jDayNo = gDayNo + 79 + (gy2 * 365) + (gy2 / 4) - (gy2 / 100) + (gy2 / 400)
        var days = jDayNo % 12053
        jy += 33 * (jDayNo / 12053)
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm: Int
        val jd: Int
        if (days < 186) {
            jm = 1 + days / 31
            jd = 1 + days % 31
        } else {
            jm = 7 + (days - 186) / 30
            jd = 1 + (days - 186) % 30
        }
        return intArrayOf(jy, jm, jd)
    }

    // ---------------------------------------------------------------- تشخیص اپراتور و تقسیم سیم‌کارت

    /**
     * تشخیص اپراتور شماره موبایل در ایران بر اساس پیش‌شماره معتبر
     */
    private fun detectOperator(phone: String): Operator {
        var p = latinDigits(phone).filter { it.isDigit() }
        if (p.startsWith("0098")) p = "0" + p.substring(4)
        else if (p.startsWith("98") && p.length == 12) p = "0" + p.substring(2)
        if (p.length == 10 && p.startsWith("9")) p = "0$p"

        if (p.length == 11 && p.startsWith("09")) {
            val prefix = p.substring(0, 4)
            return when (prefix) {
                "0910", "0911", "0912", "0913", "0914", "0915", "0916", "0917", "0918", "0919",
                "0990", "0991", "0992", "0993", "0994", "0996" -> Operator.MCI

                "0930", "0933", "0935", "0936", "0937", "0938", "0939",
                "0901", "0902", "0903", "0904", "0905", "0941" -> Operator.IRANCELL

                "0920", "0921", "0922", "0923" -> Operator.RIGHTEL

                else -> Operator.OTHER
            }
        }
        return Operator.OTHER
    }

    /**
     * استخراج فهرست سیم‌کارت‌های فعال با تشخیص اپراتور
     */
    private fun getActiveSimSlots(): List<SimSlot> {
        val list = ArrayList<SimSlot>()
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            val sm = getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            if (sm != null) {
                try {
                    val subs = sm.activeSubscriptionInfoList
                    if (!subs.isNullOrEmpty()) {
                        subs.forEachIndexed { idx, sub ->
                            val raw = ((sub.displayName?.toString() ?: "") + " " + (sub.carrierName?.toString() ?: "")).lowercase()
                            val op = when {
                                raw.contains("irancell") || raw.contains("ایرانسل") || raw.contains("mtn") -> Operator.IRANCELL
                                raw.contains("mci") || raw.contains("همراه اول") || raw.contains("tci") -> Operator.MCI
                                raw.contains("rightel") || raw.contains("رایتل") -> Operator.RIGHTEL
                                else -> Operator.OTHER
                            }
                            val cleanName = sub.displayName?.toString()?.trim() ?: sub.carrierName?.toString()?.trim() ?: "سیم‌کارت ${idx + 1}"
                            list.add(SimSlot(sub.subscriptionId, idx + 1, cleanName, op))
                        }
                    }
                } catch (_: Exception) {}
            }
        }
        return list
    }

    private fun updateSimInfo() {
        if (!::simSummaryText.isInitialized) return
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            simSummaryText.text = "⚠️ جهت تقسیم هوشمند پیامک‌ها، دسترسی تلفن لازم است."
            return
        }
        val slots = getActiveSimSlots()
        if (slots.isEmpty()) {
            simSummaryText.text = "سیم‌کارت فعال شناسایی نشد (ارسال با سیم‌کارت پیش‌فرض سیستم انجام می‌شود)."
            return
        }
        if (slots.size == 1) {
            val s = slots[0]
            val opName = when (s.op) {
                Operator.MCI -> "همراه اول"
                Operator.IRANCELL -> "ایرانسل"
                Operator.RIGHTEL -> "رایتل"
                Operator.OTHER -> ""
            }
            val tag = if (opName.isNotEmpty()) " [$opName]" else ""
            simSummaryText.text = "تنها یک سیم‌کارت فعال (${s.name}$tag) شناسایی شد. همه پیامک‌ها از همین سیم ارسال می‌شوند."
            return
        }

        val names = slots.joinToString("  |  ") { s ->
            val opLabel = when (s.op) {
                Operator.MCI -> "همراه اول"
                Operator.IRANCELL -> "ایرانسل"
                Operator.RIGHTEL -> "رایتل"
                Operator.OTHER -> "سایر"
            }
            "سیم ${s.slotIndex}: ${s.name} ($opLabel)"
        }

        simSummaryText.text = """✅ شناسایی شد: $names
🎯 الگوریتم هوشمند فعال: شماره‌های همراه اول با سیم همراه اول و شماره‌های ایرانسل با سیم ایرانسل ارسال می‌شوند (همراه با سقف ۵۰/۵۰ تا هیچ خطی مسدود نشود)."""
    }

    private fun getSmsManagerForSub(subId: Int): SmsManager {
        return if (Build.VERSION.SDK_INT >= 31) {
            val base = getSystemService(SmsManager::class.java)
                ?: @Suppress("DEPRECATION") SmsManager.getDefault()
            if (subId > 0) base.createForSubscriptionId(subId) else base
        } else {
            if (subId > 0) {
                @Suppress("DEPRECATION")
                SmsManager.getSmsManagerForSubscriptionId(subId)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
        }
    }

    // ---------------------------------------------------------------- مخاطبین

    private fun latinDigits(s: String): String {
        val sb = StringBuilder()
        for (ch in s) {
            when (ch) {
                in '۰'..'۹' -> sb.append('0' + (ch - '۰'))
                in '٠'..'٩' -> sb.append('0' + (ch - '٠'))
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private val dateRegex = Regex("""^\s*(\d{4})[/\-.]?(\d{2})[/\-.]?(\d{2})(?!\d)""")

    private fun parseMd(name: String): Int? {
        val m = dateRegex.find(latinDigits(name)) ?: return null
        val mo = m.groupValues[2].toInt()
        val da = m.groupValues[3].toInt()
        if (mo !in 1..12 || da !in 1..31) return null
        return mo * 100 + da
    }

    private fun normPhone(raw: String): String? {
        var d = latinDigits(raw).filter { it.isDigit() }
        if (d.startsWith("0098")) d = d.substring(4)
        else if (d.startsWith("98") && d.length == 12) d = d.substring(2)
        if (d.length == 10 && d.startsWith("9")) d = "0$d"
        return if (Regex("""^09\d{9}$""").matches(d)) d else null
    }

    private fun loadContacts() {
        statusText.text = "⏳ در حال خواندن مخاطبین دفترچه تلفن..."
        Thread {
            val list = ArrayList<Contact>()
            try {
                val c = contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                    ),
                    null, null, null
                )
                c?.use {
                    val ni = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val pi = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    while (it.moveToNext()) {
                        val name = it.getString(ni) ?: continue
                        val md = parseMd(name) ?: continue
                        val phone = normPhone(it.getString(pi) ?: "") ?: continue
                        list.add(Contact(name, phone, md))
                    }
                }
            } catch (e: Exception) {
                ui.post { statusText.text = "خطا در خواندن مخاطبین: ${e.message}" }
            }
            ui.post {
                contacts = list
                totalContactsReadView.text = "${list.size} مخاطب دارای تاریخ در دفترچه تلفن یافت شد"
                statusText.text = "آماده برای ارسال پیامک"
                refresh()
            }
        }.start()
    }

    private fun targetMd(): Int = currentMonth * 100 + currentDay

    private fun sentSet(): Set<String> = prefs.getStringSet("sent", emptySet()) ?: emptySet()

    private fun pending(): List<String> {
        val md = targetMd()
        val sent = sentSet()
        return contacts.asSequence()
            .filter { it.md == md }
            .map { it.phone }
            .distinct()
            .filter { !sent.contains("$todayKey|$it") }
            .toList()
    }

    private fun refresh() {
        if (!::totalCountBadge.isInitialized) return
        val md = targetMd()
        val total = contacts.filter { it.md == md }.map { it.phone }.distinct().size
        val left = pending().size
        totalCountBadge.text = "👥 مخاطبین امروز: $total نفر"
        pendingCountBadge.text = "⏳ مانده برای ارسال: $left نفر"
    }

    // ---------------------------------------------------------------- طرح‌ریزی هوشمند ارسال اپراتوری

    /**
     * برنامه‌ریزی هوشمند ارسال پیامک‌ها با تطبیق اپراتور و رعایت سقف مساوی (۵۰/۵۰)
     */
    private fun planSendingTasks(phones: List<String>, slots: List<SimSlot>): List<SendTask> {
        if (slots.isEmpty()) {
            return phones.map { SendTask(it, -1, "سیم‌کارت پیش‌فرض", "پیش‌فرض") }
        }
        if (slots.size == 1) {
            val s = slots[0]
            return phones.map { SendTask(it, s.subId, "سیم ${s.slotIndex} (${s.name})", "تک سیم‌کارت") }
        }

        // اگر دو سیم‌کارت داریم:
        val sim1 = slots[0]
        val sim2 = slots[1]

        val total = phones.size
        val quota1 = (total + 1) / 2
        val quota2 = total - quota1

        val sim1Tasks = ArrayList<SendTask>()
        val sim2Tasks = ArrayList<SendTask>()
        val unassigned = ArrayList<Pair<String, Operator>>()

        // مرحله ۱: اولویت تطبیق مستقیم اپراتور شماره با اپراتور سیم‌کارت
        for (phone in phones) {
            val op = detectOperator(phone)
            if (op == sim1.op && sim1Tasks.size < quota1) {
                sim1Tasks.add(SendTask(phone, sim1.subId, "سیم ${sim1.slotIndex} (${sim1.name})", "تطبیق اپراتور"))
            } else if (op == sim2.op && sim2Tasks.size < quota2) {
                sim2Tasks.add(SendTask(phone, sim2.subId, "سیم ${sim2.slotIndex} (${sim2.name})", "تطبیق اپراتور"))
            } else {
                unassigned.add(Pair(phone, op))
            }
        }

        // مرحله ۲: توزیع شماره‌های باقیمانده یا سرریز به سیم‌کارت دیگر تا سقف ۵۰/۵۰ حفظ شود
        for ((phone, op) in unassigned) {
            if (sim1Tasks.size < quota1) {
                sim1Tasks.add(SendTask(phone, sim1.subId, "سیم ${sim1.slotIndex} (${sim1.name})", "توازن خط"))
            } else {
                sim2Tasks.add(SendTask(phone, sim2.subId, "سیم ${sim2.slotIndex} (${sim2.name})", "توازن خط"))
            }
        }

        // ادغام تسک‌ها به صورت متناوب (Interleaved) تا ارسال‌ها درجا بین دو سیم جابجا شوند
        val result = ArrayList<SendTask>()
        var i1 = 0
        var i2 = 0
        while (i1 < sim1Tasks.size || i2 < sim2Tasks.size) {
            if (i1 < sim1Tasks.size) {
                result.add(sim1Tasks[i1++])
            }
            if (i2 < sim2Tasks.size) {
                result.add(sim2Tasks[i2++])
            }
        }
        return result
    }

    // ---------------------------------------------------------------- ارسال خودکار

    private fun onSendClicked() {
        if (sending) return
        if (!hasPerms()) {
            Toast.makeText(this, "ابتدا دسترسی‌ها را تأیید کنید", Toast.LENGTH_LONG).show()
            requestPermissions(requiredPerms, 1)
            return
        }
        val text = msgEdit.text.toString().trim()
        if (text.isEmpty()) {
            Toast.makeText(this, "لطفاً ابتدا متن پیامک را وارد کنید", Toast.LENGTH_LONG).show()
            return
        }
        val list = pending()
        if (list.isEmpty()) {
            Toast.makeText(this, "برای تاریخ امروز هیچ مخاطب ارسال نشده‌ای وجود ندارد", Toast.LENGTH_LONG).show()
            return
        }

        val slots = getActiveSimSlots()
        val tasks = planSendingTasks(list, slots)

        val sim1Count = tasks.count { slots.isNotEmpty() && it.subId == slots[0].subId }
        val sim2Count = tasks.count { slots.size > 1 && it.subId == slots[1].subId }

        val simDesc = if (slots.size >= 2) {
            """📱 تقسیم هوشمند اپراتور و بالانس سهمیه‌ها:
• سیم ۱ (${slots[0].name}): $sim1Count پیامک
• سیم ۲ (${slots[1].name}): $sim2Count پیامک
(اولویت با ارسال شماره‌های همراه اول با سیم همراه اول و ایرانسل با سیم ایرانسل + تقسیم ۵۰/۵۰ جهت پیشگیری از مسدودی)""".trimIndent()
        } else {
            "پیامک‌ها از سیم‌کارت فعال ارسال خواهند شد."
        }

        AlertDialog.Builder(this)
            .setTitle("ارسال هوشمند برای ${list.size} مخاطب")
            .setMessage("""متن ارسالی:
$text

$simDesc

آیا ارسال شروع شود؟""".trimIndent())
            .setPositiveButton("شروع ارسال") { _, _ ->
                startSending(tasks, text)
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun startSending(tasks: List<SendTask>, text: String) {
        sending = true
        sendBtn.isEnabled = false
        sendBtn.alpha = 0.5f
        resetMsgBtn.isEnabled = false
        progressBar.visibility = View.VISIBLE
        progressBar.max = tasks.size
        progressBar.progress = 0
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        sendNext(tasks, text, 0, 0)
    }

    private fun sendNext(tasks: List<SendTask>, text: String, i: Int, failed: Int) {
        if (i >= tasks.size) {
            sending = false
            sendBtn.isEnabled = true
            sendBtn.alpha = 1.0f
            resetMsgBtn.isEnabled = true
            progressBar.visibility = View.GONE
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            statusText.text = "🎉 ارسال کامل شد: ${tasks.size - failed} موفق، $failed ناموفق."
            refresh()
            return
        }
        var f = failed
        val task = tasks[i]

        try {
            val sms = getSmsManagerForSub(task.subId)
            val parts = sms.divideMessage(text)
            sms.sendMultipartTextMessage(task.phone, null, parts, null, null)
            val set = HashSet(sentSet())
            set.add("$todayKey|${task.phone}")
            prefs.edit().putStringSet("sent", set).apply()
        } catch (e: Exception) {
            f++
        }

        progressBar.progress = i + 1
        statusText.text = "در حال ارسال ${i + 1} از ${tasks.size} (${task.simLabel} - ${task.phone}) ... لطفا صفحه را نبندید"
        refresh()

        // تاخیر ۳.۵ ثانیه بین پیامک‌ها برای جلوگیری از اسپم اپراتور
        ui.postDelayed({ sendNext(tasks, text, i + 1, f) }, 3500)
    }
}
