package br.com.rastrai.scan

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExcelRouteReaderTest {
    @Test fun xlsxExportadoPeloApp() {
        val file=ByteArrayOutputStream()
        ZipOutputStream(file).use { zip ->
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            val sheet="""<?xml version="1.0" encoding="utf-8"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                <sheetData>
                <row r="1"><c r="A1" t="inlineStr"><is><t>Numero</t></is></c><c r="B1" t="inlineStr"><is><t>Codigo</t></is></c></row>
                <row r="2"><c r="B2" t="inlineStr"><is><t>BR2624148890977</t></is></c></row>
                <row r="3"><c r="B3" t="inlineStr"><is><t>SPXBR1234567890</t></is></c></row>
                <row r="4"><c r="B4" t="inlineStr"><is><t>BR2624148890977</t></is></c></row>
                </sheetData></worksheet>"""
            zip.write(sheet.toByteArray())
            zip.closeEntry()
        }
        val codes=ExcelRouteReader.read(ByteArrayInputStream(file.toByteArray()))
        assertEquals(listOf("BR2624148890977","SPXBR1234567890"),codes)
    }
    @Test fun xlsxComSharedStrings() {
        val b=ByteArrayOutputStream()
        ZipOutputStream(b).use { zip ->
            zip.putNextEntry(ZipEntry("xl/sharedStrings.xml"))
            zip.write("""<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><si><t>Rastreio</t></si><si><t>BR2345678901234</t></si></sst>""".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            zip.write("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData><row r="1"><c r="A1" t="s"><v>0</v></c></row><row r="2"><c r="A2" t="s"><v>1</v></c></row></sheetData></worksheet>""".toByteArray())
            zip.closeEntry()
        }
        assertEquals(listOf("BR2345678901234"),ExcelRouteReader.read(ByteArrayInputStream(b.toByteArray())))
    }
    @Test fun csvRemoveDuplicatas() {
        val csv="Código;Nome\nBR2624148890977;Ana\nSPXBR1234567890;Bruno\nBR2624148890977;Outro"
        assertEquals(2,ExcelRouteReader.read(ByteArrayInputStream(csv.toByteArray())).size)
    }
    @Test(expected=IllegalArgumentException::class) fun arquivoSemBrEhRejeitado() {
        ExcelRouteReader.read(ByteArrayInputStream("Nome;Rua\nAna;Centro".toByteArray()))
    }
}
