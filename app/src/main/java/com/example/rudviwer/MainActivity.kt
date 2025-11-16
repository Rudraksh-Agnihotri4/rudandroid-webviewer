package com.example.rudviwer

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.media.Image
import android.media.ImageReader
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import android.hardware.camera2.*
import java.nio.ByteBuffer

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"

    private lateinit var glView: GLSurfaceView
    private lateinit var fpsText: TextView
    private lateinit var toggleProcessed: Switch
    private lateinit var saveBtn: Button

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private lateinit var imageReader: ImageReader
    private lateinit var renderer: GLRenderer

    // JNI native functions
    external fun nativeInit(width: Int, height: Int, outBuffer: ByteBuffer)
    external fun nativeProcessFrame(nv21: ByteArray, width: Int, height: Int)
    external fun nativeRelease()

    companion object {
        init {
            System.loadLibrary("edgeproc")
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) openCamera()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        glView = findViewById(R.id.glSurfaceView)
        fpsText = findViewById(R.id.fpsText)
        toggleProcessed = findViewById(R.id.toggleProcessed)
        saveBtn = findViewById(R.id.saveButton)

        // GLSurfaceView setup
        glView.setEGLContextClientVersion(2)
        renderer = GLRenderer(this::saveBitmapCallback) { fps ->
            runOnUiThread { fpsText.text = "FPS: %.1f".format(fps) }
        }
        glView.setRenderer(renderer)
        glView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY

        toggleProcessed.setOnCheckedChangeListener { _, checked ->
            renderer.showProcessed = checked
        }

        saveBtn.setOnClickListener { renderer.requestSave() }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED)
        {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        } else openCamera()
    }

    private fun saveBitmapCallback(bitmap: Bitmap) {
        Log.i(TAG, "Bitmap save requested ${bitmap.width}x${bitmap.height}")
    }

    override fun onPause() {
        super.onPause()
        closeCamera()
        glView.onPause()
        nativeRelease()
    }

    override fun onResume() {
        super.onResume()
        glView.onResume()
    }

    private fun openCamera() {
        val manager = getSystemService(CAMERA_SERVICE) as CameraManager
        val cameraId = manager.cameraIdList[0]

        val previewWidth = 640
        val previewHeight = 480

        imageReader = ImageReader.newInstance(
            previewWidth,
            previewHeight,
            ImageFormat.YUV_420_888,
            2
        )

        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            val nv21 = yuvToNV21(image)
            image.close()

            nativeProcessFrame(nv21, previewWidth, previewHeight)
            runOnUiThread { glView.requestRender() }

        }, null)

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) return

        manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) {
                cameraDevice = device
                createSession()

                val directBuf = ByteBuffer.allocateDirect(previewWidth * previewHeight * 4)
                nativeInit(previewWidth, previewHeight, directBuf)
                renderer.setSharedBuffer(directBuf, previewWidth, previewHeight)
            }

            override fun onDisconnected(p0: CameraDevice) { }
            override fun onError(p0: CameraDevice, p1: Int) { }
        }, null)
    }

    private fun createSession() {
        val surface = imageReader.surface

        cameraDevice?.createCaptureSession(
            listOf(surface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    val request = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    request.addTarget(surface)
                    session.setRepeatingRequest(request.build(), null, null)
                }

                override fun onConfigureFailed(p0: CameraCaptureSession) { }
            },
            null
        )
    }

    private fun closeCamera() {
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
    }

    private fun yuvToNV21(image: Image): ByteArray {
        val w = image.width
        val h = image.height
        val ySize = w * h
        val nv21 = ByteArray(ySize + (ySize / 2))

        // Y plane
        image.planes[0].buffer.get(nv21, 0, ySize)

        // U and V plane
        val u = image.planes[1]
        val v = image.planes[2]
        val uBuf = u.buffer
        val vBuf = v.buffer

        val pixelStride = u.pixelStride
        val rowStride = u.rowStride

        var uvIndex = ySize
        for (row in 0 until h / 2) {
            val rowOffset = row * rowStride
            for (col in 0 until w step pixelStride) {
                nv21[uvIndex++] = vBuf[rowOffset + col]
                nv21[uvIndex++] = uBuf[rowOffset + col]
            }
        }
        return nv21
    }
}
