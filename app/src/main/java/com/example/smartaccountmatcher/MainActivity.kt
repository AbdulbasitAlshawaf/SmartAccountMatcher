package com.example.smartaccountmatcher

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.InputStream
import java.util.Locale

class MainActivity : Activity() {
    private val pickFirst = 101
    private val pickSecond = 102
    private var firstText = ""
    private var secondText = ""
    private lateinit var firstStatus: TextView
    private lateinit var secondStatus: TextView
    private lateinit var results: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PDFBoxResourceLoader.init(applicationContext)
        buildScreen()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 24)
            setBackgroundColor(Color.rgb(247, 249, 252))
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(TextView(this).apply {
            text = "المطابق الذكي للمحاسبة"
            textSize = 25f
            setTextColor(Color.rgb(20, 67, 105))
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 10)
        })
        content.addView(TextView(this).apply {
            text = "مطابقة كشوفات حساب الشركات — PDF وExcel"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.DKGRAY)
            setPadding(0, 0, 0, 22)
        })
        firstStatus = status("لم يتم اختيار كشف الشركة الأولى")
        secondStatus = status("لم يتم اختيار كشف الشركة الثانية")
        results = TextView(this).apply {
            text = "اختر كشفين بصيغة PDF أو Excel ثم ابدأ المطابقة."
            textSize = 15f
            setTextColor(Color.DKGRAY)
            setPadding(12, 18, 12, 18)
        }
        content.addView(button("اختيار كشف الشركة الأولى") { chooseFile(pickFirst) })
        content.addView(firstStatus)
        content.addView(button("اختيار كشف الشركة الثانية") { chooseFile(pickSecond) })
        content.addView(secondStatus)
        content.addView(button("مطابقة الحسابات") { compareStatements() })
        content.addView(button("مسح الاختيارات") { resetAll() })
        content.addView(results)
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun status(value: String) = TextView(this).apply {
        text = value
        textSize = 14f
        setTextColor(Color.rgb(75, 85, 99))
        setPadding(8, 8, 8, 16)
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 16f
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = 8
        }
    }

    private fun chooseFile(requestCode: Int) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "application/pdf",
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            ))
        }
        startActivityForResult(intent, requestCode)
    }

    @Deprecated("Legacy activity result API used for compatibility in this starter project")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) return
        val uri = data.data!!
        try {
            val name = getDisplayName(uri)
            val extracted = contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "تعذر فتح الملف" }
                when {
                    name.endsWith(".pdf", ignoreCase = true) || mime(uri) == "application/pdf" -> readPdf(input)
                    name.endsWith(".xls", ignoreCase = true) ||
                    name.endsWith(".xlsx", ignoreCase = true) ||
                    mime(uri).contains("spreadsheet") || mime(uri).contains("excel") -> readExcel(input)
                    else -> throw IllegalArgumentException("صيغة الملف غير مدعومة")
                }
            }
            if (requestCode == pickFirst) {
                firstText = extracted
                firstStatus.text = "الكشف الأول: $name — تمت القراءة"
            } else if (requestCode == pickSecond) {
                secondText = extracted
                secondStatus.text = "الكشف الثاني: $name — تمت القراءة"
            }
            Toast.makeText(this, "تمت قراءة الملف", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "تعذرت قراءة الملف. استخدم PDF نصي أو Excel بصيغة XLS/XLSX.", Toast.LENGTH_LONG).show()
        }
    }

    private fun readPdf(input: InputStream): String {
        PDDocument.load(input).use { document -> return PDFTextStripper().getText(document) }
    }

    private fun readExcel(input: InputStream): String {
        val out = StringBuilder()
        WorkbookFactory.create(input).use { workbook ->
            for (sheet in workbook) {
                out.append("الورقة: ").append(sheet.sheetName).append('\n')
                for (row in sheet) {
                    val values = mutableListOf<String>()
                    for (cell in row) {
                        values.add(cell.toString().trim())
                    }
                    out.append(values.joinToString(" | ")).append('\n')
                }
            }
        }
        return out.toString()
    }

    private fun mime(uri: Uri): String = contentResolver.getType(uri) ?: ""

    private fun getDisplayName(uri: Uri): String {
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && it.moveToFirst()) return it.getString(index) ?: "file"
        }
        return uri.lastPathSegment ?: "file"
    }

    private fun compareStatements() {
        if (firstText.isBlank() || secondText.isBlank()) {
            results.text = "يجب اختيار الكشفين وقراءتهما أولاً."
            return
        }
        val firstNumbers = extractNumbers(firstText)
        val secondNumbers = extractNumbers(secondText)
        val onlyFirst = firstNumbers.subtract(secondNumbers)
        val onlySecond = secondNumbers.subtract(firstNumbers)
        val out = StringBuilder()
        out.append("نتيجة الفحص الأولي\n")
        out.append("────────────────────\n")
        out.append("عدد القيم الرقمية المختلفة في الكشف الأول: ${firstNumbers.size}\n")
        out.append("عدد القيم الرقمية المختلفة في الكشف الثاني: ${secondNumbers.size}\n\n")
        out.append("قيم في الكشف الأول ولم تظهر في الثاني:\n")
        out.append(if (onlyFirst.isEmpty()) "لا توجد قيم فريدة حسب الفحص الأولي.\n" else onlyFirst.sorted().joinToString("\n") { "• $it" } + "\n")
        out.append("\nقيم في الكشف الثاني ولم تظهر في الأول:\n")
        out.append(if (onlySecond.isEmpty()) "لا توجد قيم فريدة حسب الفحص الأولي.\n" else onlySecond.sorted().joinToString("\n") { "• $it" } + "\n")
        out.append("\nتنبيه: هذه مقارنة أولية للقيم الرقمية فقط، وليست مطابقة محاسبية نهائية. يلزم تطوير تحليل صفوف الحركات والتواريخ والمدين والدائن والرصيد واختبارها على كشوف فعلية.")
        results.text = out.toString()
    }

    private fun extractNumbers(text: String): Set<String> {
        val normalized = text
            .replace('،', ',')
            .replace('٫', '.')
            .replace('٬', ',')
            .replace(Regex("[٠-٩]")) { (it.value[0] - '٠').toString() }
            .replace(Regex("[۰-۹]")) { (it.value[0] - '۰').toString() }
        val regex = Regex("(?<![\\p{L}])(?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d{1,2})?(?![\\p{L}])")
        return regex.findAll(normalized)
            .map { it.value.replace(",", "") }
            .filter { it.toDoubleOrNull() != null }
            .toSet()
    }

    private fun resetAll() {
        firstText = ""
        secondText = ""
        firstStatus.text = "لم يتم اختيار كشف الشركة الأولى"
        secondStatus.text = "لم يتم اختيار كشف الشركة الثانية"
        results.text = "اختر كشفين بصيغة PDF أو Excel ثم ابدأ المطابقة."
    }
}
