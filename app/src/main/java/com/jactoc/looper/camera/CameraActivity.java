package com.jactoc.looper.camera;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import com.jactoc.looper.R;
import com.jactoc.looper.preview.PreviewActivity;
import com.jactoc.looper.util.DoneCallback;
import com.jactoc.looper.util.DrawingView;
import com.jactoc.looper.util.Size;
import com.jactoc.looper.util.Util;

import java.io.FileOutputStream;
import java.io.IOException;

public class CameraActivity extends AppCompatActivity {

    //context
    private Context context = this;

    //camera
    private Camera mCamera;
    private CameraPreview mPreview;
    private int deviceHeight, deviceWidth;
    private MediaRecorder mrec;
    private static int cameraid = Camera.CameraInfo.CAMERA_FACING_FRONT;
    Camera.PictureCallback jpegCallback;

    //UI
    private FrameLayout preview;
    private CountDownTimer timer;
    private ImageView recordButton, flipCamera;
    //alpha
    private float transparency = (float) 0.5;
    private float no_transparency = (float) 1.0;
    private RelativeLayout.LayoutParams previewLayoutParams;
    private RelativeLayout previewLayout;

    //data
    private int timeLimit = 8000;
    private long touchDownTime = -1;
    private boolean recording;
    public static int VIDEO_HOLD_TIME = 200;
    private static String path = Util.getBasePath();
    private static String photoFilePath;
    private int indexPic = 1;

    //flag
    private boolean isCurrentlyRecording = false;
    private DrawingView drawingView;

    //TAG
    private static final String TAG = "CameraActivity";

    // Store instance variables based on arguments passed
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        //selecting the resolution of the Android device so we can create a proportional preview
        deviceWidth = CameraHelper.getDeviceScreenWidth();
        deviceHeight = CameraHelper.getDeviceScreenHeigth();
//        getWindow().setFormat(PixelFormat.TRANSLUCENT);
//        SurfaceView surface_view = new SurfaceView(getApplicationContext());
//        SurfaceHolder surface_holder;
//        addContentView(surface_view, new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.FILL_PARENT, RelativeLayout.LayoutParams.FILL_PARENT));
//
//        if (surface_holder == null) {
//            surface_holder = surface_view.getHolder();
//        }

        photoFilePath = path + indexPic + ".png";

        linkToUI();
        linkHandler();
        initView();

        /** Handles data for jpeg picture */
        jpegCallback = new Camera.PictureCallback() {
            public void onPictureTaken(byte[] data, Camera camera) {
                if(saveImage(data)) {
                    mCamera.startPreview();
                    indexPic++;
                    photoFilePath = path + indexPic + ".png";
                    //goToPreview();
                }
            }
        };
    }

    private void cameraPause() {
        if(isCurrentlyRecording)
            stopRecording();
        else {
            releaseCamera();
            releaseMediaRecorder();
        }
    }

    @Override
    public void onPause() {
        cameraPause();
        super.onPause();
    }
    @Override
    public void onDestroy() {
        cameraPause();
        super.onDestroy();
    }

    private void cameraResumed() {
        if(mCamera == null)
            createCamera(cameraid);
        if(mCamera != null && mPreview != null)
            loadCameraPreview();
    }

    @Override
    public void onResume() {
        checkWhichCamera();
        cameraResumed();
        super.onResume();
    }

    private void checkWhichCamera() {
        if(mPreview != null) {
            if (cameraid == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                Log.d(TAG, "onResumed with front camera");
                mPreview.isFrontCamera = true;
            } else {
                Log.d(TAG, "onResumed with back camera");
                mPreview.isFrontCamera = false;
            }
        }
    }

    private void linkHandler() {
        recordButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                long deltaTime = System.currentTimeMillis() - touchDownTime;
                switch (event.getAction() & MotionEvent.ACTION_MASK) {
                    case MotionEvent.ACTION_DOWN:
                        if (touchDownTime == -1) {
                            recording = false;
                            touchDownTime = System.currentTimeMillis();
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (touchDownTime != -1 && touchDownTime > 0) {
                            if (deltaTime <= VIDEO_HOLD_TIME) {
                            } else {
                                stopRecording();
                            }
                            recording = false;
                            touchDownTime = -1;
                        }
                        return true;
                }
                if (touchDownTime != -1 && touchDownTime > 0 && deltaTime > VIDEO_HOLD_TIME && !recording) {
                    recording = true;
                    startRecording();
                }
                return false;
            }
        });
    }

    protected void takePhoto() throws IOException {
        mPreview.mCamera.takePicture(null, null, jpegCallback);
    }

    private void initView() {
        cameraResumed();
    }

    private void linkToUI() {
        preview = (FrameLayout) findViewById(R.id.preview);       //initial camera at background
        recordButton = (ImageView) findViewById(R.id.record);
        flipCamera = (ImageView) findViewById(R.id.flipCamera);
        flipCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                flipCamera();
            }
        });

        //drawing view
        drawingView = new DrawingView(context);
        LinearLayout.LayoutParams layoutParamsDrawing = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.FILL_PARENT);
        addContentView(drawingView, layoutParamsDrawing);
        setDrawingView(drawingView);

        preview.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                float x1 = 0, x2;
                switch (event.getAction() & MotionEvent.ACTION_MASK) {
                    case MotionEvent.ACTION_DOWN:
                        x1 = Math.abs(event.getX());
                        return true;
                    case MotionEvent.ACTION_UP:
                        x2 = Math.abs(event.getX());
                        if (x2 - x1 <= 100 || x2 - x1 >= 100)
                            touchToFocus(v, event);
                        return true;
                }
                return false;
            }
        });
    }

    private void touchToFocus(View v, MotionEvent event) {
        if(cameraid == Camera.CameraInfo.CAMERA_FACING_BACK) {
            float x = event.getX();
            float y = event.getY();

            if ((x - 50) <= 50)
                x += 50;
            if ((x - 50) >= 600)
                x -= 50;
            if ((y - 50) <= 50)
                y += 50;
            if ((y - 50) >= 900)
                y -= 50;

            final Rect touchRect = new Rect((int) (x - 50), (int) (y - 50), (int) (x + 50), (int) (y + 50));

            final Rect targetFocusRect = new Rect(
                    touchRect.left * 2000 / v.getWidth() - 1000,
                    touchRect.top * 2000 / v.getHeight() - 1000,
                    touchRect.right * 2000 / v.getWidth() - 1000,
                    touchRect.bottom * 2000 / v.getHeight() - 1000);

            DoneCallback<String> doneCallback = new DoneCallback<String>() {
                @Override
                public void done(final String result) {
                    drawingView.setHaveFocus(true, touchRect);
                    drawingView.invalidate();
                    //remove the square indicator after 200 msec
                    Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            drawingView.setHaveFocus(false, new Rect(0, 0, 0, 0));
                            drawingView.setHaveTouch(false, new Rect(0, 0, 0, 0));
                            drawingView.invalidate();
                        }
                    }, 200);
                }
            };
            mPreview.doTouchFocus(targetFocusRect, doneCallback);

            drawingView.setHaveTouch(true, touchRect);
            drawingView.invalidate();
        }
    }

    public void setDrawingView(DrawingView dView) {
        drawingView = dView;
    }

    private void startTimer() {
        timer = new CountDownTimer(timeLimit, 700) {    //start from 8sec, called every 0.5sec
            public void onTick(long millisUntilFinished) {
                int timePassed = ((int) ((timeLimit+1000)-millisUntilFinished)/1000);

                if(timePassed > 6 && timePassed <= 8)
                    recordButton.setImageDrawable(getResources().getDrawable(R.drawable.secleft_8));
                else if(timePassed > 4 && timePassed <= 6)
                    recordButton.setImageDrawable(getResources().getDrawable(R.drawable.secleft_6));
                else if(timePassed > 2 && timePassed <= 4)
                    recordButton.setImageDrawable(getResources().getDrawable(R.drawable.secleft_4));
                else if(timePassed > 0 && timePassed <= 2)
                    recordButton.setImageDrawable(getResources().getDrawable(R.drawable.secleft_2));
                else
                    recordButton.setImageDrawable(getResources().getDrawable(R.drawable.secleft_0));

                try {
                    takePhoto();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            public void onFinish() {
                recordButton.setImageDrawable(getResources().getDrawable(R.drawable.secleft_0));
                stopRecording();
            }
        }.start();
    }

    private void stopTimer() {
        timer.cancel();
    }

    private void flipCamera(){
        //blurPreview();

        if(cameraid == Camera.CameraInfo.CAMERA_FACING_FRONT)
            cameraid = Camera.CameraInfo.CAMERA_FACING_BACK;
        else
            cameraid = Camera.CameraInfo.CAMERA_FACING_FRONT;

        mCamera = mPreview.switchCamera(cameraid, new DoneCallback() {
            @Override
            public void done(Object result) {
                //preview.setBackground(null);
            }
        });
    }

    private void blurPreview() {
        //take screen
        preview.setDrawingCacheEnabled(true);
        preview.buildDrawingCache();
        Bitmap bmp = preview.getDrawingCache();

        //bitmap blur
        //Common.blur(bmp, context, preview);
    }

    private boolean createCamera(int id){
        if(id == Camera.CameraInfo.CAMERA_FACING_BACK) {
            mCamera = CameraPreview.getBackFacingCamera();
            cameraid = Camera.CameraInfo.CAMERA_FACING_BACK;
        } else {
            mCamera = CameraPreview.getFrontFacingCamera();
            cameraid = Camera.CameraInfo.CAMERA_FACING_FRONT;
        }

        if (mCamera == null) {
            new AlertDialog.Builder(context)
                    .setTitle(getString(R.string.error))
                    .setMessage("Your camera is not working. Click here to restart the app.")
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Intent i = getBaseContext().getPackageManager().getLaunchIntentForPackage(getBaseContext().getPackageName());
                            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                            finish();
                            startActivity(i);
                        }
                    })
                    .show();
            return false;
        }
        setCameraPreview();
        return true;
    }

    private void setCameraPreview() {
        Log.d(TAG, "camera is null and set preview");
        mPreview = new CameraPreview(this, mCamera, Size.LayoutMode.NoBlank);
        previewLayoutParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        previewLayout = new RelativeLayout(this);
    }

    private void loadCameraPreview() {
        Log.d(TAG, "camera is not null and load preview");
        previewLayout.removeAllViews();
        preview.removeAllViews();

        previewLayoutParams.width = deviceWidth;
        previewLayoutParams.height = deviceHeight;

        mPreview.setCenterPosition(previewLayoutParams.width / 2, previewLayoutParams.height / 2);
        mPreview.setCurrentOrientationPortrait(!isLandscape(context));

        // Requires RelativeLayout
        previewLayout.addView(mPreview, 0, previewLayoutParams);
        preview.addView(previewLayout, 0, previewLayoutParams);
    }

    private void releaseCamera() {
        if (mCamera != null) {
            Log.e(TAG, "releaseCamera");
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
            mPreview.getHolder().removeCallback(mPreview);
            mCamera.release();
            mCamera = null;
        }
    }

    private void releaseMediaRecorder() {
        if (mrec != null) {
            mrec.reset();
            mrec.release();
            mrec = null;
        }
    }

    protected void startRecording(){
        //need for a right info about orientation
        checkWhichCamera();
        isCurrentlyRecording = true;
        startTimer();
    }

    private boolean isLandscape(Context context) {
        return (context.getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE;
    }

    protected synchronized void stopRecording() {
        stopAndRestart();
    }

    private void stopAndRestart(){
        releaseCamera();

        if(cameraid == Camera.CameraInfo.CAMERA_FACING_FRONT)
            mPreview.isFrontCamera = true;
        else
            mPreview.isFrontCamera = false;

        cameraResumed();
    }

    private void goToPreview() {
        Intent previewActivity = new Intent(this, PreviewActivity.class);
        startActivity(previewActivity);
        finish();
    }

    private boolean saveImage(byte[] data) {
        FileOutputStream outStream;
        try {
            outStream = new FileOutputStream(photoFilePath);

            Bitmap originalBitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
            //need for a right info about orientation
            checkWhichCamera();
            int orientation = mPreview.getCameraOrientation();
            Log.e(TAG, "orientation = " + orientation);

            if (orientation != 0) {
                Matrix matrix = new Matrix();
                matrix.postRotate(orientation);
                originalBitmap = Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.getWidth(), originalBitmap.getHeight(), matrix, false);
            } else {
                // To get mutable bitmap
                originalBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);
            }

            originalBitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream);
            outStream.close();
            originalBitmap.recycle();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

} //end