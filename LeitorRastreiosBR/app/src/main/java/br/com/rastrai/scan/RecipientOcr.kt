package br.com.rastrai.scan

import java.text.Normalizer
import java.util.Locale

/** Extração conservadora: dados apenas quando a etiqueta apresenta rótulos reconhecíveis. */
data class RecipientInfo(
    val name: String = "",
    val street: String = "",
    val district: String = "",
    val zip: String = ""
) {
    val firstName: String get() = name.trim().split(Regex("\\s+")).firstOrNull().orEmpty()
    val hasAny: Boolean get() = name.isNotBlank() || street.isNotBlank() || district.isNotBlank() || zip.isNotBlank()
}

object RecipientOcr {
    private fun clean(s: String): String = s.trim().replace(Regex("\\s+"), " ").trim(' ', ':', '-', '–')
    private fun ascii(s: String): String =
        Normalizer.normalize(s.uppercase(Locale("pt", "BR")), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
    private val labelName = Regex("^(?:DESTINATARIO(?:\\s*\\(A\\))?|RECEBEDOR(?:A)?|NOME\\s+DO\\s+DESTINATARIO|NOME\\s+RECEBEDOR|NOME|ENTREGAR\\s+PARA|SHIP\\s+TO)\\s*[:#\\-]?\\s*(.*)$")
    private val labelStreet = Regex("^(?:ENDERECO(?:\\s+DE\\s+ENTREGA)?|LOGRADOURO|RUA|AVENIDA|AV|TRAVESSA|ESTRADA|PRACA|ALAMEDA)\\s*[:#\\-]?\\s*(.*)$")
    private val labelDistrict = Regex("^(?:BAIRRO|DISTRITO)\\s*[:#\\-]?\\s*(.*)$")
    private val labelZip = Regex("\\bCEP\\b\\s*[:#\\-]?\\s*(\\d{5}[\\s-]?\\d{3})(?!\\d)")
    private val forbidden = Regex("\\b(?:DESTINATARIO|NOME|BAIRRO|CEP|ENDERECO|RUA|CODIGO|RASTREIO|REMETENTE|PEDIDO|SHOPEE|BR\\d{8,}|SPXBR\\d{8,})\\b")

    private fun plausibleName(v: String): Boolean {
        val w=clean(v)
        if(w.length !in 2..90 || w.any { it.isDigit() }) return false
        if(!w.matches(Regex("[\\p{L} .'-]{2,90}"))) return false
        return !forbidden.containsMatchIn(ascii(w))
    }
    private fun takeValue(lines: List<String>, i: Int, same: String, check: (String)->Boolean): String {
        val inLine = clean(same)
        if(inLine.isNotEmpty() && check(inLine)) return inLine
        val next = lines.getOrNull(i+1)?.let(::clean).orEmpty()
        return if(check(next)) next else ""
    }
    /** Extrai nome/rua/bairro/CEP da MESMA etiqueta. Nunca deduz nome a partir do código. */
    fun parse(ocrText: String): RecipientInfo {
        val lines=ocrText.lines().map(::clean).filter { it.isNotBlank() }.take(60)
        var name=""
        var street=""
        var district=""
        var zip=""
        for((i,original) in lines.withIndex()) {
            val line=ascii(original)
            if(zip.isBlank()) {
                val m=if(line.contains("CEP")) labelZip.find(line) else null
                if(m!=null) {
                    val digits=m.groupValues[1].filter(Char::isDigit)
                    if(digits.length == 8) zip=digits.take(5)+"-"+digits.takeLast(3)
                }
            }
            if(name.isBlank()) {
                val m=labelName.matchEntire(line)
                if(m!=null) {
                    val raw=original.substring(original.length - m.groupValues[1].length)
                    name=takeValue(lines,i,raw,::plausibleName)
                }
            }
            if(street.isBlank()) {
                val m=labelStreet.matchEntire(line)
                if(m!=null) {
                    val suffix=original.substring(original.length - m.groupValues[1].length)
                    val prefix=ascii(original).take(original.length-suffix.length).trim().trimEnd(':','-').trim()
                    // Evita confundir "RUA" solto com valor e preserva o tipo de logradouro.
                    val value=takeValue(lines,i,suffix) { v -> v.length in 3..120 && v.any(Char::isLetter) &&
                        !ascii(v).startsWith("BAIRRO") && !ascii(v).startsWith("CEP") &&
                        !ascii(v).startsWith("BR") && !ascii(v).startsWith("SPXBR") }
                    if(value.isNotBlank()) street=if(prefix in setOf("RUA","AVENIDA","AV","TRAVESSA","ESTRADA","PRACA","ALAMEDA")) "$prefix $value" else value
                }
            }
            if(district.isBlank()) {
                val m=labelDistrict.matchEntire(line)
                if(m!=null) {
                    val suffix=original.substring(original.length-m.groupValues[1].length)
                    district=takeValue(lines,i,suffix) { v -> v.length in 2..65 && !ascii(v).startsWith("CEP") && !ascii(v).startsWith("ENDERECO") }
                }
            }
        }
        return RecipientInfo(name,street,district,zip)
    }
}
