/*
 * Copyright 2018 Google LLC. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.myapplication;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.ar.core.Anchor;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Pose;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.HitTestResult;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.collision.Ray;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.rendering.ShapeFactory;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.google.ar.sceneform.ux.ArFragment;

/**
 * This is an example activity that uses the Sceneform UX package to make common AR tasks easier.
 */
public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final double MIN_OPENGL_VERSION = 3.0;

    private ArFragment arFragment;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    @SuppressWarnings({"AndroidApiChecker", "FutureReturnValueIgnored"})
    // CompletableFuture requires api level 24
    // FutureReturnValueIgnored is not valid
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!checkIsSupportedDeviceOrFinish(this)) {
            return;
        }

        setContentView(R.layout.activity_main);
        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.ux_fragment);


        arFragment.getArSceneView().getScene().setOnTouchListener(
            (hitResult, motionEvent) -> {
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN &&
                        arFragment.getArSceneView().getArFrame().getCamera().getTrackingState() == TrackingState.TRACKING) {
                    Vector3 myCastPoint = arFragment.getArSceneView().getScene().getCamera().screenPointToRay(
                            motionEvent.getX(), motionEvent.getY()).getPoint(2);
                    makeSphere(myCastPoint);
                }
                return true;
            }
        );
    }

    private void makeSphere(Vector3 worldPosition) {
        // Note: you can make one material and one sphere and reuse them.
        MaterialFactory.makeOpaqueWithColor(this, new Color(0, 0.8f, 0))
                .thenAccept(material -> {
                            Node sphereNode = new Node();
                            sphereNode.setParent(arFragment.getArSceneView().getScene());
//                            sphereNode.setWorldPosition(new Vector3(0.00f, 0.1f, -0.1f));
                            sphereNode.setWorldPosition(worldPosition);


                            // Create sphere shape
                            Renderable sphere = ShapeFactory.makeSphere(0.05f, Vector3.zero(),
                                    material);
                            sphereNode.setRenderable(sphere);

                            Vector3 position = sphereNode.getWorldPosition();
                            System.out.println("X: " + (position.x) + "\n" +
                                    "Y: " + (position.y) + "\n" +
                                    "Z: " + (position.z));
                            // Delete if sphere is touched
                            sphereNode.setOnTouchListener((hitTestResult, motionEvent) -> {
                                arFragment.getArSceneView().getScene().removeChild(sphereNode);
                                sphereNode.setEnabled(false);
                                return true;
                            });

                            // Make a new view so we can use it for the sphere label
                            ViewRenderable.builder()
                                    .setView(this, R.layout.sphere_label)
                                    .build()
                                    .thenAccept(
                                            (renderable) -> {
                                                makeSphereLabel(renderable, sphereNode);
                                            }
                                    )
                                    .exceptionally(
                                            (throwable) -> {
                                                throw new AssertionError("Could not load card view.", throwable);
                                            }
                                    );
                        }
                );

    }

    private void makeSphereLabel(ViewRenderable sphereViewRenderable, Node sphereNode) {
        Node sphereLabelNode = new Node();
        sphereLabelNode.setParent(sphereNode);
        sphereLabelNode.setEnabled(false);
        sphereLabelNode.setLocalPosition(new Vector3(0.0f, 0.1f, 0.0f));
        sphereLabelNode.setRenderable(sphereViewRenderable);
        sphereLabelNode.setEnabled(true);

        TextView distanceTextView = sphereViewRenderable.getView().findViewById(R.id.sphereLabel);
        arFragment.getArSceneView().getScene().addOnUpdateListener(
                frameTime -> {
                    Vector3 position = sphereNode.getWorldPosition();
                    distanceTextView.setText(
                            "X: " + (position.x) + "\n" +
                                    "Y: " + (position.y) + "\n" +
                                    "Z: " + (position.z)
                    );

                }
        );
    }


    /**
     * Returns false and displays an error message if Sceneform can not run, true if Sceneform can run
     * on this device.
     *
     * <p>Sceneform requires Android N on the device as well as OpenGL 3.0 capabilities.
     *
     * <p>Finishes the activity if Sceneform can not run
     */
    public static boolean checkIsSupportedDeviceOrFinish(final Activity activity) {
        if (Build.VERSION.SDK_INT < VERSION_CODES.N) {
            Log.e(TAG, "Sceneform requires Android N or later");
            Toast.makeText(activity, "Sceneform requires Android N or later", Toast.LENGTH_LONG).show();
            activity.finish();
            return false;
        }
        String openGlVersionString =
                ((ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE))
                        .getDeviceConfigurationInfo()
                        .getGlEsVersion();
        if (Double.parseDouble(openGlVersionString) < MIN_OPENGL_VERSION) {
            Log.e(TAG, "Sceneform requires OpenGL ES 3.0 later");
            Toast.makeText(activity, "Sceneform requires OpenGL ES 3.0 or later", Toast.LENGTH_LONG)
                    .show();
            activity.finish();
            return false;
        }
        return true;
    }
}
