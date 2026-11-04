package br.com.rastrai.scan

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/** Importa apenas códigos BR da primeira aba de Excel .xlsx ou de texto/CSV. */
object ExcelRouteReader {
    private const val MAX_FILE_BYTES=12*1024*1024
    private const val MAX_XML_BYTES=22*1024*1024
    fun read(input: InputStream): List<String> {
        val source=readLimited(input,MAX_FILE_BYTES)
        require(source.isNotEmpty()) { "Arquivo vazio" }
        require(!(source.size >= 8 && source.take(8).map { it.toInt() and 255 } ==
            listOf(208,207,17,224,161,177,26,225))) {
            "Excel .xls antigo não é compatível: salve como .xlsx"
        }
        val codes=if(source.size>2 && source[0].toInt()==0x50 && source[1].toInt()==0x4B)
            xlsx(source) else csv(source)
        val cleaned=codes.distinct()
        require(cleaned.isNotEmpty()) { "Nenhum código começando com BR encontrado no arquivo" }
        require(cleaned.size<=10000) { "Arquivo acima do limite de 10.000 rastreios" }
        return cleaned
    }
    private fun readLimited(input: InputStream, max: Int): ByteArray {
        val bytes=ByteArrayOutputStream()
        val buffer=ByteArray(8192)
        while(true) {
            val n=input.read(buffer)
            if(n<0) break
            if(n==0) continue
            require(bytes.size()+n<=max) { "Arquivo muito grande (limite 12 MB)" }
            bytes.write(buffer,0,n)
        }
        return bytes.toByteArray()
    }
    private fun xml(bytes: ByteArray): org.w3c.dom.Document {
        // O parser DOM do Android não aceita várias features específicas do Java SE.
        // Em vez de chamar setFeature (que gerava "parser does not support specification"),
        // bloqueamos DOCTYPE/ENTITY antes de criar o documento.
        require(bytes.none { it == 0.toByte() }) { "XML com codificação incompatível" }
        val content = String(bytes, StandardCharsets.UTF_8)
        require(!Regex("<!\\s*(?:DOCTYPE|ENTITY)\\b", RegexOption.IGNORE_CASE).containsMatchIn(content)) {
            "Planilha contém XML não permitido"
        }
        val factory=DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware=true
        // Não solicitar recursos de parser que algumas versões do Android desconhecem.
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    }
    private fun Element.all(tag:String): List<Element> {
        val list=getElementsByTagNameNS("*",tag)
        return (0 until list.length).mapNotNull { list.item(it) as? Element }
    }
    private fun xlsx(bytes:ByteArray): List<String> {
        val sheets=mutableMapOf<String,ByteArray>()
        var shared:ByteArray?=null
        var total=0
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while(true) {
                val item=zip.nextEntry ?: break
                val path=item.name.replace('\\','/')
                val allowed=path=="xl/sharedStrings.xml" ||
                    (path.startsWith("xl/worksheets/") && path.endsWith(".xml"))
                if(!item.isDirectory && allowed) {
                    val b=readLimited(zip,MAX_XML_BYTES)
                    total+=b.size
                    require(total<=MAX_XML_BYTES) { "Conteúdo XLSX excessivamente grande" }
                    if(path=="xl/sharedStrings.xml") shared=b else sheets[path]=b
                }
                zip.closeEntry()
            }
        }
        require(sheets.isNotEmpty()) { "Planilha XLSX sem aba de dados" }
        val strings=shared?.let { bytesShared ->
            xml(bytesShared).documentElement.all("si").map { it.textContent.orEmpty() }
        } ?: emptyList()
        val sheetName=sheets.keys.sortedWith(compareBy<String> {
            if(it.endsWith("/sheet1.xml")) 0 else 1
        }.thenBy { it }).first()
        val doc=xml(sheets.getValue(sheetName))
        val result=mutableListOf<String>()
        for(row in doc.documentElement.all("row")) {
            for(cell in row.all("c")) {
                val kind=cell.getAttribute("t")
                val value=if(kind=="inlineStr") {
                    cell.all("is").firstOrNull()?.textContent.orEmpty()
                } else {
                    val raw=cell.all("v").firstOrNull()?.textContent.orEmpty()
                    if(kind=="s") strings.getOrNull(raw.toIntOrNull() ?: -1).orEmpty() else raw
                }
                if(value.isNotBlank()) {
                    result.addAll(TrackingParser.extractAll(value))
                }
            }
            require(result.size<=10000) { "Mais de 10.000 rastreios nesta rota" }
        }
        return result
    }
    private fun csv(bytes:ByteArray): List<String> {
        val source=bytes.toString(StandardCharsets.UTF_8).removePrefix("\uFEFF")
        val normalized=source.replace("\r\n","\n")
        val codes=mutableListOf<String>()
        for(row in normalized.lineSequence()) {
            // Extrai apenas BR mesmo se houver nomes, endereços ou outras colunas.
            codes.addAll(TrackingParser.extractAll(row))
            require(codes.size<=10000) { "Mais de 10.000 rastreios nesta rota" }
        }
        return codes
    }
}
