package br.com.rastrai.scan

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExcelRouteReaderTest {
    private fun makeXlsx(sheet:String, shared:String?=null):ByteArray {
        val file=ByteArrayOutputStream()
        ZipOutputStream(file).use { zip ->
            if(shared!=null) {
                zip.putNextEntry(ZipEntry("xl/sharedStrings.xml"))
                zip.write(shared.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            zip.write(sheet.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return file.toByteArray()
    }
    @Test fun importaExcelAntigoComOutrasColunasIgnorandoNomes() {
        val sheet="""<?xml version="1.0" encoding="utf-8"?>
        <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
        <row r="1"><c r="A1" t="inlineStr"><is><t>Nº</t></is></c><c r="B1" t="inlineStr"><is><t>Código</t></is></c></row>
        <row r="2"><c r="A2" t="inlineStr"><is><t>Maria</t></is></c><c r="B2" t="inlineStr"><is><t>BR261087203125T</t></is></c></row>
        <row r="3"><c r="B3" t="inlineStr"><is><t>BR2634199134265</t></is></c><c r="C3" t="inlineStr"><is><t>Rua das Flores</t></is></c></row>
        <row r="4"><c r="B4" t="inlineStr"><is><t>BR261087203125T</t></is></c></row>
        </sheetData></worksheet>"""
        val result=ExcelRouteReader.read(ByteArrayInputStream(makeXlsx(sheet)))
        assertEquals(listOf("BR261087203125T","BR2634199134265"),result)
    }
    @Test fun importaXlsxComSharedStrings() {
        val shared="""<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><si><t>Nome</t></si><si><t>BR262241282602R</t></si><si><t>BR264960487237O</t></si></sst>"""
        val sheet="""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
        <row r="1"><c r="A1" t="s"><v>0</v></c></row>
        <row r="2"><c r="B2" t="s"><v>1</v></c></row>
        <row r="3"><c r="B3" t="s"><v>2</v></c></row>
        </sheetData></worksheet>"""
        val result=ExcelRouteReader.read(ByteArrayInputStream(makeXlsx(sheet,shared)))
        assertEquals(listOf("BR262241282602R","BR264960487237O"),result)
    }
    @Test fun planilhaExportadaPeloAppVoltaSemPerdaDeCodigos() {
        val bytes=ByteArrayOutputStream()
        Excel.write(listOf(
            Record("BR261087203125T",1,"QR","Pessoa Ignorada"),
            Record("BR2634199134265",2,"BARRAS"),
            Record("SPXBR1234567890",3,"QR")
        ),bytes)
        assertEquals(listOf("BR261087203125T","BR2634199134265"),
            ExcelRouteReader.read(ByteArrayInputStream(bytes.toByteArray())))
    }
    @Test fun importaListaDeTextoComoWhatsApp() {
        val text="""BR261087203125T
BR2634199134265
BR261087203125T
Nome: Ana; Endereço: Av principal
BR262241282602R BR264960487237O"""
        val result=ExcelRouteReader.read(ByteArrayInputStream(text.toByteArray()))
        assertEquals(listOf("BR261087203125T","BR2634199134265","BR262241282602R","BR264960487237O"),result)
    }
    @Test fun xmlComDoctypeEhBloqueado() {
        val unsafe="""<!DOCTYPE worksheet [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
        <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData/></worksheet>"""
        try {
            ExcelRouteReader.read(ByteArrayInputStream(makeXlsx(unsafe)))
            fail("Planilha maliciosa não foi rejeitada")
        } catch(e:IllegalArgumentException) {
            assertTrue(e.message.orEmpty().contains("XML"))
        }
    }
    @Test(expected=IllegalArgumentException::class)
    fun semBRNaoImporta() {
        ExcelRouteReader.read(ByteArrayInputStream("Nome;Rua\nAna;Centro".toByteArray()))
    }
}
