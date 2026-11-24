package ir.yalda.dailysms

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.telephony.SmsManager
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar

/**
 * منطق برنامه:
 * - نام مخاطبین در گوشی با تاریخ شمسی شروع می‌شود، مثل: 14050819 مستاجر سفری
 * - هر روز با زدن دکمه، به همه مخاطبینی که «ماه و روز» تاریخشان برابر «امروز» است
 *   (در هر سالی: 1405، 1404، 1403 و ...) پیامک تبلیغاتی ارسال می‌شود.
 */
class MainActivity : Activity() {

    data class Contact(val name: String, val phone: String, val md: Int) // md = ماه*100 + روز

    private val ui = Handler(Looper.getMainLooper())
    private lateinit var prefs: SharedPreferences

    private var contacts: List<Contact> = emptyList()
    private var sending = false
    private var todayKey = ""
    private var initializedForKey = ""

    private lateinit var todayText: TextView
    private lateinit var monthEdit: EditText
    private lateinit var dayEdit: EditText
    private lateinit var infoText: TextView
    private lateinit var msgEdit: EditText
    private lateinit var sendBtn: Button
    private lateinit var statusText: TextView

    // ---------------------------------------------------------------- UI

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("dailysms", Context.MODE_PRIVATE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(16), dp(24), dp(16), dp(16))
        }

        todayText = TextView(this).apply { textSize = 16f; setTextColor(Color.DKGRAY) }
        root.addView(todayText)

        val dateRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        dateRow.addView(TextView(this).apply { text = "ارسال به مخاطبین ماه "; textSize = 16f })
        monthEdit = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setEms(2); gravity = Gravity.CENTER
        }
        dateRow.addView(monthEdit)
        dateRow.addView(TextView(this).apply { text = " روز "; textSize = 16f })
        dayEdit = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setEms(2); gravity = Gravity.CENTER
        }
        dateRow.addView(dayEdit)
        dateRow.addView(TextView(this).apply { text = " (همه سال‌ها)"; textSize = 16f })
        root.addView(dateRow)

        infoText = TextView(this).apply {
            textSize = 18f
            setTextColor(Color.parseColor("#0B5394"))
            setPadding(0, dp(12), 0, dp(12))
        }
        root.addView(infoText)

        root.addView(TextView(this).apply { text = "متن پیامک تبلیغاتی:"; textSize = 16f })
        msgEdit = EditText(this).apply {
            minLines = 5
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setText(prefs.getString("msg", ""))
        }
        msgEdit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                prefs.edit().putString("msg", s?.toString() ?: "").apply()
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        root.addView(msgEdit)

        sendBtn = Button(this).apply {
            text = "ارسال پیامک امروز"
            textSize = 20f
            setOnClickListener { onSendClicked() }
        }
        root.addView(
            sendBtn,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(64)
            ).apply { topMargin = dp(16) }
        )

        statusText = TextView(this).apply {
            textSize = 15f
            setPadding(0, dp(12), 0, 0)
        }
        root.addView(statusText)

        val watcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { refresh() }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        }
        monthEdit.addTextChangedListener(watcher)
        dayEdit.addTextChangedListener(watcher)

        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        updateToday()
        if (hasPerms()) loadContacts()
        else requestPermissions(
            arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.SEND_SMS), 1
        )
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        if (hasPerms()) loadContacts()
        else statusText.text = "برای کار کردن برنامه، دسترسی به مخاطبین و پیامک را تأیید کنید."
    }

    private fun hasPerms(): Boolean =
        checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    // ---------------------------------------------------------------- تاریخ

    /** تاریخ شمسی امروز؛ پیش‌فرض ماه/روز = امروز */
    private fun updateToday() {
        val now = Calendar.getInstance()
        val j = toJalali(now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH))
        todayKey = "${j[0]}/${j[1]}/${j[2]}"
        todayText.text = "امروز: ${j[0]}/${j[1]}/${j[2]}"

        // سابقه ارسال روزهای قبل پاک شود
        val old = prefs.getStringSet("sent", emptySet()) ?: emptySet()
        val keep = old.filter { it.startsWith("$todayKey|") }.toSet()
        if (keep.size != old.size) prefs.edit().putStringSet("sent", keep).apply()

        // پیش‌فرض: ماه و روزِ امروز (فقط یک‌بار در هر روز تنظیم می‌شود تا تغییر دستی پاک نشود)
        if (initializedForKey != todayKey) {
            monthEdit.setText(j[1].toString())
            dayEdit.setText(j[2].toString())
            initializedForKey = todayKey
        }
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

    // ---------------------------------------------------------------- مخاطبین

    private fun latinDigits(s: String): String {
        val sb = StringBuilder()
        for (ch in s) {
            when (ch) {
                in '\u06F0'..'\u06F9' -> sb.append('0' + (ch - '\u06F0'))
                in '\u0660'..'\u0669' -> sb.append('0' + (ch - '\u0660'))
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private val dateRegex = Regex("^\\s*(\\d{4})[/\\-.]?(\\d{2})[/\\-.]?(\\d{2})(?!\\d)")

    /** از ابتدای نام مخاطب، ماه*100+روز را درمی‌آورد (مثلاً 14050819 → 819) */
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
        statusText.text = "در حال خواندن مخاطبین..."
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
                statusText.text = "${list.size} مخاطب دارای تاریخ خوانده شد."
                refresh()
            }
        }.start()
    }

    private fun targetMd(): Int? {
        val mo = latinDigits(monthEdit.text.toString()).toIntOrNull() ?: return null
        val da = latinDigits(dayEdit.text.toString()).toIntOrNull() ?: return null
        if (mo !in 1..12 || da !in 1..31) return null
        return mo * 100 + da
    }

    private fun sentSet(): Set<String> = prefs.getStringSet("sent", emptySet()) ?: emptySet()

    /** شماره‌های یکتا که امروز باید پیامک بگیرند و هنوز نگرفته‌اند */
    private fun pending(): List<String> {
        val md = targetMd() ?: return emptyList()
        val sent = sentSet()
        return contacts.asSequence()
            .filter { it.md == md }
            .map { it.phone }
            .distinct()
            .filter { !sent.contains("$todayKey|$it") }
            .toList()
    }

    private fun refresh() {
        if (!::infoText.isInitialized) return
        val md = targetMd()
        if (md == null) {
            infoText.text = "ماه (۱ تا ۱۲) و روز (۱ تا ۳۱) را درست وارد کنید."
            return
        }
        val total = contacts.filter { it.md == md }.map { it.phone }.distinct().size
        val left = pending().size
        infoText.text = "مخاطبین این تاریخ: $total نفر\nمانده برای ارسال امروز: $left نفر"
    }

    // ---------------------------------------------------------------- ارسال

    private fun onSendClicked() {
        if (sending) return
        if (!hasPerms()) {
            Toast.makeText(this, "ابتدا دسترسی‌ها را تأیید کنید", Toast.LENGTH_LONG).show()
            requestPermissions(
                arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.SEND_SMS), 1
            )
            return
        }
        val text = msgEdit.text.toString().trim()
        if (text.isEmpty()) {
            Toast.makeText(this, "متن پیامک را بنویسید", Toast.LENGTH_LONG).show()
            return
        }
        val list = pending()
        if (list.isEmpty()) {
            Toast.makeText(this, "کسی برای ارسال نمانده", Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("تأیید ارسال")
            .setMessage("پیامک برای ${list.size} نفر ارسال شود؟")
            .setPositiveButton("ارسال") { _, _ -> startSending(list, text) }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun smsManager(): SmsManager =
        if (Build.VERSION.SDK_INT >= 31) getSystemService(SmsManager::class.java)
        else @Suppress("DEPRECATION") SmsManager.getDefault()

    private fun startSending(list: List<String>, text: String) {
        sending = true
        sendBtn.isEnabled = false
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        sendNext(list, text, 0, 0)
    }

    private fun sendNext(list: List<String>, text: String, i: Int, failed: Int) {
        if (i >= list.size) {
            sending = false
            sendBtn.isEnabled = true
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            statusText.text = "تمام شد: ${list.size - failed} ارسال شد، $failed ناموفق."
            refresh()
            return
        }
        var f = failed
        val phone = list[i]
        try {
            val sms = smsManager()
            val parts = sms.divideMessage(text)
            sms.sendMultipartTextMessage(phone, null, parts, null, null)
            val set = HashSet(sentSet())
            set.add("$todayKey|$phone")
            prefs.edit().putStringSet("sent", set).apply()
        } catch (e: Exception) {
            f++
        }
        statusText.text = "در حال ارسال ${i + 1} از ${list.size} ... (برنامه را باز نگه دارید)"
        refresh()
        // فاصله ۳ ثانیه بین پیامک‌ها تا خط کمتر محدود شود
        ui.postDelayed({ sendNext(list, text, i + 1, f) }, 3000)
    }
}
