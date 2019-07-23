package com.github.alekdevninja.simpletorch;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/*
Hi there! :)
This is my first Android app published on play store.
I've made it mostly to learn the publishing pipeline.
App was made mostly based on:
https://github.com/pinguo-yuyidong/Camera2/
https://github.com/googlesamples/

Flashlight icon credits goes to Kiran Shastry -> https://www.flaticon.com/free-icon/torch_1905179

 */


public class Flashlight extends Activity {
    private CameraDevice cameraDevice;
    private CameraManager cameraManager;
    private CaptureRequest.Builder captureRequestBuilder;
    private CameraCaptureSession cameraCaptureSession;
    private int MY_PERMISSIONS_REQUEST_CAMERA;

    @Override
    protected void onCreate(Bundle savedInstanceState) {


        // Checking if the hardware camera permission is already given
        if (ContextCompat.checkSelfPermission(Flashlight.this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(Flashlight.this,
                    Manifest.permission.CAMERA)) {
                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
            } else {
                // No explanation needed; request the permission
                ActivityCompat.requestPermissions(Flashlight.this,
                        new String[]{Manifest.permission.CAMERA},
                        MY_PERMISSIONS_REQUEST_CAMERA);

            }
        } else {
            // Permission has already been granted - just carry on
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            initializeCameraManager();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        // main ON/OFF light button listener
        Switch mainSwitch = findViewById(R.id.button_main_switch);
        mainSwitch.setChecked(true); //flashlight will be ON on start by default
        mainSwitch.setOnCheckedChangeListener(new OnOffButtonListener());
    }

    @SuppressWarnings("ResourceType")
    private void initializeCameraManager() throws CameraAccessException {
        cameraManager = (CameraManager) Flashlight.this.getSystemService(Context.CAMERA_SERVICE);

        //is the flashlight in this device available?
        CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics("0");

        boolean isCameraFlashAvailable = cameraCharacteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
        if (isCameraFlashAvailable) {
            cameraManager.openCamera("0", new CameraCallback(), null);
        } else {
            Toast.makeText(Flashlight.this, "Flashlight is not available on this device", Toast.LENGTH_SHORT).show();
        }

        // creating a CameraCallback anyway just not to crush the app after not finding a flash device
        cameraManager.openCamera("0", new CameraCallback(), null);
    }

    private Size setSize(String cameraId) throws CameraAccessException {
        Size[] outputSizes = Objects.requireNonNull(cameraManager.getCameraCharacteristics(cameraId)
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP))
                .getOutputSizes(SurfaceTexture.class);

        if (outputSizes == null || outputSizes.length == 0) {
            throw new IllegalStateException(
                    "camera doesn't support any output size");
        }

        Size size = outputSizes[0];
        for (Size s : outputSizes) {
            if (size.getWidth() >= s.getWidth() && size.getHeight() >= s.getHeight()) {
                size = s;
            }
        }
        return size;
    }

    class CameraCaptureSessionStateCallback extends CameraCaptureSession.StateCallback {

        @Override
        public void onConfigured(CameraCaptureSession session) {
            cameraCaptureSession = session;
            try {
                cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, null);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(CameraCaptureSession session) {
            Log.i("CameraStateCallback", "onConfigureFailed()");
        }

    }

    class OnOffButtonListener implements CompoundButton.OnCheckedChangeListener {

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            try {
                if (isChecked) {
                    captureRequestBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);
                    cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, null);
                } else {
                    captureRequestBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
                    cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, null);
                }
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

    }

    private void close() {
        if (cameraDevice == null || cameraCaptureSession == null) {
            return;
        }
        cameraCaptureSession.close();
        cameraDevice.close();
        cameraDevice = null;
        cameraCaptureSession = null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        close();
    }

    class CameraCallback extends CameraDevice.StateCallback {
        private SurfaceTexture surfaceTexture;
        private Surface surface;

        @Override
        public void onOpened(CameraDevice camera) {
            cameraDevice = camera;

            //get captureRequestBuilder
            try {
                captureRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_MANUAL);

                //flashlight default is on
                captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AF_MODE_AUTO);
                captureRequestBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);

                List<Surface> surfaceList = new ArrayList<>();
                surfaceTexture = new SurfaceTexture(1);
                Size size = setSize(cameraDevice.getId());
                surfaceTexture.setDefaultBufferSize(size.getWidth(), size.getHeight());
                surface = new Surface(surfaceTexture);
                surfaceList.add(surface);
                captureRequestBuilder.addTarget(surface);
                camera.createCaptureSession(surfaceList, new CameraCaptureSessionStateCallback(), null);

            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            Log.i("CameraCallback", "CameraDevice has disconnected");

        }

        @Override
        public void onError(CameraDevice camera, int error) {
            Log.i("CameraCallback", "CameraDevice had error: " + error);
        }
    }

}

















