package br.com.rastrai.scan

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class Record(val code: String, val at: Long, val kind: String)

object TrackingParser {
    private val br = Regex("(?<![A-Z0-9])(?:SPX)?BR[A-Z0-9]{8,32}(?![A-Z0-9])")
    private val generic = Regex("^[A-Z0-9][A-Z0-9_-]{5,59}$")
    fun parse(raw: String, strict: Boolean): String? {
        val normalized = raw.uppercase(Locale.ROOT).trim()
        br.find(normalized)?.let { return it.value }
        if (strict) return null
        return normalized.split(Regex("[\\s,;|=:?&/]+")).firstOrNull { generic.matches(it) }
    }
}
object Store {
    private fun p(c: Context) = c.getSharedPreferences("rastreios", Context.MODE_PRIVATE)
    @Synchronized fun active(c: Context): String {
        val saved = p(c).getString("active", "") ?: ""
        if (saved.isNotBlank()) return saved
        return newList(c)
    }
    @Synchronized fun newList(c: Context): String {
        val id = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        p(c).edit().putString("active", id).putString("rows_" + id, "[]").commit()
        return id
    }
    fun select(c: Context, id: String) { p(c).edit().putString("active", id).apply() }
    fun strict(c: Context) = p(c).getBoolean("strict", true)
    fun setStrict(c: Context, enabled: Boolean) { p(c).edit().putBoolean("strict", enabled).apply() }
    @Synchronized fun list(c: Context, id: String = active(c)): List<Record> {
        val data = JSONArray(p(c).getString("rows_" + id, "[]") ?: "[]")
        return (0 until data.length()).map { i ->
            val x = data.getJSONObject(i)
            Record(x.getString("c"), x.getLong("t"), x.optString("k", "QR/BARRAS"))
        }
    }
    @Synchronized fun add(c: Context, code: String, kind: String): Boolean {
        val id = active(c)
        val rows = list(c, id)
        if (rows.any { it.code == code }) return false
        val a = JSONArray(p(c).getString("rows_" + id, "[]") ?: "[]")
        a.put(JSONObject().put("c", code).put("t", System.currentTimeMillis()).put("k", kind))
        p(c).edit().putString("rows_" + id, a.toString()).commit()
        return true
    }
    fun sessions(c: Context): List<String> =
        p(c).all.keys.filter { it.startsWith("rows_") }.map { it.removePrefix("rows_") }.sortedDescending()
}
object QueueStore {
    private fun p(c: Context) = c.getSharedPreferences("rastreios_queue", Context.MODE_PRIVATE)
    @Synchronized fun list(c: Context): List<String> {
        val a = JSONArray(p(c).getString("queue", "[]") ?: "[]")
        return (0 until a.length()).map { a.getString(it) }
    }
    @Synchronized fun append(c: Context, codes: List<String>) {
        val a = JSONArray()
        (list(c) + codes).forEach { a.put(it) }
        p(c).edit().putString("queue", a.toString()).commit()
    }
    @Synchronized fun pop(c: Context) {
        val a = JSONArray()
        list(c).drop(1).forEach { a.put(it) }
        p(c).edit().putString("queue", a.toString()).commit()
    }
}
object Excel {
    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
    fun write(rows: List<Record>, output: OutputStream) {
        ZipOutputStream(output).use { zip ->
            fun entry(name: String, value: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(value.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry("[Content_Types].xml", """<?xml version="1.0" encoding="utf-8"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="xml" ContentType="application/xml"/>
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>""")
            entry("_rels/.rels", """<?xml version="1.0" encoding="utf-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>""")
            entry("xl/workbook.xml", """<?xml version="1.0" encoding="utf-8"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets><sheet name="Rastreios" sheetId="1" r:id="rId1"/></sheets></workbook>""")
            entry("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="utf-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
</Relationships>""")
            fun cell(pos: String, value: String): String =
                "<c r=\"" + pos + "\" t=\"inlineStr\"><is><t>" + esc(value) + "</t></is></c>"
            val sheetRows = buildString {
                append("<row r=\"1\">")
                listOf("Nº", "Código de rastreio", "Data e hora", "Tipo").forEachIndexed { i,v ->
                    append(cell(('A' + i).toString() + "1",v))
                }
                append("</row>")
                val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("pt","BR"))
                rows.forEachIndexed { i,r ->
                    val n = (i + 2).toString()
                    append("<row r=\"" + n + "\">")
                    append(cell("A" + n,(i+1).toString()))
                    append(cell("B" + n,r.code))
                    append(cell("C" + n,fmt.format(Date(r.at))))
                    append(cell("D" + n,r.kind))
                    append("</row>")
                }
            }
            entry("xl/worksheets/sheet1.xml", """<?xml version="1.0" encoding="utf-8"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""" +
                sheetRows + "</sheetData></worksheet>")
        }
    }
}
object Ui {
    val navy = Color.rgb(21,38,61)
    val blue = Color.rgb(37,95,204)
    fun dp(c: Context, n: Int) = (c.resources.displayMetrics.density * n).toInt()
    fun column(c: Context) = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(c,12),dp(c,10),dp(c,12),dp(c,12)) }
    fun row(c: Context) = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
    fun label(c: Context, s: String, size: Float = 16f) = TextView(c).apply { text = s; textSize = size; setTextColor(navy); setPadding(0,dp(c,7),0,dp(c,7)) }
    fun button(c: Context, s: String) = Button(c).apply { text = s; isAllCaps = false; textSize=13f }
    fun add(l: LinearLayout, v: View) { l.addView(v,LinearLayout.LayoutParams(-1,-2)) }
}
