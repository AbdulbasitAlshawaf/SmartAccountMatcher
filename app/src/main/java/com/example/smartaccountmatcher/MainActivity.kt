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
            root.setBackgroundResource(R.drawable.background)
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 24)
            setBackgroundColor(Color.rgb(247, 249, 252))
        }

        val scroll = ScrollView(this)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        content.addView(TextView(this).apply {
            text = "المطابق الذكي للمحاسبة"
            textSize = 25f
            setTextColor(Color.rgb(20, 67, 105))
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 10)
        })

        content.addView(TextView(this).apply {
            text = "قراءة ومقارنة كشوفات الحساب PDF وExcel"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.DKGRAY)
            setPadding(0, 0, 0, 22)
        })

        firstStatus = status("لم يتم اختيار الكشف الأول")
        secondStatus = status("لم يتم اختيار الكشف الثاني")

        results = TextView(this).apply {
            text = "اختر كشفين ثم اضغط مطابقة الحسابات."
            textSize = 15f
            setTextColor(Color.DKGRAY)
            setPadding(12, 18, 12, 18)
        }

        content.addView(button("اختيار كشف الشركة الأولى") {
            chooseFile(pickFirst)
        })

        content.addView(firstStatus)

        content.addView(button("اختيار كشف الشركة الثانية") {
            chooseFile(pickSecond)
        })

        content.addView(secondStatus)

        content.addView(button("مطابقة الحسابات") {
            compareStatements()
        })

        content.addView(button("مسح الاختيارات") {
            resetAll()
        })

        content.addView(results)

        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(
            -1, 0, 1f
        ))

        setContentView(root)
    }

    private fun status(value: String) = TextView(this).apply {
        text = value
        textSize = 14f
        setTextColor(Color.rgb(75, 85, 99))
        setPadding(8, 8, 8, 16)
    }

    private fun button(
        label: String,
        action: () -> Unit
    ) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 16f

        setOnClickListener {
            action()
        }

        layoutParams = LinearLayout.LayoutParams(
            -1,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = 8
        }
    }

    private fun chooseFile(requestCode: Int) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"

            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf(
                    "application/pdf",
                    "application/vnd.ms-excel",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                )
            )
        }

        startActivityForResult(intent, requestCode)
    }

    @Deprecated("Legacy activity result API")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != RESULT_OK || data?.data == null) {
            return
        }

        val uri = data.data!!

        try {
            val name = getDisplayName(uri)

            val extracted = contentResolver
                .openInputStream(uri)
                .use { input ->

                    requireNotNull(input) {
                        "تعذر فتح الملف"
                    }

                    when {
                        name.endsWith(".pdf", true) ||
                            mime(uri) == "application/pdf" -> {
                            readPdf(input)
                        }

                        name.endsWith(".xls", true) ||
                            name.endsWith(".xlsx", true) ||
                            mime(uri).contains("spreadsheet") ||
                            mime(uri).contains("excel") -> {
                            readExcel(input)
                        }

                        else -> {
                            throw IllegalArgumentException(
                                "صيغة الملف غير مدعومة"
                            )
                        }
                    }
                }

            if (extracted.isBlank()) {
                throw IllegalArgumentException(
                    "لم يتم استخراج نص من الملف"
                )
            }

            if (requestCode == pickFirst) {
                firstText = extracted
                firstStatus.text =
                    "الكشف الأول: $name — تمت القراءة"
            } else if (requestCode == pickSecond) {
                secondText = extracted
                secondStatus.text =
                    "الكشف الثاني: $name — تمت القراءة"
            }

            Toast.makeText(
                this,
                "تمت قراءة الملف بنجاح",
                Toast.LENGTH_SHORT
            ).show()

        } catch (e: Exception) {
            Toast.makeText(
                this,
                "تعذرت قراءة الملف: ${e.message ?: "خطأ غير معروف"}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun readPdf(input: InputStream): String {
        PDDocument.load(input).use { document ->
            return PDFTextStripper().getText(document)
        }
    }

    private fun readExcel(input: InputStream): String {
        val out = StringBuilder()

        WorkbookFactory.create(input).use { workbook ->

            for (sheet in workbook) {
                out.append("الورقة: ")
                    .append(sheet.sheetName)
                    .append('\n')

                for (row in sheet) {
                    val values = mutableListOf<String>()

                    for (cell in row) {
                        values.add(cell.toString().trim())
                    }

                    out.append(values.joinToString(" | "))
                        .append('\n')
                }
            }
        }

        return out.toString()
    }

    private fun mime(uri: Uri): String {
        return contentResolver.getType(uri) ?: ""
    }

    private fun getDisplayName(uri: Uri): String {
        val cursor = contentResolver.query(
            uri,
            null,
            null,
            null,
            null
        )

        cursor?.use {
            val index = it.getColumnIndex(
                android.provider.OpenableColumns.DISPLAY_NAME
            )

            if (index >= 0 && it.moveToFirst()) {
                return it.getString(index) ?: "file"
            }
        }

        return uri.lastPathSegment ?: "file"
    }

    // توحيد الأرقام العربية والهندية والإنجليزية
    private fun normalizeDigits(text: String): String {
        return text
            .replace(Regex("[٠-٩]")) {
                (it.value[0] - '٠').toString()
            }
            .replace(Regex("[۰-۹]")) {
                (it.value[0] - '۰').toString()
            }
    }

    // توحيد شكل السطر لتقليل الاختلافات الناتجة عن التنسيق
    private fun normalizeLine(line: String): String {
        return normalizeDigits(line)
            .replace('٫', '.')
            .replace('٬', ',')
            .replace('،', ',')
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase(Locale.ROOT)
    }

    // استخراج السطور التي تحتوي على بيانات
    private fun extractRows(text: String): List<String> {
        return text
            .split('\n', '\r')
            .map { it.trim() }
            .filter { it.length >= 2 }
    }

    private fun compareStatements() {
        if (firstText.isBlank() || secondText.isBlank()) {
            results.text =
                "يجب اختيار الكشفين وقراءتهما أولاً."
            return
        }

        val firstRows = extractRows(firstText)
        val secondRows = extractRows(secondText)

        if (firstRows.isEmpty() || secondRows.isEmpty()) {
            results.text =
                "لم يتم العثور على سطور قابلة للمقارنة."
            return
        }

        // نحفظ عدد مرات ظهور كل سطر حتى لا تختفي الحركات المكررة
        val firstMap = firstRows.groupBy {
            normalizeLine(it)
        }

        val secondMap = secondRows.groupBy {
            normalizeLine(it)
        }

        val firstKeys = firstMap.keys
        val secondKeys = secondMap.keys

        val commonKeys = firstKeys.intersect(secondKeys)

        var matchedCount = 0

        for (key in commonKeys) {
            val firstCount = firstMap[key]?.size ?: 0
            val secondCount = secondMap[key]?.size ?: 0

            matchedCount += minOf(firstCount, secondCount)
        }

        val onlyFirst = mutableListOf<String>()
        val onlySecond = mutableListOf<String>()

        // السطور الموجودة في الكشف الأول أكثر من الكشف الثاني
        for ((key, rows) in firstMap) {
            val secondCount = secondMap[key]?.size ?: 0
            val extraCount = rows.size - secondCount

            if (extraCount > 0) {
                repeat(extraCount) { index ->
                    onlyFirst.add(rows[index])
                }
            }
        }

        // السطور الموجودة في الكشف الثاني أكثر من الكشف الأول
        for ((key, rows) in secondMap) {
            val firstCount = firstMap[key]?.size ?: 0
            val extraCount = rows.size - firstCount

            if (extraCount > 0) {
                repeat(extraCount) { index ->
                    onlySecond.add(rows[index])
                }
            }
        }

        // اكتشاف السطور المتكررة داخل كل كشف
        val duplicatesFirst = firstMap.values
            .filter { it.size > 1 }
            .flatten()

        val duplicatesSecond = secondMap.values
            .filter { it.size > 1 }
            .flatten()

        val out = StringBuilder()

        out.append("نتيجة مقارنة الكشوفات\n")
        out.append("====================\n\n")

        out.append("عدد السطور في الكشف الأول: ")
            .append(firstRows.size)
            .append('\n')

        out.append("عدد السطور في الكشف الثاني: ")
            .append(secondRows.size)
            .append('\n')

        out.append("عدد السطور المتطابقة: ")
            .append(matchedCount)
            .append("\n\n")

        out.append("السطور الزائدة أو غير المتطابقة في الكشف الأول:\n")

        if (onlyFirst.isEmpty()) {
            out.append("لا توجد سطور زائدة وفق المقارنة الحالية.\n")
        } else {
            onlyFirst.forEachIndexed { index, row ->
                out.append(index + 1)
                    .append(". ")
                    .append(row)
                    .append('\n')
            }
        }

        out.append("\nالسطور الزائدة أو غير المتطابقة في الكشف الثاني:\n")

        if (onlySecond.isEmpty()) {
            out.append("لا توجد سطور زائدة وفق المقارنة الحالية.\n")
        } else {
            onlySecond.forEachIndexed { index, row ->
                out.append(index + 1)
                    .append(". ")
                    .append(row)
                    .append('\n')
            }
        }

        out.append("\nالسطور المكررة في الكشف الأول:\n")

        if (duplicatesFirst.isEmpty()) {
            out.append("لم تُكتشف سطور مكررة.\n")
        } else {
            duplicatesFirst.forEach {
                out.append("• ")
                    .append(it)
                    .append('\n')
            }
        }

        out.append("\nالسطور المكررة في الكشف الثاني:\n")

        if (duplicatesSecond.isEmpty()) {
            out.append("لم تُكتشف سطور مكررة.\n")
        } else {
            duplicatesSecond.forEach {
                out.append("• ")
                    .append(it)
                    .append('\n')
            }
        }

        out.append(
            "\nتنبيه مهم: هذه مقارنة أولية للسطور، " +
                "وليست تدقيقاً محاسبياً نهائياً. " +
                "قد تظهر اختلافات بسبب ترتيب الأعمدة أو " +
                "تنسيق الكشوفات. يجب اختبار النتائج قبل " +
                "اعتمادها محاسبياً."
        )

        results.text = out.toString()
    }

    private fun resetAll() {
        firstText = ""
        secondText = ""

        firstStatus.text =
            "لم يتم اختيار الكشف الأول"

        secondStatus.text =
            "لم يتم اختيار الكشف الثاني"

        results.text =
            "اختر كشفين ثم اضغط مطابقة الحسابات."
    }
}
