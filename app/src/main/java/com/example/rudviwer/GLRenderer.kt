package com.example.rudviwer

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class GLRenderer(
    private val saveCallback: (Bitmap) -> Unit,
    private val fpsCallback: (Float) -> Unit
) : GLSurfaceView.Renderer {

    @Volatile var showProcessed: Boolean = true

    private var texWidth = 0
    private var texHeight = 0
    private var textureId = 0
    private var vertexBuffer: FloatBuffer
    private var texBuffer: FloatBuffer
    private var program = 0
    private var positionHandle = 0
    private var texCoordHandle = 0
    private var textureHandle = 0

    @Volatile
    private var sharedBuffer: ByteBuffer? = null

    private var lastTimeNs = 0L
    private var frameCount = 0

    @Volatile
    private var shouldSave = false

    init {
        val vertices = floatArrayOf(
            -1f, -1f,
             1f, -1f,
            -1f,  1f,
             1f,  1f
        )

        val texCoords = floatArrayOf(
            0f, 1f,
            1f, 1f,
            0f, 0f,
            1f, 0f
        )

        vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        vertexBuffer.put(vertices).position(0)

        texBuffer = ByteBuffer.allocateDirect(texCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        texBuffer.put(texCoords).position(0)
    }

    fun setSharedBuffer(buf: ByteBuffer, w: Int, h: Int) {
        sharedBuffer = buf
        texWidth = w
        texHeight = h
    }

    fun requestSave() {
        shouldSave = true
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        program = ShaderUtils.createProgram(
            ShaderUtils.VERTEX_SHADER,
            ShaderUtils.FRAGMENT_SHADER
        )
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        texCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
        textureHandle = GLES20.glGetUniformLocation(program, "uTexture")

        textureId = TextureUtils.createTexture()
        GLES20.glClearColor(0f, 0f, 0f, 1f)

        lastTimeNs = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        val buf = sharedBuffer
        if (buf != null && showProcessed && texWidth > 0 && texHeight > 0) {
            buf.position(0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D,
                0,
                GLES20.GL_RGBA,
                texWidth,
                texHeight,
                0,
                GLES20.GL_RGBA,
                GLES20.GL_UNSIGNED_BYTE,
                buf
            )
        }

        GLES20.glUseProgram(program)

        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(
            positionHandle,
            2,
            GLES20.GL_FLOAT,
            false,
            0,
            vertexBuffer
        )

        GLES20.glEnableVertexAttribArray(texCoordHandle)
        GLES20.glVertexAttribPointer(
            texCoordHandle,
            2,
            GLES20.GL_FLOAT,
            false,
            0,
            texBuffer
        )

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glUniform1i(textureHandle, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)

        // FPS
        frameCount++
        val now = System.nanoTime()
        if (now - lastTimeNs >= 1_000_000_000L) {
            val fps = frameCount * 1_000_000_000f / (now - lastTimeNs)
            fpsCallback(fps)
            frameCount = 0
            lastTimeNs = now
        }

        if (shouldSave) {
            shouldSave = false
            saveCurrentFrame()
        }
    }

    private fun saveCurrentFrame() {
        if (texWidth == 0 || texHeight == 0) return

        val pixelBuffer = ByteBuffer.allocateDirect(texWidth * texHeight * 4)
        pixelBuffer.order(ByteOrder.nativeOrder())

        GLES20.glReadPixels(
            0,
            0,
            texWidth,
            texHeight,
            GLES20.GL_RGBA,
            GLES20.GL_UNSIGNED_BYTE,
            pixelBuffer
        )

        val tmp = IntArray(texWidth * texHeight)
        val out = IntArray(texWidth * texHeight)
        pixelBuffer.asIntBuffer().get(tmp)

        for (y in 0 until texHeight) {
            System.arraycopy(
                tmp,
                y * texWidth,
                out,
                (texHeight - 1 - y) * texWidth,
                texWidth
            )
        }

        val bmp = Bitmap.createBitmap(texWidth, texHeight, Bitmap.Config.ARGB_8888)
        bmp.setPixels(out, 0, texWidth, 0, 0, texWidth, texHeight)

        saveCallback(bmp)
    }
}
