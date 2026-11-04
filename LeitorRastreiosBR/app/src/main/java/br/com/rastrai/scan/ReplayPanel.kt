package br.com.rastrai.scan

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

/**
 * Execução de BRs em campo Android. O teclado não controla a página web.
 * Após um Enter que oculta o IME, guarda a intenção e tenta retomar SOMENTE
 * se o campo original reaparece no mesmo aplicativo dentro da janela segura.
 */
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
    private lateinit var clearButton: Button
    private lateinit var cancelButton: Button
    private lateinit var seconds: TextView
    private lateinit var progressText: TextView
    private lateinit var currentCode: TextView
    private lateinit var codeList: LinearLayout
    private lateinit var state: TextView
    private lateinit var progress: ProgressBar

    private var running=false
    private var awaitingFocus=false
    private var generation=0
    private var selectedId=""
    private var hostPackage=""
    private var hostField=-1
    private var hostInputType=0
    private var hostHint=""
    private var lastEnterAt=0L
    private var cancelConfirmUntil=0L
    private var clearConfirmUntil=0L

    private fun dp(n: Int) = (service.resources.displayMetrics.density*n).toInt()
    private fun background(color:Int) = GradientDrawable().apply {
        setColor(color); cornerRadius=dp(11).toFloat()
    }
    private fun text(value:String,size:Float=13f, color:Int=ink) = TextView(service).apply {
        text=value; textSize=size; setTextColor(color)
    }
    private fun button(value:String,callback:()->Unit)=Button(service).apply {
        text=value; isAllCaps=false; textSize=12f
        minHeight=0; minimumHeight=0; setPadding(dp(4),0,dp(4),0)
        background=background(Color.WHITE)
        setTextColor(ink);setOnClickListener { callback() }
    }
    private fun put(parent:LinearLayout,child:View,height:Int=44) {
        parent.addView(child,LinearLayout.LayoutParams(-1,dp(height)).apply { bottomMargin=dp(4) })
    }
    init {
        view=LinearLayout(service).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(8),dp(6),dp(8),dp(8))
            setBackgroundColor(Color.rgb(232,238,247))
        }
        val header=LinearLayout(service).apply { gravity=Gravity.CENTER_VERTICAL }
        header.addView(button("← VOLTAR") { manualPause("Pausado ao voltar à câmera");back() },
            LinearLayout.LayoutParams(dp(105),dp(38)))
        header.addView(text("REENVIO DE RASTREIOS",14f).apply {
            gravity=Gravity.CENTER;setTypeface(null,Typeface.BOLD)
        },LinearLayout.LayoutParams(0,dp(38),1f))
        put(view,header,39)

        val imports=LinearLayout(service).apply { orientation=LinearLayout.HORIZONTAL }
        val importer=button("IMPORTAR EXCEL") { launchImport() }.apply {
            background=background(blue);setTextColor(Color.WHITE)
        }
        imports.addView(importer,LinearLayout.LayoutParams(0,dp(44),1f).apply { rightMargin=dp(4) })
        clearButton=button("LIMPAR LISTA") { clearSelectedRoute() }.apply {
            background=background(Color.rgb(255,233,230));setTextColor(red)
        }
        imports.addView(clearButton,LinearLayout.LayoutParams(0,dp(44),1f))
        put(view,imports,46)

        routeButton=button("SELECIONAR ROTA") { selectRoute() }.apply { textSize=13f }
        put(view,routeButton,39)

        val interval=LinearLayout(service).apply { orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL }
        interval.addView(text("Intervalo após Enter",12f),LinearLayout.LayoutParams(0,dp(43),1f))
        interval.addView(button("−") { ReplayRoutes.adjustInterval(service,-1);refresh() },
            LinearLayout.LayoutParams(dp(43),dp(43)).apply { rightMargin=dp(5) })
        seconds=text("3,0 s",15f).apply { gravity=Gravity.CENTER;setTypeface(null,Typeface.BOLD) }
        interval.addView(seconds,LinearLayout.LayoutParams(dp(58),dp(43)))
        interval.addView(button("+") { ReplayRoutes.adjustInterval(service,1);refresh() },
            LinearLayout.LayoutParams(dp(43),dp(43)))
        put(view,interval,44)

        progressText=text("Nenhuma rota",13f)
        put(view,progressText,27)
        progress=ProgressBar(service,null,android.R.attr.progressBarStyleHorizontal).apply { max=1000;progress=0 }
        put(view,progress,18)
        currentCode=text("Importe um arquivo para começar",12f)
        put(view,currentCode,25)

        val scroll=ScrollView(service).apply {
            background=background(Color.WHITE)
            setPadding(dp(7),dp(4),dp(7),dp(4))
        }
        codeList=LinearLayout(service).apply { orientation=LinearLayout.VERTICAL }
        scroll.addView(codeList)
        put(view,scroll,90)

        state=text("Aguardando",12f,blue)
        put(view,state,29)
        val actions=LinearLayout(service).apply { orientation=LinearLayout.HORIZONTAL }
        fun action(label:String,color:Int,callback:()->Unit):Button {
            val b=button(label,callback)
            b.background=background(color);b.setTextColor(Color.WHITE)
            actions.addView(b,LinearLayout.LayoutParams(0,dp(51),1f).apply { rightMargin=dp(5) })
            return b
        }
        action("▶ INICIAR",Color.rgb(16,126,78)) { start() }
        action("Ⅱ PAUSAR",blue) { manualPause("Pausado pelo usuário") }
        cancelButton=action("✕ CANCELAR",red) { cancelOrReset() }
        put(view,actions,53)
        put(view,text("Enter enviado ≠ baixa confirmada. Continuação somente no mesmo campo.",10f),29)

        restoreContinuation()
        refresh()
    }
    private fun restoreContinuation() {
        val cont=ReplayRoutes.continuation(service) ?: return
        val route=ReplayRoutes.active(service)
        if(route==null || route.id!=cont.routeId || route.remaining==0) {
            ReplayRoutes.stopContinuation(service)
            return
        }
        selectedId=cont.routeId
        hostPackage=cont.targetPackage
        hostField=cont.targetFieldId
        hostInputType=cont.targetInputType
        hostHint=cont.targetHint
        lastEnterAt=cont.lastEnterAt
        awaitingFocus=true
        ReplayRoutes.saveRunState(service,"Aguardando retorno ao campo para continuar")
    }
    private fun launchImport() {
        manualPause("Pausado para importar arquivo")
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
            manualPause("Rota alterada")
            ReplayRoutes.select(service,routes[item.itemId].id)
            clearConfirmUntil=0
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
            if(it.remaining==0) "Rota concluída. Use Cancelar duas vezes para reiniciar."
            else "Próximo: "+it.codes[it.nextIndex]+" • restam "+it.remaining
        } ?: "Selecione uma planilha Excel do celular"
        codeList.removeAllViews()
        if(route==null) codeList.addView(text("Nenhum BR carregado",12f))
        else {
            val begin=(route.nextIndex-2).coerceAtLeast(0)
            val end=(begin+25).coerceAtMost(route.codes.size)
            for(index in begin until end) {
                val marker=when {
                    index<route.nextIndex -> "✓"
                    index==route.nextIndex -> "▶"
                    else -> "•"
                }
                codeList.addView(text(marker+" "+(index+1)+". "+route.codes[index],
                    12f,if(index==route.nextIndex) blue else ink).apply {
                    setPadding(0,dp(4),0,dp(4))
                    if(index==route.nextIndex) setTypeface(null,Typeface.BOLD)
                })
            }
        }
        if(!running && !awaitingFocus) state.text=ReplayRoutes.runState(service)
    }
    private fun currentTargetIsCompatible():Boolean {
        val editor=service.currentInputEditorInfo ?: return false
        if(!service.isInputViewShown || service.currentInputConnection==null ||
            service.isSecretField()) return false
        if(!ReplayResumePolicy.sameField(hostPackage,hostField,hostInputType,hostHint,
                editor.packageName.orEmpty(),editor.fieldId,editor.inputType,
                editor.hintText?.toString().orEmpty())) return false
        val variation=editor.inputType and InputType.TYPE_MASK_VARIATION
        if(variation==InputType.TYPE_TEXT_VARIATION_URI ||
            variation==InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT &&
            hostInputType==0) return false
        return true
    }
    /** O campo de destino precisa estar vazio ou com texto selecionado para substituir. */
    private fun safeToType():Boolean {
        val ic=service.currentInputConnection ?: return false
        val selected=runCatching { ic.getSelectedText(0)?.toString().orEmpty() }.getOrDefault("")
        if(selected.isNotEmpty()) return true
        val before=runCatching { ic.getTextBeforeCursor(128,0)?.toString().orEmpty() }.getOrDefault("")
        val after=runCatching { ic.getTextAfterCursor(128,0)?.toString().orEmpty() }.getOrDefault("")
        return before.isBlank() && after.isBlank()
    }
    fun onInputReady() {
        refresh()
        if(!awaitingFocus) {
            val saved=ReplayRoutes.continuation(service)
            if(saved!=null) restoreContinuation()
        }
        if(!awaitingFocus) return
        // onStartInputView pode ocorrer ANTES da janela ser exibida; deixa a UI estabilizar.
        val epoch=++generation
        handler.postDelayed({ autoResume(epoch) },450)
    }
    private fun autoResume(epoch:Int) {
        if(epoch!=generation || !awaitingFocus) return
        val cont=ReplayRoutes.continuation(service)
        if(cont==null || cont.routeId!=selectedId) {
            manualPause("Continuação expirada ou rota modificada; toque INICIAR.")
            return
        }
        if(!service.isInputViewShown || service.currentInputConnection==null) {
            state.text="Aguardando o campo de entrada voltar"
            return
        }
        if(!currentTargetIsCompatible()) {
            manualPause("Campo diferente detectado. Reenvio bloqueado; confira e toque INICIAR.")
            return
        }
        if(!safeToType()) {
            manualPause("O campo contém texto não selecionado. Revise para não concatenar BRs.")
            return
        }
        awaitingFocus=false
        running=true
        val version=++generation
        ReplayRoutes.saveRunState(service,"Retomando após Enter e retorno do teclado")
        // Cumpre o intervalo desde o Enter anterior, mesmo se a janela se ocultou.
        val remaining=ReplayResumePolicy.remainingDelay(lastEnterAt,
            ReplayRoutes.intervalMillis(service),System.currentTimeMillis())
        handler.postDelayed({ sendNext(version) },remaining)
        state.text="Campo recuperado • continuando automaticamente"
    }
    /** Ao sumir o teclado, mantém a intenção; NÃO reenvia sem verificar o campo. */
    fun onHidden() {
        if(running || awaitingFocus || ReplayRoutes.continuation(service)!=null) {
            running=false
            awaitingFocus=true
            generation++
            ReplayRoutes.saveRunState(service,"Aguardando campo: retomada automática se for o mesmo")
            state.text="Aguardando campo de texto voltar"
        }
    }
    fun onDestroyed() {
        if(running || awaitingFocus) {
            running=false
            generation++
            ReplayRoutes.saveRunState(service,"Aguardando campo após reinício do teclado")
        }
    }
    fun onLeaveReplayPage() { manualPause("Pausado ao voltar à câmera") }
    private fun start() {
        if(running) return
        val route=ReplayRoutes.active(service)
        if(route==null) { state.text="Importe Excel antes de iniciar";return }
        if(route.remaining==0) { state.text="Rota terminou. Cancele duas vezes para reiniciar do primeiro.";return }
        if(!service.isInputViewShown || service.currentInputConnection==null) {
            state.text="Selecione um campo de texto e abra o teclado";return
        }
        if(service.isSecretField()) { state.text="Envio bloqueado em campos de senha";return }
        val editor=service.currentInputEditorInfo ?: run {
            state.text="Campo indisponível";return
        }
        if(editor.packageName.isNullOrBlank()) {
            state.text="Não foi possível identificar o aplicativo de destino";return
        }
        val variation=editor.inputType and InputType.TYPE_MASK_VARIATION
        if(variation==InputType.TYPE_TEXT_VARIATION_URI) {
            state.text="Não enviar rastreios à barra de endereço do navegador";return
        }
        if(!safeToType()) {
            state.text="Selecione o texto do campo ou limpe antes de iniciar";return
        }
        cancelConfirmUntil=0
        cancelButton.text="✕ CANCELAR"
        selectedId=route.id
        hostPackage=editor.packageName
        hostField=editor.fieldId
        hostInputType=editor.inputType
        hostHint=editor.hintText?.toString().orEmpty()
        lastEnterAt=0
        ReplayRoutes.armContinuation(service,selectedId,hostPackage,hostField,hostInputType,hostHint)
        awaitingFocus=false
        running=true
        val version=++generation
        ReplayRoutes.saveRunState(service,"Executando")
        state.text="Executando • "+route.remaining+" BRs restantes"
        sendNext(version)
    }
    private fun sendNext(version:Int) {
        if(!running || version!=generation) return
        val route=ReplayRoutes.active(service)
        if(route==null || route.id!=selectedId) {
            manualPause("Rota modificada. Execução interrompida.");return
        }
        if(route.remaining==0) { finish();return }
        if(!currentTargetIsCompatible()) {
            onHidden()
            return
        }
        if(!safeToType()) {
            manualPause("Campo não está vazio. Corrija antes de continuar.");return
        }
        val connection=service.currentInputConnection ?: run { onHidden();return }
        val index=route.nextIndex
        val code=route.codes[index]
        val inserted=try { connection.commitText(code,1) } catch(_:Exception) { false }
        if(!inserted) {
            manualPause("Falha ao digitar "+code);return
        }
        // Enter enviado imediatamente após cada BR. Se não for aceito, não continuar sozinho.
        val entered=try {
            val down=connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER))
            val up=connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_ENTER))
            if(down && up) true
            else connection.performEditorAction(EditorInfo.IME_ACTION_DONE)
        } catch(_:Exception) { false }
        if(!entered) {
            manualPause("BR digitado, mas Enter incerto. Revise o destino antes de reiniciar.")
            return
        }
        if(!ReplayRoutes.advance(service,route.id,index)) {
            manualPause("Progresso não foi salvo. Reenvio interrompido por segurança.")
            return
        }
        lastEnterAt=System.currentTimeMillis()
        ReplayRoutes.updateLastEnter(service,route.id,lastEnterAt)
        refresh()
        state.text="Enviado: "+code+" • próximo em "+String.format(Locale("pt","BR"),
            "%.1f",ReplayRoutes.intervalMillis(service)/1000.0)+" s"
        // Se o Enter ocultar o teclado, onHidden invalida esta chamada.
        if(running && version==generation) {
            handler.postDelayed({ sendNext(version) },ReplayRoutes.intervalMillis(service).toLong())
        }
    }
    private fun finish() {
        running=false
        awaitingFocus=false
        generation++
        ReplayRoutes.stopContinuation(service)
        ReplayRoutes.saveRunState(service,"Concluído (Enter enviado; baixa não confirmada)")
        refresh()
    }
    private fun manualPause(reason:String) {
        running=false
        awaitingFocus=false
        generation++
        ReplayRoutes.stopContinuation(service)
        ReplayRoutes.saveRunState(service,reason)
        refresh()
        state.text=reason
    }
    private fun cancelOrReset() {
        val now=System.currentTimeMillis()
        val route=ReplayRoutes.active(service)
        if(route==null) { state.text="Nenhuma rota para cancelar";return }
        if(cancelConfirmUntil>now) {
            manualPause("Reiniciando rota")
            cancelConfirmUntil=0
            cancelButton.text="✕ CANCELAR"
            if(ReplayRoutes.resetProgress(service,route.id)) {
                ReplayRoutes.saveRunState(service,"Rota reiniciada: primeiro BR pronto para INICIAR")
                refresh()
                state.text="Rota reiniciada do 1º BR. Toque INICIAR para reenviar."
            }
            return
        }
        manualPause("Cancelado. Toque CANCELAR novamente para voltar ao primeiro BR.")
        cancelConfirmUntil=now+8000L
        cancelButton.text="↺ REINICIAR?"
        handler.postDelayed({
            if(System.currentTimeMillis()>=cancelConfirmUntil) {
                cancelConfirmUntil=0
                cancelButton.text="✕ CANCELAR"
            }
        },8100L)
    }
    private fun clearSelectedRoute() {
        val route=ReplayRoutes.active(service)
        if(route==null) { state.text="Lista já está vazia";return }
        val now=System.currentTimeMillis()
        if(clearConfirmUntil>now) {
            manualPause("Limpando rota selecionada")
            val removed=ReplayRoutes.removeRoute(service,route.id)
            clearConfirmUntil=0
            clearButton.text="LIMPAR LISTA"
            refresh()
            state.text="Lista limpa: "+removed+" BRs removidos da rota. Outras rotas preservadas."
            return
        }
        manualPause("Toque LIMPAR LISTA novamente para apagar "+route.codes.size+" BRs")
        clearConfirmUntil=now+8000L
        clearButton.text="CONFIRMAR LIMPEZA"
        handler.postDelayed({
            if(System.currentTimeMillis()>=clearConfirmUntil) {
                clearConfirmUntil=0
                clearButton.text="LIMPAR LISTA"
            }
        },8100L)
    }
}
