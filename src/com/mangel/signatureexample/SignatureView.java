package com.mangel.signatureexample;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Cap;
import android.graphics.Path;
import android.graphics.Paint.Style;
import android.graphics.PathMeasure;
import android.graphics.Point;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;

public class SignatureView extends View {

	public static final String TAG = "signature";

	private List<List<PressurePoint>> mAllPaths;
	private List<PressurePoint> mPath;
	private Paint mPaint;
	private Paint mWhitePaint;

	private Path mDrawablePath;

	private Context mContext;

	private DisplayMetrics mMetrics;

	private Bitmap bitmap;

	private Canvas bitmapCanvas;
	private Float lastWidth = 1f;

	private VelocityTracker mVelocityTracker;

	public SignatureView(Context context) {
		super(context);
		initialize(context);
	}

	private void initialize(Context context) {
		mContext = context;
		mMetrics = mContext.getResources().getDisplayMetrics();
		setWillNotDraw(false);

		clear();
		mPaint = new Paint();
		mPaint.setAntiAlias(true);
		mPaint.setStrokeCap(Cap.BUTT);

		mPaint.setColor(Color.BLACK);
		mPaint.setStyle(Style.FILL_AND_STROKE);

		mWhitePaint = new Paint();
		mWhitePaint.setColor(Color.WHITE);
		
		mVelocityTracker = VelocityTracker.obtain();
	}

	public SignatureView(Context context, AttributeSet attrs) {
		super(context, attrs);

		initialize(context);
	}

	public SignatureView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);

		initialize(context);
	}

	public void clear() {
		mAllPaths = Collections.synchronizedList(new ArrayList<List<PressurePoint>>());
		mPath = null;
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN:
			mVelocityTracker.addMovement(event);
			beginNewPath();
			return true;
		case MotionEvent.ACTION_MOVE:
			mVelocityTracker.addMovement(event);
			addPointsToCurrentPath(event);
			break;
		case MotionEvent.ACTION_UP:
		case MotionEvent.ACTION_CANCEL:
			addPathToBitmap();
			mVelocityTracker.clear();
			mPath = null;
			break;
		default:
			return false;
		}

		// Schedules a repaint.
		invalidate();
		return true;
	}

	private void addPathToBitmap() {
		if (bitmap == null) {
			bitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
			bitmapCanvas = new Canvas(bitmap);
		}
		synchronized (bitmapCanvas) {
			if (mPath != null) {
				int size = mPath.size();
				if (size > 2) {
					for (int i = 2; i < size; i+=2) {
						drawArcFromPoints(bitmapCanvas, mPath.get(i-2), mPath.get(i-1), mPath.get(i));
					}
				} else if (size == 2) {
					drawArcFromPoints(bitmapCanvas, mPath.get(0), mPath.get(0), mPath.get(1));
				}
			}	
		}
	}

	@Override
	protected void onDraw(Canvas canvas) {
		canvas.drawRect(0, 0, getWidth(), getHeight(), mWhitePaint);
		if (bitmap != null) {
			canvas.drawBitmap(bitmap, 0, 0, mPaint);
		}

		if (mPath != null) {
			int size = mPath.size();
			if (size > 2) {
				for (int i = 2; i < size; i+=2) {
					drawArcFromPoints(canvas, mPath.get(i-2), mPath.get(i-1), mPath.get(i));
				}
			} else if (size == 2) {
				drawArcFromPoints(canvas, mPath.get(0), mPath.get(0), mPath.get(1));
			}
		}
	}

	private void drawArcFromPoints(Canvas canvas, PressurePoint last, PressurePoint middle, PressurePoint first) {
		List<PointF> points;
		float endWidth;
		float widthStep;
		mDrawablePath = buildPathFromPoints(last, middle, first);
		points = getPointsFromPath(mDrawablePath);
//		endWidth = strokeFromTime(last.time, first.time);
		endWidth = strokeFromVelocity(first.velocity);
		widthStep = (endWidth - lastWidth) / points.size();
		for (PointF point : points) {
//			mPaint.setStrokeWidth(lastWidth + widthStep);
//			canvas.drawPoint(point.x, point.y, mPaint);
			canvas.drawCircle(point.x, point.y, (lastWidth + widthStep)/2, mPaint);
			lastWidth += widthStep;
		}
		lastWidth = endWidth;
	}

	private Path buildPathFromPoints(final PressurePoint last, final PressurePoint middle, final PressurePoint first) {
		Path drawablePath = new Path();
		drawablePath.moveTo(last.x, last.y);
		drawablePath.quadTo(middle.x, middle.y, first.x, first.y);
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
		velocity = Math.min(5.0f, (float) Math.pow(Math.abs(velocity), -1.0d));
		float toReturn = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, velocity, mMetrics);
		Log.d(TAG, "" + toReturn);
		return toReturn;
	}

	private float strokeFromTime(Long time, Long time2) {
		Long difference = time2 - time;
		float toReturn = (float) (2.5f * (Math.log10(difference)));
		toReturn = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, toReturn, mMetrics);
		Log.d(TAG, "" + toReturn + "   " + difference);
		return toReturn;
	}

	private void addPointsToCurrentPath(MotionEvent event) {
		float x;
		float y;
		long time;
		mVelocityTracker.computeCurrentVelocity(1);
		float velocity = (mVelocityTracker.getXVelocity() + mVelocityTracker.getYVelocity()) / 2.0f;
		for (int i = 0; i < event.getHistorySize(); i++) {
			x = event.getHistoricalX(i);
			y = event.getHistoricalY(i);
			time = event.getHistoricalEventTime(i);
			mPath.add(new PressurePoint(x, y, time, velocity));
		}
		mPath.add(new PressurePoint(event.getX(), event.getY(), event.getEventTime(), velocity));
	}

	private void beginNewPath() {
		mPath = Collections.synchronizedList(new ArrayList<PressurePoint>());
	}

	private static class PressurePoint implements Comparable<PressurePoint> {

		private float x;
		private float y;
		private Long time;
		private Float velocity;

		private PressurePoint(float x, float y, long time, float velocity) {
			this.x = x;
			this.y = y;
			this.time = time;
			this.velocity = velocity;
		}

		public String toString() {
			return String.format("PressurePoint[x:%s, y:%s, time:%s]", x, y, time);
		}

		@Override
		public int compareTo(PressurePoint another) {
			return time.compareTo(another.time);
		}
	}
}
