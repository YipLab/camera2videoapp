package yipgroup.camera2video;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraDevice.StateCallback;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest.Builder;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.TextureView.SurfaceTextureListener;
import android.view.View;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static SparseIntArray ORIENTATIONS = new SparseIntArray();
    private static final int REQUEST_CAMERA_PERMISSION_RESULT = 0;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundHandlerThread;
    private CameraDevice mCameraDevice;
    private StateCallback mCameraDeviceStateCallback = new C01962();
    private String mCameraId;
    private Builder mCaptureRequestBuilder;
    private Size mPreviewSize;
    private SurfaceTextureListener mSurfaceTextureListener = new C01951();
    private TextureView mTextureView;

    class C01951 implements SurfaceTextureListener {
        C01951() {
        }

        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            MainActivity.this.setupCamera(width, height);
            MainActivity.this.connectCamera();
        }

        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height1) {
        }

        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    }

    class C01962 extends StateCallback {
        C01962() {
        }

        public void onOpened(@NonNull CameraDevice camera) {
            MainActivity.this.mCameraDevice = camera;
            MainActivity.this.startPreview();
        }

        public void onDisconnected(@NonNull CameraDevice camera) {
            camera.close();
            MainActivity.this.mCameraDevice = null;
        }

        public void onError(@NonNull CameraDevice camera, int error) {
            camera.close();
            MainActivity.this.mCameraDevice = null;
        }
    }

    class C01973 extends CameraCaptureSession.StateCallback {
        C01973() {
        }

        public void onConfigured(@NonNull CameraCaptureSession session) {
            try {
                session.setRepeatingRequest(MainActivity.this.mCaptureRequestBuilder.build(), null, MainActivity.this.mBackgroundHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
            Toast.makeText(MainActivity.this.getApplicationContext(), "Unable to setup camera preview", Toast.LENGTH_SHORT).show();
        }
    }

    private static class CompareSizeByArea implements Comparator<Size> {
        private CompareSizeByArea() {
        }

        public int compare(Size lhs, Size rhs) {
            return Long.signum((((long) lhs.getWidth()) * ((long) lhs.getHeight())) - (((long) rhs.getWidth()) * ((long) rhs.getHeight())));
        }
    }

    static {
        ORIENTATIONS.append(0, 0);
        ORIENTATIONS.append(1, 90);
        ORIENTATIONS.append(2, 180);
        ORIENTATIONS.append(3, 270);
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView((int) R.layout.activity_main);
        this.mTextureView = (TextureView) findViewById(R.id.textureView);
    }

    protected void onResume() {
        super.onResume();
        startBackgroundThread();
        if (this.mTextureView.isAvailable()) {
            setupCamera(this.mTextureView.getWidth(), this.mTextureView.getHeight());
            connectCamera();
            return;
        }
        this.mTextureView.setSurfaceTextureListener(this.mSurfaceTextureListener);
    }

    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 0 && grantResults[0] == 0) {
            Toast.makeText(getApplicationContext(), "Application will not run without camera services", Toast.LENGTH_SHORT).show();
        }
    }

    protected void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    public void onWindowFocusChange(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        View decorView = getWindow().getDecorView();
        if (hasFocus) {
            decorView.setSystemUiVisibility(5894);
        }
    }

    private void setupCamera(int width, int height) {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            boolean swapRotation = false;
            int sensorToDeviceRotation;
            for (String cameraId : cameraManager.getCameraIdList()) {
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
                if (contains((int[]) cameraCharacteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES), 3)) {
                    int rotatedWidth;
                    int rotatedHeight;
                    StreamConfigurationMap map = (StreamConfigurationMap) cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    sensorToDeviceRotation = sensorToDeviceRotation(cameraCharacteristics, getWindowManager().getDefaultDisplay().getRotation());
                    if (sensorToDeviceRotation != 90) {
                        if (sensorToDeviceRotation != 270) {
                            rotatedWidth = width;
                            rotatedHeight = height;
                            if (swapRotation) {
                                rotatedWidth = height;
                                rotatedHeight = width;
                            }
                            this.mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), rotatedWidth, rotatedHeight);
                            this.mCameraId = cameraId;
                            return;
                        }
                    }
                    swapRotation = true;
                    rotatedWidth = width;
                    rotatedHeight = height;
                    if (swapRotation) {
                        rotatedWidth = height;
                        rotatedHeight = width;
                    }
                    this.mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), rotatedWidth, rotatedHeight);
                    this.mCameraId = cameraId;
                    return;
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void connectCamera() {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if(ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED) {
                    cameraManager.openCamera(mCameraId, mCameraDeviceStateCallback, mBackgroundHandler);
                } else {
                    if(shouldShowRequestPermissionRationale(android.Manifest.permission.CAMERA)) {
                        Toast.makeText(this,
                                "Video app required access to camera", Toast.LENGTH_SHORT).show();
                    }
                    requestPermissions(new String[] {android.Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO
                    }, REQUEST_CAMERA_PERMISSION_RESULT);
                }

            } else {
                cameraManager.openCamera(mCameraId, mCameraDeviceStateCallback, mBackgroundHandler);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void startPreview() {
        SurfaceTexture surfaceTexture = this.mTextureView.getSurfaceTexture();
        surfaceTexture.setDefaultBufferSize(this.mPreviewSize.getWidth(), this.mPreviewSize.getHeight());
        Surface previewSurface = new Surface(surfaceTexture);
        try {
            this.mCaptureRequestBuilder = this.mCameraDevice.createCaptureRequest(1);
            this.mCaptureRequestBuilder.addTarget(previewSurface);
            this.mCameraDevice.createCaptureSession(Arrays.asList(new Surface[]{previewSurface}), new C01973(), null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        if (this.mCameraDevice != null) {
            this.mCameraDevice.close();
            this.mCameraDevice = null;
        }
    }

    private void startBackgroundThread() {
        this.mBackgroundHandlerThread = new HandlerThread("Camera2VideoImage");
        this.mBackgroundHandlerThread.start();
        this.mBackgroundHandler = new Handler(this.mBackgroundHandlerThread.getLooper());
    }

    private void stopBackgroundThread() {
        this.mBackgroundHandlerThread.quitSafely();
        try {
            this.mBackgroundHandlerThread.join();
            this.mBackgroundHandlerThread = null;
            this.mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static int sensorToDeviceRotation(CameraCharacteristics cameraCharacteristics, int deviceOrientation) {
        return ((((Integer) cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)).intValue() + ORIENTATIONS.get(deviceOrientation)) + 360) % 360;
    }

    private static Size chooseOptimalSize(Size[] choices, int width, int height) {
        List<Size> bigEnough = new ArrayList();
        for (Size option : choices) {
            if (option.getHeight() == (option.getWidth() * height) / width && option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }
        if (bigEnough.size() > 0) {
            return (Size) Collections.min(bigEnough, new CompareSizeByArea());
        }
        return choices[0];
    }

    private static boolean contains(int[] modes, int mode) {
        if (modes == null) {
            return false;
        }
        for (int i : modes) {
            if (i == mode) {
                return true;
            }
        }
        return false;
    }
}
