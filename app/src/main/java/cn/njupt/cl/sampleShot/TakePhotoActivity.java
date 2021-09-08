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

import android.annotation.SuppressLint;
import android.graphics.Matrix;
import android.media.Image;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
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
import java.util.Locale;

import static android.content.ContentValues.TAG;

public class TakePhotoActivity extends AppCompatActivity {

    private static final float DEFAULT_ASPECT_RATIO = 3.395f;
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

        setUI();

    }


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
                overlayerView.postInvalidate();     // 刷新一下，使获得了宽高后再次调用 onDraw
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

                // 当拖动条的滑块位置发生改变时触发该方法,在这里直接使用参数progress，即当前滑块代表的进度值
                aspectRatio = progress/10f;
                textViewRatio.setText(String.format(Locale.ENGLISH, format, aspectRatio));
                overlayerView.resetAspectRatio(aspectRatio);
                overlayerView.bringToFront();
                overlayerView.setCenterRect();
                overlayerView.invalidate();     // 刷新一下，使获得了宽高后再次调用 onDraw
                }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                Log.i("------------", "开始滑动！");
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                Log.i("------------", "停止滑动！");
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

    private void startCamera () {

        CameraX.unbindAll();// 从生命周期中解除所有用例的绑定

        // 1 preview
        PreviewConfig previewConfig = new PreviewConfig.Builder()
                .setLensFacing(CameraX.LensFacing.BACK)
                .build();
        Preview preview = new Preview(previewConfig);// realTime 预览框实时更新
        realTimePreview( preview );


        // 2. capture
        ImageCaptureConfig imageCaptureConfig = new ImageCaptureConfig.Builder()
                .setCaptureMode(ImageCapture.CaptureMode.MIN_LATENCY) // 优化捕获管道以优先考虑延迟而不是图像质量。当捕获模式设置为 MIN_LATENCY 时，图像捕获速度可能会更快，但图像质量可能会降低
                .build();

        final ImageCapture imageCapture = new ImageCapture(imageCaptureConfig);


        takePhotoAction( imageCapture );


        // 3. analyze
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

        // 按键拍照
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
                Log.d("尺寸", image.getWidth() + "," + image.getHeight());        // 实时欸
                // 这里写自己的处理
                // 考虑留此处接口用以环境检测
            }
        }
    }

/*--------------------------------------------------------------------------------------------------------------------*/

    private void saveImage(ImageCapture imageCapture) {
        Log.i(TAG, "saveImage: imageCapture.toString()="+ imageCapture.toString());
        File externalFilesDir = getExternalFilesDir(Environment.DIRECTORY_DCIM);        // 一般放一些长时间保存的数据

        long currentTime = System.currentTimeMillis();
        String storePath = externalFilesDir + File.separator + currentTime + ".jpeg";

        File photo = new File(storePath);

        /* 对照片进行裁剪处理 */

        imageCapture.setTargetRotation(Surface.ROTATION_90);

        imageCapture.setTargetAspectRatio(new Rational((int) (aspectRatio*10),10)); 

        /* 注意这里似乎不能二次旋转，而裁切似乎又必须占满宽，因此在取照片识别之前记得旋转 */


        /* 保存照片 */
        imageCapture.takePicture(photo, new ImageCapture.OnImageSavedListener() {
            @Override
            public void onImageSaved(@NonNull File file) {
                showToast("saved pictures successfully");
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


    public void showToast(String msg) {

        Toast.makeText(TakePhotoActivity.this, msg, Toast.LENGTH_SHORT).show();

    }


}