package com.jactoc.looper;

import android.app.Application;
import android.content.Context;

/**
 * Created by jactoc on 16-03-14.
 */
public class LooperApp extends Application {

    private static Context context;

    public void onCreate(){
        super.onCreate();
        context = getApplicationContext();
    }

    public static Context getAppContext() {
        return LooperApp.context;
    }

} //end