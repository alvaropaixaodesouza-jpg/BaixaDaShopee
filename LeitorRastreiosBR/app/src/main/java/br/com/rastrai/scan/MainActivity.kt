package br.com.rastrai.scan

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {
    private lateinit var root: LinearLayout
    private lateinit var countLabel: android.widget.TextView
    private lateinit var listLabel: android.widget.TextView
    private lateinit var items: LinearLayout
    private var exportSession = ""
    private val save = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if (uri != null) {
            try {
                val output = contentResolver.openOutputStream(uri) ?: error("Arquivo indisponível")
                output.use { Excel.write(Store.list(this, exportSession), it) }
                toast("Planilha Excel salva")
            } catch (e: Exception) { toast("Erro ao salvar: " + e.message) }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        root = Ui.column(this)
        scroll.addView(root)
        setContentView(scroll)
        Ui.add(root, Ui.label(this,"LEITOR DE RASTREIOS BR",24f))
        Ui.add(root, Ui.label(this,"QR Code + barras • somente BR • sem internet",13f))
        countLabel = Ui.label(this,"0 pacotes",29f)
        Ui.add(root,countLabel)
        listLabel = Ui.label(this,"",12f)
        Ui.add(root,listLabel)
        val scan = Ui.button(this,"ABRIR CÂMERA E ESCANEAR")
        Ui.add(root,scan)
        scan.setOnClickListener { startActivity(Intent(this,ScanActivity::class.java)) }
        val export = Ui.button(this,"SALVAR PLANILHA EXCEL (.xlsx)")
        Ui.add(root,export)
        export.setOnClickListener {
            exportSession=Store.active(this)
            val total=Store.list(this,exportSession).size
            if(total==0) toast("Escaneie pelo menos um pacote") else
                save.launch("rastreios_" + exportSession + "_" + total + ".xlsx")
        }
        val newList=Ui.button(this,"Criar nova lista de pacotes")
        Ui.add(root,newList)
        newList.setOnClickListener {
            AlertDialog.Builder(this).setTitle("Nova lista?")
                .setMessage("A lista atual ficará salva no histórico.")
                .setPositiveButton("Criar") { _,_ -> Store.newList(this); refresh() }
                .setNegativeButton("Cancelar",null).show()
        }
        val history=Ui.button(this,"ABRIR LISTAS ANTERIORES")
        Ui.add(root,history)
        history.setOnClickListener {
            val ids=Store.sessions(this)
            AlertDialog.Builder(this).setTitle("Escolha a lista")
                .setItems(ids.map { id -> id + " (" + Store.list(this,id).size + ")" }.toTypedArray()) { _, index ->
                    Store.select(this,ids[index]); refresh()
                }.setNegativeButton("Fechar",null).show()
        }
        Ui.add(root,Ui.label(this,"TECLADO PARA OUTROS APLICATIVOS",18f))
        val enable=Ui.button(this,"1. Ativar teclado de rastreios no Android")
        Ui.add(root,enable)
        enable.setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        val select=Ui.button(this,"2. Selecionar teclado Rastreios BR")
        Ui.add(root,select)
        select.setOnClickListener {
            val im=getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            im.showInputMethodPicker()
        }
        Ui.add(root,Ui.label(this,"A câmera lê somente BR e mostra os códigos à direita. Na aba ROTA importe Excel e use Iniciar para enviar BR + Enter com intervalo ajustável.",13f))
        Ui.add(root,Ui.label(this,"CÓDIGOS DESTA LISTA",18f))
        items=Ui.column(this)
        Ui.add(root,items)
        refresh()
    }
    override fun onResume() { super.onResume(); if (::items.isInitialized) refresh() }
    private fun refresh() {
        val id=Store.active(this)
        val rows=Store.list(this,id)
        listLabel.text="Lista: " + id
        countLabel.text=rows.size.toString() + " pacotes"
        items.removeAllViews()
        rows.takeLast(80).asReversed().forEachIndexed { i,r ->
            Ui.add(items,Ui.label(this,(rows.size-i).toString() + "  " + r.code,14f))
        }
        if(rows.size>80) Ui.add(items,Ui.label(this,"Mostrando os últimos 80; o Excel terá todos.",12f))
    }
    private fun toast(message:String)=Toast.makeText(this,message,Toast.LENGTH_LONG).show()
}
