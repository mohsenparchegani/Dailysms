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
 * - نمایش خودکار متن روز در کادر با قابلیت ویرایش آزاد
 * - تقسیم خودکار پیامک‌ها بین سیم‌کارت ۱ و ۲ جهت جلوگیری از مسدودی خط
 * - نمایش تاریخ ثابت شمسی امروز (روز و ماه)
 */
class MainActivity : Activity() {

    data class Contact(val name: String, val phone: String, val md: Int)

    private val ui = Handler(Looper.getMainLooper())
    private lateinit var prefs: SharedPreferences

    private var contacts: List<Contact> = emptyList()
    private var sending = false
    private var todayKey = ""
    private var currentMonth = 0
    private var currentDay = 0

    // ویجت‌ها
    private lateinit var todayBadgeText: TextView
    private lateinit var targetDateView: TextView
    private lateinit var totalCountBadge: TextView
    private lateinit var pendingCountBadge: TextView
    private lateinit var totalContactsReadView: TextView
    // ۴ گزینه انتخابی پیامک روز
    private var selectedOptionIndex: Int = 0
    private val optionViews = mutableListOf<LinearLayout>()
    private val optionRadioViews = mutableListOf<TextView>()
    private val optionTextViews = mutableListOf<TextView>()
    private var currentDailyOptions = listOf<String>()

    private lateinit var simSummaryText: TextView
    private lateinit var msgEdit: EditText
    private lateinit var charCounterText: TextView
    private lateinit var sendBtn: Button
    private lateinit var resetMsgBtn: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView

    private val requiredPerms = arrayOf(
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_PHONE_STATE
    )

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("dailysms", Context.MODE_PRIVATE)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#F8FAFC"))
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(16), dp(18), dp(16), dp(28))
        }

        // --- ۱. هدر شکیل برنامه ---
        val headerCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#2563EB"), Color.parseColor("#1E40AF"))
            ).apply { cornerRadius = dp(16).toFloat() }
            setPadding(dp(16), dp(16), dp(16), dp(16))
            elevation = dp(3).toFloat()
        }

        val appTitle = TextView(this).apply {
            text = "✨ پیامک هوشمند املاک یلدا"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }
        headerCard.addView(appTitle)

        todayBadgeText = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#DBEAFE"))
            setPadding(0, dp(4), 0, 0)
        }
        headerCard.addView(todayBadgeText)
        root.addView(headerCard)

        // --- ۲. کارت تاریخ ارسال امروز (غیرقابل تغییر، ترتیب روز و ماه) ---
        val dateCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundRect(Color.WHITE, 14, 1, Color.parseColor("#E2E8F0"))
            setPadding(dp(16), dp(12), dp(16), dp(12))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(12)
            layoutParams = lp
        }

        val dateLabel = TextView(this).apply {
            text = "📅 تاریخ ارسال امروز"
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

        // --- ۳. کارت آمار مخاطبین ---
        val statsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(10)
            layoutParams = lp
        }

        totalCountBadge = TextView(this).apply {
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1E40AF"))
            background = roundRect(Color.parseColor("#EFF6FF"), 12, 1, Color.parseColor("#BFDBFE"))
            setPadding(dp(10), dp(10), dp(10), dp(10))
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lp.marginEnd = dp(5)
            layoutParams = lp
            text = "مخاطبین امروز: ۰ نفر"
        }
        statsRow.addView(totalCountBadge)

        pendingCountBadge = TextView(this).apply {
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#065F46"))
            background = roundRect(Color.parseColor("#ECFDF5"), 12, 1, Color.parseColor("#A7F3D0"))
            setPadding(dp(10), dp(10), dp(10), dp(10))
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lp.marginStart = dp(5)
            layoutParams = lp
            text = "مانده برای ارسال: ۰ نفر"
        }
        statsRow.addView(pendingCountBadge)
        root.addView(statsRow)

        // --- ۴. کارت وضعیت سیم‌کارت‌ها و تقسیم خودکار ---
        val simCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundRect(Color.WHITE, 14, 1, Color.parseColor("#E2E8F0"))
            setPadding(dp(16), dp(12), dp(16), dp(12))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(12)
            layoutParams = lp
        }

        val simHeader = TextView(this).apply {
            text = "📶 تقسیم خودکار پیامک‌ها بین سیم‌کارت‌ها (ضد مسدودی)"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1E293B"))
        }
        simCard.addView(simHeader)

        simSummaryText = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#475569"))
            setPadding(0, dp(6), 0, 0)
            text = "در حال بررسی سیم‌کارت‌های فعال..."
        }
        simCard.addView(simSummaryText)
        root.addView(simCard)


        // --- ۴.۵. کارت انتخاب ۴ متن پیشنهادی روز ---
        val optionsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundRect(Color.WHITE, 14, 1, Color.parseColor("#E2E8F0"))
            setPadding(dp(16), dp(14), dp(16), dp(14))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(12)
            layoutParams = lp
        }

        val optionsHeader = TextView(this).apply {
            text = "🎯 انتخاب متن امروز از بین ۴ گزینه (یکی را لمس کنید):"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1E293B"))
            setPadding(0, 0, 0, dp(8))
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
                background = roundRect(Color.parseColor("#F8FAFC"), 10, 1, Color.parseColor("#E2E8F0"))
                setPadding(dp(12), dp(10), dp(12), dp(10))
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.topMargin = dp(6)
                layoutParams = lp
            }

            val topRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
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
                text = if (i == 0) "🔘 انتخابی" else "⚪ انتخاب"
                textSize = 11f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (i == 0) Color.parseColor("#2563EB") else Color.parseColor("#64748B"))
            }
            topRow.addView(radioView)
            optItem.addView(topRow)

            val bodyView = TextView(this).apply {
                textSize = 13f
                setTextColor(Color.parseColor("#334155"))
                setPadding(0, dp(4), 0, 0)
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

        // --- ۵. کادر متن پیامک (نمایش متن روز + قابلیت ویرایش) ---
        val msgCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundRect(Color.WHITE, 14, 1, Color.parseColor("#E2E8F0"))
            setPadding(dp(16), dp(14), dp(16), dp(14))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(12)
            layoutParams = lp
        }

        val msgHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            layoutParams = lp
        }

        val msgLabel = TextView(this).apply {
            text = "✉️ متن پیامک امروز (قابل ویرایش):"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1E293B"))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = lp
        }
        msgHeaderRow.addView(msgLabel)

        resetMsgBtn = Button(this).apply {
            text = "🔄 بازنشانی متن روز"
            textSize = 11f
            setTextColor(Color.parseColor("#2563EB"))
            background = roundRect(Color.parseColor("#EFF6FF"), 8, 1, Color.parseColor("#BFDBFE"))
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setOnClickListener {
                val defaultOpt = if (selectedOptionIndex in currentDailyOptions.indices) currentDailyOptions[selectedOptionIndex] else DailySmsBank.getMessage(currentMonth, currentDay)
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
            background = roundRect(Color.parseColor("#F8FAFC"), 10, 1, Color.parseColor("#CBD5E1"))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            hint = "متن پیامک امروز..."
            textSize = 15f
            setTextColor(Color.parseColor("#0F172A"))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(8)
            layoutParams = lp
        }

        charCounterText = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#64748B"))
            gravity = Gravity.START
            setPadding(0, dp(4), 0, 0)
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
                    charCounterText.setTextColor(Color.parseColor("#D97706"))
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

        // --- ۶. دکمه ارسال پیامک ---
        sendBtn = Button(this).apply {
            text = "🚀 ارسال هوشمند پیامک‌های امروز"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = roundRect(Color.parseColor("#2563EB"), 14)
            elevation = dp(3).toFloat()
            setOnClickListener { onSendClicked() }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54))
            lp.topMargin = dp(16)
            layoutParams = lp
        }
        root.addView(sendBtn)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            visibility = View.GONE
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8))
            lp.topMargin = dp(12)
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
            setPadding(0, dp(4), 0, 0)
        }
        root.addView(totalContactsReadView)

        scroll.addView(root)
        setContentView(scroll)
    }


    private fun selectOption(index: Int) {
        selectedOptionIndex = index
        prefs.edit().putInt("selected_opt_idx_$todayKey", index).apply()

        for (i in 0 until optionViews.size) {
            val isSelected = (i == index)
            if (isSelected) {
                optionViews[i].background = roundRect(Color.parseColor("#EFF6FF"), 10, 2, Color.parseColor("#2563EB"))
                optionRadioViews[i].text = "🔘 انتخاب شد"
                optionRadioViews[i].setTextColor(Color.parseColor("#2563EB"))
            } else {
                optionViews[i].background = roundRect(Color.parseColor("#F8FAFC"), 10, 1, Color.parseColor("#E2E8F0"))
                optionRadioViews[i].text = "⚪ انتخاب"
                optionRadioViews[i].setTextColor(Color.parseColor("#64748B"))
            }
        }

        if (index in currentDailyOptions.indices) {
            val chosen = currentDailyOptions[index]
            msgEdit.setText(chosen)
            prefs.edit().putString("custom_msg_$todayKey", chosen).apply()
            Toast.makeText(this, "گزینه ${index + 1} در کادر پیامک درج شد", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateOptionCards(options: List<String>) {
        currentDailyOptions = options
        for (i in 0 until minOf(options.size, optionTextViews.size)) {
            val len = options[i].length
            optionTextViews[i].text = "${options[i]} (${len} کاراکتر)"
        }
        val savedIdx = prefs.getInt("selected_opt_idx_$todayKey", 0)
        selectedOptionIndex = if (savedIdx in options.indices) savedIdx else 0

        for (i in 0 until optionViews.size) {
            val isSelected = (i == selectedOptionIndex)
            if (isSelected) {
                optionViews[i].background = roundRect(Color.parseColor("#EFF6FF"), 10, 2, Color.parseColor("#2563EB"))
                optionRadioViews[i].text = "🔘 انتخاب شد"
                optionRadioViews[i].setTextColor(Color.parseColor("#2563EB"))
            } else {
                optionViews[i].background = roundRect(Color.parseColor("#F8FAFC"), 10, 1, Color.parseColor("#E2E8F0"))
                optionRadioViews[i].text = "⚪ انتخاب"
                optionRadioViews[i].setTextColor(Color.parseColor("#64748B"))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateToday()
        if (hasPerms()) {
            loadContacts()
            updateSimInfo()
        } else {
            requestPermissions(requiredPerms, 1)
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        if (hasPerms()) {
            loadContacts()
            updateSimInfo()
        } else {
            statusText.text = "⚠️ برای عملکرد صحیح برنامه، لطفاً دسترسی‌های پیامک، مخاطبین و تلفن را تأیید نمایید."
        }
    }

    private fun hasPerms(): Boolean =
        checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    // ---------------------------------------------------------------- تاریخ

    private fun updateToday() {
        val now = Calendar.getInstance()
        val j = toJalali(now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH))
        currentMonth = j[1]
        currentDay = j[2]
        todayKey = "${j[0]}/${j[1]}/${j[2]}"
        todayBadgeText.text = "امروز: ${j[0]}/${j[1]}/${j[2]}"

        // نمایش دقیق تاریخ طبق فرمت درخواستی (غیرقابل تغییر)
        targetDateView.text = "ارسال به مخاطبین روز ${j[2]} ماه ${j[1]} (همه سال‌ها)"

        // بارگذاری ۴ گزینه اختصاصی امروز و نمایش در کارت‌ها
        val dailyOpts = DailySmsBank.getMessages(currentMonth, currentDay)
        updateOptionCards(dailyOpts)

        // بارگذاری متن در کادر (متن سفارشی ذخیره‌شده یا گزینه انتخابی)
        val savedForToday = prefs.getString("custom_msg_$todayKey", null)
        val defaultOpt = if (selectedOptionIndex in dailyOpts.indices) dailyOpts[selectedOptionIndex] else dailyOpts[0]
        val textToSet = savedForToday ?: defaultOpt
        if (msgEdit.text.toString() != textToSet) {
            msgEdit.setText(textToSet)
        }

        val old = prefs.getStringSet("sent", emptySet()) ?: emptySet()
        val keep = old.filter { it.startsWith("$todayKey|") }.toSet()
        if (keep.size != old.size) prefs.edit().putStringSet("sent", keep).apply()
    }

    private fun toJalali(gy: Int, gm: Int, gd: Int): IntArray {
        val gdm = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) +
            ((gy2 + 399) / 400) + gd + gdm[gm - 1]
        var jy = -1595 + (33 * (days / 12053))
        days %= 12053
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

    // ---------------------------------------------------------------- سیم‌کارت و تقسیم خودکار

    private fun getActiveSubscriptionIds(): List<Int> {
        val list = ArrayList<Int>()
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            val sm = getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            if (sm != null) {
                try {
                    val subs = sm.activeSubscriptionInfoList
                    if (!subs.isNullOrEmpty()) {
                        for (sub in subs) {
                            list.add(sub.subscriptionId)
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
            simSummaryText.text = "جهت تقسیم پیامک‌ها دسترسی تلفن لازم است."
            return
        }
        val sm = getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
        val subs = sm?.activeSubscriptionInfoList
        if (subs.isNullOrEmpty()) {
            simSummaryText.text = "سیم‌کارت فعال شناسایی نشد (ارسال با سیم‌کارت پیش‌فرض سیستم)."
            return
        }
        if (subs.size == 1) {
            val sub = subs[0]
            val carrier = sub.displayName?.toString()?.trim() ?: sub.carrierName?.toString()?.trim() ?: "سیم‌کارت ۱"
            simSummaryText.text = "تنها یک سیم‌کارت فعال ($carrier) شناسایی شد. همه پیامک‌ها از همین سیم ارسال می‌شوند."
            return
        }

        val names = subs.mapIndexed { idx, s ->
            val c = s.displayName?.toString()?.trim() ?: s.carrierName?.toString()?.trim() ?: "سیم ${idx + 1}"
            "سیم ${idx + 1}: $c"
        }.joinToString("  |  ")

        simSummaryText.text = "✅ شناسایی شد: $names\n🔄 پیامک‌ها به صورت خودکار و مساوی (۵۰/۵۰) بین دو سیم‌کارت تقسیم و ارسال می‌شوند تا خط مسدود نشود."
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

    private val dateRegex = Regex("^\\s*(\\d{4})[/\\-.]?(\\d{2})[/\\-.]?(\\d{2})(?!\\d)")

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
        return if (Regex("^09\\d{9}$").matches(d)) d else null
    }

    private fun loadContacts() {
        statusText.text = "⏳ در حال خواندن مخاطبین گوشی..."
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
        totalCountBadge.text = "مخاطبین امروز: $total نفر"
        pendingCountBadge.text = "مانده برای ارسال: $left نفر"
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

        val subs = getActiveSubscriptionIds()
        val simDesc = if (subs.size >= 2) {
            "پیامک‌ها به صورت خودکار ۵۰/۵۰ بین دو سیم‌کارت تقسیم می‌شوند تا از مسدود شدن سیم‌کارت جلوگیری شود."
        } else {
            "پیامک‌ها از سیم‌کارت فعال ارسال خواهند شد."
        }

        AlertDialog.Builder(this)
            .setTitle("ارسال برای ${list.size} مخاطب")
            .setMessage("متن ارسالی:\n$text\n\n$simDesc\n\nآیا ارسال شروع شود؟")
            .setPositiveButton("شروع ارسال") { _, _ ->
                startSending(list, text, subs)
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun startSending(list: List<String>, text: String, subs: List<Int>) {
        sending = true
        sendBtn.isEnabled = false
        sendBtn.alpha = 0.5f
        resetMsgBtn.isEnabled = false
        progressBar.visibility = View.VISIBLE
        progressBar.max = list.size
        progressBar.progress = 0
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        sendNext(list, text, subs, 0, 0)
    }

    private fun sendNext(list: List<String>, text: String, subs: List<Int>, i: Int, failed: Int) {
        if (i >= list.size) {
            sending = false
            sendBtn.isEnabled = true
            sendBtn.alpha = 1.0f
            resetMsgBtn.isEnabled = true
            progressBar.visibility = View.GONE
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            statusText.text = "🎉 ارسال کامل شد: ${list.size - failed} موفق، $failed ناموفق."
            refresh()
            return
        }
        var f = failed
        val phone = list[i]

        // تقسیم خودکار متوازن بین سیم‌کارت‌ها (نوبتی - round-robin)
        val chosenSubId = if (subs.isNotEmpty()) subs[i % subs.size] else -1
        val simSlotNum = if (subs.isNotEmpty()) (i % subs.size) + 1 else 1

        try {
            val sms = getSmsManagerForSub(chosenSubId)
            val parts = sms.divideMessage(text)
            sms.sendMultipartTextMessage(phone, null, parts, null, null)
            val set = HashSet(sentSet())
            set.add("$todayKey|$phone")
            prefs.edit().putStringSet("sent", set).apply()
        } catch (e: Exception) {
            f++
        }

        progressBar.progress = i + 1
        statusText.text = "در حال ارسال ${i + 1} از ${list.size} (از سیم‌کارت $simSlotNum) ... لطفا صفحه را نبندید"
        refresh()

        // تاخیر ۳.۵ ثانیه بین پیامک‌ها برای جلوگیری از اسپم اپراتور
        ui.postDelayed({ sendNext(list, text, subs, i + 1, f) }, 3500)
    }
}
