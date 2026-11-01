package br.com.rastrai.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.view.KeyEvent
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private class KeyboardCameraLifecycle : LifecycleOwner {
    private val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.CREATED }
    override val lifecycle: Lifecycle get() = registry
    fun visible() { registry.currentState = Lifecycle.State.RESUMED }
    fun hidden() { if (registry.currentState != Lifecycle.State.DESTROYED) registry.currentState = Lifecycle.State.CREATED }
    fun destroy() { registry.currentState = Lifecycle.State.DESTROYED }
}

class TrackingImeService : InputMethodService() {
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)
    private val owner = KeyboardCameraLifecycle()
    private val mlkit = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder().setBarcodeFormats(
            Barcode.FORMAT_QR_CODE, Barcode.FORMAT_CODE_128, Barcode.FORMAT_CODE_39,
            Barcode.FORMAT_CODE_93, Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8,
            Barcode.FORMAT_PDF417, Barcode.FORMAT_DATA_MATRIX, Barcode.FORMAT_ITF,
            Barcode.FORMAT_CODABAR
        ).build()
    )
    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var previewCase: Preview? = null
    private var analysisCase: ImageAnalysis? = null
    private var previewView: PreviewView? = null
    private var status: TextView? = null
    private var history: LinearLayout? = null
    private var count: TextView? = null
    private var toggle: Button? = null
    private var active = false
    private var enabled = true
    private var sending = false
    private var lastCandidate = ""
    private var lastCandidateAt = 0L
    private var lastScannedAt = 0L
    private val sound by lazy { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 65) }

    private fun d(n: Int) = (resources.displayMetrics.density * n).toInt()
    private val bg = Color.rgb(230,236,245)
    private val ink = Color.rgb(20,35,57)
    private val blue = Color.rgb(37,95,204)

    private fun card(color: Int, radius: Int = 11) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = d(radius).toFloat()
    }
    private fun label(text: String, size: Float, color: Int = ink) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color)
    }
    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text; textSize = 11f; isAllCaps = false
        setTextColor(ink); setPadding(d(2),0,d(2),0)
        background = card(Color.WHITE,10)
        setOnClickListener { onClick() }
    }
    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(d(7),d(5),d(7),d(8))
            setBackgroundColor(bg)
        }
        val title = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
        title.addView(label("RASTREIOS BR  •  CÂMERA NO TECLADO", 12f).apply { setTypeface(null,Typeface.BOLD) },
            LinearLayout.LayoutParams(0,d(26),1f))
        count = label("",12f,blue)
        title.addView(count)
        root.addView(title)

        val main = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        val cameraBox = FrameLayout(this).apply { background = card(Color.BLACK,12) }
        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        cameraBox.addView(previewView,FrameLayout.LayoutParams(-1,-1))
        val hint = label("Aponte a etiqueta para a câmera",10f,Color.WHITE).apply {
            gravity = Gravity.CENTER;setBackgroundColor(Color.argb(170,0,0,0))
        }
        cameraBox.addView(hint,FrameLayout.LayoutParams(-1,d(28),Gravity.BOTTOM))
        main.addView(cameraBox,LinearLayout.LayoutParams(0,d(205),1.25f).apply {
            marginEnd = d(6)
        })
        val right = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; background=card(Color.WHITE,12);setPadding(d(6),d(6),d(4),d(4)) }
        right.addView(label("ÚLTIMOS CÓDIGOS",11f).apply {setTypeface(null,Typeface.BOLD)})
        val scroll = ScrollView(this)
        history = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        scroll.addView(history)
        right.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        main.addView(right,LinearLayout.LayoutParams(0,d(205),0.85f))
        root.addView(main)

        status = label("Lendo QR e código de barras",12f).apply { setPadding(d(3),d(5),d(3),d(5)) }
        root.addView(status)
        val actions=LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun action(text:String,weight:Float,click:()->Unit) {
            val b=button(text,click)
            actions.addView(b,LinearLayout.LayoutParams(0,d(49),weight).apply { marginEnd = d(4) })
            if(text == "QR / BARRAS") toggle=b
        }
        action("QR / BARRAS",1.1f) {
            enabled = !enabled
            toggle?.text=if(enabled) "QR / BARRAS" else "RETOMAR"
            if(enabled) {
                setStatus("Leitura ativada")
                if(!active) startCamera()
            } else setStatus("Leitura pausada • câmera visível")
        }
        action("ENVIAR LOTE",1f) { sendQueue() }
        action("APAGAR",0.75f) { deleteLast() }
        root.addView(actions)
        val save = button("SALVAR PLANILHA EXCEL  •  ESCOLHER MEMÓRIA / CARTÃO SD") { exportExcel() }
        save.setTextColor(Color.WHITE)
        save.background=card(blue,11)
        root.addView(save,LinearLayout.LayoutParams(-1,d(49)).apply { topMargin=d(4) })
        render()
        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info,restarting)
        handler.post { render();sendQueue() }
    }
    override fun onWindowShown() {
        super.onWindowShown()
        active=true
        owner.visible()
        previewView?.post { startCamera() }
        handler.postDelayed({ sendQueue() },250)
    }
    override fun onWindowHidden() {
        active=false
        stopCamera()
        owner.hidden()
        sending=false
        handler.removeCallbacksAndMessages(null)
        super.onWindowHidden()
    }
    override fun onFinishInputView(finishingInput:Boolean) {
        active=false
        stopCamera()
        owner.hidden()
        sending=false
        handler.removeCallbacksAndMessages(null)
        super.onFinishInputView(finishingInput)
    }

    private fun setStatus(text:String) { status?.text=text }
    private fun render() {
        val records=Store.list(this)
        count?.text = records.size.toString()+" salvos"
        val container=history ?: return
        container.removeAllViews()
        if(records.isEmpty()) {
            container.addView(label("Ainda sem rastreios.",10f))
        } else {
            records.takeLast(6).asReversed().forEach { r ->
                val l=label(r.code,10f)
                l.maxLines=2
                l.setPadding(0,d(4),0,d(4))
                container.addView(l)
            }
        }
    }

    private fun startCamera() {
        if(!active || previewView==null) return
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            setStatus("Permita a câmera abrindo o aplicativo Leitor de Rastreios BR")
            return
        }
        val future=ProcessCameraProvider.getInstance(this)
        future.addListener({
            if(!active || previewView==null) return@addListener
            try {
                val cp=future.get()
                provider=cp
                stopCamera()
                val preview = Preview.Builder().build()
                preview.setSurfaceProvider(previewView!!.surfaceProvider)
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(worker) { image -> detect(image) }
                previewCase=preview
                analysisCase=analysis
                camera=cp.bindToLifecycle(owner,CameraSelector.DEFAULT_BACK_CAMERA,preview,analysis)
                setStatus(if(enabled) "Câmera ativa • aproxime o pacote" else "Câmera ativa • leitura pausada")
            } catch(e:Exception) { setStatus("Erro ao abrir a câmera: " + e.message) }
        },ContextCompat.getMainExecutor(this))
    }
    private fun stopCamera() {
        camera?.cameraControl?.enableTorch(false)
        val a=analysisCase
        val p=previewCase
        if(a!=null && p!=null) try { provider?.unbind(a,p) } catch(_:Exception) {}
        analysisCase=null;previewCase=null;camera=null
    }

    @OptIn(ExperimentalGetImage::class)
    private fun detect(proxy:ImageProxy) {
        if(!active || !enabled || !busy.compareAndSet(false,true)) {
            proxy.close(); return
        }
        val media=proxy.image
        if(media==null) { busy.set(false);proxy.close();return }
        val input=InputImage.fromMediaImage(media,proxy.imageInfo.rotationDegrees)
        mlkit.process(input)
            .addOnSuccessListener { barcodes ->
                if(!active || !enabled) return@addOnSuccessListener
                val found=barcodes.firstNotNullOfOrNull { b ->
                    b.rawValue?.let { raw ->
                        TrackingParser.parse(raw,Store.strict(this))?.let { Pair(it,b.format) }
                    }
                }
                if(found!=null) accept(found.first,found.second)
            }
            .addOnFailureListener { setStatus("Falha na leitura. Ajuste iluminação e foco.") }
            .addOnCompleteListener { proxy.close(); busy.set(false) }
    }
    private fun accept(code:String, format:Int) {
        if(!active || !enabled) return
        val now=System.currentTimeMillis()
        if(code!=lastCandidate || now-lastCandidateAt>1500) {
            lastCandidate=code;lastCandidateAt=now;return
        }
        if(now-lastScannedAt<800) return
        lastCandidate="";lastScannedAt=now
        val type=if(format==Barcode.FORMAT_QR_CODE) "QR" else "BARRAS"
        if(!Store.add(this,code,type)) {
            setStatus("Já está salvo: " + code)
            return
        }
        QueueStore.append(this,listOf(code))
        try {
            sound.startTone(ToneGenerator.TONE_PROP_BEEP,100)
            (getSystemService(VIBRATOR_SERVICE) as Vibrator).vibrate(
                VibrationEffect.createOneShot(55,VibrationEffect.DEFAULT_AMPLITUDE))
        } catch(_:Exception) {}
        setStatus("Registrado: " + code)
        render()
        sendQueue()
    }
    private fun passwordField():Boolean {
        val type=currentInputEditorInfo?.inputType ?: 0
        val v=type and InputType.TYPE_MASK_VARIATION
        return (type and InputType.TYPE_MASK_CLASS)==InputType.TYPE_CLASS_TEXT &&
            (v==InputType.TYPE_TEXT_VARIATION_PASSWORD ||
             v==InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
             v==InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
    }
    private fun sendQueue() {
        if(sending) return
        val first=QueueStore.list(this).firstOrNull() ?: return
        if(passwordField()) { setStatus("Campo de senha: envio bloqueado");return }
        val connection=currentInputConnection
        if(connection==null || !isInputViewShown) {
            setStatus("Salvo: "+first+" • toque em um campo para enviar")
            return
        }
        sending=true
        val committed=try { connection.commitText(first,1) } catch(_:Exception) { false }
        if(!committed) {
            sending=false;setStatus("Falha ao preencher campo • use ENVIAR LOTE");return
        }
        setStatus("Inserido: "+first+" • Enter automático...")
        handler.postDelayed({
            try {
                // Envia Enter ao MESMO campo que recebeu o código.
                if(!connection.performEditorAction(EditorInfo.IME_ACTION_GO)) {
                    connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER))
                    connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_ENTER))
                }
                QueueStore.pop(this)
                setStatus("Enviado + Enter: "+first)
            } catch(e:Exception) {
                setStatus("Falha no Enter: "+e.message)
                // Não repetir automaticamente um código já inserido.
                QueueStore.pop(this)
            } finally {
                sending=false
                if(active) handler.postDelayed({ sendQueue() },650)
            }
        },650)
    }
    private fun deleteLast() {
        if(sending) { setStatus("Conclua o envio atual antes de apagar");return }
        val removed=Store.removeLast(this)
        if(removed!=null) {
            QueueStore.remove(this,removed.code)
            render()
            setStatus("Removido da lista: "+removed.code+" (não desfaz no outro app)")
        } else setStatus("Lista vazia")
    }
    private fun exportExcel() {
        val records=Store.list(this)
        if(records.isEmpty()) { setStatus("Escaneie um pacote antes de salvar");return }
        startActivity(Intent(this,ExportActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("session",Store.active(this)))
    }
    override fun onDestroy() {
        active=false
        stopCamera()
        owner.destroy()
        handler.removeCallbacksAndMessages(null)
        mlkit.close()
        worker.shutdown()
        super.onDestroy()
    }
}
