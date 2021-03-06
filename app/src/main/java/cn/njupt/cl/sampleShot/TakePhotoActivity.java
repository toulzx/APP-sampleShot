package cn.njupt.cl.sampleShot;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraX;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageAnalysisConfig;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureConfig;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.PreviewConfig;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Matrix;
import android.media.Image;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Rational;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

import static android.content.ContentValues.TAG;

/*
 * MainActivity
 * @date 2022/2/11
 * @author tou
 */
public class TakePhotoActivity extends AppCompatActivity {

    private static final float DEFAULT_ASPECT_RATIO = 3.395f;

    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static final int REQUEST_CAMERA = 3;

    private static final String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    private float aspectRatio;

    final String format = "%.1f";

    private TextureView viewFinder;
    private Button btnShot;
    private SeekBar seekBar;
    private TextView textViewRatio;

    private OverlayerView overlayerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        aspectRatio = 3.395f;

        setContentView(R.layout.activity_takephoto);

        permissionRequest();

        setUI();

    }

    /**
     * ??????????????????
     * @return void
     *
     */
    private void permissionRequest() {

        // ????????????????????????
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    this,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }

        // ??????????????????
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            // You can directly ask for the permission.
            requestPermissions(new String[] { Manifest.permission.CAMERA }, REQUEST_CAMERA);
        }

    }


    /**
     * ???????????????
     * @return void
     * @author tou
     *
     */
    private void setUI () {

        viewFinder = findViewById(R.id.view_finder);
        btnShot = findViewById(R.id.btn_shot);
        seekBar = findViewById(R.id.seekBar);
        textViewRatio = findViewById(R.id.tv_ratio);
        overlayerView = findViewById(R.id.overlayerView);

        viewFinder.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View view, int i, int i1, int i2, int i3, int i4, int i5, int i6, int i7) {
                updateTransform();
            }
        });

        viewFinder.post(new Runnable() {
            @Override
            public void run() {
                startCamera();
            }
        });

        viewFinder.post(new Runnable() {
            @Override
            public void run() {
                overlayerView.resetAspectRatio(DEFAULT_ASPECT_RATIO);
                overlayerView.bringToFront();
                overlayerView.initPaint();
                overlayerView.setCenterRect();
                overlayerView.postInvalidate();     // ???????????????????????????????????????????????? onDraw
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

                // ?????????????????????????????????????????????????????????,???????????????????????????progress????????????????????????????????????
                aspectRatio = progress/10f;
                textViewRatio.setText(String.format(Locale.ENGLISH, format, aspectRatio));
                overlayerView.resetAspectRatio(aspectRatio);
                overlayerView.bringToFront();
                overlayerView.setCenterRect();
                overlayerView.invalidate();     // ?????????????????????????????? onDraw()
                }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                Log.i("------------", "???????????????");
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                Log.i("------------", "???????????????");
            }
        });

    }

    /*----------------------------------------------------------------------------------------------------------------*/

    private void updateTransform() {

        Matrix matrix = new Matrix();

        // Compute the center of the view finder
        float centerX = viewFinder.getWidth() / 2f;
        float centerY = viewFinder.getHeight() / 2f;

        float[] rotations = {0,90,180,270};

        // Correct preview output to account for display rotation
        float rotationDegrees = rotations[viewFinder.getDisplay().getRotation()];

        matrix.postRotate(-rotationDegrees, centerX, centerY);

        // Finally, apply transformations to our TextureView
        viewFinder.setTransform(matrix);

    }

    /**
     * ????????????????????????????????????
     * @return void
     *
     */
    private void startCamera () {

        CameraX.unbindAll();                // ?????????????????????????????????????????????

        // 1 preview
        PreviewConfig previewConfig = new PreviewConfig.Builder()
                .setLensFacing(CameraX.LensFacing.BACK)
                .build();
        Preview preview = new Preview(previewConfig);
        realTimePreview( preview );


        // 2 capture
        ImageCaptureConfig imageCaptureConfig = new ImageCaptureConfig.Builder()
                .setCaptureMode(ImageCapture.CaptureMode.MAX_QUALITY)           // ???????????????????????????????????????????????????????????????
                .build();

        final ImageCapture imageCapture = new ImageCapture(imageCaptureConfig);


        takePhotoAction( imageCapture );


        // 3 analyze
        HandlerThread handlerThread = new HandlerThread("Analyze-thread");
        handlerThread.start();
        ImageAnalysisConfig imageAnalysisConfig = new ImageAnalysisConfig.Builder()
                .setCallbackHandler(new Handler(handlerThread.getLooper()))
                .setImageReaderMode(ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE)
                .build();
        ImageAnalysis imageAnalysis = new ImageAnalysis(imageAnalysisConfig);
        imageAnalysis.setAnalyzer(new MyAnalyzer());


        CameraX.bindToLifecycle(this, preview, imageCapture, imageAnalysis);

    }

/*--------------------------------------------------------------------------------------------------------------------*/

    private void realTimePreview(Preview preview) {

        preview.setOnPreviewOutputUpdateListener(new Preview.OnPreviewOutputUpdateListener() {
            @Override
            public void onUpdated(Preview.PreviewOutput output) {
                ViewGroup parent = (ViewGroup) viewFinder.getParent();
                parent.removeView(viewFinder);
                parent.addView(viewFinder, 0);
                viewFinder.setSurfaceTexture(output.getSurfaceTexture());
                updateTransform();
            }
        });

    }

    private void takePhotoAction(ImageCapture imageCapture) {

        // ????????????
        btnShot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveImage( imageCapture );
            }
        });

    }

    private static class MyAnalyzer implements ImageAnalysis.Analyzer {
        @Override
        public void analyze(ImageProxy imageProxy, int rotationDegrees) {
            final Image image = imageProxy.getImage();
            if(image != null) {
                // ????????????????????????
                // ???????????????????????????????????????
            }
        }
    }

/*--------------------------------------------------------------------------------------------------------------------*/

    /**
     * ?????????????????????
     * @param imageCapture:
     * @return void
     * @author tou
     *
     */
    private void saveImage(ImageCapture imageCapture) {

        Log.i(TAG, "saveImage: imageCapture.toString()="+ imageCapture.toString());

        long currentTime = System.currentTimeMillis();

        /* ????????????????????? */
        File externalFilesDir = getExternalFilesDir(Environment.DIRECTORY_DCIM);
        String picName = currentTime + ".jpeg";

        // ?????? Android 11+ ?????????????????????
        // ??????????????????????????? imageCapture.takePicture ??????
        // ????????????????????????????????????
        File photo = new File(externalFilesDir, picName);

        /* ??????????????????????????? */

        imageCapture.setTargetRotation(Surface.ROTATION_90);

        imageCapture.setTargetAspectRatio(new Rational((int) (aspectRatio*10),10)); 

        /* ?????????????????????????????????????????????????????????????????????????????????????????????????????????????????? */

        /* ???????????? */

        imageCapture.takePicture(photo, new ImageCapture.OnImageSavedListener() {
            @Override
            public void onImageSaved(@NonNull File file) {
                // ????????????????????????????????????
                String picName = currentTime + ".jpeg";
                try {
                    FileInputStream fileInputStream = new FileInputStream(file);
                    insert2Album(fileInputStream, picName);
                } catch (Exception e) {
                    Log.d("test", e.getLocalizedMessage());
                }
                // ???????????????????????????
                if (file.delete()){
                    showToast("got it!");
                } else {
                    showToast("failed to delete from app's cache storage");
                }
            }
            @Override
            public void onError(@NonNull  ImageCapture.ImageCaptureError imageCaptureError, @NonNull  String message, @Nullable Throwable cause) {
                showToast("error " + message);
                assert cause != null;
                cause.printStackTrace();
            }
        });

    }

    /*----------------------------------------------------------------------------------------------------------------*/

    /**
     * ??????????????????
     * @param msg:  ????????????
     * @return void
     * @author tou
     *
     */
    public void showToast(String msg) {

        Toast.makeText(TakePhotoActivity.this, msg, Toast.LENGTH_SHORT).show();

    }

    /*
     * ?????? Android 11 ???????????????????????????
     * @param inputStream:
     * @param fileName: ?????????????????????????????????
     * @date 2022/2/11 11:12
     * @author fishforest
     *
     */
    private void insert2Album(InputStream inputStream, String fileName) {
        if (inputStream == null)
            return;
        ContentValues contentValues = new ContentValues();
        // ?????????
        contentValues.put(MediaStore.Images.ImageColumns.DISPLAY_NAME, fileName);
        // ????????????
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {   // >= Android 10 ??????????????????????????????
            //RELATIVE_PATH ????????????????????????
            String relativePath = Environment.DIRECTORY_DCIM + File.separator + "sampleShot";
            contentValues.put(MediaStore.Images.ImageColumns.RELATIVE_PATH, relativePath);
        } else {
            String dstPath = Environment.getExternalStorageDirectory() + File.separator + Environment.DIRECTORY_DCIM
                    + File.separator + "sampleShot" + File.separator + fileName;
            //DATA ????????? Android 10.0 ??????????????????
            contentValues.put(MediaStore.Images.ImageColumns.DATA, dstPath);
        }
        //????????????????????? Uri ????????????
        Uri duplicateUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
        //????????????
        write2File(duplicateUri, inputStream);
    }

    /*----------------------------------------------------------------------------------------------------------------*/

    /*
     * ????????????
     * @param uri: ???????????????????????????
     * @param inputStream:  ????????????????????????
     * @date 2022/2/11 11:15
     * @author fishforest
     */
    private void write2File(Uri uri, InputStream inputStream) {
        if (uri == null || inputStream == null)
            return;
        try {
            //???Uri???????????????
            OutputStream outputStream = getContentResolver().openOutputStream(uri);
            byte[] in = new byte[1024];
            int len = 0;
            do {
                //???????????????????????????
                len = inputStream.read(in);
                if (len != -1) {
                    outputStream.write(in, 0, len);
                    outputStream.flush();
                }
            } while (len != -1);
            inputStream.close();
            outputStream.close();
        } catch (Exception e) {
            Log.d(TAG, e.getLocalizedMessage());
        }
    }

}