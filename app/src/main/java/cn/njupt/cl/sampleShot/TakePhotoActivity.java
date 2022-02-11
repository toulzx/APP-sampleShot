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
     * 动态申请权限
     * @return void
     *
     */
    private void permissionRequest() {

        // 内存读取权限申请
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    this,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }

        // 相机权限申请
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            // You can directly ask for the permission.
            requestPermissions(new String[] { Manifest.permission.CAMERA }, REQUEST_CAMERA);
        }

    }


    /**
     * 初始化界面
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
                overlayerView.invalidate();     // 刷新一下，使重新调用 onDraw()
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

    /**
     * 启动相机实现界面实时预览
     * @return void
     *
     */
    private void startCamera () {

        CameraX.unbindAll();                // 从生命周期中解除所有用例的绑定

        // 1 preview
        PreviewConfig previewConfig = new PreviewConfig.Builder()
                .setLensFacing(CameraX.LensFacing.BACK)
                .build();
        Preview preview = new Preview(previewConfig);
        realTimePreview( preview );


        // 2 capture
        ImageCaptureConfig imageCaptureConfig = new ImageCaptureConfig.Builder()
                .setCaptureMode(ImageCapture.CaptureMode.MAX_QUALITY)           // 优化捕获管道以优先考虑图像质量而不是延迟。
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
                // 这里写自己的处理
                // 考虑留此处接口用以环境检测
            }
        }
    }

/*--------------------------------------------------------------------------------------------------------------------*/

    /**
     * 裁剪并保存照片
     * @param imageCapture:
     * @return void
     * @author tou
     *
     */
    private void saveImage(ImageCapture imageCapture) {

        Log.i(TAG, "saveImage: imageCapture.toString()="+ imageCapture.toString());

        long currentTime = System.currentTimeMillis();

        /* 存放在内部存储 */
        File externalFilesDir = getExternalFilesDir(Environment.DIRECTORY_DCIM);
        String picName = currentTime + ".jpeg";

        // 由于 Android 11+ 强制分区存储，
        // 而目前照片保存通过 imageCapture.takePicture 实现
        // 则暂时存放在应用内部空间
        File photo = new File(externalFilesDir, picName);

        /* 对照片进行裁剪处理 */

        imageCapture.setTargetRotation(Surface.ROTATION_90);

        imageCapture.setTargetAspectRatio(new Rational((int) (aspectRatio*10),10)); 

        /* 注意这里似乎不能二次旋转，而裁切要求占满宽度，因此在取照片后处理之前记得旋转 */

        /* 保存照片 */

        imageCapture.takePicture(photo, new ImageCapture.OnImageSavedListener() {
            @Override
            public void onImageSaved(@NonNull File file) {
                // 外部存储，从内部复制一份
                String picName = currentTime + ".jpeg";
                try {
                    FileInputStream fileInputStream = new FileInputStream(file);
                    insert2Album(fileInputStream, picName);
                } catch (Exception e) {
                    Log.d("test", e.getLocalizedMessage());
                }
                // 删除内部存储的照片
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
     * 底部悬浮提示
     * @param msg:  提示内容
     * @return void
     * @author tou
     *
     */
    public void showToast(String msg) {

        Toast.makeText(TakePhotoActivity.this, msg, Toast.LENGTH_SHORT).show();

    }

    /*
     * 适合 Android 11 共享空间的照片存取
     * @param inputStream:
     * @param fileName: 需要保存到相册的图片名
     * @date 2022/2/11 11:12
     * @author fishforest
     *
     */
    private void insert2Album(InputStream inputStream, String fileName) {
        if (inputStream == null)
            return;
        ContentValues contentValues = new ContentValues();
        // 图片名
        contentValues.put(MediaStore.Images.ImageColumns.DISPLAY_NAME, fileName);
        // 图片路径
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {   // >= Android 10 时，遵循分区存储策略
            //RELATIVE_PATH 字段表示相对路径
            String relativePath = Environment.DIRECTORY_DCIM + File.separator + "sampleShot";
            contentValues.put(MediaStore.Images.ImageColumns.RELATIVE_PATH, relativePath);
        } else {
            String dstPath = Environment.getExternalStorageDirectory() + File.separator + Environment.DIRECTORY_DCIM
                    + File.separator + "sampleShot" + File.separator + fileName;
            //DATA 字段在 Android 10.0 之后已经废弃
            contentValues.put(MediaStore.Images.ImageColumns.DATA, dstPath);
        }
        //插入相册（通过 Uri 的方式）
        Uri duplicateUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
        //写入文件
        write2File(duplicateUri, inputStream);
    }

    /*----------------------------------------------------------------------------------------------------------------*/

    /*
     * 文件写入
     * @param uri: 关联着待写入的文件
     * @param inputStream:  表示原始的文件流
     * @date 2022/2/11 11:15
     * @author fishforest
     */
    private void write2File(Uri uri, InputStream inputStream) {
        if (uri == null || inputStream == null)
            return;
        try {
            //从Uri构造输出流
            OutputStream outputStream = getContentResolver().openOutputStream(uri);
            byte[] in = new byte[1024];
            int len = 0;
            do {
                //从输入流里读取数据
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