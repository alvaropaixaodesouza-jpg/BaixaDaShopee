package br.com.rastrai.scan

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/** Lê a primeira aba de um XLSX real ou CSV sem dependências externas. */
object ExcelRouteReader {
    private const val MAX_FILE_BYTES=12*1024*1024
    private const val MAX_XML_BYTES=22*1024*1024
    fun read(input: InputStream): List<String> {
        val source=readLimited(input,MAX_FILE_BYTES)
        require(source.isNotEmpty()) { "Arquivo vazio" }
        val codes=if(source.size>2 && source[0].toInt()==0x50 && source[1].toInt()==0x4B)
            xlsx(source) else csv(source)
        val cleaned=codes.mapNotNull { TrackingParser.parse(it,true) }.distinct()
        require(cleaned.isNotEmpty()) { "Nenhum rastreio BR/SPXBR encontrado" }
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
        val f=DocumentBuilderFactory.newInstance()
        f.isNamespaceAware=true
        f.isXIncludeAware=false
        f.isExpandEntityReferences=false
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true)
        f.setFeature("http://xml.org/sax/features/external-general-entities",false)
        f.setFeature("http://xml.org/sax/features/external-parameter-entities",false)
        f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd",false)
        return f.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
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
                    val code=TrackingParser.parse(value,true)
                    if(code!=null) { result.add(code);break }
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
            // Cobre os formatos ; , e tab, e colunas entre aspas.
            val candidate=TrackingParser.parse(row,true)
            if(candidate!=null) codes.add(candidate)
            require(codes.size<=10000) { "Mais de 10.000 rastreios nesta rota" }
        }
        return codes
    }
}
