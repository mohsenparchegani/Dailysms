package ir.yalda.dailysms

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
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
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar

/**
 * برنامه ارسال خودکار پیامک بر اساس تاریخ نام مخاطب
 * طراحی مدرن، پشتیبانی از دو سیم‌کارت و تاریخ ثابت
 */
class MainActivity : Activity() {

    data class Contact(val name: String, val phone: String, val md: Int)
    data class SimOption(val label: String, val subId: Int)

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
    private lateinit var msgEdit: EditText
    private lateinit var simContainer: LinearLayout
    private lateinit var sendBtn: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView

    private var activeSimOptions: List<SimOption> = emptyList()
    private var selectedSubId: Int = -1

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
        selectedSubId = prefs.getInt("selected_sub_id", -1)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#F8FAFC")) // تم مدرن لایت متمایل به اسلیت
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(16), dp(20), dp(16), dp(32))
        }

        // --- ۱. کارت سربرگ و تاریخ امروز ---
        val headerCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#3B82F6"), Color.parseColor("#1D4ED8"))
            ).apply { cornerRadius = dp(18).toFloat() }
            setPadding(dp(18), dp(18), dp(18), dp(18))
            elevation = dp(4).toFloat()
        }

        val appTitle = TextView(this).apply {
            text = "✨ پیامک هوشمند مشتریان"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }
        headerCard.addView(appTitle)

        todayBadgeText = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.parseColor("#DBEAFE"))
            setPadding(0, dp(4), 0, 0)
        }
        headerCard.addView(todayBadgeText)
        root.addView(headerCard)

        // --- ۲. کارت تنظیم تاریخ هدف (غیرقابل تغییر و مطابق درخواست کاربر) ---
        val dateCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundRect(Color.WHITE, 16, 1, Color.parseColor("#E2E8F0"))
            setPadding(dp(16), dp(14), dp(16), dp(14))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(14)
            layoutParams = lp
        }

        val dateHeaderLabel = TextView(this).apply {
            text = "📅 تاریخ ارسال امروز"
            textSize = 13f
            setTextColor(Color.parseColor("#64748B"))
            setTypeface(null, Typeface.BOLD)
        }
        dateCard.addView(dateHeaderLabel)

        targetDateView = TextView(this).apply {
            textSize = 17f
            setTextColor(Color.parseColor("#0F172A"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(4), 0, 0)
        }
        dateCard.addView(targetDateView)
        root.addView(dateCard)

        // --- ۳. کارت آمار (کل مخاطبین امروز و مانده) ---
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
            background = roundRect(Color.parseColor("#EFF6FF"), 12, 1, Color.parseColor("#BFDBFE"))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lp.marginEnd = dp(6)
            layoutParams = lp
        }
        statsRow.addView(totalCountBadge)

        pendingCountBadge = TextView(this).apply {
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#065F46"))
            background = roundRect(Color.parseColor("#ECFDF5"), 12, 1, Color.parseColor("#A7F3D0"))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lp.marginStart = dp(6)
            layoutParams = lp
        }
        statsRow.addView(pendingCountBadge)
        root.addView(statsRow)

        // --- ۴. انتخاب سیم‌کارت (مدرن با دکمه‌های شکیل) ---
        val simSectionCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundRect(Color.WHITE, 16, 1, Color.parseColor("#E2E8F0"))
            setPadding(dp(16), dp(14), dp(16), dp(14))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(14)
            layoutParams = lp
        }

        val simHeader = TextView(this).apply {
            text = "📶 انتخاب سیم‌کارت ارسال"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#334155"))
        }
        simSectionCard.addView(simHeader)

        simContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        simSectionCard.addView(simContainer)
        root.addView(simSectionCard)

        // --- ۵. متن پیامک تبلیغاتی ---
        val msgCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundRect(Color.WHITE, 16, 1, Color.parseColor("#E2E8F0"))
            setPadding(dp(16), dp(14), dp(16), dp(14))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(14)
            layoutParams = lp
        }

        val msgLabel = TextView(this).apply {
            text = "✉️ متن پیامک"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#334155"))
        }
        msgCard.addView(msgLabel)

        msgEdit = EditText(this).apply {
            minLines = 4
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            background = roundRect(Color.parseColor("#F8FAFC"), 12, 1, Color.parseColor("#CBD5E1"))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            hint = "متن پیامک ارسالی را اینجا بنویسید..."
            textSize = 15f
            setTextColor(Color.parseColor("#0F172A"))
            setText(prefs.getString("msg", ""))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(8)
            layoutParams = lp
        }
        msgEdit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                prefs.edit().putString("msg", s?.toString() ?: "").apply()
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        msgCard.addView(msgEdit)
        root.addView(msgCard)

        // دکمه ارسال پیامک
        sendBtn = Button(this).apply {
            text = "🚀 ارسال پیامک امروز"
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = roundRect(Color.parseColor("#2563EB"), 16)
            elevation = dp(3).toFloat()
            setOnClickListener { onSendClicked() }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56))
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
            textSize = 13f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, 0)
        }
        root.addView(totalContactsReadView)

        scroll.addView(root)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        updateToday()
        if (hasPerms()) {
            loadContacts()
            updateSimSelectionUi()
        } else {
            requestPermissions(requiredPerms, 1)
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        if (hasPerms()) {
            loadContacts()
            updateSimSelectionUi()
        } else {
            statusText.text = "⚠️ لطفاً برای کارکرد صحیح، دسترسی به مخاطبین، پیامک و تلفن را تأیید کنید."
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

        // طبق خواسته کاربر: ترتیب روز و ماه، و کاملاً غیرقابل تغییر
        targetDateView.text = "ارسال به مخاطبین روز ${j[2]} ماه ${j[1]} (همه سال‌ها)"

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

    // ---------------------------------------------------------------- سیم‌کارت

    private fun getSimOptions(): List<SimOption> {
        val list = ArrayList<SimOption>()
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            val sm = getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            if (sm != null) {
                try {
                    val subs = sm.activeSubscriptionInfoList
                    if (!subs.isNullOrEmpty()) {
                        for (sub in subs) {
                            val slot = sub.simSlotIndex
                            val carrier = sub.displayName?.toString()?.trim() ?: ""
                            val carrierName = sub.carrierName?.toString()?.trim() ?: ""
                            val name = if (carrier.isNotEmpty()) carrier else carrierName
                            val label = "سیم‌کارت ${slot + 1}" + if (name.isNotEmpty() && name != "SIM ${slot + 1}") " ($name)" else ""
                            list.add(SimOption(label, sub.subscriptionId))
                        }
                    }
                } catch (_: Exception) {}
            }
        }
        if (list.isEmpty()) {
            list.add(SimOption("سیم‌کارت ۱", -1))
            list.add(SimOption("سیم‌کارت ۲", -2))
        } else {
            list.add(SimOption("پیش‌فرض سیستم", -1))
        }
        return list
    }

    private fun updateSimSelectionUi() {
        if (!::simContainer.isInitialized) return
        simContainer.removeAllViews()
        activeSimOptions = getSimOptions()
        selectedSubId = prefs.getInt("selected_sub_id", -1)

        var hasMatch = false
        for (opt in activeSimOptions) {
            if (opt.subId == selectedSubId) hasMatch = true
        }
        if (!hasMatch && activeSimOptions.isNotEmpty()) {
            selectedSubId = activeSimOptions[0].subId
            prefs.edit().putInt("selected_sub_id", selectedSubId).apply()
        }

        for (opt in activeSimOptions) {
            val isSelected = (opt.subId == selectedSubId)
            val btn = TextView(this).apply {
                text = (if (isSelected) "🔘 " else "⚪ ") + opt.label
                textSize = 15f
                setTypeface(null, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(if (isSelected) Color.parseColor("#1D4ED8") else Color.parseColor("#475569"))
                background = if (isSelected) {
                    roundRect(Color.parseColor("#EFF6FF"), 12, 1, Color.parseColor("#3B82F6"))
                } else {
                    roundRect(Color.parseColor("#F8FAFC"), 12, 1, Color.parseColor("#E2E8F0"))
                }
                setPadding(dp(14), dp(10), dp(14), dp(10))
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.bottomMargin = dp(8)
                layoutParams = lp
                setOnClickListener {
                    selectedSubId = opt.subId
                    prefs.edit().putInt("selected_sub_id", selectedSubId).apply()
                    updateSimSelectionUi()
                }
            }
            simContainer.addView(btn)
        }
    }

    private fun resolveSubId(targetSubId: Int): Int {
        if (targetSubId > 0) return targetSubId
        if (targetSubId == -2) {
            if (Build.VERSION.SDK_INT >= 29) {
                try {
                    val id = SubscriptionManager.getSubscriptionId(1)
                    if (id != SubscriptionManager.INVALID_SUBSCRIPTION_ID) return id
                } catch (_: Exception) {}
            }
        }
        return -1
    }

    private fun getSmsManager(subId: Int): SmsManager {
        val finalSubId = resolveSubId(subId)
        return if (Build.VERSION.SDK_INT >= 31) {
            val base = getSystemService(SmsManager::class.java)
                ?: @Suppress("DEPRECATION") SmsManager.getDefault()
            if (finalSubId > 0) base.createForSubscriptionId(finalSubId) else base
        } else {
            if (finalSubId > 0) {
                @Suppress("DEPRECATION")
                SmsManager.getSmsManagerForSubscriptionId(finalSubId)
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
                totalContactsReadView.text = "${list.size} مخاطب دارای تاریخ ثبت شده در سیستم"
                statusText.text = "آماده برای ارسال پیامک"
                refresh()
            }
        }.start()
    }

    private fun targetMd(): Int {
        return currentMonth * 100 + currentDay
    }

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

    // ---------------------------------------------------------------- ارسال

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

        val simOptions = getSimOptions()
        var selectedIndex = simOptions.indexOfFirst { it.subId == selectedSubId }
        if (selectedIndex < 0) selectedIndex = 0

        val labels = simOptions.map { it.label }.toTypedArray()
        var chosenSubId = simOptions[selectedIndex].subId

        AlertDialog.Builder(this)
            .setTitle("ارسال برای ${list.size} نفر")
            .setSingleChoiceItems(labels, selectedIndex) { _, which ->
                chosenSubId = simOptions[which].subId
            }
            .setPositiveButton("تأیید و ارسال") { _, _ ->
                selectedSubId = chosenSubId
                prefs.edit().putInt("selected_sub_id", chosenSubId).apply()
                updateSimSelectionUi()
                startSending(list, text, chosenSubId)
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun startSending(list: List<String>, text: String, subId: Int) {
        sending = true
        sendBtn.isEnabled = false
        sendBtn.alpha = 0.5f
        progressBar.visibility = View.VISIBLE
        progressBar.max = list.size
        progressBar.progress = 0
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        sendNext(list, text, subId, 0, 0)
    }

    private fun sendNext(list: List<String>, text: String, subId: Int, i: Int, failed: Int) {
        if (i >= list.size) {
            sending = false
            sendBtn.isEnabled = true
            sendBtn.alpha = 1.0f
            progressBar.visibility = View.GONE
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            statusText.text = "🎉 ارسال کامل شد: ${list.size - failed} موفق، $failed ناموفق."
            refresh()
            return
        }
        var f = failed
        val phone = list[i]
        try {
            val sms = getSmsManager(subId)
            val parts = sms.divideMessage(text)
            sms.sendMultipartTextMessage(phone, null, parts, null, null)
            val set = HashSet(sentSet())
            set.add("$todayKey|$phone")
            prefs.edit().putStringSet("sent", set).apply()
        } catch (e: Exception) {
            f++
        }
        progressBar.progress = i + 1
        statusText.text = "در حال ارسال ${i + 1} از ${list.size} ... (صفحه را نبندید)"
        refresh()
        ui.postDelayed({ sendNext(list, text, subId, i + 1, f) }, 3000)
    }
}
