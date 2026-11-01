package br.com.rastrai.scan

import android.content.Intent
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView

class TrackingImeService : InputMethodService() {
    private val handler=Handler(Looper.getMainLooper())
    private var sending=false
    private var title:TextView?=null

    override fun onCreateInputView():View {
        val box=Ui.column(this).apply { setBackgroundColor(Color.rgb(236,241,247)) }
        val controls=Ui.row(this)
        fun action(label:String,weight:Float,callback:()->Unit) {
            val b=Ui.button(this,label).apply { setPadding(1,1,1,1) }
            controls.addView(b,LinearLayout.LayoutParams(0,Ui.dp(this,56),weight))
            b.setOnClickListener { callback() }
        }
        action("QR / BARRAS",1.5f) { openScanner(false) }
        action("LOTE",0.8f) { openScanner(true) }
        action("ENVIAR",0.9f) { sendPending() }
        action("ENTER",0.9f) { enter() }
        Ui.add(box,controls)
        title=Ui.label(this,"Rastreios BR: escanear, preencher e Enter",12f)
        Ui.add(box,title!!)
        listOf("1234567890","QWERTYUIOP","ASDFGHJKL","ZXCVBNM").forEach { chars ->
            val line=Ui.row(this)
            chars.forEach { ch ->
                val b=Ui.button(this,ch.toString()).apply {
                    minWidth=0; minimumWidth=0;setPadding(0,0,0,0)
                }
                line.addView(b,LinearLayout.LayoutParams(0,Ui.dp(this,43),1f))
                b.setOnClickListener { currentInputConnection?.commitText(ch.toString(),1) }
            }
            Ui.add(box,line)
        }
        val bottom=Ui.row(this)
        fun bottomKey(value:String,weight:Float,run:()->Unit) {
            val b=Ui.button(this,value)
            bottom.addView(b,LinearLayout.LayoutParams(0,Ui.dp(this,46),weight))
            b.setOnClickListener { run() }
        }
        bottomKey("BR",0.8f) { currentInputConnection?.commitText("BR",1) }
        bottomKey("ESPAÇO",2f) { currentInputConnection?.commitText(" ",1) }
        bottomKey("APAGAR",1.1f) { currentInputConnection?.deleteSurroundingText(1,0) }
        Ui.add(box,bottom)
        handler.postDelayed({ sendPending() },450)
        return box
    }
    private fun openScanner(batch:Boolean) {
        sending=false
        handler.removeCallbacksAndMessages(null)
        try {
            val intent=Intent(this,ScanActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(ScanActivity.FROM_IME,true)
                putExtra(ScanActivity.BATCH,batch)
            }
            startActivity(intent)
        } catch(e:Exception) { title?.text="Não foi possível abrir câmera: "+e.message }
    }
    override fun onStartInputView(info:EditorInfo?,restarting:Boolean) {
        super.onStartInputView(info,restarting)
        handler.postDelayed({ sendPending() },350)
    }
    override fun onWindowShown() {
        super.onWindowShown()
        handler.postDelayed({ sendPending() },350)
    }
    override fun onFinishInput() {
        sending=false
        handler.removeCallbacksAndMessages(null)
        super.onFinishInput()
    }
    override fun onDestroy() {
        sending=false
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
    private fun isPassword():Boolean {
        val type=currentInputEditorInfo?.inputType ?: 0
        val variation=type and InputType.TYPE_MASK_VARIATION
        return (type and InputType.TYPE_MASK_CLASS)==InputType.TYPE_CLASS_TEXT &&
            (variation==InputType.TYPE_TEXT_VARIATION_PASSWORD ||
             variation==InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
             variation==InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
    }
    private fun enter() {
        if(!sendDefaultEditorAction(true)) sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
    }
    private fun sendPending() {
        if(sending) return
        val pending=QueueStore.list(this)
        if(pending.isEmpty()) { title?.text="QR / BARRAS: um código • LOTE: vários";return }
        if(isPassword()) { title?.text="Campo de senha: envio bloqueado";return }
        if(!isInputViewShown || currentInputConnection==null) {
            title?.text="Selecione um campo para enviar "+pending.size+" código(s)"
            return
        }
        sending=true
        next()
    }
    private fun next() {
        if(!sending || isPassword() || !isInputViewShown) { sending=false;return }
        val ic=currentInputConnection ?: run { sending=false;return }
        val pending=QueueStore.list(this)
        if(pending.isEmpty()) { sending=false;title?.text="Todos os códigos foram enviados";return }
        val code=pending.first()
        val committed=try { ic.commitText(code,1) } catch(_:Exception) { false }
        if(!committed) { sending=false;title?.text="Falha: toque ENVIAR";return }
        enter()
        QueueStore.pop(this)
        title?.text="Enviado: "+code+" • restam "+(pending.size-1)
        if(pending.size>1) handler.postDelayed({ next() },1100) else sending=false
    }
}
