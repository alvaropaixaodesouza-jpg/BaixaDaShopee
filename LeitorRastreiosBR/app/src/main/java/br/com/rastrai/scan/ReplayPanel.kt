package br.com.rastrai.scan

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import java.util.Locale

/** Reenvio embutido no InputMethodService. Sem Accessibility nem cliques em apps externos. */
class ReplayPanel(
    private val service: TrackingImeService,
    private val handler: Handler,
    private val back: () -> Unit
) {
    val view: LinearLayout
    private val ink=Color.rgb(22,40,60)
    private val blue=Color.rgb(34,93,204)
    private val red=Color.rgb(164,30,30)
    private lateinit var routeButton: Button
    private lateinit var seconds: TextView
    private lateinit var progressText: TextView
    private lateinit var currentCode: TextView
    private lateinit var state: TextView
    private lateinit var progress: ProgressBar
    private var running=false
    private var version=0
    private var selectedId=""
    private var hostPackage=""
    private var hostField=-1

    private fun dp(n: Int) = (service.resources.displayMetrics.density*n).toInt()
    private fun background(color:Int) = GradientDrawable().apply {
        setColor(color); cornerRadius=dp(11).toFloat()
    }
    private fun text(value:String,size:Float=13f, color:Int=ink) = TextView(service).apply {
        text=value; textSize=size; setTextColor(color)
    }
    private fun button(value:String,callback:()->Unit)= Button(service).apply {
        text=value; isAllCaps=false; textSize=12f
        minHeight=0; minimumHeight=0; setPadding(dp(4),0,dp(4),0)
        background=background(Color.WHITE)
        setTextColor(ink);setOnClickListener { callback() }
    }
    private fun put(parent:LinearLayout,child:View,height:Int=44) {
        parent.addView(child, LinearLayout.LayoutParams(-1,dp(height)).apply { bottomMargin=dp(4) })
    }
    init {
        view=LinearLayout(service).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(8),dp(6),dp(8),dp(8))
            setBackgroundColor(Color.rgb(232,238,247))
        }
        val title=LinearLayout(service).apply { gravity=Gravity.CENTER_VERTICAL }
        val backButton=button("← VOLTAR") { pause("Pausado ao voltar à câmera"); back() }
        title.addView(backButton,LinearLayout.LayoutParams(dp(105),dp(38)))
        title.addView(text("REENVIO DE RASTREIOS",14f).apply {
            gravity=Gravity.CENTER; setTypeface(null,Typeface.BOLD)
        },LinearLayout.LayoutParams(0,dp(38),1f))
        put(view,title,39)
        val imports=LinearLayout(service).apply { orientation=LinearLayout.HORIZONTAL }
        val importer=button("IMPORTAR EXCEL") { launchImport() }
        importer.background=background(blue); importer.setTextColor(Color.WHITE)
        imports.addView(importer,LinearLayout.LayoutParams(0,dp(44),1f).apply { rightMargin=dp(4) })
        imports.addView(button("+ NOVO EXCEL") { launchImport() },LinearLayout.LayoutParams(0,dp(44),1f))
        put(view,imports,46)
        routeButton=button("SELECIONAR ROTA") { selectRoute() }.apply { textSize=13f }
        put(view,routeButton,39)

        val interval=LinearLayout(service).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
        interval.addView(text("Intervalo após Enter",12f),
            LinearLayout.LayoutParams(0,dp(43),1f))
        interval.addView(button("−") { ReplayRoutes.adjustInterval(service,-1);refresh() },
            LinearLayout.LayoutParams(dp(43),dp(43)).apply { rightMargin=dp(5) })
        seconds=text("3,0 s",15f).apply { gravity=Gravity.CENTER;setTypeface(null,Typeface.BOLD) }
        interval.addView(seconds, LinearLayout.LayoutParams(dp(58),dp(43)))
        interval.addView(button("+") { ReplayRoutes.adjustInterval(service,1);refresh() },
            LinearLayout.LayoutParams(dp(43),dp(43)))
        put(view,interval,44)

        progressText=text("Nenhuma rota",13f)
        put(view,progressText,27)
        progress=ProgressBar(service,null,android.R.attr.progressBarStyleHorizontal).apply { max=1000;progress=0 }
        put(view,progress,18)
        currentCode=text("Importe um arquivo para começar",12f)
        put(view,currentCode,25)
        state=text("Aguardando",12f,blue)
        put(view,state,29)

        val actions=LinearLayout(service).apply { orientation=LinearLayout.HORIZONTAL }
        fun action(label:String,color:Int,callback:()->Unit) {
            val b=button(label,callback)
            b.background=background(color)
            b.setTextColor(Color.WHITE)
            actions.addView(b,LinearLayout.LayoutParams(0,dp(51),1f).apply { rightMargin=dp(5) })
        }
        action("▶ INICIAR",Color.rgb(16,126,78)) { start() }
        action("Ⅱ PAUSAR",blue) { pause("Pausado pelo usuário") }
        action("✕ CANCELAR",red) { cancel() }
        put(view,actions,53)
        put(view,text("Só envia ao campo ativo. Enter enviado não comprova baixa.",10f),30)
        if(ReplayRoutes.runState(service)=="Executando") {
            ReplayRoutes.saveRunState(service,"Interrompido (verifique o último BR)")
        }
        refresh()
    }
    private fun launchImport() {
        pause("Pausado para importar arquivo")
        service.startActivity(Intent(service,RouteImportActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    private fun selectRoute() {
        val routes=ReplayRoutes.routes(service)
        if(routes.isEmpty()) { state.text="Importe um Excel primeiro";return }
        val menu=PopupMenu(service,routeButton)
        routes.forEachIndexed { i,r ->
            menu.menu.add(0,i,i,r.title+" ("+r.codes.size+")")
        }
        menu.setOnMenuItemClickListener { item ->
            pause("Rota alterada")
            ReplayRoutes.select(service,routes[item.itemId].id)
            refresh()
            true
        }
        menu.show()
    }
    fun refresh() {
        val route=ReplayRoutes.active(service)
        routeButton.text=route?.let { "ROTA: "+it.title+" ▾" } ?: "SELECIONAR ROTA ▾"
        seconds.text=String.format(Locale("pt","BR"),"%.1f s",ReplayRoutes.intervalMillis(service)/1000.0)
        progressText.text=route?.let { it.nextIndex.toString()+" / "+it.codes.size+" códigos com Enter enviado" }
            ?: "Nenhuma rota importada"
        progress.progress=route?.let { if(it.codes.isEmpty()) 0 else it.nextIndex*1000/it.codes.size } ?: 0
        currentCode.text=route?.let {
            if(it.remaining==0) "Rota concluída. Importe outro arquivo para uma nova execução."
            else "Próximo: "+it.codes[it.nextIndex]+"  •  restam "+it.remaining
        } ?: "Selecione uma planilha Excel do celular"
        if(!running) state.text=ReplayRoutes.runState(service)
    }
    fun onInputReady() {
        refresh()
        if(running) pause("Pausado após mudança de campo")
    }
    fun onHidden() { pause("Pausado: teclado fechado ou foco perdido") }
    fun onDestroyed() { pause("Interrompido") }
    private fun start() {
        if(running) return
        val route=ReplayRoutes.active(service)
        if(route==null) { state.text="Importe Excel antes de iniciar";return }
        if(route.remaining==0) { state.text="Rota finalizada; sem repetição automática";return }
        if(!service.isInputViewShown || service.currentInputConnection==null) {
            state.text="Selecione o campo de destino e abra o teclado";return
        }
        if(service.isSecretField()) { state.text="Envio desativado em campos de senha";return }
        selectedId=route.id
        hostPackage=service.currentInputEditorInfo?.packageName.orEmpty()
        hostField=service.currentInputEditorInfo?.fieldId ?: -1
        version++
        running=true
        ReplayRoutes.saveRunState(service,"Executando")
        state.text="Executando • "+route.remaining+" BRs pendentes"
        sendNext(version)
    }
    private fun sameTarget(): Boolean {
        val now=service.currentInputEditorInfo ?: return false
        if(now.packageName != hostPackage) return false
        if(hostField>=0 && now.fieldId>=0 && hostField!=now.fieldId) return false
        return true
    }
    private fun sendNext(expectedVersion:Int) {
        if(!running || expectedVersion!=version) return
        val route=ReplayRoutes.active(service)
        if(route==null || route.id!=selectedId) { pause("Rota alterada; reenvio pausado");return }
        if(route.remaining==0) { finish();return }
        val ic=service.currentInputConnection
        if(ic==null || !service.isInputViewShown || !sameTarget() || service.isSecretField()) {
            pause("Campo mudou ou perdeu foco. Selecione o destino e toque INICIAR.")
            return
        }
        val pos=route.nextIndex
        val code=route.codes[pos]
        val inserted=try { ic.commitText(code,1) } catch(_:Exception) { false }
        if(!inserted) {
            pause("Não foi possível digitar "+code+". Verifique o campo.");return
        }
        // Enter no mesmo ciclo do código: evita duplicação por pausas entre os dois.
        val entered=try {
            val down=ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER))
            val up=ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_ENTER))
            if(down && up) true
            else ic.performEditorAction(EditorInfo.IME_ACTION_DONE)
        } catch(_:Exception) { false }
        if(!entered) {
            pause("BR "+code+" digitado, mas Enter incerto. Revise antes de retomar.")
            return
        }
        if(!ReplayRoutes.advance(service,route.id,pos)) {
            pause("Falha ao salvar progresso. Não continuar para evitar repetição.");return
        }
        refresh()
        state.text="Enter enviado: "+code+" • aguardando "+String.format(Locale("pt","BR"),"%.1f",ReplayRoutes.intervalMillis(service)/1000.0)+" s"
        val delay=ReplayRoutes.intervalMillis(service).toLong()
        handler.postDelayed({ sendNext(expectedVersion) },delay)
    }
    private fun finish() {
        running=false
        version++
        ReplayRoutes.saveRunState(service,"Concluído (envios pelo teclado, não baixas confirmadas)")
        refresh()
    }
    private fun pause(reason:String) {
        running=false
        version++
        ReplayRoutes.saveRunState(service,reason)
        refresh()
        state.text=reason
    }
    private fun cancel() {
        pause("Cancelado; progresso guardado. Iniciar continua do próximo BR.")
    }
}
