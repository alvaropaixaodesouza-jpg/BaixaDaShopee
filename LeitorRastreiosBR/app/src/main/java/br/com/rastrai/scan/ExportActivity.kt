package br.com.rastrai.scan

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/** Exporta por Storage Access Framework: usuário pode escolher pasta local ou SD. */
class ExportActivity : ComponentActivity() {
    private val document = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if(uri != null) {
            try {
                val session=intent.getStringExtra("session") ?: Store.active(this)
                val stream=contentResolver.openOutputStream(uri) ?: error("Arquivo indisponível")
                stream.use { Excel.write(Store.list(this,session),it) }
                Toast.makeText(this,"Planilha Excel salva!",Toast.LENGTH_LONG).show()
            } catch(e:Exception) {
                Toast.makeText(this,"Erro ao exportar: " + e.message,Toast.LENGTH_LONG).show()
            }
        }
        finish()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if(savedInstanceState == null) {
            val id=intent.getStringExtra("session") ?: Store.active(this)
            val total=Store.list(this,id).size
            if(total==0) {
                Toast.makeText(this,"Lista vazia",Toast.LENGTH_SHORT).show()
                finish()
            } else document.launch("rastreios_"+id+"_"+total+".xlsx")
        }
    }
}
