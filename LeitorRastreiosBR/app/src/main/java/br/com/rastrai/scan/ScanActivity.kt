package br.com.rastrai.scan

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class ScanActivity : ComponentActivity() {
    companion object {
        const val FROM_IME="from_ime"
        const val BATCH="batch"
    }
    private val worker=Executors.newSingleThreadExecutor()
    private val busy=AtomicBoolean(false)
    private val scanned=mutableListOf<String>()
    private lateinit var status:TextView
    private lateinit var counter:TextView
    private lateinit var previewView:PreviewView
    private var cam:Camera?=null
    private var closed=false
    private var fromIme=false
    private var batch=false
    private var lastCandidate=""
    private var lastSeen=0L
    private var lastAccepted=0L
    private val tone by lazy { ToneGenerator(AudioManager.STREAM_NOTIFICATION,75) }
    private val scanner=BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder().setBarcodeFormats(
            Barcode.FORMAT_QR_CODE,Barcode.FORMAT_CODE_128,Barcode.FORMAT_CODE_39,
            Barcode.FORMAT_CODE_93,Barcode.FORMAT_EAN_13,Barcode.FORMAT_EAN_8,
            Barcode.FORMAT_PDF417,Barcode.FORMAT_DATA_MATRIX,Barcode.FORMAT_ITF,
            Barcode.FORMAT_CODABAR).build()
    )
    private val permission=registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if(granted) startCamera() else status.text="Autorize a câmera para escanear."
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        fromIme=intent.getBooleanExtra(FROM_IME,false)
        batch=intent.getBooleanExtra(BATCH,false)
        drawScreen()
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)
            startCamera()
        else permission.launch(Manifest.permission.CAMERA)
    }
    private fun drawScreen() {
        val screen=FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        previewView=PreviewView(this).apply { scaleType=PreviewView.ScaleType.FILL_CENTER }
        screen.addView(previewView,FrameLayout.LayoutParams(-1,-1))
        val header=Ui.column(this).apply { setBackgroundColor(Color.argb(225,21,38,61)) }
        status=Ui.label(this,"Aponte a câmera para o QR Code ou barras",16f).apply { setTextColor(Color.WHITE) }
        counter=Ui.label(this,"0 escaneados",23f).apply { setTextColor(Color.WHITE) }
        Ui.add(header,status); Ui.add(header,counter)
        screen.addView(header,FrameLayout.LayoutParams(-1,-2,Gravity.TOP))
        val bar=Ui.row(this).apply { setBackgroundColor(Color.argb(225,21,38,61)) }
        val flash=Ui.button(this,"LANTERNA")
        val done=Ui.button(this,if(fromIme) "CONCLUIR / ENVIAR" else "CONCLUIR")
        bar.addView(flash,LinearLayout.LayoutParams(0,Ui.dp(this,66),1f))
        bar.addView(done,LinearLayout.LayoutParams(0,Ui.dp(this,66),2f))
        screen.addView(bar,FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM))
        flash.setOnClickListener { cam?.cameraControl?.enableTorch(cam?.cameraInfo?.torchState?.value != 1) }
        done.setOnClickListener { finishScanner() }
        setContentView(screen)
    }
    private fun startCamera() {
        val future=ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                if(closed) return@addListener
                val provider=future.get()
                val preview=Preview.Builder().build()
                preview.surfaceProvider=previewView.surfaceProvider
                val analysis=ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(worker) { proxy -> detect(proxy) }
                provider.unbindAll()
                cam=provider.bindToLifecycle(this,CameraSelector.DEFAULT_BACK_CAMERA,preview,analysis)
            } catch(e:Exception) { status.text="Falha na câmera: "+e.message }
        },ContextCompat.getMainExecutor(this))
    }
    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    private fun detect(proxy:ImageProxy) {
        if(closed || !busy.compareAndSet(false,true)) { proxy.close();return }
        val image=proxy.image
        if(image==null) { proxy.close();busy.set(false);return }
        val input=InputImage.fromMediaImage(image,proxy.imageInfo.rotationDegrees)
        scanner.process(input).addOnSuccessListener { barcodes ->
            val code=barcodes.firstNotNullOfOrNull { b ->
                b.rawValue?.let { raw -> TrackingParser.parse(raw,Store.strict(this))?.let { Pair(it,b.format) } }
            }
            if(code!=null) runOnUiThread { accept(code.first,code.second) }
            else if(barcodes.isNotEmpty()) runOnUiThread { status.text="Etiqueta lida, mas não corresponde ao filtro BR/SPXBR" }
        }.addOnCompleteListener {
            proxy.close()
            busy.set(false)
        }
    }
    private fun accept(code:String,format:Int) {
        if(closed) return
        val now=System.currentTimeMillis()
        if(code!=lastCandidate || now-lastSeen>1700) {
            lastCandidate=code;lastSeen=now;return
        }
        if(now-lastAccepted<1200) return
        lastAccepted=now
        lastCandidate=""
        val kind=if(format==Barcode.FORMAT_QR_CODE) "QR" else "BARRAS"
        if(Store.add(this,code,kind)) {
            scanned.add(code)
            counter.text=scanned.size.toString()+" nesta câmera"
            status.text="Registrado: "+code
            tone.startTone(ToneGenerator.TONE_PROP_BEEP,130)
            try {
                (getSystemService(VIBRATOR_SERVICE) as Vibrator)
                    .vibrate(VibrationEffect.createOneShot(70,VibrationEffect.DEFAULT_AMPLITUDE))
            } catch(_:Exception) {}
            if(fromIme && !batch) finishScanner()
        } else status.text="Código repetido na lista: "+code
    }
    private fun finishScanner() {
        if(closed) return
        closed=true
        if(fromIme && scanned.isNotEmpty()) QueueStore.append(this,scanned)
        finish()
    }
    @Deprecated("Legacy back")
    override fun onBackPressed() { finishScanner() }
    override fun onDestroy() {
        closed=true
        scanner.close()
        if(toneInitialized()) tone.release()
        worker.shutdown()
        super.onDestroy()
    }
    private fun toneInitialized() = try { tone.hashCode(); true } catch(_:Exception) { false }
}
