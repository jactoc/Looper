package com.jactoc.looper.camera;

import android.util.DisplayMetrics;

import com.jactoc.looper.LooperApp;

/**
 * Created by jactoc on 2016-03-14.
 */
public class CameraHelper {

    public static class CameraSettings {

        //frame size
        private static int frameSizeWidth  = 480;
        private static int frameSizeHeight = 640;

        //video bit rate
        private static int videoBitRate = 1507104;

        //frame rate
        private static int frameRate = 30;

        //get
        public static int getFrameSizeWidth(){
            return frameSizeWidth;
        }
        public static int getFrameSizeHeight(){
            return frameSizeHeight;
        }
        public static int getVideoBitRate() {
            return videoBitRate;
        }
        public static int getFrameRate() { return frameRate; }
    }

    private static Integer deviceScreenWidth;
    private static Integer deviceScreenHeight;

    public static void captureDeviceScreenSize(){
        if(deviceScreenHeight == null){
            DisplayMetrics metrics = LooperApp.getAppContext().getResources().getDisplayMetrics();
            int width = metrics.widthPixels;
            int height = metrics.heightPixels;

            deviceScreenWidth = width;
            deviceScreenHeight = height;
        }
    }

    public static Integer getDeviceScreenWidth(){
        captureDeviceScreenSize();
        return deviceScreenWidth;
    }

    public static Integer getDeviceScreenHeigth(){
        captureDeviceScreenSize();
        return deviceScreenHeight;
    }

} //end