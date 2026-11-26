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
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

/**
 * برنامه ارسال پیامک روزانه املاک یلدا
 * - طراحی جدید با تم گرم سفالی و کرم (Warm Terracotta & Cream)
 * - چیدمان مدرن با اسلایدر روزهای تقویم، کارت آمار مخاطبین و کادرهای ۲×۲ متن‌های روز
 * - تقسیم هوشمند اپراتوری پیامک‌ها (همراه اول / ایرانسل) با بالانس ۵۰/۵۰ جهت جلوگیری از مسدودی
 * - بانک ۱۴۶۰ پیامک آماده با انتخاب تک‌لمسی و قابلیت ویرایش
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

    // ویوهای اصلی صفحه
    private lateinit var todayBadgeText: TextView
    private lateinit var targetDateView: TextView
    private lateinit var targetRelationBadge: TextView
    private lateinit var calendarStripContainer: LinearLayout
    private lateinit var totalCountBadge: TextView
    private lateinit var pendingCountBadge: TextView
    private lateinit var statsSubtitle: TextView
    private lateinit var viewListBtn: TextView
    private lateinit var sim1StatusText: TextView
    private lateinit var sim2StatusText: TextView
    private lateinit var simSummaryText: TextView
    private lateinit var msgEdit: EditText
    private lateinit var charCounterText: TextView
    private lateinit var sendBtn: Button
    private lateinit var resetMsgBtn: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var totalContactsReadView: TextView

    // گزینه‌های ۲ در ۲ قالب‌های پیامک
    private val optionViews = ArrayList<LinearLayout>()
    private val optionRadioViews = ArrayList<TextView>()
    private val optionTextViews = ArrayList<TextView>()
    private var selectedOptionIndex = 0
    private var currentDailyOptions = listOf<String>()

    private var currentYear: Int = 1405
    private var currentMonth: Int = 1
    private var currentDay: Int = 1
    private var todayKey: String = ""
    private var targetCal: Calendar = Calendar.getInstance()

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

    private fun createWarmCard(
        bgColor: Int = Color.WHITE,
        radiusDp: Int = 16,
        elevationDp: Int = 4,
        strokeWidthDp: Int = 1,
        strokeColor: Int = Color.parseColor("#EBE5DC")
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundRect(bgColor, radiusDp, strokeWidthDp, strokeColor)
            elevation = dp(elevationDp).toFloat()
            clipToOutline = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("dailysms", Context.MODE_PRIVATE)

        // پس‌زمینه کلی عاجی و کرم ملایم و گرم
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#FAF8F5"))
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(16), dp(16), dp(16), dp(28))
        }

        // =========================================================================
        // ۱. هدر شیک با آیکون املاک یلدا و شعار برند
        // =========================================================================
        val headerCard = createWarmCard(Color.WHITE, 18, 4, 1, Color.parseColor("#EBE5DC")).apply {
            setPadding(dp(16), dp(16), dp(16), dp(14))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            layoutParams = lp
        }

        val headerTopRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            layoutParams = lp
        }

        // کادر آیکون خانه در سمت راست
        val brandIconBox = TextView(this).apply {
            text = "🏠"
            textSize = 22f
            gravity = Gravity.CENTER
            background = roundRect(Color.parseColor("#FFF2EB"), 12, 1, Color.parseColor("#FCD7C6"))
            val lp = LinearLayout.LayoutParams(dp(44), dp(44))
            lp.marginStart = dp(10)
            layoutParams = lp
        }
        headerTopRow.addView(brandIconBox)

        // عنوان و زیرعنوان برند املاک یلدا
        val brandTitlesCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = lp
        }

        val appTitle = TextView(this).apply {
            text = "املاک یلدا"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1C1917"))
        }
        brandTitlesCol.addView(appTitle)

        val appSubtitle = TextView(this).apply {
            text = "ارتباط امروز، مشتری فردا"
            textSize = 12.5f
            setTextColor(Color.parseColor("#78716C"))
            setPadding(0, dp(2), 0, 0)
        }
        brandTitlesCol.addView(appSubtitle)
        headerTopRow.addView(brandTitlesCol)

        val versionBadge = TextView(this).apply {
            text = "نسخه ۱.۸"
            textSize = 11f
            setTextColor(Color.parseColor("#C25E34"))
            setTypeface(null, Typeface.BOLD)
            background = roundRect(Color.parseColor("#FFF2EB"), 10, 1, Color.parseColor("#FCD7C6"))
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        headerTopRow.addView(versionBadge)
        headerCard.addView(headerTopRow)

        todayBadgeText = TextView(this).apply {
            textSize = 12.5f
            setTextColor(Color.parseColor("#8C7A6B"))
            setPadding(0, dp(10), 0, 0)
        }
        headerCard.addView(todayBadgeText)
        root.addView(headerCard)

        // =========================================================================
        // ۲. کارت اسلایدر تقویم و انتخاب روز ارسال پیامک‌ها
        // =========================================================================
        val dateCard = createWarmCard(Color.WHITE, 18, 4, 1, Color.parseColor("#EBE5DC")).apply {
            setPadding(dp(16), dp(16), dp(16), dp(16))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(14)
            layoutParams = lp
        }

        val dateHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            layoutParams = lp
        }

        val dateLabel = TextView(this).apply {
            text = "📅 تقویم ارسال پیامک"
            textSize = 14f
            setTextColor(Color.parseColor("#292524"))
            setTypeface(null, Typeface.BOLD)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = lp
        }
        dateHeaderRow.addView(dateLabel)

        targetRelationBadge = TextView(this).apply {
            textSize = 11.5f
            setTextColor(Color.parseColor("#C25E34"))
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(8), dp(3), dp(8), dp(3))
            background = roundRect(Color.parseColor("#FFF2EB"), 8, 1, Color.parseColor("#FCD7C6"))
        }
        dateHeaderRow.addView(targetRelationBadge)
        dateCard.addView(dateHeaderRow)

        targetDateView = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.parseColor("#1C1917"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(8), 0, dp(4))
        }
        dateCard.addView(targetDateView)

        // ردیف کپسول‌های روزهای هفته (ش، ی، د، س، چ، پ، ج)
        calendarStripContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(8)
            layoutParams = lp
        }
        dateCard.addView(calendarStripContainer)

        val calQuote = TextView(this).apply {
            text = "☀️ روز خوبی برای ارتباط مؤثر با مشتریان است"
            textSize = 12f
            setTextColor(Color.parseColor("#78716C"))
            setPadding(0, dp(8), 0, 0)
        }
        dateCard.addView(calQuote)

        // دکمه‌های سریع تغییر روز
        val quickShiftRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(12)
            layoutParams = lp
        }

        fun warmDateBtn(label: String, isPrimary: Boolean = false, weight: Float = 1f, action: () -> Unit): TextView {
            return TextView(this).apply {
                text = label
                textSize = 12f
                setTextColor(if (isPrimary) Color.WHITE else Color.parseColor("#292524"))
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(8), dp(4), dp(8))
                background = if (isPrimary) {
                    GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        intArrayOf(Color.parseColor("#C85A32"), Color.parseColor("#A83E1B"))
                    ).apply { cornerRadius = dp(10).toFloat() }
                } else {
                    roundRect(Color.parseColor("#FAF7F2"), 10, 1, Color.parseColor("#E5DDD0"))
                }
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight)
                lp.marginEnd = dp(4)
                layoutParams = lp
                setOnClickListener { action() }
            }
        }

        quickShiftRow.addView(warmDateBtn("◀ دیروز") {
            targetCal.add(Calendar.DAY_OF_MONTH, -1)
            syncDateFromTargetCal()
        })
        quickShiftRow.addView(warmDateBtn("⚡ ۱ روز قبل (پیش‌فرض)", true, 1.3f) {
            resetToYesterdayDefault(true)
        })
        quickShiftRow.addView(warmDateBtn("امروز", false, 0.9f) {
            resetToToday(true)
        })
        quickShiftRow.addView(warmDateBtn("فردا ▶") {
            targetCal.add(Calendar.DAY_OF_MONTH, 1)
            syncDateFromTargetCal()
        })
        dateCard.addView(quickShiftRow)

        val changeDateBtn = TextView(this).apply {
            text = "✏️ انتخاب تاریخ دلخواه از تقویم"
            textSize = 12f
            setTextColor(Color.parseColor("#A8421F"))
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = roundRect(Color.parseColor("#FFF7ED"), 10, 1, Color.parseColor("#FED7AA"))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(8)
            layoutParams = lp
            setOnClickListener { showDatePickerDialog() }
        }
        dateCard.addView(changeDateBtn)
        root.addView(dateCard)

        // =========================================================================
        // ۳. کارت برجسته آمار مخاطبین (با تم آرام مریم‌گلی/زیتونی طبیعی)
        // =========================================================================
        val statsCard = createWarmCard(Color.parseColor("#F2F7F2"), 18, 3, 1, Color.parseColor("#D4E6D6")).apply {
            setPadding(dp(16), dp(14), dp(16), dp(14))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(14)
            layoutParams = lp
        }

        val statsTopRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            layoutParams = lp
        }

        val statsIconBox = TextView(this).apply {
            text = "👥"
            textSize = 20f
            gravity = Gravity.CENTER
            background = roundRect(Color.parseColor("#E0EFE2"), 14, 1, Color.parseColor("#BFDEBF"))
            val lp = LinearLayout.LayoutParams(dp(44), dp(44))
            lp.marginStart = dp(12)
            layoutParams = lp
        }
        statsTopRow.addView(statsIconBox)

        val statsTextCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = lp
        }

        totalCountBadge = TextView(this).apply {
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1B4D2E"))
            text = "۰ مخاطب آماده ارسال"
        }
        statsTextCol.addView(totalCountBadge)

        statsSubtitle = TextView(this).apply {
            text = "مخاطبین روز انتخابی (همه سال‌ها)"
            textSize = 12f
            setTextColor(Color.parseColor("#446B4F"))
            setPadding(0, dp(2), 0, 0)
        }
        statsTextCol.addView(statsSubtitle)
        statsTopRow.addView(statsTextCol)

        pendingCountBadge = TextView(this).apply {
            text = "● آماده ارسال"
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#2E6A3F"))
            background = roundRect(Color.parseColor("#D8EBD9"), 8, 1, Color.parseColor("#B4D9B6"))
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        statsTopRow.addView(pendingCountBadge)
        statsCard.addView(statsTopRow)

        val statsActionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, 0)
        }
        viewListBtn = TextView(this).apply {
            text = "📋 مشاهده شماره‌های این روز"
            textSize = 12f
            setTextColor(Color.parseColor("#C25E34"))
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(8), dp(5), dp(8), dp(5))
            background = roundRect(Color.parseColor("#FFF7ED"), 8, 1, Color.parseColor("#FDBA74"))
            setOnClickListener { showContactsListDialog() }
        }
        statsActionRow.addView(viewListBtn)

        val reloadContactsBtn = TextView(this).apply {
            text = "🔄 بروزرسانی مخاطبین"
            textSize = 12f
            setTextColor(Color.parseColor("#1B4D2E"))
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(8), dp(5), dp(8), dp(5))
            background = roundRect(Color.parseColor("#EAF4EC"), 8, 1, Color.parseColor("#BFDEBF"))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.marginStart = dp(8)
            layoutParams = lp
            setOnClickListener {
                if (canReadContacts()) {
                    loadContacts(true)
                } else {
                    Toast.makeText(this@MainActivity, "دسترسی به مخاطبین داده نشده است", Toast.LENGTH_SHORT).show()
                }
            }
        }
        statsActionRow.addView(reloadContactsBtn)
        statsCard.addView(statsActionRow)
        statsCard.setOnClickListener { showContactsListDialog() }
        root.addView(statsCard)

        // =========================================================================
        // ۴. ویجت دو ستونه وضعیت سیم‌کارت‌ها (همراه اول و ایرانسل)
        // =========================================================================
        val simSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(14)
            layoutParams = lp
        }

        val simHeaderLabel = TextView(this).apply {
            text = "📶 وضعیت سیم‌کارت‌ها (تقسیم هوشمند ۵۰/۵۰)"
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#292524"))
            setPadding(0, 0, 0, dp(6))
        }
        simSection.addView(simHeaderLabel)

        val simRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            layoutParams = lp
        }

        // کارت سیم ۱
        val sim1Card = createWarmCard(Color.WHITE, 14, 3, 1, Color.parseColor("#EBE5DC")).apply {
            setPadding(dp(12), dp(10), dp(12), dp(10))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lp.marginEnd = dp(6)
            layoutParams = lp
        }
        val sim1Title = TextView(this).apply {
            text = "📱 سیم‌کارت ۱"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#78716C"))
        }
        sim1Card.addView(sim1Title)
        sim1StatusText = TextView(this).apply {
            text = "همراه اول • فعال"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1C1917"))
            setPadding(0, dp(2), 0, 0)
        }
        sim1Card.addView(sim1StatusText)
        simRow.addView(sim1Card)

        // کارت سیم ۲
        val sim2Card = createWarmCard(Color.WHITE, 14, 3, 1, Color.parseColor("#EBE5DC")).apply {
            setPadding(dp(12), dp(10), dp(12), dp(10))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lp.marginStart = dp(6)
            layoutParams = lp
        }
        val sim2Title = TextView(this).apply {
            text = "📱 سیم‌کارت ۲"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#78716C"))
        }
        sim2Card.addView(sim2Title)
        sim2StatusText = TextView(this).apply {
            text = "ایرانسل • فعال"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1C1917"))
            setPadding(0, dp(2), 0, 0)
        }
        sim2Card.addView(sim2StatusText)
        simRow.addView(sim2Card)
        simSection.addView(simRow)

        simSummaryText = TextView(this).apply {
            textSize = 11.5f
            setTextColor(Color.parseColor("#78716C"))
            setPadding(0, dp(6), 0, 0)
            text = "تقسیم خودکار نیمی از پیامک‌ها با همراه اول و نیمی با ایرانسل جهت جلوگیری از مسدودی خط"
        }
        simSection.addView(simSummaryText)
        root.addView(simSection)

        // =========================================================================
        // ۵. بخش ۲×۲ متن‌های پیشنهادی روز (۴ قالب کارت مدرن)
        // =========================================================================
        val optionsSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(16)
            layoutParams = lp
        }

        val optionsHeader = TextView(this).apply {
            text = "📋 متن‌های پیشنهادی روز (برای انتخاب یکی را لمس کنید):"
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#292524"))
            setPadding(0, 0, 0, dp(8))
        }
        optionsSection.addView(optionsHeader)

        val optionSpecs = listOf(
            Triple("🏠 معرفی ملک ویژه", "فرصت‌های مناسب خرید و اجاره", "#C25E34"),
            Triple("🤝 پیگیری مشتری", "در ارتباط با درخواست شما", "#2563EB"),
            Triple("💎 پیشنهاد ویژه", "تخفیف‌ها و شرایط سرمایه‌گذاری", "#7C3AED"),
            Triple("💙 پیام مناسبتی", "با شما در تمام روزها و اعیاد", "#059669")
        )

        // ردیف اول: گزینه‌های ۰ و ۱
        // ردیف دوم: گزینه‌های ۲ و ۳
        val gridRows = listOf(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                layoutParams = lp
            },
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.topMargin = dp(8)
                layoutParams = lp
            }
        )

        for (i in 0 until 4) {
            val (title, sub, accentColor) = optionSpecs[i]
            val parentRow = if (i < 2) gridRows[0] else gridRows[1]

            val optCard = createWarmCard(Color.WHITE, 14, 3, 1, Color.parseColor("#EBE5DC")).apply {
                setPadding(dp(12), dp(10), dp(12), dp(10))
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                if (i % 2 == 0) lp.marginEnd = dp(4) else lp.marginStart = dp(4)
                layoutParams = lp
            }

            val cardTopRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                layoutParams = lp
            }

            val cardTitle = TextView(this).apply {
                text = title
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor(accentColor))
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                layoutParams = lp
            }
            cardTopRow.addView(cardTitle)

            val radioBadge = TextView(this).apply {
                text = if (i == 0) "✓ انتخابی" else "انتخاب"
                textSize = 10f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (i == 0) Color.parseColor("#C25E34") else Color.parseColor("#78716C"))
                background = roundRect(
                    if (i == 0) Color.parseColor("#FFF2EB") else Color.parseColor("#F5F5F4"),
                    6
                )
                setPadding(dp(6), dp(2), dp(6), dp(2))
            }
            cardTopRow.addView(radioBadge)
            optCard.addView(cardTopRow)

            val cardSubtitle = TextView(this).apply {
                text = sub
                textSize = 11f
                setTextColor(Color.parseColor("#78716C"))
                setPadding(0, dp(4), 0, 0)
            }
            optCard.addView(cardSubtitle)

            val previewBody = TextView(this).apply {
                textSize = 11.5f
                setTextColor(Color.parseColor("#44403C"))
                setPadding(0, dp(6), 0, 0)
                maxLines = 2
                text = "در حال بارگذاری..."
            }
            optCard.addView(previewBody)

            val optIndex = i
            optCard.setOnClickListener { selectOption(optIndex) }

            optionViews.add(optCard)
            optionRadioViews.add(radioBadge)
            optionTextViews.add(previewBody)
            parentRow.addView(optCard)
        }

        optionsSection.addView(gridRows[0])
        optionsSection.addView(gridRows[1])
        root.addView(optionsSection)

        // =========================================================================
        // ۶. کادر ویرایشگر متن پیامک نهایی (با پس‌زمینه گرم و بازنشانی)
        // =========================================================================
        val msgCard = createWarmCard(Color.WHITE, 16, 4, 1, Color.parseColor("#EBE5DC")).apply {
            setPadding(dp(16), dp(14), dp(16), dp(14))
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
            text = "✉️ متن پیامک ارسالی (قابل ویرایش):"
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#292524"))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = lp
        }
        msgHeaderRow.addView(msgLabel)

        resetMsgBtn = Button(this).apply {
            text = "🔄 بازنشانی متن"
            textSize = 11f
            setTextColor(Color.parseColor("#A8421F"))
            background = roundRect(Color.parseColor("#FFF7ED"), 8, 1, Color.parseColor("#FED7AA"))
            setPadding(dp(10), dp(4), dp(10), dp(4))
            setOnClickListener {
                val defaultOpt = if (selectedOptionIndex in currentDailyOptions.indices) {
                    currentDailyOptions[selectedOptionIndex]
                } else {
                    DailySmsBank.getMessage(currentMonth, currentDay)
                }
                msgEdit.setText(defaultOpt)
                prefs.edit().putString("custom_msg_$todayKey", defaultOpt).apply()
                Toast.makeText(this@MainActivity, "متن به قالب انتخابی بازنشانی شد", Toast.LENGTH_SHORT).show()
            }
        }
        msgHeaderRow.addView(resetMsgBtn)
        msgCard.addView(msgHeaderRow)

        msgEdit = EditText(this).apply {
            minLines = 3
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            background = roundRect(Color.parseColor("#FAF8F5"), 12, 1, Color.parseColor("#E7E0D6"))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            hint = "متن پیامک امروز..."
            textSize = 14.5f
            setTextColor(Color.parseColor("#1C1917"))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(8)
            layoutParams = lp
        }

        charCounterText = TextView(this).apply {
            textSize = 11.5f
            setTextColor(Color.parseColor("#78716C"))
            gravity = Gravity.START
            setPadding(0, dp(6), 0, 0)
            text = "تعداد کاراکتر: ۰ / ۷۰ (۱ پیامک فارسی)"
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
                    charCounterText.setTextColor(Color.parseColor("#78716C"))
                }
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        msgCard.addView(msgEdit)
        msgCard.addView(charCounterText)
        root.addView(msgCard)

        // =========================================================================
        // ۷. دکمه ارگونومیک و بزرگ ارسال پیامک با رنگ سفالی گرم
        // =========================================================================
        sendBtn = Button(this).apply {
            text = "✈️ ارسال پیامک به مخاطبین"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#C85A32"), Color.parseColor("#A83E1B"))
            ).apply { cornerRadius = dp(16).toFloat() }
            elevation = dp(8).toFloat()
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
            lp.topMargin = dp(12)
            layoutParams = lp
        }
        root.addView(progressBar)

        statusText = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#57534E"))
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(4))
        }
        root.addView(statusText)

        totalContactsReadView = TextView(this).apply {
            textSize = 11.5f
            setTextColor(Color.parseColor("#A8A29E"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(6))
        }
        root.addView(totalContactsReadView)

        // پاورقی اطمینان برند
        val footerText = TextView(this).apply {
            text = "✨ اعتماد شما، سرمایه ماست — املاک یلدا"
            textSize = 11.5f
            setTextColor(Color.parseColor("#A8A29E"))
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(8))
        }
        root.addView(footerText)

        scroll.addView(root)
        setContentView(scroll)

        setupToday()
        loadOptions()
        updateSimInfo()

        if (canReadContacts()) {
            loadContacts()
        }
        if (!hasPerms()) {
            requestPermissions(requiredPerms, 1)
        }
    }

    override fun onResume() {
        super.onResume()
        if (canReadContacts() && !sending) {
            loadContacts(false)
        }
        updateSimInfo()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        updateSimInfo()
        if (canReadContacts()) {
            loadContacts()
        }
        if (!hasPerms()) {
            Toast.makeText(this, "دسترسی پیامک و وضعیت تلفن جهت ارسال الزامی است", Toast.LENGTH_SHORT).show()
        }
    }

    private fun canReadContacts(): Boolean =
        checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    private fun hasPerms(): Boolean {
        for (p in requiredPerms) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) return false
        }
        return true
    }

    private val monthNames = arrayOf(
        "", "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )

    private fun setupToday() {
        val todayCal = Calendar.getInstance()
        val jy = gregorianToJalali(
            todayCal.get(Calendar.YEAR),
            todayCal.get(Calendar.MONTH) + 1,
            todayCal.get(Calendar.DAY_OF_MONTH)
        )
        val mName = if (jy[1] in 1..12) monthNames[jy[1]] else "${jy[1]}"
        val dayOfWeek = when (todayCal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SATURDAY -> "شنبه"
            Calendar.SUNDAY -> "یک‌شنبه"
            Calendar.MONDAY -> "دوشنبه"
            Calendar.TUESDAY -> "سه‌شنبه"
            Calendar.WEDNESDAY -> "چهارشنبه"
            Calendar.THURSDAY -> "پنج‌شنبه"
            Calendar.FRIDAY -> "جمعه"
            else -> ""
        }
        todayBadgeText.text = "امروز: $dayOfWeek ${jy[2]} $mName ${jy[0]}"
        resetToYesterdayDefault(false)
    }

    private fun resetToYesterdayDefault(showToast: Boolean = false) {
        targetCal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, -1)
        }
        syncDateFromTargetCal()
        if (showToast) {
            val mName = if (currentMonth in 1..12) monthNames[currentMonth] else "$currentMonth"
            Toast.makeText(this, "تاریخ پیش‌فرض (یک روز قبل): $currentDay $mName", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resetToToday(showToast: Boolean = false) {
        targetCal = Calendar.getInstance()
        syncDateFromTargetCal()
        if (showToast) {
            val mName = if (currentMonth in 1..12) monthNames[currentMonth] else "$currentMonth"
            Toast.makeText(this, "تاریخ روی امروز تنظیم شد: $currentDay $mName", Toast.LENGTH_SHORT).show()
        }
    }

    private fun syncDateFromTargetCal() {
        val jy = gregorianToJalali(
            targetCal.get(Calendar.YEAR),
            targetCal.get(Calendar.MONTH) + 1,
            targetCal.get(Calendar.DAY_OF_MONTH)
        )
        currentYear = jy[0]
        currentMonth = jy[1]
        currentDay = jy[2]
        todayKey = String.format("%02d%02d", currentMonth, currentDay)

        updateTargetDateLabel()
        rebuildCalendarStrip()
        loadOptions()
        refresh()
    }

    private fun updateTargetDateLabel() {
        if (!::targetDateView.isInitialized) return
        val mName = if (currentMonth in 1..12) monthNames[currentMonth] else "$currentMonth"
        targetDateView.text = "ارسال به مخاطبین روز $currentDay  ماه $mName  (همه سال‌ها)"

        val today = Calendar.getInstance()
        val todayJ = gregorianToJalali(today.get(Calendar.YEAR), today.get(Calendar.MONTH) + 1, today.get(Calendar.DAY_OF_MONTH))
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -1) }
        val yesterdayJ = gregorianToJalali(yesterday.get(Calendar.YEAR), yesterday.get(Calendar.MONTH) + 1, yesterday.get(Calendar.DAY_OF_MONTH))
        targetRelationBadge.text = when {
            currentYear == yesterdayJ[0] && currentMonth == yesterdayJ[1] && currentDay == yesterdayJ[2] -> "⚡ پیش‌فرض: یک روز قبل"
            currentYear == todayJ[0] && currentMonth == todayJ[1] && currentDay == todayJ[2] -> "📌 تاریخ امروز"
            else -> "📅 تاریخ انتخابی"
        }
    }

    /**
     * بازسازی نوار افقی روزهای تقویم (مشابه اسکرین‌شات ارسالی)
     */
    private fun rebuildCalendarStrip() {
        if (!::calendarStripContainer.isInitialized) return
        calendarStripContainer.removeAllViews()

        // ایجاد ستون‌های ۷ روز هفته با مرکزیت روز فعال
        val startCal = (targetCal.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, -3) }
        for (i in 0 until 7) {
            val c = (startCal.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, i) }
            val cJ = gregorianToJalali(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
            val isTarget = (cJ[1] == currentMonth && cJ[2] == currentDay)

            val dayCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                layoutParams = lp
            }

            val weekDayLetter = when (c.get(Calendar.DAY_OF_WEEK)) {
                Calendar.SATURDAY -> "ش"
                Calendar.SUNDAY -> "ی"
                Calendar.MONDAY -> "د"
                Calendar.TUESDAY -> "س"
                Calendar.WEDNESDAY -> "چ"
                Calendar.THURSDAY -> "پ"
                Calendar.FRIDAY -> "ج"
                else -> ""
            }

            val letterView = TextView(this).apply {
                text = weekDayLetter
                textSize = 11f
                setTextColor(if (isTarget) Color.parseColor("#C25E34") else Color.parseColor("#A8A29E"))
                setTypeface(null, if (isTarget) Typeface.BOLD else Typeface.NORMAL)
            }
            dayCol.addView(letterView)

            val numPill = TextView(this).apply {
                text = "${cJ[2]}"
                textSize = 12.5f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                if (isTarget) {
                    setTextColor(Color.WHITE)
                    background = GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        intArrayOf(Color.parseColor("#C85A32"), Color.parseColor("#A83E1B"))
                    ).apply { cornerRadius = dp(10).toFloat() }
                } else {
                    setTextColor(Color.parseColor("#292524"))
                    background = roundRect(Color.parseColor("#FAF7F2"), 8, 1, Color.parseColor("#E7DFD4"))
                }
                val lp = LinearLayout.LayoutParams(dp(32), dp(32))
                lp.topMargin = dp(4)
                layoutParams = lp
            }
            dayCol.addView(numPill)

            dayCol.setOnClickListener {
                targetCal = c
                syncDateFromTargetCal()
            }
            calendarStripContainer.addView(dayCol)
        }
    }

    private fun jalaliMonthLength(year: Int, month: Int): Int {
        val start = jalaliToGregorian(year, month, 1)
        val next = if (month == 12) jalaliToGregorian(year + 1, 1, 1) else jalaliToGregorian(year, month + 1, 1)
        fun epochDay(date: IntArray): Long {
            val calendar = GregorianCalendar(TimeZone.getTimeZone("UTC")).apply {
                clear()
                set(date[0], date[1] - 1, date[2], 0, 0, 0)
            }
            return calendar.timeInMillis / 86_400_000L
        }
        return (epochDay(next) - epochDay(start)).toInt().coerceIn(29, 31)
    }

    private fun showDatePickerDialog() {
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(8))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val prompt = TextView(this).apply {
            text = "روز و ماه مورد نظر جهت ارسال پیامک را انتخاب کنید:"
            textSize = 14f
            setTextColor(Color.parseColor("#1C1917"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, dp(12))
        }
        dialogView.addView(prompt)

        val spinRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val monthSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, (1..12).map { monthNames[it] })
            setSelection(currentMonth - 1)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lp.marginStart = dp(6)
            layoutParams = lp
        }

        val daySpinner = Spinner(this)
        fun updateDayChoices(month: Int, preferredDay: Int) {
            val maxDay = jalaliMonthLength(currentYear, month)
            daySpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, (1..maxDay).map { it.toString() })
            daySpinner.setSelection((preferredDay.coerceAtMost(maxDay) - 1).coerceAtLeast(0))
        }
        updateDayChoices(currentMonth, currentDay)
        val dayLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dp(6)
        }
        daySpinner.layoutParams = dayLp
        spinRow.addView(daySpinner)
        spinRow.addView(monthSpinner)
        dialogView.addView(spinRow)

        monthSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedDay = (daySpinner.selectedItemPosition + 1).coerceAtLeast(1)
                updateDayChoices(position + 1, selectedDay)
            }
        }

        AlertDialog.Builder(this)
            .setTitle("انتخاب تاریخ ارسال")
            .setView(dialogView)
            .setPositiveButton("تأیید و اعمال") { _, _ ->
                val chosenDay = daySpinner.selectedItemPosition + 1
                val chosenMonth = monthSpinner.selectedItemPosition + 1
                val gDate = jalaliToGregorian(currentYear, chosenMonth, chosenDay)
                targetCal = Calendar.getInstance().apply {
                    clear()
                    set(gDate[0], gDate[1] - 1, gDate[2], 12, 0, 0)
                }
                syncDateFromTargetCal()
                Toast.makeText(this, "تاریخ ارسال تغییر یافت: $chosenDay ${monthNames[chosenMonth]}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    // ---------------------------------------------------------------- گزینه‌های پیامک روز

    private fun loadOptions() {
        currentDailyOptions = DailySmsBank.getMessages(currentMonth, currentDay)
        selectedOptionIndex = prefs.getInt("selected_opt_$todayKey", 0)
        if (selectedOptionIndex !in 0 until 4) selectedOptionIndex = 0

        for (i in 0 until 4) {
            val optText = if (i in currentDailyOptions.indices) currentDailyOptions[i] else ""
            if (i < optionTextViews.size) {
                optionTextViews[i].text = optText
            }
        }

        updateOptionStyles()

        val savedMsg = prefs.getString("custom_msg_$todayKey", null)
        val defaultForOpt = if (selectedOptionIndex in currentDailyOptions.indices) {
            currentDailyOptions[selectedOptionIndex]
        } else {
            DailySmsBank.getMessage(currentMonth, currentDay)
        }
        msgEdit.setText(savedMsg ?: defaultForOpt)
    }

    private fun selectOption(index: Int) {
        if (index !in 0 until 4) return
        selectedOptionIndex = index
        prefs.edit().putInt("selected_opt_$todayKey", index).apply()
        updateOptionStyles()

        val newMsg = if (index in currentDailyOptions.indices) {
            currentDailyOptions[index]
        } else {
            DailySmsBank.getMessage(currentMonth, currentDay)
        }
        msgEdit.setText(newMsg)
        prefs.edit().putString("custom_msg_$todayKey", newMsg).apply()

        val optName = when (index) {
            0 -> "معرفی ملک ویژه"
            1 -> "پیگیری مشتری"
            2 -> "پیشنهاد ویژه"
            3 -> "پیام مناسبتی"
            else -> "گزینه ${index + 1}"
        }
        Toast.makeText(this, "قالب «$optName» انتخاب شد", Toast.LENGTH_SHORT).show()
    }

    private fun updateOptionStyles() {
        for (i in 0 until 4) {
            val isSel = (i == selectedOptionIndex)
            if (i < optionViews.size) {
                if (isSel) {
                    optionViews[i].background = roundRect(
                        Color.parseColor("#FFF8F4"),
                        14,
                        2,
                        Color.parseColor("#C25E34")
                    )
                    optionViews[i].elevation = dp(6).toFloat()
                } else {
                    optionViews[i].background = roundRect(
                        Color.WHITE,
                        14,
                        1,
                        Color.parseColor("#EBE5DC")
                    )
                    optionViews[i].elevation = dp(3).toFloat()
                }
            }
            if (i < optionRadioViews.size) {
                val rv = optionRadioViews[i]
                rv.text = if (isSel) "✓ انتخابی" else "انتخاب"
                rv.setTextColor(if (isSel) Color.parseColor("#C25E34") else Color.parseColor("#78716C"))
                rv.background = roundRect(
                    if (isSel) Color.parseColor("#FFF2EB") else Color.parseColor("#F5F5F4"),
                    6
                )
            }
        }
    }

    // ---------------------------------------------------------------- تبدیل دقیق تقویم شمسی/میلادی

    // تبدیل استاندارد تاریخ شمسی و میلادی، با آزمون روی تاریخ‌های متوالی
    private fun jalaliToGregorian(jy: Int, jm: Int, jd: Int): IntArray {
        val jyBase = jy - 979
        val jmBase = jm - 1
        val jdBase = jd - 1
        var jDayNo = 365 * jyBase + (jyBase / 33) * 8 + ((jyBase % 33 + 3) / 4)
        for (i in 0 until jmBase) {
            jDayNo += if (i < 6) 31 else 30
        }
        jDayNo += jdBase

        var gDayNo = jDayNo + 79
        var gy = 1600 + 400 * (gDayNo / 146097)
        gDayNo %= 146097
        var leap = true
        if (gDayNo >= 36525) {
            gDayNo -= 1
            gy += 100 * (gDayNo / 36524)
            gDayNo %= 36524
            if (gDayNo >= 365) gDayNo += 1 else leap = false
        }
        gy += 4 * (gDayNo / 1461)
        gDayNo %= 1461
        if (gDayNo >= 366) {
            leap = false
            gDayNo -= 1
            gy += gDayNo / 365
            gDayNo %= 365
        }

        val monthLengths = intArrayOf(31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var gm = 0
        while (gm < 12 && gDayNo >= monthLengths[gm]) {
            gDayNo -= monthLengths[gm]
            gm++
        }
        return intArrayOf(gy, gm + 1, gDayNo + 1)
    }

    private fun gregorianToJalali(gy: Int, gm: Int, gd: Int): IntArray {
        val gDaysBeforeMonth = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        var jy = if (gy > 1600) 979 else 0
        var adjustedGy = if (gy > 1600) gy - 1600 else gy - 621
        val gy2 = if (gm > 2) adjustedGy + 1 else adjustedGy
        var days = 365 * adjustedGy + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400 - 80 + gd + gDaysBeforeMonth[gm - 1]
        jy += 33 * (days / 12053)
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

    // ---------------------------------------------------------------- اپراتور و سیم‌کارت‌ها

    private fun detectOperator(phone: String): Operator {
        val p = normPhone(phone) ?: phone
        val clean = when {
            p.startsWith("+98") -> "0" + p.substring(3)
            p.startsWith("0098") -> "0" + p.substring(4)
            p.startsWith("98") -> "0" + p.substring(2)
            !p.startsWith("0") -> "0$p"
            else -> p
        }
        if (clean.length < 4) return Operator.OTHER
        val pre4 = clean.substring(0, 4)

        val mciPrefixes = setOf(
            "0910", "0911", "0912", "0913", "0914", "0915", "0916", "0917", "0918", "0919", "0990", "0991", "0992", "0993", "0994", "0996"
        )
        val mtnPrefixes = setOf(
            "0930", "0933", "0935", "0936", "0937", "0938", "0939", "0901", "0902", "0903", "0904", "0905", "0941"
        )
        val rightelPrefixes = setOf("0920", "0921", "0922")

        return when {
            mciPrefixes.contains(pre4) -> Operator.MCI
            mtnPrefixes.contains(pre4) -> Operator.IRANCELL
            rightelPrefixes.contains(pre4) -> Operator.RIGHTEL
            else -> Operator.OTHER
        }
    }

    private fun getActiveSimSlots(): List<SimSlot> {
        val result = ArrayList<SimSlot>()
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return result
        }
        val sm = getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager ?: return result
        try {
            val list = sm.activeSubscriptionInfoList ?: return result
            for (info in list) {
                val subId = info.subscriptionId
                val slot = info.simSlotIndex + 1
                val displayName = info.displayName?.toString() ?: "سیم $slot"
                val carrier = info.carrierName?.toString() ?: ""
                val combined = "$displayName $carrier".lowercase()

                val op = when {
                    combined.contains("mci") || combined.contains("hamrah") || combined.contains("همراه") -> Operator.MCI
                    combined.contains("mtn") || combined.contains("irancell") || combined.contains("ایرانسل") -> Operator.IRANCELL
                    combined.contains("rightel") || combined.contains("رایتل") -> Operator.RIGHTEL
                    else -> if (slot == 1) Operator.MCI else Operator.IRANCELL
                }
                result.add(SimSlot(subId, slot, displayName, op))
            }
        } catch (_: Exception) {}
        return result
    }

    fun updateSimInfo() {
        if (!::simSummaryText.isInitialized) return
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            simSummaryText.text = "⚠️ دسترسی تلفن برای تقسیم خودکار سیم‌کارت‌ها لازم است."
            return
        }
        val slots = getActiveSimSlots()
        if (slots.isEmpty()) {
            simSummaryText.text = "سیم‌کارت فعال شناسایی نشد (ارسال با سیم‌کارت پیش‌فرض انجام می‌شود)."
            return
        }

        if (slots.size >= 1) {
            val s1 = slots[0]
            val opName = when (s1.op) {
                Operator.MCI -> "همراه اول"
                Operator.IRANCELL -> "ایرانسل"
                Operator.RIGHTEL -> "رایتل"
                Operator.OTHER -> "فعال"
            }
            sim1StatusText.text = "$opName • فعال"
        }
        if (slots.size >= 2) {
            val s2 = slots[1]
            val opName = when (s2.op) {
                Operator.MCI -> "همراه اول"
                Operator.IRANCELL -> "ایرانسل"
                Operator.RIGHTEL -> "رایتل"
                Operator.OTHER -> "فعال"
            }
            sim2StatusText.text = "$opName • فعال"
        } else {
            sim2StatusText.text = "غیرفعال"
        }

        if (slots.size == 1) {
            simSummaryText.text = "یک سیم‌کارت فعال شناسایی شد. همه پیامک‌ها از این خط ارسال می‌شوند."
        } else {
            simSummaryText.text = "هر دو سیم‌کارت فعال هستند. پیامک‌ها با بالانس مساوی (۵۰/۵۰) بین دو سیم تقسیم می‌شوند."
        }
    }

    private fun getSmsManagerForSub(subId: Int): SmsManager {
        return if (subId != -1) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val ctx = createAttributionContext(null)
                ctx.getSystemService(SmsManager::class.java).createForSubscriptionId(subId)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getSmsManagerForSubscriptionId(subId)
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
        }
    }

    // ---------------------------------------------------------------- مخاطبین و ارسال

    private fun latinDigits(s: String): String {
        val fa = "۰۱۲۳۴۵۶۷۸۹"
        val ar = "٠١٢٣٤٥٦٧٨٩"
        val sb = StringBuilder()
        for (c in s) {
            val ifa = fa.indexOf(c)
            val iar = ar.indexOf(c)
            when {
                ifa >= 0 -> sb.append(ifa)
                iar >= 0 -> sb.append(iar)
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    /**
     * استخراج ماه و روز شمسی از نام مخاطب:
     * پشتیبانی کامل از:
     * ۱) ۱۴۰۵۰۹۰۳ یا ۱۴۰۴۰۹۰۳ (۸ رقم پیوسته با سال ۴ رقمی)
     * ۲) ۱۴۰۵/۰۹/۰۳ یا ۱۴۰۵-۰۹-۰۳ یا ۱۴۰۵.۰۹.۰۳
     * ۳) ۱۴۰۵/۹/۳ یا ۱۴۰۵-۹-۳ (ماه یا روز تک‌رقمی با جداکننده)
     * ۴) ۰۹/۰۳ یا ۹/۳ بدون سال
     * ۵) ۰۹۰۳ (۴ رقم ماه و روز)
     */
    private fun parseMd(name: String): Int? {
        val s = latinDigits(name).trim()

        // ۱) ۴ رقم سال در ابتدای نام (مثل 14050904 یا 1405/9/4 یا [1405/09/04] یا (14050904))
        val m1 = Regex("""^[\[\(\{\s]*(\d{4})[/\-. ]?(\d{1,2})[/\-. ]?(\d{1,2})([\]\)\}\\s]|$)""").find(s)
        if (m1 != null) {
            val mo = m1.groupValues[2].toIntOrNull() ?: return null
            val da = m1.groupValues[3].toIntOrNull() ?: return null
            if (mo in 1..12 && da in 1..31) return mo * 100 + da
        }

        // ۲) ماه و روز با جداکننده در ابتدای نام بدون سال (مثل 09/04 یا 9/4 یا 09-04)
        val m2 = Regex("""^[\[\(\{\s]*(\d{1,2})[/\-. ]+(\d{1,2})([\]\)\}\\s]|$)""").find(s)
        if (m2 != null) {
            val mo = m2.groupValues[1].toIntOrNull() ?: return null
            val da = m2.groupValues[2].toIntOrNull() ?: return null
            if (mo in 1..12 && da in 1..31) return mo * 100 + da
        }

        // ۳) ۴ رقم ماه و روز فشرده در ابتدای نام (مثل 0904 علی)
        val m3 = Regex("""^[\[\(\{\s]*(0[1-9]|1[0-2])(0[1-9]|[12]\d|3[01])([\]\)\}\\s]|$)""").find(s)
        if (m3 != null) {
            val mo = m3.groupValues[1].toIntOrNull() ?: return null
            val da = m3.groupValues[2].toIntOrNull() ?: return null
            return mo * 100 + da
        }

        return null
    }

    private fun normPhone(raw: String): String? {
        val digits = latinDigits(raw).filter { it.isDigit() }
        var d = digits
        if (d.startsWith("0098")) d = d.substring(4)
        else if (d.startsWith("98") && d.length == 12) d = d.substring(2)
        if (d.length == 10 && d.startsWith("9")) d = "0$d"
        return if (Regex("""^09\d{9}$""").matches(d)) d else null
    }

    private fun loadContacts(showToast: Boolean = false) {
        if (!canReadContacts()) return
        ui.post {
            statusText.text = "⏳ در حال خواندن مخاطبین دفترچه تلفن..."
        }
        Thread {
            val list = ArrayList<Contact>()
            var totalRead = 0
            try {
                val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                val proj = arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                )
                val cursor = contentResolver.query(uri, proj, null, null, null)
                cursor?.use { c ->
                    val nameIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    while (c.moveToNext()) {
                        totalRead++
                        val name = if (nameIdx >= 0) c.getString(nameIdx) ?: "" else ""
                        val num = if (numIdx >= 0) c.getString(numIdx) ?: "" else ""
                        val md = parseMd(name)
                        if (md != null) {
                            val p = normPhone(num)
                            if (p != null) list.add(Contact(name, p, md))
                        }
                    }
                }
            } catch (e: Exception) {
                ui.post {
                    statusText.text = "خطا در دسترسی به مخاطبین: ${e.message}"
                }
            }

            contacts = list
            ui.post {
                refresh()
                if (showToast) {
                    val md = targetMd()
                    val mName = if (currentMonth in 1..12) monthNames[currentMonth] else "$currentMonth"
                    val todayCount = list.count { it.md == md }
                    Toast.makeText(this@MainActivity, "مخاطبین بروز شدند: $todayCount شماره برای $currentDay $mName", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    /**
     * دیالوگ نمایش لیست مخاطبین و شماره‌های فیلتر شده برای تاریخ انتخابی
     */
    private fun showContactsListDialog() {
        val md = targetMd()
        val matching = contacts.filter { it.md == md }
        val mName = if (currentMonth in 1..12) monthNames[currentMonth] else "$currentMonth"

        val byPhone = LinkedHashMap<String, Contact>()
        for (c in matching) {
            if (!byPhone.containsKey(c.phone)) byPhone[c.phone] = c
        }

        if (byPhone.isEmpty()) {
            Toast.makeText(this, "هیچ شماره‌ای برای روز $currentDay ماه $mName یافت نشد. اطمینان یابید تاریخ در نام مخاطب ثبت شده باشد.", Toast.LENGTH_LONG).show()
            return
        }

        val scroll = ScrollView(this).apply {
            setPadding(dp(16), dp(12), dp(16), dp(12))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val sent = sentSet()
        var idx = 1
        for ((_, c) in byPhone) {
            val isSent = sent.contains(c.phone)
            val op = detectOperator(c.phone)
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = roundRect(Color.parseColor(if (isSent) "#F5F5F4" else "#FFFFFF"), 10, 1, Color.parseColor("#E7E5E4"))
                setPadding(dp(12), dp(8), dp(12), dp(8))
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.bottomMargin = dp(6)
                layoutParams = lp
            }

            val nameCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                layoutParams = lp
            }
            val numView = TextView(this).apply {
                text = "$idx. ${c.name}"
                textSize = 13.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#1C1917"))
            }
            nameCol.addView(numView)
            item.addView(nameCol)

            val phoneCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.END
            }
            val pView = TextView(this).apply {
                text = c.phone
                textSize = 13f
                setTypeface(Typeface.MONOSPACE)
                setTextColor(Color.parseColor("#44403C"))
            }
            phoneCol.addView(pView)

            val opName = when (op) {
                Operator.MCI -> "همراه اول"
                Operator.IRANCELL -> "ایرانسل"
                Operator.RIGHTEL -> "رایتل"
                Operator.OTHER -> "سایر"
            }
            val tagView = TextView(this).apply {
                text = if (isSent) "✓ ارسال شده" else opName
                textSize = 10.5f
                setTextColor(Color.parseColor(if (isSent) "#16A34A" else "#C25E34"))
                setTypeface(null, Typeface.BOLD)
            }
            phoneCol.addView(tagView)
            item.addView(phoneCol)

            listContainer.addView(item)
            idx++
        }

        scroll.addView(listContainer)

        AlertDialog.Builder(this)
            .setTitle("مخاطبین روز $currentDay ماه $mName (${byPhone.size} شماره)")
            .setView(scroll)
            .setPositiveButton("بستن", null)
            .show()
    }

    private fun targetMd(): Int = currentMonth * 100 + currentDay

    private fun sentSet(): Set<String> {
        return prefs.getStringSet("sent_$todayKey", emptySet()) ?: emptySet()
    }

    private fun pending(): List<Contact> {
        val md = targetMd()
        val sent = sentSet()
        val forToday = contacts.filter { it.md == md }
        val byPhone = LinkedHashMap<String, Contact>()
        for (c in forToday) {
            if (!sent.contains(c.phone) && !byPhone.containsKey(c.phone)) {
                byPhone[c.phone] = c
            }
        }
        return byPhone.values.toList()
    }

    private fun refresh() {
        if (!::totalCountBadge.isInitialized) return
        val md = targetMd()
        val mName = if (currentMonth in 1..12) monthNames[currentMonth] else "$currentMonth"
        val matchingContacts = contacts.filter { it.md == md }
        val total = matchingContacts.map { it.phone }.distinct().size
        val left = pending().size

        totalCountBadge.text = "$total مخاطب برای $currentDay $mName"
        if (::statsSubtitle.isInitialized) {
            statsSubtitle.text = "فقط مخاطبین روز $currentDay ماه $mName (همه سال‌ها)"
        }
        pendingCountBadge.text = if (left == 0 && total > 0) "✓ تماماً ارسال شد" else "● $left در صف"
        sendBtn.text = if (left > 0) "✈️ ارسال پیامک به $left مخاطب ($currentDay $mName)" else "✈️ ارسال پیامک به مخاطبین ($currentDay $mName)"

        if (::viewListBtn.isInitialized) {
            viewListBtn.text = "📋 مشاهده $total شماره روز $currentDay $mName"
        }
        if (::totalContactsReadView.isInitialized) {
            totalContactsReadView.text = "مخاطبین تاریخ‌دار: ${contacts.size} کل | فیلتر روز $currentDay $mName: $total شماره"
        }
        if (::statusText.isInitialized && !sending) {
            statusText.text = if (left > 0) "آماده برای ارسال به $left مخاطب ($currentDay $mName)" else if (total > 0) "تمامی پیامک‌های روز $currentDay $mName ارسال شده‌اند" else "هیچ مخاطبی برای روز $currentDay $mName یافت نشد"
        }
    }

    private fun planSendingTasks(phones: List<String>, slots: List<SimSlot>): List<SendTask> {
        if (slots.isEmpty()) {
            return phones.map { SendTask(it, -1, "پیش‌فرض", "پیش‌فرض") }
        }
        if (slots.size == 1) {
            val s = slots[0]
            return phones.map { SendTask(it, s.subId, "سیم ${s.slotIndex} (${s.name})", "تک‌سیم") }
        }

        val sim1 = slots[0]
        val sim2 = slots[1]

        val total = phones.size
        val quota1 = (total + 1) / 2
        val quota2 = total - quota1

        val sim1Tasks = ArrayList<SendTask>()
        val sim2Tasks = ArrayList<SendTask>()
        val unassigned = ArrayList<Pair<String, Operator>>()

        for (phone in phones) {
            val op = detectOperator(phone)
            if (op == sim1.op && sim1Tasks.size < quota1) {
                sim1Tasks.add(SendTask(phone, sim1.subId, "سیم ۱ (${sim1.name})", "تطبیق اپراتور"))
            } else if (op == sim2.op && sim2Tasks.size < quota2) {
                sim2Tasks.add(SendTask(phone, sim2.subId, "سیم ۲ (${sim2.name})", "تطبیق اپراتور"))
            } else {
                unassigned.add(Pair(phone, op))
            }
        }

        for ((phone, _) in unassigned) {
            if (sim1Tasks.size < quota1) {
                sim1Tasks.add(SendTask(phone, sim1.subId, "سیم ۱ (${sim1.name})", "بالانس ۵۰٪"))
            } else if (sim2Tasks.size < quota2) {
                sim2Tasks.add(SendTask(phone, sim2.subId, "سیم ۲ (${sim2.name})", "بالانس ۵۰٪"))
            } else {
                sim1Tasks.add(SendTask(phone, sim1.subId, "سیم ۱ (${sim1.name})", "سرریز"))
            }
        }

        val finalTasks = ArrayList<SendTask>(total)
        var i1 = 0
        var i2 = 0
        while (i1 < sim1Tasks.size || i2 < sim2Tasks.size) {
            if (i1 < sim1Tasks.size) finalTasks.add(sim1Tasks[i1++])
            if (i2 < sim2Tasks.size) finalTasks.add(sim2Tasks[i2++])
        }
        return finalTasks
    }

    private fun onSendClicked() {
        if (!hasPerms()) {
            requestPermissions(requiredPerms, 1)
            return
        }
        if (sending) {
            Toast.makeText(this, "ارسال در حال انجام است...", Toast.LENGTH_SHORT).show()
            return
        }
        val toSend = pending()
        if (toSend.isEmpty()) {
            Toast.makeText(this, "مخاطبی با تاریخ انتخابی برای ارسال وجود ندارد", Toast.LENGTH_LONG).show()
            return
        }
        val text = msgEdit.text.toString().trim()
        if (text.isEmpty()) {
            Toast.makeText(this, "متن پیامک نمی‌تواند خالی باشد", Toast.LENGTH_SHORT).show()
            return
        }

        val slots = getActiveSimSlots()
        val count = toSend.size
        val mName = if (currentMonth in 1..12) monthNames[currentMonth] else "$currentMonth"

        val simPlanDesc = if (slots.size >= 2) {
            val q1 = (count + 1) / 2
            val q2 = count - q1
            "تقسیم متوازن: $q1 پیام از سیم ۱ و $q2 پیام از سیم ۲"
        } else if (slots.size == 1) {
            "ارسال از سیم‌کارت ۱ (${slots[0].name})"
        } else {
            "ارسال از سیم‌کارت پیش‌فرض"
        }

        AlertDialog.Builder(this)
            .setTitle("تأیید ارسال پیامک‌ها")
            .setMessage(
                "تعداد: $count پیامک برای مخاطبین $currentDay $mName\n" +
                "$simPlanDesc\n\n" +
                "متن پیام:\n$text\n\n" +
                "آیا ارسال آغاز شود؟"
            )
            .setPositiveButton("شروع ارسال") { _, _ -> startSending(toSend, text, slots) }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun startSending(toSend: List<Contact>, text: String, slots: List<SimSlot>) {
        sending = true
        sendBtn.isEnabled = false
        progressBar.visibility = View.VISIBLE
        progressBar.max = toSend.size
        progressBar.progress = 0

        val phones = toSend.map { it.phone }
        val tasks = planSendingTasks(phones, slots)

        Thread {
            sendNext(tasks, 0, text, 0, 0)
        }.start()
    }

    private fun sendNext(tasks: List<SendTask>, index: Int, text: String, okCount: Int, failCount: Int) {
        if (index >= tasks.size) {
            ui.post {
                sending = false
                sendBtn.isEnabled = true
                progressBar.visibility = View.GONE
                statusText.text = "ارسال پایان یافت: $okCount موفق، $failCount ناموفق"
                refresh()
                Toast.makeText(this, "ارسال به اتمام رسید ($okCount موفق)", Toast.LENGTH_LONG).show()
            }
            return
        }

        val task = tasks[index]
        var ok = false
        try {
            val mgr = getSmsManagerForSub(task.subId)
            val parts = mgr.divideMessage(text)
            if (parts.size > 1) {
                mgr.sendMultipartTextMessage(task.phone, null, parts, null, null)
            } else {
                mgr.sendTextMessage(task.phone, null, text, null, null)
            }
            ok = true
        } catch (_: Exception) {}

        if (ok) {
            val set = HashSet(sentSet())
            set.add(task.phone)
            prefs.edit().putStringSet("sent_$todayKey", set).apply()
        }

        val nextOk = if (ok) okCount + 1 else okCount
        val nextFail = if (ok) failCount else failCount + 1

        ui.post {
            progressBar.progress = index + 1
            statusText.text = "در حال ارسال (${index + 1}/${tasks.size}) از طریق ${task.simLabel}..."
        }

        try { Thread.sleep(2500) } catch (_: InterruptedException) {}
        sendNext(tasks, index + 1, text, nextOk, nextFail)
    }
}
