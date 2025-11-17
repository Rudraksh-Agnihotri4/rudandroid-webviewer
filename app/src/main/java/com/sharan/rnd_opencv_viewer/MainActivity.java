package com.sharan.rnd_opencv_viewer; 

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.GLSurfaceView; 
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;

import javax.microedition.khronos.egl.EGLConfig; 
import javax.microedition.khronos.opengles.GL10; 

// We REMOVED TextureView.SurfaceTextureListener
// We ADDED GLSurfaceView.Renderer
public class MainActivity extends AppCompatActivity implements Camera.PreviewCallback, GLSurfaceView.Renderer {

    private static final String TAG = "MainActivity";
    private static final int CAMERA_REQUEST_CODE = 100;

    // REMOVED: private TextureView textureView;
    private GLSurfaceView glSurfaceView; 
    private Camera camera;
    private byte[] mFrame;
    private SurfaceTexture mSurfaceTexture; // We need this to send preview to a dummy texture

    // --- Our JNI functions ---
    public native void processFrame(int width, int height, byte[] frameData);

    // --- NEW JNI functions for OpenGL ---
    public native void nativeOnSurfaceCreated();
    public native void nativeOnSurfaceChanged(int width, int height);
    public native void nativeOnDrawFrame();

    // Load C++ library
    static {
        System.loadLibrary("rnd_opencv_viewer");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // --- NEW GLSurfaceView setup ---
        glSurfaceView = findViewById(R.id.glSurfaceView);
        glSurfaceView.setEGLContextClientVersion(2); // Use OpenGL ES 2.0
        glSurfaceView.setRenderer(this);
        glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY); // Keep re-drawing
        // --- END NEW GLSurfaceView setup ---

        // We still need a dummy SurfaceTexture to make the Camera1 API work
        mSurfaceTexture = new SurfaceTexture(10); // 10 is an arbitrary texture ID

        // Check for camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_REQUEST_CODE);
        } else {
            // Permission already granted, start camera
            startCamera();
        }
    }

    private void startCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            try {
                camera = Camera.open();

                // Set up the buffer for frame data
                Camera.Parameters params = camera.getParameters();
                // Let's try to set a smaller, faster resolution
                // 1920x1080 is too big for real-time processing on many phones
                params.setPreviewSize(1280, 720); // Try 720p
                camera.setParameters(params);

                Camera.Size previewSize = camera.getParameters().getPreviewSize();
                int dataSize = previewSize.width * previewSize.height * 3 / 2; // (for NV21 format)
                mFrame = new byte[dataSize];

                // Add the buffer to the camera
                camera.addCallbackBuffer(mFrame);

                // Set the callback that fires on every frame
                camera.setPreviewCallbackWithBuffer(this); // 'this' works because we implement Camera.PreviewCallback

                // Tell the camera to start sending preview frames to our dummy texture
                camera.setPreviewTexture(mSurfaceTexture);
                camera.startPreview();
                Log.d(TAG, "Camera preview started with callback");

            } catch (IOException e) {
                Log.e(TAG, "Error setting camera preview", e);
            } catch (RuntimeException e) {
                Log.e(TAG, "Error opening camera. Is it in use?", e);
            }
        } else {
            Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show();
        }
    }

    // This is called when the app is paused (e.g., home button)
    @Override
    protected void onPause() {
        super.onPause();
        glSurfaceView.onPause();
        if (camera != null) {
            camera.stopPreview();
            camera.setPreviewCallbackWithBuffer(null);
            camera.release();
            camera = null;
        }
    }

    // This is called when the app resumes
    @Override
    protected void onResume() {
        super.onResume();
        glSurfaceView.onResume();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        }
    }

    // --- Camera.PreviewCallback implementation ---
    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        if (data != null) {
            // Pass the frame to our C++ function (which stores it in processedMat)
            Camera.Size previewSize = camera.getParameters().getPreviewSize();
            processFrame(previewSize.width, previewSize.height, data);
        }
        // Re-add the buffer for the next frame
        camera.addCallbackBuffer(mFrame);
    }

    // --- GLSurfaceView.Renderer implementation ---
    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        // Call C++ function
        nativeOnSurfaceCreated();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        // Call C++ function
        nativeOnSurfaceChanged(width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        // Call C++ function
        nativeOnDrawFrame();
    }

    // --- Permission request result ---
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission was granted, start camera
                startCamera();
            } else {
                Toast.makeText(this, "Camera permission is required to run this app", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }
}