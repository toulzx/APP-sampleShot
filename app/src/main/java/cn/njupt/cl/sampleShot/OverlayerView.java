package cn.njupt.cl.sampleShot;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;

// 由于 TextureView 的子类不能使用 Canvas 对象进行自己的渲染 https://developer.android.com/reference/android/view/TextureView?hl=en#draw(android.graphics.Canvas)
// 而 MainActivity 中通过 textureView 展示实时画面的
// 现决定多加 1 层 imageView，在其上 Canvas 渲染

public class OverlayerView extends androidx.appcompat.widget.AppCompatImageView {
    private static final String TAG = cn.njupt.cl.sampleShot.OverlayerView.class.getSimpleName();
    private static final int CORNER_LINE_LENGTH = 50;
    private static final float DEFAULT_ASPECT_RATIO = 3.395f;
    private float aspectRatio = DEFAULT_ASPECT_RATIO;

//    private TextureView textureView;

    private Paint mLinePaint;
    private Paint mAreaPaint;
    private Paint mTargetPaint;
    private Paint paint;
    private Rect mCenterRect = null;
    private final Context mContext;

    private DisplayMetrics dm;
    private int screenWidth;

    public OverlayerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.mContext = context;

        this.setDrawingCacheEnabled(true);

        this.dm = mContext.getResources().getDisplayMetrics();
        this.screenWidth = dm.widthPixels;

//        aspectRatio = DEFAULT_ASPECT_RATIO;

        this.initPaint();
        this.setCenterRect();
    }

    public void resetAspectRatio(float ratio) {

        this.aspectRatio = ratio;

    }

    public void initPaint() {

        // 绘制中间透明区域的边
        mLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mLinePaint.setColor(Color.BLUE);
        mLinePaint.setStyle(Style.STROKE);
        mLinePaint.setStrokeWidth(5f);
        mLinePaint.setAlpha(100);

        // 绘制四周阴影区域
        mAreaPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mAreaPaint.setColor(Color.GRAY);
        mAreaPaint.setStyle(Style.FILL);
        mAreaPaint.setAlpha(100);//遮罩层的阴影程度

        // 绘制框
        mTargetPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTargetPaint.setColor(Color.GREEN);
        mTargetPaint.setStyle(Style.STROKE);
        mTargetPaint.setStrokeWidth(10f);
        mTargetPaint.setAlpha(100);//遮罩层的阴影程度

        // 绘制四角
        paint = new Paint();
        paint.setColor(Color.WHITE);
        paint.setAlpha(150);

    }


    public void setCenterRect() {

        int xl = Math.round( ( this.getWidth() - this.getHeight() / aspectRatio ) / 2 );

        this.mCenterRect = new Rect(
                xl,
                this.getTop(),
                this.getWidth() - xl,
                this.getBottom()
        );

    }

    public Rect getCenterRect() {
        return mCenterRect;
    }

    public void clearCenterRect(Rect r) {
        this.mCenterRect = null;
    }






    @Override
    public void onDraw(Canvas canvas) {

        Log.i(TAG, "onDraw...");
        if (mCenterRect == null) {
            Log.w(TAG, "onDraw: mCenterRect == null");
            return;
        }

        // 绘制四周阴影区域
        canvas.drawRect(
                this.getLeft(),
                this.getTop(),
                mCenterRect.left,
                this.getBottom(),
                mAreaPaint);     // 左侧阴影
        canvas.drawRect(
                mCenterRect.right,
                this.getTop() ,
                this.getRight(),
                this.getBottom(),
                mAreaPaint);     // 右侧阴影

        canvas.drawRect(
                mCenterRect.left - 1,
                this.getTop() ,
                mCenterRect.right + 1,
                this.getBottom(),
                mTargetPaint);     // 识别区域边框



        //绘制标记(顺时针)
        canvas.drawLine(
                mCenterRect.left,
                mCenterRect.top,
                mCenterRect.left,
                mCenterRect.top + CORNER_LINE_LENGTH,
                mLinePaint
        );
        canvas.drawLine(
                mCenterRect.left,
                mCenterRect.top,
                mCenterRect.left + CORNER_LINE_LENGTH,
                mCenterRect.top,
                mLinePaint
        );
        canvas.drawLine(
                mCenterRect.right,
                mCenterRect.top,
                mCenterRect.right - CORNER_LINE_LENGTH,
                mCenterRect.top,
                mLinePaint
        );
        canvas.drawLine(
                mCenterRect.right,
                mCenterRect.top,
                mCenterRect.right,
                mCenterRect.top + CORNER_LINE_LENGTH,
                mLinePaint
        );
        canvas.drawLine(
                mCenterRect.right,
                mCenterRect.bottom,
                mCenterRect.right,
                mCenterRect.bottom - CORNER_LINE_LENGTH,
                mLinePaint
        );
        canvas.drawLine(
                mCenterRect.right,
                mCenterRect.bottom,
                mCenterRect.right - CORNER_LINE_LENGTH,
                mCenterRect.bottom,
                mLinePaint
        );
        canvas.drawLine(
                mCenterRect.left,
                mCenterRect.bottom,
                mCenterRect.left + CORNER_LINE_LENGTH,
                mCenterRect.bottom,
                mLinePaint
        );
        canvas.drawLine(
                mCenterRect.left,
                mCenterRect.bottom,
                mCenterRect.left,
                mCenterRect.bottom - CORNER_LINE_LENGTH,
                mLinePaint
        );

        super.onDraw(canvas);
    }


}

