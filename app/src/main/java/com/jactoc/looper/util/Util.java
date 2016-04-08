package com.jactoc.looper.util;

import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import java.io.File;

/**
 * Created by jactoc on 2016-03-14.
 */
public class Util {

    public static String getBasePath() {
        return Environment.getExternalStorageDirectory()+"/Looper/";
    }

    public static void cleanUnusedMediaFiles(){
        String[] mediaFiles = new String[]{Util.getBasePath()};

        for(String mediaFile : mediaFiles){
            File file = new File(mediaFile);
            if(file.exists()){
                file.delete();
            }
        }
        File base = new File(getBasePath());
        if(!base.exists())
            base.mkdir();
    }

    public static boolean isAndroidM() {
        if(Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP)
            return true;
        else
            return false;
    }

    public static boolean verifyPermissions(int[] grantResults) {
        // At least one result must be checked.
        if(grantResults.length < 1){
            return false;
        }

        // Verify that each required permission has been granted, otherwise return false.
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

} //end