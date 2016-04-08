package com.jactoc.looper.util;

/**
 * Created by jactoc on 2016-03-14.
 */
public class Size {

    private  int mWidth;
    private  int mHeight;

    public enum LayoutMode {
        FitToParent, // Scale to the size that no side is larger than the parent
        NoBlank      // Scale to the size that no side is smaller than the parent
    }

    public Size(int width,int height) {
        this.mWidth = width;
        this.mHeight=height;
    }

    public int getWidth() {
        return mWidth;
    }

    public void setWidth(int mWidth) {
        this.mWidth = mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    public void setHeight(int mHeight) {
        this.mHeight = mHeight;
    }
}
