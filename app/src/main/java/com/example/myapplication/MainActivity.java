package com.example.myapplication;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.transition.Scene;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import com.google.ar.core.Anchor;
import com.google.ar.core.Camera;
import com.google.ar.core.CameraConfig;
import com.google.ar.core.CameraConfigFilter;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.InstantPlacementPoint;
import com.google.ar.core.Session;
import com.google.ar.core.exceptions.CameraNotAvailableException;


import java.io.IOException;
import java.util.EnumSet;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class MainActivity extends AppCompatActivity implements GLSurfaceView.Renderer {
    private Session session;
    private Config config;
    private MotionEvent lastTapMotionEvent;
    private boolean placementIsDone;
    private GLSurfaceView surfaceView;
    private final Object frameImageInUseLock = new Object();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        surfaceView = findViewById(R.id.surfaceview);
        surfaceView.setPreserveEGLContextOnPause(true);
        surfaceView.setEGLContextClientVersion(2);
        surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0); // Alpha used for plane blending.
        surfaceView.setRenderer(this);
        surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        try {
            session = new Session(this);
        } catch (Exception e) {
            System.out.println("---------------------------------------new session ERROR: " + e.getMessage());
            this.finishAffinity();
            return;
        }
        config = new Config(session);
        // Set the Instant Placement mode.
        config.setInstantPlacementMode(Config.InstantPlacementMode.LOCAL_Y_UP);
        session.configure(config);

        // Create filter here with desired fps filters.
        CameraConfigFilter cameraConfigFilter =
                new CameraConfigFilter(session)
                        .setTargetFps(
                                EnumSet.of(
                                        CameraConfig.TargetFps.TARGET_FPS_30, CameraConfig.TargetFps.TARGET_FPS_60));
        List<CameraConfig> cameraConfigs = session.getSupportedCameraConfigs(cameraConfigFilter);
        session.setCameraConfig(cameraConfigs.get(0));
        session.setCameraTextureName(-1);
        // ARCore requires camera permissions to operate. If we did not yet obtain runtime
        // permission on Android M and above, now is a good time to ask the user for it.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
                PackageManager.PERMISSION_GRANTED) {
            int CAMERA_PERMISSION_CODE = 0;
            String CAMERA_PERMISSION = Manifest.permission.CAMERA;
            ActivityCompat.requestPermissions(this,
                    new String[]{CAMERA_PERMISSION}, CAMERA_PERMISSION_CODE);
            return;
        }

        try {
            session.resume();
        } catch (CameraNotAvailableException e) {
            System.out.println("Camera not available. Try restarting the app.");
            session = null;
            return;
        }

        lastTapMotionEvent = null;
        placementIsDone = false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent motionEvent) {
        super.onTouchEvent(motionEvent);
        if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
            lastTapMotionEvent = motionEvent;
        }
        return true;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);
//
//        // Create the texture and pass it to ARCore session to be filled during update().
//        try {
//            cpuImageRenderer.createOnGlThread(/* context= */ this);
//
//            // The image format can be either IMAGE_FORMAT_RGBA or IMAGE_FORMAT_I8.
//            // Set keepAspectRatio to false so that the output image covers the whole viewport.
//            textureReader.create(
//                    /* context= */ this,
//                    TextureReaderImage.IMAGE_FORMAT_I8,
//                    IMAGE_WIDTH,
//                    IMAGE_HEIGHT,
//                    false);
//
//        } catch (IOException e) {
//            Log.e(TAG, "Failed to read an asset file", e);
//        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
//        cpuImageDisplayRotationHelper.onSurfaceChanged(width, height);
//        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    protected void onDestroy() {
        if (session != null) {
            // Explicitly close ARCore Session to release native resources.
            // Review the API reference for important considerations before calling close() in apps with
            // more complicated lifecycle requirements:
            // https://developers.google.com/ar/reference/java/arcore/reference/com/google/ar/core/Session#close()
            session.close();
            session = null;
        }

        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            session.resume();
            surfaceView.onResume();
        } catch (CameraNotAvailableException e) {
            System.out.println("Camera not available. Try restarting the app.");
            session = null;
            return;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (session != null) {
            surfaceView.onPause();
            session.pause();
        }
    }


    private void disableInstantPlacement() {
        config.setInstantPlacementMode(Config.InstantPlacementMode.DISABLED);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        if (session == null) {
            return;
        }

        Frame frame;
        try {
            frame = session.update();
        } catch (Exception e) {
            System.out.println("---------------------------------------ERROR in session update: " + e.getMessage());
            this.finishAffinity();
            return;
        }

        // Place an object on tap.
        if (!placementIsDone && (lastTapMotionEvent != null)) {
            // Use estimated distance from the user's device to the real world, based
            // on expected user interaction and behavior.
            float approximateDistanceMeters = 2.0f;
            // Performs a ray cast given a screen tap position.
            List<HitResult> results =
                    frame.hitTestInstantPlacement(lastTapMotionEvent.getX(), lastTapMotionEvent.getY(),
                            approximateDistanceMeters);
            if (!results.isEmpty()) {
                System.out.println("RESULTS NOT EMPTY!");
                InstantPlacementPoint point = (InstantPlacementPoint) results.get(0).getTrackable();
                // Create an Anchor from the point's pose.
                Anchor anchor = point.createAnchor(point.getPose());
                placementIsDone = true;
                disableInstantPlacement();
            }
            else {
                System.out.println("RESULTS EMPTY!");
            }
            lastTapMotionEvent = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
//        if (!CameraPermissionHelper.hasCameraPermission(this)) {
//            // Use toast instead of snackbar here since the activity will exit.
//            Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
//                    .show();
//            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
//                // Permission denied with checking "Do not ask again".
//                CameraPermissionHelper.launchPermissionSettings(this);
//            }
//            finish();
//        }
    }
}

//    @Override
//    public void onDrawFrame(GL10 gl) {
//        // Clear screen to notify driver it should not load any pixels from previous frame.
//        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
//
//        if (session == null) {
//            return;
//        }
//
//        // Synchronize here to avoid calling Session.update or Session.acquireCameraImage while paused.
//        synchronized (frameImageInUseLock) {
//            // Notify ARCore session that the view size changed so that the perspective matrix and
//            // the video background can be properly adjusted.
//            cpuImageDisplayRotationHelper.updateSessionIfNeeded(session);
//
//            try {
//                session.setCameraTextureName(cpuImageRenderer.getTextureId());
//                final Frame frame = session.update();
//                final Camera camera = frame.getCamera();
//
//                // Keep the screen unlocked while tracking, but allow it to lock when tracking stops.
//                trackingStateHelper.updateKeepScreenOnFlag(camera.getTrackingState());
//
//                renderFrameTimeHelper.nextFrame();
//
//                switch (imageAcquisitionPath) {
//                    case CPU_DIRECT_ACCESS:
//                        renderProcessedImageCpuDirectAccess(frame);
//                        break;
//                    case GPU_DOWNLOAD:
//                        renderProcessedImageGpuDownload(frame);
//                        break;
//                }
//
//                // Update the camera intrinsics' text.
//                runOnUiThread(() -> cameraIntrinsicsTextView.setText(getCameraIntrinsicsText(frame)));
//            } catch (Exception t) {
//                // Avoid crashing the application due to unhandled exceptions.
//                Log.e(TAG, "Exception on the OpenGL thread", t);
//            }
//        }
//    }