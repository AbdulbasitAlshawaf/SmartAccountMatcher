
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
import kotlin.math.abs

class MainActivity : Activity() {

    private val pickFirst = 101
    private val pickSecond = 102

    private var firstText = ""
    private var secondText = ""

    private lateinit var firstStatus: TextView
    private lateinit var secondStatus: TextView
    private lateinit var results: TextView

    data class Transaction(
        val description: String,
        val amount: Double
    )

    data class ParsedStatement(
        val transactions: List<Transaction>,
        val message: String
    )

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
            text = "مطابقة البيان والمبلغ تلقائيًا"
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

        root.addView(
            scroll,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        setContentView(root)
    }

    private fun status(value: String) =
        TextView(this).apply {
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
                    val last = row.lastCellNum.toInt()

                    for (i in 0 until last.coerceAtLeast(0)) {
                        val cell = row.getCell(i)
                        values.add(
                            cell?.toString()?.trim().orEmpty()
                        )
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

    private fun normalizeDigits(text: String): String {
        return text
            .replace(Regex("[٠-٩]")) {
                (it.value[0] - '٠').toString()
            }
            .replace(Regex("[۰-۹]")) {
                (it.value[0] - '۰').toString()
            }
    }

    private fun cleanHeader(text: String): String {
        return normalizeDigits(text)
            .lowercase(Locale.ROOT)
            .replace("أ", "ا")
            .replace("إ", "ا")
            .replace("آ", "ا")
            .replace("ى", "ي")
            .replace(
                Regex("""[\s\p{Punct}،؛:：()\[\]{}]+"""),
                ""
            )
            .trim()
    }

    private fun isDescriptionHeader(value: String): Boolean {
        val h = cleanHeader(value)

        return h in setOf(
            "البيان",
            "بيان",
            "وصفالعملية",
            "وصفالحركة",
            "تفاصيلالعملية",
            "تفاصيلالحركة",
            "التفاصيل",
            "الشرح",
            "البيانات"
        )
    }

    private fun isAmountHeader(value: String): Boolean {
        val h = cleanHeader(value)

        return h in setOf(
            "المبلغ",
            "قيمة",
            "القيمة",
            "مبلغ",
            "قيمةالعملية",
            "مبلغالعملية"
        )
    }

    private fun splitColumns(line: String): List<String> {
        if (line.contains('|')) {
            return line.split('|').map { it.trim() }
        }

        return line.trim()
            .split(Regex("""\t+| {2,}"""))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun parseAmount(raw: String): Double? {
        var s = normalizeDigits(raw)
            .replace("٬", ",")
            .replace("٫", ".")
            .replace("−", "-")
            .replace(" ", "")
            .replace(Regex("""[^0-9,().+-]"""), "")


        if (s.isBlank() || s == "-" || s == "." || s == ",") {
            return null
        }

        val negativeParentheses =
            s.startsWith("(") && s.endsWith(")")

        s = s.removePrefix("(").removeSuffix(")")

        if (s.contains(',') && s.contains('.')) {
            s = if (s.lastIndexOf('.') > s.lastIndexOf(',')) {
                s.replace(",", "")
            } else {
                s.replace(".", "").replace(",", ".")
            }
        } else if (s.contains(',')) {
            val tail = s.substringAfterLast(',')

            s = if (tail.length in 1..2) {
                s.replace(",", ".")
            } else {
                s.replace(",", "")
            }
        }

        val value = s.toDoubleOrNull() ?: return null

        return if (negativeParentheses) -abs(value) else value
    }

    private fun parseStatement(text: String): ParsedStatement {
        val lines = text.lines()

        var headerIndex = -1
        var descriptionIndex = -1
        var amountIndex = -1

        for (i in lines.indices) {
            val columns = splitColumns(lines[i])

            val desc = columns.indexOfFirst {
                isDescriptionHeader(it)
            }

            val amount = columns.indexOfFirst {
                isAmountHeader(it)
            }

            if (desc >= 0 && amount >= 0 && desc != amount) {
                headerIndex = i
                descriptionIndex = desc
                amountIndex = amount
                break
            }
        }

        if (headerIndex < 0) {
            return ParsedStatement(
                emptyList(),
                "لم يتم العثور على عناوين البيان والمبلغ في صف واحد."
            )
        }

        val transactions = mutableListOf<Transaction>()

        for (i in headerIndex + 1 until lines.size) {
            val line = lines[i].trim()

            if (line.isBlank()) continue

            val columns = splitColumns(line)

            if (
                columns.size <= maxOf(
                    descriptionIndex,
                    amountIndex
                )
            ) {
                continue
            }

            if (
                columns.any { isDescriptionHeader(it) } &&
                columns.any { isAmountHeader(it) }
            ) {
                continue
            }

            val description =
                columns.getOrNull(descriptionIndex)
                    ?.trim()
                    .orEmpty()

            val amount = parseAmount(
                columns.getOrNull(amountIndex).orEmpty()
            )

            if (description.isNotBlank() && amount != null) {
                transactions.add(
                    Transaction(description, amount)
                )
            }
        }

        return ParsedStatement(
            transactions,
            "تم تحديد عناوين الأعمدة."
        )
    }

    private fun normalizeDescription(text: String): String {
        return normalizeDigits(text)
            .lowercase(Locale.ROOT)
            .replace("أ", "ا")
            .replace("إ", "ا")
            .replace("آ", "ا")
            .replace("ى", "ي")
            .replace("ة", "ه")
            .filter { it.isLetterOrDigit() }
            .trim()
    }

    private fun compareStatements() {
        if (firstText.isBlank() || secondText.isBlank()) {
            results.text =
                "يجب اختيار الكشفين وقراءتهما أولاً."
            return
        }

        val first = parseStatement(firstText)
        val second = parseStatement(secondText)

        if (
            first.transactions.isEmpty() ||
            second.transactions.isEmpty()
        ) {
            results.text = buildString {
                append("تعذر إجراء المطابقة.\n\n")
                append("الكشف الأول: ")
                    .append(first.message)
                    .append('\n')

                append("الكشف الثاني: ")
                    .append(second.message)
                    .append('\n\n')

                append(
                    "قد لا تكون أعمدة PDF مفصولة بوضوح. " +
                        "لم يتم اعتماد نتيجة غير مؤكدة."
                )
            }
            return
        }

        val firstGroups = first.transactions.groupBy {
            normalizeDescription(it.description)
        }

        val secondGroups = second.transactions.groupBy {
            normalizeDescription(it.description)
        }

        val allKeys = (
            firstGroups.keys + secondGroups.keys
        ).toSortedSet()

        val out = StringBuilder()

        out.append("نتيجة مطابقة الكشوفات\n")
        out.append("====================\n\n")

        out.append("عدد العمليات في الكشف الأول: ")
            .append(first.transactions.size)
            .append('\n')

        out.append("عدد العمليات في الكشف الثاني: ")
            .append(second.transactions.size)
            .append("\n\n")

        var differentCount = 0

        for (key in allKeys) {
            val a = firstGroups[key].orEmpty()
            val b = secondGroups[key].orEmpty()

            val sumA = a.sumOf { it.amount }
            val sumB = b.sumOf { it.amount }

            val name = a.firstOrNull()?.description
                ?: b.firstOrNull()?.description
                ?: key

            val diff = sumA - sumB

            val same =
                a.size == b.size &&
                    abs(diff) < 0.005

            if (!same) {
                differentCount++
            }

            out.append("البيان: ")
                .append(name)
                .append('\n')

            out.append("  الكشف الأول: ")
                .append(a.size)
                .append(" عملية، الإجمالي ")
                .append(
                    String.format(Locale.US, "%.2f", sumA)
                )
                .append('\n')

            out.append("  الكشف الثاني: ")
                .append(b.size)
                .append(" عملية، الإجمالي ")
                .append(
                    String.format(Locale.US, "%.2f", sumB)
                )
                .append('\n')

            out.append("  الفرق (الأول - الثاني): ")
                .append(
                    String.format(Locale.US, "%.2f", diff)
                )
                .append('\n')

            out.append(
                if (same) {
                    "  الحالة: متطابق في العدد والإجمالي\n\n"
                } else {
                    "  الحالة: يحتاج إلى مراجعة\n\n"
                }
            )
        }

        out.append(
            "عدد مجموعات البيانات التي تحتاج إلى مراجعة: "
        )
            .append(differentCount)
            .append("\n\n")

        out.append(
            "تنبيه: تُجمع العمليات حسب البيان بعد توحيد " +
                "بعض اختلافات الكتابة. لا يضمن هذا الإصدار " +
                "مطابقة العبارات ذات المعاني المتشابهة. " +
                "راجع النتائج قبل اعتمادها محاسبيًا."
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
