package com.mangel.signatureexample;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Join;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;

public class SignaturePadView extends View {

	public static final String TAG = "signature";

	private static final String WIDTH = "width";
	private static final String HEIGHT = "height";
	private static final String POINTS = "points";
	private static final String SUPER_STATE = "super_state";


	private List<VelocityPoint> mPath;
	private ArrayList<List<VelocityPoint>> mAllPaths;
	private Paint mPaint;
	private Paint mWhitePaint;

	private Path mDrawablePath;

	private Context mContext;

	private DisplayMetrics mMetrics;

	private Bitmap mStoredBitmap;

	private Canvas mBitmapCanvas;
	private Float mLastWidth = 1f;

	private VelocityTracker mVelocityTracker;

	private int mPreviousWidth;
	private int mPreviousHeight;
	private boolean mScaleAllPathsAfterMeasure;

	//Styleable
	private int mBackgroundColor;
	private int mSignatureColor;
	private boolean mShowUnderline;
	private int mUnderlineColor;
	private int mTextColor;
	private float mTextSize;
	private String mUnderlineLabel;
	private boolean mRetainSignatureOnRotate;

	private float mUnderlineThickness;

	private Paint mTextPaint;

	private Paint mUnderlinePaint;

	public SignaturePadView(Context context) {
		super(context);
		initialize(context);
	}

	private void initialize(Context context) {
		setWillNotDraw(false);
		mContext = context;
		mMetrics = mContext.getResources().getDisplayMetrics();

		clear();
		mPaint = new Paint();
		mPaint.setAntiAlias(true);
		mPaint.setStrokeJoin(Join.ROUND);

		mPaint.setColor(mSignatureColor);
		mPaint.setStyle(Style.FILL_AND_STROKE);

		mWhitePaint = new Paint();
		mWhitePaint.setColor(mBackgroundColor);

		mTextPaint = new Paint();
		mTextPaint.setColor(mTextColor);
		mTextPaint.setTextSize(mTextSize);
		mTextPaint.setAntiAlias(true);
		
		mUnderlinePaint = new Paint();
		mUnderlinePaint.setColor(mUnderlineColor);
		mUnderlinePaint.setStrokeWidth(mUnderlineThickness);
		mUnderlinePaint.setStyle(Style.STROKE);
		mUnderlinePaint.setAntiAlias(true);

		mAllPaths = new ArrayList<List<VelocityPoint>>();
	}

	public SignaturePadView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public SignaturePadView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);

		mContext = context;
		mMetrics = mContext.getResources().getDisplayMetrics();
		
		TypedArray a = context.getTheme().obtainStyledAttributes(
				attrs,
				R.styleable.SignaturePadView,
				0, 0);

		try {
			mBackgroundColor = a.getColor(R.styleable.SignaturePadView_backgroundColor, Color.WHITE);
			mSignatureColor = a.getColor(R.styleable.SignaturePadView_signatureColor, 0xff2d2d2d);
			mShowUnderline = a.getBoolean(R.styleable.SignaturePadView_showUnderline, false);
			mUnderlineColor = a.getColor(R.styleable.SignaturePadView_underlineColor, 0xff4d4d4d);
			mUnderlineThickness = a.getDimensionPixelSize(R.styleable.SignaturePadView_underlineThickness, 2);
			mTextColor = a.getColor(R.styleable.SignaturePadView_signatureColor, 0xff4d4d4d);
			mTextSize = a.getDimensionPixelSize(R.styleable.SignaturePadView_textSize, (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 15, mMetrics));
			mUnderlineLabel = a.getString(R.styleable.SignaturePadView_underlineLabel);
			mRetainSignatureOnRotate = a.getBoolean(R.styleable.SignaturePadView_retainSignatureOnRotate, true);
		} finally {
			a.recycle();
		}

		initialize(context);
	}

	public void clear() {
		mPath = null;
		mStoredBitmap = null;
		mAllPaths = new ArrayList<List<VelocityPoint>>();

		addPathToBitmap(null);
		invalidate();
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		initVelocityTrackerIfNotExists();
		mVelocityTracker.addMovement(event);
		switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN:
			beginNewPath();
			return true;
		case MotionEvent.ACTION_MOVE:
			addPointsToCurrentPath(event);
			break;
		case MotionEvent.ACTION_UP:
		case MotionEvent.ACTION_CANCEL:
			//			Log.d(TAG, findMinAndMaxVelocity());
			addPathToBitmap(mPath);
			recycleVelocityTracker();

			mPath = null;
			break;
		default:
			return false;
		}

		invalidate();

		return true;
	}

	private void initVelocityTrackerIfNotExists() {
		if (mVelocityTracker == null) {
			mVelocityTracker = VelocityTracker.obtain();
		}
	}

	private void recycleVelocityTracker() {
		mVelocityTracker.recycle();
		mVelocityTracker = null;
	}

	private void addPathToBitmap(List<VelocityPoint> path) {
		if (mStoredBitmap == null && getWidth() > 0 && getHeight() > 0) {
			initBitmap();
		} else if (mStoredBitmap.getWidth() != getWidth() && mStoredBitmap.getHeight() != getHeight()) {
			initBitmap();
			redrawAllPathsWithScalar(1.0f);
		}

		if (mBitmapCanvas == null) {
			return;
		}
		
		synchronized (mBitmapCanvas) {
			if (path != null) {
				mAllPaths.add(path);
				int size = path.size();
				if (size > 2) {
					for (int i = 2; i < size; i+=2) {
						drawArcFromPoints(mBitmapCanvas, path.get(i-2), path.get(i-1), path.get(i));
					}
				} else if (size == 2) {
					drawArcFromPoints(mBitmapCanvas, path.get(0), path.get(0), path.get(1));
				}
			}	
		}
	}

	private void initBitmap() {
		mStoredBitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
		mBitmapCanvas = new Canvas(mStoredBitmap);
		addExtrasToBitmap();
	}

	private void addExtrasToBitmap() {
		Rect bounds = new Rect();
		if (mUnderlineLabel != null) {
			mTextPaint.getTextBounds(mUnderlineLabel, 0, mUnderlineLabel.length(), bounds);
			int offset = (int) ((getWidth() - bounds.width()) / 2);
			mBitmapCanvas.drawText(mUnderlineLabel, offset, (getHeight() * 0.93f), mTextPaint);
		}
		if (mShowUnderline) {
			float offset;
			if (mUnderlineLabel != null) {
				offset = bounds.height();
			} else {
				offset = 0.0f;
			}
			float y = getHeight() * 0.9f - offset;
			mBitmapCanvas.drawLine(getWidth() * 0.1f, y, getWidth() * 0.9f, y, mUnderlinePaint);
		}
	}

	@Override
	protected void onDraw(Canvas canvas) {
		canvas.drawRect(0, 0, getWidth(), getHeight(), mWhitePaint);
		if (mStoredBitmap != null) {
			canvas.drawBitmap(mStoredBitmap, 0, 0, mPaint);
		}

		if (mPath != null) {
			drawPath(mPath, canvas);
		}
	}

	private void drawPath(List<VelocityPoint> path, Canvas canvas) {
		int size = path.size();
		if (size > 2) {
			for (int i = 2; i < size; i+=2) {
				drawArcFromPoints(canvas, path.get(i-2), path.get(i-1), path.get(i));
			}
		} else if (size == 2) {
			drawArcFromPoints(canvas, path.get(0), path.get(0), path.get(1));
		}
	}

	private void drawArcFromPoints(Canvas canvas, VelocityPoint last, VelocityPoint middle, VelocityPoint first) {
		List<PointF> points;
		float endWidth;
		float widthStep;
		mDrawablePath = buildPathFromPoints(last, middle, first);
		points = getPointsFromPath(mDrawablePath);
		endWidth = strokeFromVelocity(first.velocity);
		widthStep = (endWidth - mLastWidth) / points.size();
		for (PointF point : points) {
			canvas.drawCircle(point.x, point.y, (mLastWidth + widthStep)/2, mPaint);
			mLastWidth += widthStep;
		}
		mLastWidth = endWidth;
	}

	private Path buildPathFromPoints(final VelocityPoint last, final VelocityPoint middle, final VelocityPoint first) {
		Path drawablePath = new Path();
		drawablePath.moveTo(last.x, last.y);
		//		drawablePath.quadTo(middle.x, middle.y, first.x, first.y);
		drawablePath.cubicTo(last.x, last.y, middle.x, middle.y, first.x, first.y);
		return drawablePath;
	}

	private List<PointF> getPointsFromPath(final Path path) {
		PathMeasure measure = new PathMeasure(path, false);
		float[] rawPoints = {0f, 0f};
		List<PointF> points = new ArrayList<PointF>();
		float pathLength = measure.getLength();
		for (int i = 0; i < pathLength; i++) {
			if (measure.getPosTan(i, rawPoints, null)) {
				points.add(new PointF(rawPoints[0], rawPoints[1]));
			}
		}
		return points;
	}

	private float strokeFromVelocity(float velocity) {
		velocity = Math.max(1.0f, Math.min(5.0f, (float) Math.pow(Math.abs(velocity), -0.75d)));
		float toReturn = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, velocity, mMetrics);
		return toReturn;
	}

	private void addPointsToCurrentPath(MotionEvent event) {
		float x;
		float y;

		mVelocityTracker.computeCurrentVelocity(1);
		float velocity = (mVelocityTracker.getXVelocity() + mVelocityTracker.getYVelocity()) / 2.0f;
		for (int i = 0; i < event.getHistorySize(); i++) {
			x = event.getHistoricalX(i);
			y = event.getHistoricalY(i);
			mPath.add(new VelocityPoint(x, y, velocity));
		}
		mPath.add(new VelocityPoint(event.getX(), event.getY(), velocity));
	}

	private void beginNewPath() {
		mPath = Collections.synchronizedList(new ArrayList<VelocityPoint>());
	}

	public boolean hasBeenMarked() {
		return mPath != null || mStoredBitmap != null;
	}

	public Bitmap getImage() {
		Bitmap response = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
		Canvas responseCanvas = new Canvas(response);
		draw(responseCanvas);
		return response;
	}

	@Override
	protected Parcelable onSaveInstanceState() {
		Bundle bundle = new Bundle();

		bundle.putParcelable(SUPER_STATE, super.onSaveInstanceState());
		
		if (mRetainSignatureOnRotate) {
			bundle.putSerializable(POINTS, mAllPaths);
			bundle.putInt(WIDTH, getWidth());
			bundle.putInt(HEIGHT, getHeight());
		}
		
		return bundle;
	}

	@SuppressWarnings("unchecked")
	@Override
	protected void onRestoreInstanceState(Parcelable state) {
		Bundle bundle = (Bundle) state;
		if (bundle != null) {
			super.onRestoreInstanceState(bundle.getParcelable(SUPER_STATE));
			if (mRetainSignatureOnRotate) {
				mPreviousWidth = bundle.getInt(WIDTH, -1);
				mPreviousHeight = bundle.getInt(HEIGHT, -1);
				Serializable points = bundle.getSerializable(POINTS);
				if (points != null && points instanceof ArrayList) {
					mAllPaths = (ArrayList<List<VelocityPoint>>) points;
					scheduleAllPathScaling();
				}
			}
		}
	}

	private void scheduleAllPathScaling() {
		mScaleAllPathsAfterMeasure = true;
	}

	@SuppressLint("DrawAllocation")
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		if (getWidth() > 0 && getHeight() > 0) {
			addPathToBitmap(null);
			if (mScaleAllPathsAfterMeasure) {
				mScaleAllPathsAfterMeasure = false;
				int currentWidth = getWidth();
				int currentHeight = getHeight();

				if (currentWidth == mPreviousWidth && currentHeight == mPreviousHeight) {
					for (List<VelocityPoint> path : mAllPaths) {
						addPathToBitmap(path);
					}
				} else {
					float widthScale = ((float) currentWidth / (float) mPreviousWidth);
					float heightScale = ((float) currentHeight / (float) mPreviousHeight);
					float scale = Math.min(widthScale, heightScale);

					redrawAllPathsWithScalar(scale);
				}
			}
		}
	}

	private void redrawAllPathsWithScalar(float scale) {
		ArrayList<List<VelocityPoint>> tempAllPaths = new ArrayList<List<VelocityPoint>>();
		List<VelocityPoint> currentPath;
		for (List<VelocityPoint> path : mAllPaths) {
			currentPath = Collections.synchronizedList(new ArrayList<VelocityPoint>());
			for (VelocityPoint point : path) {
				point.addScalar(scale);
				currentPath.add(point);
			}
			tempAllPaths.add(currentPath);
		}
		mAllPaths.clear();
		for (List<VelocityPoint> path : tempAllPaths) {
			addPathToBitmap(path);
		}
	}

	private static class VelocityPoint implements Serializable {

		private static final long serialVersionUID = -4341259980288212184L;
		private float x;
		private float y;
		private Float velocity;

		private VelocityPoint(float x, float y, float velocity) {
			this.x = x;
			this.y = y;
			this.velocity = velocity;
		}

		public void addScalar(float scale) {
			x *= scale;
			y *= scale;
		}

		public String toString() {
			return String.format("PressurePoint[x:%s, y:%s, velocity:%s]", x, y, velocity);
		}
	}
}
