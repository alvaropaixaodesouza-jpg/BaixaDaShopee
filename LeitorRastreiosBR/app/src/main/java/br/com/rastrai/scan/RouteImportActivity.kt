package br.com.rastrai.scan

import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import java.util.concurrent.Executors

/** Atividade auxiliar: abre só o seletor de Excel, não a tela principal do app. */
class RouteImportActivity : ComponentActivity() {
    private val executor=Executors.newSingleThreadExecutor()
    private val picker=registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri==null) { finish();return@registerForActivityResult }
        val name=displayName(uri)
        if(name.endsWith(".xls",true) && !name.endsWith(".xlsx",true)) {
            toast("Formato .xls antigo não suportado. Exporte como .xlsx.")
            finish();return@registerForActivityResult
        }
        executor.execute {
            val result=runCatching {
                val input=contentResolver.openInputStream(uri) ?: error("Sem acesso ao arquivo")
                val codes=input.use { ExcelRouteReader.read(it) }
                ReplayRoutes.addFile(this,name,codes)
            }
            runOnUiThread {
                result.onSuccess { route ->
                    toast("Rota importada: "+route.codes.size+" códigos BR. "+route.title)
                }.onFailure { ex ->
                    toast("Não foi possível importar: "+(ex.message ?: "arquivo inválido"))
                }
                finish()
            }
        }
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        if(savedInstanceState==null) picker.launch(arrayOf(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "text/csv", "text/plain", "application/octet-stream"
        ))
    }
    private fun displayName(uri:Uri):String {
        return runCatching {
            contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { c ->
                if(c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                else null
            }
        }.getOrNull()?.take(90) ?: "Rota_"+System.currentTimeMillis()+".xlsx"
    }
    private fun toast(message:String)=Toast.makeText(this,message,Toast.LENGTH_LONG).show()
    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }
}
