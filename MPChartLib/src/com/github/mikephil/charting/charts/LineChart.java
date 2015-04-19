
package com.github.mikephil.charting.charts;

import android.content.Context;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathEffect;
import android.graphics.PathMeasure;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.MotionEvent;

import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.ChartInterface;
import com.github.mikephil.charting.utils.FillFormatter;

import java.util.ArrayList;

/**
 * Chart that draws lines, surfaces, circles, ...
 * 
 * @author Philipp Jahoda
 */
public class LineChart extends BarLineChartBase<LineData> {

    /** the width of the highlighning line */
    protected float mHighlightWidth = 3f;

    /** paint for the inner circle of the value indicators */
    protected Paint mCirclePaintInner;

    private FillFormatter mFillFormatter;
    
    private Handler mHanler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            scaleYAdaptive();
            super.handleMessage(msg);
        }
        
    };

    public LineChart(Context context) {
        super(context);
    }

    public LineChart(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public LineChart(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void init() {
        super.init();

        mFillFormatter = new DefaultFillFormatter();

        mCirclePaintInner = new Paint(Paint.ANTI_ALIAS_FLAG);
        mCirclePaintInner.setStyle(Paint.Style.FILL);
        mCirclePaintInner.setColor(Color.WHITE);

        mHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mHighlightPaint.setStyle(Paint.Style.STROKE);
        mHighlightPaint.setStrokeWidth(2f);
        mHighlightPaint.setColor(Color.rgb(255, 187, 115));        
    }

    @Override
    protected void calcMinMax(boolean fixedValues) {
        super.calcMinMax(fixedValues);

        // // if there is only one value in the chart
        // if (mOriginalData.getYValCount() == 1
        // || mOriginalData.getYValCount() <= mOriginalData.getDataSetCount()) {
        // mDeltaX = 1;
        // }

        if (mDeltaX == 0 && mData.getYValCount() > 0)
            mDeltaX = 1;
    }

    @Override
    protected void drawHighlights() {

        for (int i = 0; i < mIndicesToHightlight.length; i++) {

            LineDataSet set = mData.getDataSetByIndex(mIndicesToHightlight[i]
                    .getDataSetIndex());

            if (set == null)
                continue;

            mHighlightPaint.setColor(set.getHighLightColor());

            int xIndex = mIndicesToHightlight[i].getXIndex(); // get the
                                                              // x-position

            if (xIndex > mDeltaX * mPhaseX)
                continue;

            float y = set.getYValForXIndex(xIndex) * mPhaseY; // get the
                                                              // y-position

            float[] pts = new float[] {
                    xIndex, mYChartMax, xIndex, mYChartMin, 0, y, mDeltaX, y
            };

            mTrans.pointValuesToPixel(pts);
            // draw the highlight lines
            mDrawCanvas.drawLines(pts, mHighlightPaint);
        }
    }

    /**
     * Class needed for saving the points when drawing cubic-lines.
     * 
     * @author Philipp Jahoda
     */
    private class CPoint {

        public float x = 0f;
        public float y = 0f;

        /** x-axis distance */
        public float dx = 0f;

        /** y-axis distance */
        public float dy = 0f;

        public CPoint(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    @Override
    protected void drawRefData() {
        int[] xRange = { 0, 0 };
        getXRangeInScreen(xRange);
        int mid = (xRange[0] + xRange[1]) / 2;

        int start = xRange[0] - 31; // 两个ref data之间相差一个月
        if (start < 0)
            start = 0;
        int end = xRange[1] + 31; // 所以在起始处分别多留一个月的margin
        if (end > mData.getXValCount() - 1)
            end = mData.getXValCount() - 1;
        int relStart = -1;
        int relEnd = -1;

        ArrayList<LineDataSet> dataSets = mData.getDataSets();

        // the path for the cubic-spline
        Path outer = new Path();
        Path inner = new Path();
        Path upperBorder = new Path();
        Path lowerBorder = new Path();
        
        // Index 0 stands for the real data line
        // Index 1 stands for the 3DS reference line
        for (int i = 1; i < mData.getDataSetCount(); i++) {

            LineDataSet dataSet = dataSets.get(i);
            ArrayList<Entry> entries = dataSet.getYVals();

            if (entries.size() < 1)
                continue;

            mRenderPaint.setStrokeWidth(dataSet.getLineWidth());
            mRenderPaint.setPathEffect(dataSet.getDashPathEffect());

            float intensity = dataSet.getCubicIntensity();
            
            ArrayList<CPoint> points = new ArrayList<CPoint>();
            for (Entry e : entries) {
                int tmpX = e.getXIndex();
                if (tmpX < start) {
                    continue;
                } else if (tmpX <= end) {
                    if (relStart == -1)
                        relStart = tmpX;
                    points.add(new CPoint(tmpX, e.getVal()));
                    relEnd = tmpX;
                } else {
                    break;
                }
            }

            if (points.size() > 1) {
                for (int j = 0; j < points.size() * mPhaseX; j++) {

                    CPoint point = points.get(j);

                    if (j == 0) {
                        CPoint next = points.get(j + 1);
                        point.dx = ((next.x - point.x) * intensity);
                        point.dy = ((next.y - point.y) * intensity);
                    } else if (j == points.size() - 1) {
                        CPoint prev = points.get(j - 1);
                        point.dx = ((point.x - prev.x) * intensity);
                        point.dy = ((point.y - prev.y) * intensity);
                    } else {
                        CPoint next = points.get(j + 1);
                        CPoint prev = points.get(j - 1);
                        point.dx = ((next.x - prev.x) * intensity);
                        point.dy = ((next.y - prev.y) * intensity);
                    }

                    if (i == 1) {
                        if (j == 0) {
                            outer.moveTo(point.x, point.y * mPhaseY);
                        } else {
                            CPoint prev = points.get(j - 1);
                            outer.cubicTo(prev.x + prev.dx, (prev.y + prev.dy) * mPhaseY, point.x - point.dx,
                                    (point.y - point.dy) * mPhaseY, point.x, point.y * mPhaseY);
                        }
                    } else if (i == 2) {
                        if (j == 0) {
                            inner.moveTo(point.x, point.y * mPhaseY);
                            upperBorder.moveTo(point.x, point.y * mPhaseY);
                        } else {
                            CPoint prev = points.get(j - 1);
                            inner.cubicTo(prev.x + prev.dx, (prev.y + prev.dy) * mPhaseY, point.x - point.dx,
                                    (point.y - point.dy) * mPhaseY, point.x, point.y * mPhaseY);
                            upperBorder.cubicTo(prev.x + prev.dx, (prev.y + prev.dy) * mPhaseY, point.x - point.dx,
                                    (point.y - point.dy) * mPhaseY, point.x, point.y * mPhaseY);
                        }
                    }
                }
                
                for (int j = points.size() - 1; j >= 0; j--) {

                    CPoint point = points.get(j);

                    if (j == 0) {
                        CPoint prev = points.get(j + 1);
                        point.dx = ((point.x - prev.x) * intensity);
                        point.dy = ((point.y - prev.y) * intensity);
                    } else if (j == points.size() - 1) {
                        CPoint next = points.get(j - 1);
                        point.dx = ((next.x - point.x) * intensity);
                        point.dy = ((next.y - point.y) * intensity);
                    } else {
                        CPoint prev = points.get(j + 1);
                        CPoint next = points.get(j - 1);
                        point.dx = ((next.x - prev.x) * intensity);
                        point.dy = ((next.y - prev.y) * intensity);
                    }

                    if (i == 3) {
                        if (j == points.size() - 1) {
                            inner.lineTo(point.x, point.y * mPhaseY);
                            lowerBorder.moveTo(point.x, point.y * mPhaseY);
                        } else {
                            CPoint prev = points.get(j + 1);
                            inner.cubicTo(prev.x + prev.dx, (prev.y + prev.dy) * mPhaseY, point.x - point.dx,
                                    (point.y - point.dy) * mPhaseY, point.x, point.y * mPhaseY);
                            lowerBorder.cubicTo(prev.x + prev.dx, (prev.y + prev.dy) * mPhaseY, point.x - point.dx,
                                    (point.y - point.dy) * mPhaseY, point.x, point.y * mPhaseY);
                        }
                        
                        if (j == 0) {
                            inner.close();
                        }
                    } else if (i == 4) {
                        if (j == points.size() - 1) {
                            outer.lineTo(point.x, point.y * mPhaseY);
                        } else {
                            CPoint prev = points.get(j + 1);
                            outer.cubicTo(prev.x + prev.dx, (prev.y + prev.dy) * mPhaseY, point.x - point.dx,
                                    (point.y - point.dy) * mPhaseY, point.x, point.y * mPhaseY);
                        }
                        
                        if (j == 0) {
                            outer.close();
                            mRenderPaint.setStyle(Paint.Style.FILL_AND_STROKE);
                            mRenderPaint.setColor(Color.rgb(0xf0, 0xee, 0xd1));
                            mTrans.pathValueToPixel(outer);
                            mDrawCanvas.drawPath(outer, mRenderPaint);

                            mRenderPaint.setColor(Color.rgb(0xa2, 0xe3, 0xc1));
                            mTrans.pathValueToPixel(inner);
                            mDrawCanvas.drawPath(inner, mRenderPaint);

                            mRenderPaint.setStyle(Paint.Style.STROKE);
                            PathEffect effect =
                                    new DashPathEffect(new float[] { 8, 8, 8, 8 }, 1);
                            mRenderPaint.setPathEffect(effect);
                            mRenderPaint.setColor(Color.rgb(0x50, 0xe3, 0xc2));
                            mTrans.pathValueToPixel(upperBorder);
                            PathMeasure measure = new PathMeasure(upperBorder, false);
                            // 要画的虚线长度最多为画布长宽之和
                            float maxLen = mDrawCanvas.getWidth() + mDrawCanvas.getHeight();
                            float length = measure.getLength(); // path中虚线的长度
                            Path dstPath = new Path();
                            if (maxLen < length) { // path中的虚线过长了，需要剪裁
                                // 关键是要找到屏幕区域内的曲线的中心点
                                float midSeg = length * (float) (mid - relStart)
                                        / (float) (relEnd - relStart);
                                float startSeg = midSeg - maxLen / 2f;
                                if (startSeg < 0)
                                    startSeg = 0;
                                float endSeg = midSeg + maxLen / 2f;
                                if (endSeg > length)
                                    endSeg = length;
                                measure.getSegment(startSeg, endSeg, dstPath, true);
                            } else {
                                dstPath = upperBorder;
                            }
                            mDrawCanvas.drawPath(dstPath, mRenderPaint);

                            mRenderPaint.setColor(Color.rgb(0x50, 0xe3, 0xc2));
                            mTrans.pathValueToPixel(lowerBorder);
                            measure.setPath(lowerBorder, false);
                            length = measure.getLength(); // path中虚线的长度
                            dstPath.reset();
                            if (maxLen < length) { // path中的虚线过长了，需要剪裁
                                // 注意这里是从右向左绘制
                                float midSeg = length * (float) (relEnd - mid)
                                        / (float) (relEnd - relStart);
                                float startSeg = midSeg - maxLen / 2f;
                                if (startSeg < 0)
                                    startSeg = 0;
                                float endSeg = midSeg + maxLen / 2f;
                                if (endSeg > length)
                                    endSeg = length;
                                measure.getSegment(startSeg, endSeg, dstPath, true);
                            } else {
                                dstPath = lowerBorder;
                            }
                            mDrawCanvas.drawPath(dstPath, mRenderPaint);
                        }
                    }
                }
            }
            mRenderPaint.setPathEffect(null);
        }
    }

    /**
     * draws the given y values to the screen
     */
    @Override
    protected void drawData() {

        ArrayList<LineDataSet> dataSets = mData.getDataSets();

        for (int i = 0; i < 1; i++) {

            LineDataSet dataSet = dataSets.get(i);
            ArrayList<Entry> entries = dataSet.getYVals();

            if (entries.size() < 1)
                continue;

            mRenderPaint.setStrokeWidth(dataSet.getLineWidth());
            mRenderPaint.setPathEffect(dataSet.getDashPathEffect());

            // if drawing cubic lines is enabled
            if (dataSet.isDrawCubicEnabled()) {

                // get the color that is specified for this position from the
                // DataSet
                mRenderPaint.setColor(dataSet.getColor());

                float intensity = dataSet.getCubicIntensity();

                // the path for the cubic-spline
                Path spline = new Path();

                ArrayList<CPoint> points = new ArrayList<CPoint>();
                for (Entry e : entries)
                    points.add(new CPoint(e.getXIndex(), e.getVal()));

                if (points.size() > 1) {
                    for (int j = 0; j < points.size() * mPhaseX; j++) {

                        CPoint point = points.get(j);

                        if (j == 0) {
                            CPoint next = points.get(j + 1);
                            point.dx = ((next.x - point.x) * intensity);
                            point.dy = ((next.y - point.y) * intensity);
                        }
                        else if (j == points.size() - 1) {
                            CPoint prev = points.get(j - 1);
                            point.dx = ((point.x - prev.x) * intensity);
                            point.dy = ((point.y - prev.y) * intensity);
                        }
                        else {
                            CPoint next = points.get(j + 1);
                            CPoint prev = points.get(j - 1);
                            point.dx = ((next.x - prev.x) * intensity);
                            point.dy = ((next.y - prev.y) * intensity);
                        }

                        // create the cubic-spline path
                        if (j == 0) {
                            spline.moveTo(point.x, point.y * mPhaseY);
                        }
                        else {
                            CPoint prev = points.get(j - 1);
                            spline.cubicTo(prev.x + prev.dx, (prev.y + prev.dy) * mPhaseY, point.x
                                    - point.dx,
                                    (point.y - point.dy) * mPhaseY, point.x, point.y * mPhaseY);
                        }
                    }
                }

                // if filled is enabled, close the path
                if (dataSet.isDrawFilledEnabled()) {

                    float fillMin = mFillFormatter
                            .getFillLinePosition(dataSet, mData, mYChartMax, mYChartMin);

                    spline.lineTo((entries.size() - 1) * mPhaseX, fillMin);
                    spline.lineTo(0, fillMin);
                    spline.close();

                    mRenderPaint.setStyle(Paint.Style.FILL);
                } else {
                    mRenderPaint.setStyle(Paint.Style.STROKE);
                }

                mTrans.pathValueToPixel(spline);

                mDrawCanvas.drawPath(spline, mRenderPaint);

                // draw normal (straight) lines
            } else {

                mRenderPaint.setStyle(Paint.Style.STROKE);

                // more than 1 color
                if (dataSet.getColors() == null || dataSet.getColors().size() > 1) {

                    float[] valuePoints = mTrans.generateTransformedValuesLineScatter(entries, mPhaseY);

                    for (int j = 0; j < (valuePoints.length - 2) * mPhaseX; j += 2) {

                        if (isOffContentRight(valuePoints[j]))
                            break;

                        // make sure the lines don't do shitty things outside
                        // bounds
                        if (j != 0 && isOffContentLeft(valuePoints[j - 1]) // j-1是y轴的数据吧？
                                && isOffContentTop(valuePoints[j + 1])
                                && isOffContentBottom(valuePoints[j + 1]))
                            continue;

                        // get the color that is set for this line-segment
                        mRenderPaint.setColor(dataSet.getColor(j / 2));

                        mDrawCanvas.drawLine(valuePoints[j], valuePoints[j + 1],
                                valuePoints[j + 2], valuePoints[j + 3], mRenderPaint);
                    }

                } else { // only one color per dataset

                    mRenderPaint.setColor(dataSet.getColor());

                    Path line = generateLinePath(entries);
                    mTrans.pathValueToPixel(line);

                    mDrawCanvas.drawPath(line, mRenderPaint);
                }

                mRenderPaint.setPathEffect(null);

                // if drawing filled is enabled
                if (dataSet.isDrawFilledEnabled() && entries.size() > 0) {
                    // mDrawCanvas.drawVertices(VertexMode.TRIANGLE_STRIP,
                    // valuePoints.length, valuePoints, 0,
                    // null, 0, null, 0, null, 0, 0, paint);

                    mRenderPaint.setStyle(Paint.Style.FILL);

                    mRenderPaint.setColor(dataSet.getFillColor());
                    // filled is drawn with less alpha
                    mRenderPaint.setAlpha(dataSet.getFillAlpha());

                    // mRenderPaint.setShader(dataSet.getShader());

                    Path filled = generateFilledPath(entries,
                            mFillFormatter.getFillLinePosition(dataSet, mData, mYChartMax,
                                    mYChartMin));

                    mTrans.pathValueToPixel(filled);

                    mDrawCanvas.drawPath(filled, mRenderPaint);

                    // restore alpha
                    mRenderPaint.setAlpha(255);
                    // mRenderPaint.setShader(null);
                }
            }

            mRenderPaint.setPathEffect(null);
        }
    }
    
    /**
     * Generates the path that is used for filled drawing.
     * 
     * @param entries
     * @return
     */
    private Path generateFilledPath(ArrayList<Entry> entries, float fillMin) {

        Path filled = new Path();
        filled.moveTo(entries.get(0).getXIndex(), entries.get(0).getVal() * mPhaseY);

        // create a new path
        for (int x = 1; x < entries.size() * mPhaseX; x++) {

            Entry e = entries.get(x);
            filled.lineTo(e.getXIndex(), e.getVal() * mPhaseY);
        }

        // close up
        filled.lineTo(entries.get((int) ((entries.size() - 1) * mPhaseX)).getXIndex(), fillMin);
        filled.lineTo(entries.get(0).getXIndex(), fillMin);
        filled.close();

        return filled;
    }

    /**
     * Generates the path that is used for drawing a single line.
     * 
     * @param entries
     * @return
     */
    private Path generateLinePath(ArrayList<Entry> entries) {

        Path line = new Path();
        line.moveTo(entries.get(0).getXIndex(), entries.get(0).getVal() * mPhaseY);

        // create a new path
        for (int x = 1; x < entries.size() * mPhaseX; x++) {

            Entry e = entries.get(x);
            line.lineTo(e.getXIndex(), e.getVal() * mPhaseY);
        }

        return line;
    }

    @Override
    protected void drawValues() {

        // if values are drawn
        // 判断条件不太适用
        if (mDrawYValues && mData.getYValCount() < mMaxVisibleCount * mTrans.getScaleX()) {

            ArrayList<LineDataSet> dataSets = mData.getDataSets();

            // 只用画dataSet0的数值
            for (int i = 0; i < mData.getDataSetCount(); i++) {

                LineDataSet dataSet = dataSets.get(i);

                // make sure the values do not interfear with the circles
                int valOffset = (int) (dataSet.getCircleSize() * 1.75f);

                if (!dataSet.isDrawCirclesEnabled())
                    valOffset = valOffset / 2;

                ArrayList<Entry> entries = dataSet.getYVals();

                float[] positions = mTrans.generateTransformedValuesLineScatter(entries, mPhaseY);

                for (int j = 0; j < positions.length * mPhaseX; j += 2) {

                    if (isOffContentRight(positions[j]))
                        break;

                    if (isOffContentLeft(positions[j]) || isOffContentTop(positions[j + 1])
                            || isOffContentBottom(positions[j + 1]))
                        continue;

                    float val = entries.get(j / 2).getVal();

                    if (mDrawUnitInChart) {

                        mDrawCanvas.drawText(mValueFormatter.getFormattedValue(val) + mUnit,
                                positions[j],
                                positions[j + 1]
                                        - valOffset, mValuePaint);
                    } else {

                        mDrawCanvas.drawText(mValueFormatter.getFormattedValue(val), positions[j],
                                positions[j + 1] - valOffset,
                                mValuePaint);
                    }
                }
            }
        }
    }

    /**
     * draws the circle value indicators
     */
    @Override
    protected void drawAdditional() {

        mRenderPaint.setStyle(Paint.Style.FILL);

        ArrayList<LineDataSet> dataSets = mData.getDataSets();

        for (int i = 0; i < mData.getDataSetCount(); i++) {

            LineDataSet dataSet = dataSets.get(i);

            // if drawing circles is enabled for this dataset
            if (dataSet.isDrawCirclesEnabled()) {

                ArrayList<Entry> entries = dataSet.getYVals();

                float[] positions = mTrans.generateTransformedValuesLineScatter(entries, mPhaseY);

                for (int j = 0; j < positions.length * mPhaseX; j += 2) {

                    // Set the color for the currently drawn value. If the index
                    // is
                    // out of bounds, reuse colors.
                    mRenderPaint.setColor(dataSet.getCircleColor(j / 2));

                    if (isOffContentRight(positions[j]))
                        break;

                    // make sure the circles don't do shitty things outside
                    // bounds
                    if (isOffContentLeft(positions[j]) ||
                            isOffContentTop(positions[j + 1])
                            || isOffContentBottom(positions[j + 1]))
                        continue;

                    mDrawCanvas.drawCircle(positions[j], positions[j + 1], dataSet.getCircleSize(),
                            mRenderPaint);
                    mDrawCanvas.drawCircle(positions[j], positions[j + 1],
                            dataSet.getCircleSize() / 2f,
                            mCirclePaintInner);
                }
            } // else do nothing

        }
    }

    /**
     * set the width of the highlightning lines, default 3f
     * 
     * @param width
     */
    public void setHighlightLineWidth(float width) {
        mHighlightWidth = width;
    }

    /**
     * returns the width of the highlightning line, default 3f
     * 
     * @return
     */
    public float getHighlightLineWidth() {
        return mHighlightWidth;
    }

    @Override
    public void setPaint(Paint p, int which) {
        super.setPaint(p, which);

        switch (which) {
            case PAINT_CIRCLES_INNER:
                mCirclePaintInner = p;
                break;
        }
    }

    @Override
    public Paint getPaint(int which) {
        Paint p = super.getPaint(which);
        if (p != null)
            return p;

        switch (which) {
            case PAINT_CIRCLES_INNER:
                return mCirclePaintInner;
        }

        return null;
    }

    /**
     * Sets a custom FillFormatter to the chart that handles the position of the
     * filled-line for each DataSet. Set this to null to use the default logic.
     * 
     * @param formatter
     */
    public void setFillFormatter(FillFormatter formatter) {

        if (formatter == null)
            formatter = new DefaultFillFormatter();

        mFillFormatter = formatter;
    }

    /**
     * Default formatter that calculates the position of the filled line.
     * 
     * @author Philipp Jahoda
     */
    private class DefaultFillFormatter implements FillFormatter {

        @Override
        public float getFillLinePosition(LineDataSet dataSet, LineData data,
                float chartMaxY, float chartMinY) {

            float fillMin = 0f;

            if (dataSet.getYMax() > 0 && dataSet.getYMin() < 0) {
                fillMin = 0f;
            } else {

                if (!mStartAtZero) {

                    float max, min;

                    if (data.getYMax() > 0)
                        max = 0f;
                    else
                        max = chartMaxY;
                    if (data.getYMin() < 0)
                        min = 0f;
                    else
                        min = chartMinY;

                    fillMin = dataSet.getYMin() >= 0 ? min : max;
                } else {
                    fillMin = 0f;
                }

            }

            return fillMin;
        }
    }

    public synchronized void showNPoints(final int num, final int xIndex) {

        int i = xIndex;
        if (xIndex == 0) {
            if (valuesToHighlight()) {
                i = mIndicesToHightlight[0].getXIndex();
            } else if (mData != null && mData.getDataSets() != null) {
                ArrayList<Entry> yVals = mData.getDataSets().get(0).getYVals();
                i = yVals.get(yVals.size() - 1).getXIndex();
            } else {
                return;
            }
        }

        int[] xRange = new int[] { (i - num) < 0 ? 0 : i - num, i };
        float[] yRange = new float[] { 0, 0 };
        getYRangeInXRange(xRange, yRange);
        float deltaY = yRange[1] - yRange[0];
        float maxY = yRange[1] + deltaY * 0.1f;
        
        final float scaleX = mDeltaX / num;
        // float scaleY = mDeltaY / (1.2f * deltaY);

        // num * 0.15f表示：选中的点（最新的点）离视图的右边缘有15％的margin
        final float[] pts = new float[] { (float) (i - num) + num * 0.15f, maxY };
        final ChartInterface chart = this;

        // the post makes it possible that this call waits until the view has
        // finisted setting up
        post(new Runnable() {
            @Override
            public void run() {
                // showNPoints不再scaleY，由scaleYAdaptive统一完成
                // showNPoints只会更新matrix，不再刷新视图
                mTrans.showNPoints(pts, scaleX, 1f, chart);
                scaleYAdaptive();
            }
        });

    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        mHanler.removeMessages(0);
        mHanler.sendEmptyMessageDelayed(0, 1000);
        return super.onTouchEvent(event);
    }
    
    public synchronized void scaleYAdaptive() {
        if (mDataNotSet)
            return;

        int[] xRange = new int[] { 0, 0 };
        float[] yRange = new float[] { 0, 0 };
        getXRangeInScreen(xRange);
        getYRangeInXRange(xRange, yRange);
        float deltaY = yRange[1] - yRange[0];
        float scaleY = mDeltaY / (1.2f * deltaY);
        float maxY = yRange[1] + deltaY * 0.1f;
        float[] pts = new float[] { 0, maxY };
        mTrans.scaleYAdaptive(pts, scaleY, this);
    }
    
    protected boolean getYRangeInXRange(int [] xRange, float [] yRange) {
        ArrayList<LineDataSet> dataSets = mData.getDataSets();
        float yMax = 0;
        float yMin = 0;
        float[] yMinMax = new float[] { 0, 0 };

        if (dataSets == null || dataSets.size() < 1)
            return false;

        int i = 0;
        while (i < dataSets.size() && !dataSets.get(i).getYRangeInXRange(xRange, yMinMax))
            i++;

        if (i == dataSets.size())
            return false;

        yMin = yMinMax[0];
        yMax = yMinMax[1];

        for (i = i + 1; i < dataSets.size(); i++) {

            if (!dataSets.get(i).getYRangeInXRange(xRange, yMinMax))
                continue;

            if (yMinMax[0] < yMin)
                yMin = yMinMax[0];

            if (yMinMax[1] > yMax)
                yMax = yMinMax[1];
        }

        yRange[0] = yMin;
        yRange[1] = yMax;

        return true;
    }
}
