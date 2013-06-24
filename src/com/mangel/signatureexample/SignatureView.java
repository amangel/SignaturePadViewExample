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

		mPaint.setColor(Color.BLACK);
		mPaint.setStyle(Style.STROKE);

		mWhitePaint = new Paint();
		mWhitePaint.setColor(Color.WHITE);
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
			beginNewPath();
			return true;
		case MotionEvent.ACTION_MOVE:
			addPointsToCurrentPath(event);
			break;
		case MotionEvent.ACTION_UP:
			//			mAllPaths.add(mPath);
			addPathToBitmap();
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
			bitmap = Bitmap.createBitmap(getWidth(), getHeight(), 
					Bitmap.Config.ARGB_8888);
			bitmapCanvas = new Canvas(bitmap);
		}
		synchronized (bitmapCanvas) {
			if (mPath != null) {
				int size = mPath.size();
				if (size > 2) {
					for (int i = 2; i < size; i+=2) {
						drawArcFromPoints(bitmapCanvas, mPath.get(i-2), mPath.get(i-1), mPath.get(i));
//						mDrawablePath = buildPathFromPoints(mPath.get(i-2), mPath.get(i-1), mPath.get(i));
//						mPaint.setStrokeWidth(strokeFromTime(mPath.get(0).time, mPath.get(1).time));
//						bitmapCanvas.drawPath(mDrawablePath, mPaint);
					}
				} else if (size == 2) {
					drawArcFromPoints(bitmapCanvas, mPath.get(0), mPath.get(0), mPath.get(1));
//					mDrawablePath = buildPathFromPoints(mPath.get(0), mPath.get(0), mPath.get(1));
//					mPaint.setStrokeWidth(strokeFromTime(mPath.get(0).time, mPath.get(1).time));
//					bitmapCanvas.drawPath(mDrawablePath, mPaint);
				}
			}	
		}
	}

	@Override
	protected void onDraw(Canvas canvas) {
		canvas.drawRect(0, 0, getWidth(), getHeight(), mWhitePaint);
//		for (List<PressurePoint> path : mAllPaths) {
//			int size = path.size();
//			if (size > 2) {
//				for (int i = 2; i < size; i+=2) {
//					mDrawablePath = buildPathFromPoints(path.get(i-2), path.get(i-1), path.get(i));
//					canvas.drawPath(mDrawablePath, mPaint);
//				}
//			} else if (size == 2) {
//				mDrawablePath = buildPathFromPoints(path.get(0), path.get(0), path.get(1));
//				canvas.drawPath(mDrawablePath, mPaint);
//			}
//		}
		if (bitmap != null) {
			canvas.drawBitmap(bitmap, 0, 0, mPaint);
		}

		if (mPath != null) {
			int size = mPath.size();
			if (size > 2) {
				Log.d(TAG, "path size: " + size);
				for (int i = 2; i < size; i+=2) {
					drawArcFromPoints(canvas, mPath.get(i-2), mPath.get(i-1), mPath.get(i));
				}
			} else if (size == 2) {
//				mDrawablePath = buildPathFromPoints(mPath.get(0), mPath.get(0), mPath.get(1));
//				mPaint.setStrokeWidth(strokeFromTime(mPath.get(0).time, mPath.get(1).time));
//				canvas.drawPath(mDrawablePath, mPaint);
				drawArcFromPoints(canvas, mPath.get(0), mPath.get(0), mPath.get(1));
			}
		}
//		lastWidth = 1f;
	}

	private void drawArcFromPoints(Canvas canvas, PressurePoint last, PressurePoint middle, PressurePoint first) {
		List<PointF> points;
		float endWidth;
		float widthStep;
		mDrawablePath = buildPathFromPoints(last, middle, first);
//					canvas.drawPath(mDrawablePath, mPaint);
//					Log.d(TAG, "width step: " + widthStep + " endWidth: " + endWidth + " lastWidth: " + lastWidth + " size: " + points.size());
		points = getPointsFromPath(mDrawablePath);
		endWidth = strokeFromTime(last.time, first.time);
		widthStep = (endWidth - lastWidth) / points.size();
		for (PointF point : points) {
			mPaint.setStrokeWidth(lastWidth + widthStep);
			canvas.drawPoint(point.x, point.y, mPaint);
			lastWidth += widthStep;
		}
//					for (int j = 0; i < points.size(); j++) {
//						mPaint.setStrokeWidth(widthStep * (j + 1));
//						canvas.drawPoint(points.get(j).x, points.get(j).y, mPaint);
//					}
		lastWidth = endWidth;
	}

	private Path buildPathFromPoints(final PressurePoint last, final PressurePoint middle, final PressurePoint first) {
		Path drawablePath = new Path();
		//		mPaint.setStrokeWidth(distanceToStroke(last.x, last.y, first.x, first.y));
//		mPaint.setStrokeWidth(strokeFromTime(last.time, first.time));
		drawablePath.moveTo(last.x, last.y);
		drawablePath.quadTo(middle.x, middle.y, first.x, first.y);
		return drawablePath;
	}
	
	private List<PointF> getPointsFromPath(final Path path) {
		int STEPS = 100;
		PathMeasure measure = new PathMeasure(path, false);
		float[] rawPoints = {0f, 0f};
		List<PointF> points = new ArrayList<PointF>();
		float pathLength = measure.getLength();
		float increment = pathLength / STEPS;
		for (int i = 0; i < STEPS; i++) {
			if (measure.getPosTan(increment * i, rawPoints, null)) {
				points.add(new PointF(rawPoints[0], rawPoints[1]));
			}
		}
		return points;
	}

	private float strokeFromTime(Long time, Long time2) {
		Long difference = time2 - time;
		float toReturn = (float) (15.0f * (Math.log10(difference) - 1.4f));
		toReturn = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, toReturn, mMetrics);
		Log.d(TAG, "" + toReturn + "   " + difference);
		return toReturn;
	}

	//	private float distanceToStroke(float x, float y, float x2, float y2) {
	//		float xDelta = Math.abs(x - x2);
	//		float yDelta = Math.abs(y - y2);
	//		float distance = (float) Math.sqrt(Math.pow(xDelta, 2.0) + Math.pow(yDelta, 2.0));
	//		float returnValue = (float) (3.0 * Math.pow(distance, -0.75) + 0.3f) * 4f;
	//		Log.d(TAG, "" + returnValue);
	//	    returnValue = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, returnValue, mMetrics);
	//	    return (float) Math.min(returnValue, 5.0d);
	//	}

	private void addPointsToCurrentPath(MotionEvent event) {
		float pressure;
		float x;
		float y;
		long time;
		for (int i = 0; i < event.getHistorySize(); i++) {
			pressure = event.getHistoricalPressure(i);
			x = event.getHistoricalX(i);
			y = event.getHistoricalY(i);
			time = event.getHistoricalEventTime(i);
			mPath.add(new PressurePoint(x, y, pressure, time));
		}
		mPath.add(new PressurePoint(event.getX(), event.getY(), event.getPressure(), event.getEventTime()));
	}

	private void beginNewPath() {
		mPath = Collections.synchronizedList(new ArrayList<PressurePoint>());
	}

	private static class PressurePoint implements Comparable<PressurePoint> {
		public static final Comparator<PressurePoint> COMPARATOR = new Comparator<PressurePoint>() {

			@Override
			public int compare(PressurePoint lhs, PressurePoint rhs) {
				return lhs.compareTo(rhs);
			}
		};

		private float x;
		private float y;
		private float pressure;
		private Long time;

		private PressurePoint(float x, float y, float pressure, long time) {
			this.x = x;
			this.y = y;
			this.pressure = pressure;
			this.time = time;
		}

		public String toString() {
			return String.format("PressurePoint[x:%s, y:%s, pressure:%s, time:%s]", x, y, pressure, time);
		}

		@Override
		public int compareTo(PressurePoint another) {
			return time.compareTo(another.time);
		}
	}
	
	private static class FancyPoint {
		private FancyPoint(List<PointF> points, float startWidth, float endWidth) {
			
		}
	}
}
