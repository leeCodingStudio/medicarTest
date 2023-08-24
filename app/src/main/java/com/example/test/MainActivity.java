package com.example.test;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaScannerConnection;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.Manifest;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements TextureView.SurfaceTextureListener {
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private CameraCaptureSession mCaptureSession = null; // 클래스 멤버 변수 초기화
    private Handler mBackgroundHandler = new Handler();
    private TextureView mTextureView;
    private CameraDevice mCameraDevice;
    private CaptureRequest.Builder mPreviewBuilder;
    private CameraCaptureSession mPreviewSession;

    @Override

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mTextureView = findViewById(R.id.textureView);
        System.out.println("mTextureView: " + mTextureView);
        mTextureView.setSurfaceTextureListener(this);
        Button captureButton = findViewById(R.id.captureButton);
        captureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                captureImage();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        System.out.println("onResume");
        openCamera();
    }

    @Override
    protected void onPause() {
        closeCamera();
        super.onPause();
    }

    private void openCamera() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        try {
            String cameraId = manager.getCameraIdList()[0]; // Use the first available camera
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
                return;
            }
            manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    mCameraDevice = camera;
                    createCameraPreview();
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    mCameraDevice.close();
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    mCameraDevice.close();
                    mCameraDevice = null;
                }
            }, null);
        } catch (Exception e) {
            Log.e("Camera2", "Failed to open camera: " + e.getMessage());
        }
    }

    private void createCameraPreview() {
        System.out.println("createCameraPreview");
        Surface surface = new Surface(mTextureView.getSurfaceTexture());
        System.out.println("surface: " + mTextureView.getSurfaceTexture());
        try {
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewBuilder.addTarget(surface);
            mCameraDevice.createCaptureSession(Collections.singletonList(surface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            mPreviewSession = session;
                            try {
                                mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), null, null);
                            } catch (Exception e) {
                                Log.e("Camera2", "Failed to set up preview: " + e.getMessage());
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Log.e("Camera2", "Failed to configure camera preview.");
                        }
                    }, null);
        } catch (Exception e) {
            Log.e("Camera2", "Failed to create camera preview: " + e.getMessage());
        }
    }

    private void closeCamera() {
        if (mPreviewSession != null) {
            mPreviewSession.close();
            mPreviewSession = null;
        }
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
    }
    private ImageReader mImageReader;

    private void captureImage() {
        if (mCameraDevice == null) {
            return;
        }
        try {
            CaptureRequest.Builder captureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());

            // 캡처 요청 생성 및 세션에 보내기
            CaptureRequest captureRequest = captureBuilder.build();
            mCaptureSession.capture(captureRequest, mCaptureCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    private final CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            // 캡처가 완료된 경우, ImageReader에서 이미지 가져오기
            Image image = mImageReader.acquireLatestImage();
            if (image != null) {
                // 이미지를 디바이스에 저장하는 작업을 수행
                saveImageToDevice(image);
                // 사용 후 이미지 리소스 해제
                image.close();
            }
        }
    };
    private void saveImageToDevice(Image image) {
        // 이미지를 저장할 파일 생성
        File imageFile = createImageFile();
        if (imageFile != null) {
            try (OutputStream outputStream = new FileOutputStream(imageFile)) {
                // Image 객체에서 이미지 데이터를 가져와 파일에 저장
                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                outputStream.write(bytes);

                // 이미지 스캔하여 갤러리에 추가
                MediaScannerConnection.scanFile(this,
                        new String[]{imageFile.getAbsolutePath()},
                        null,
                        (path, uri) -> {
                            // 이미지가 성공적으로 갤러리에 추가된 경우 처리
                            // 예를 들어, 갤러리에서 이미지를 열거나 공유하는 등의 작업을 수행
                        });

                // 파일 출력 스트림 닫기
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private File createImageFile() {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File imageFile = null;
        try {
            imageFile = File.createTempFile(imageFileName, ".jpg", storageDir);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return imageFile;
    }

    @Override
    public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
        openCamera();
    }

    @Override
    public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {

    }

    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {
        super.onPointerCaptureChanged(hasCapture);
    }
}