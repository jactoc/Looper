package com.jactoc.looper;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import com.jactoc.looper.camera.CameraActivity;
import com.jactoc.looper.util.Util;
import java.util.ArrayList;
import java.util.List;

public class SplashActivity extends AppCompatActivity {

    //runtime permission
    private static final int PERMISSION_REQUEST = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        //clean first
        Util.cleanUnusedMediaFiles();

        //ask runtime permission
        if(Util.isAndroidM()) {
            checkRuntimePermissions();
        } else {
            goToTheNextScreen();
        }

    }

    private void goToTheNextScreen() {
        //start camera intent
        Intent intent = new Intent(this, CameraActivity.class);
        startActivity(intent);
        finish();
    }

    @TargetApi(Build.VERSION_CODES.M)
    private void checkRuntimePermissions() {
        List<String> permissionsList = new ArrayList<>();
        addPermission(permissionsList, Manifest.permission.CAMERA);
        addPermission(permissionsList, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if(permissionsList.size() > 0) {
            requestPermissions(permissionsList.toArray(new String[permissionsList.size()]), PERMISSION_REQUEST);
        } else {
            goToTheNextScreen();
        }
    }
    @TargetApi(Build.VERSION_CODES.M)
    private void addPermission(List<String> permissionsList, String permission) {
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            permissionsList.add(permission);
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(requestCode == PERMISSION_REQUEST) {
            if (Util.verifyPermissions(grantResults)) {
                goToTheNextScreen();
            }
        } else {
            checkRuntimePermissions();
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

} //end