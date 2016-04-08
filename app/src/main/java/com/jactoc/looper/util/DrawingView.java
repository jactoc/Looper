package com.jactoc.looper.util;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;

/**
 * Created by jacopo on 2016-02-19.
 */
public class DrawingView extends View {

    Paint drawingPaint;
    boolean haveTouch, haveFocus;
    Rect touchArea;
    int x, y, radius;

    public DrawingView(Context context) {
        super(context);
        drawingPaint = new Paint();
        drawingPaint.setColor(Color.WHITE);
        drawingPaint.setStyle(Paint.Style.STROKE);
        drawingPaint.setAntiAlias(true);
        drawingPaint.setStrokeWidth(2);
        haveTouch = false;
        haveFocus = false;
        x = 0;
        y = 0;
        radius = 50;
    }

    public void setHaveTouch(boolean t, Rect tArea){
        haveTouch = t;
        touchArea = tArea;
        x = tArea.centerX();
        y = tArea.centerY();
    }

    public void setHaveFocus(boolean t, Rect tArea){
        haveFocus = t;
        touchArea = tArea;
        x = tArea.centerX();
        y = tArea.centerY();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if(haveTouch){
            drawingPaint.setColor(Color.WHITE);
            //canvas.drawRect(touchArea.left, touchArea.top, touchArea.right, touchArea.bottom, drawingPaint);
            canvas.drawCircle(x, y, radius, drawingPaint);
            canvas.drawCircle(x, y, radius/5, drawingPaint);
        }
        if(haveFocus) {
            drawingPaint.setColor(Color.GREEN);
            //canvas.drawRect(touchArea.left, touchArea.top, touchArea.right, touchArea.bottom, drawingPaint);
            canvas.drawCircle(x, y, radius, drawingPaint);
            canvas.drawCircle(x, y, radius/5, drawingPaint);
        }
    }

} //end