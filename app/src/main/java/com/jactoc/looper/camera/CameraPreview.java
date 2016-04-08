package com.jactoc.looper.camera;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.RelativeLayout;
import com.jactoc.looper.util.DoneCallback;
import com.jactoc.looper.util.Size;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * This class assumes the parent layout is RelativeLayout.LayoutParams.
 */
public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {

    //TAG
    private static final String TAG = CameraPreview.class.getSimpleName();

    private static boolean DEBUGGING = true;
    private static final String LOG_TAG = "CameraPreview";
    protected Activity mActivity;
    public SurfaceHolder mHolder;
    public Camera mCamera;
    protected Camera.Parameters cameraParams;
    private MediaRecorder recorder;
    protected List<Camera.Size> mPreviewSizeList, mPictureSizeList, mVideoSizeList;
    protected Camera.Size mPreviewSize, mPictureSize;
    private int mSurfaceChangedCallDepth = 0;
    private Size.LayoutMode mLayoutMode;
    private int mCenterPosX = -1;
    private int mCenterPosY;
    private Handler answerRecordHandler = null;
    public boolean isFrontCamera = true;

    public PreviewReadyCallback mPreviewReadyCallback = null;

    //tapToFocus
    private static final int FOCUS_SQR_SIZE = 100;
    private static final int FOCUS_MAX_BOUND = 1000;
    private static final int FOCUS_MIN_BOUND = -FOCUS_MAX_BOUND;

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private static final SparseIntArray TABLETEORIENTATIONS = new SparseIntArray();
    private static final SparseIntArray LOLLIPOPORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 270);//...
        ORIENTATIONS.append(Surface.ROTATION_90, 90);//checked
        ORIENTATIONS.append(Surface.ROTATION_180, 270);//checked
        ORIENTATIONS.append(Surface.ROTATION_270, 90);//checked
    }
    static {
        TABLETEORIENTATIONS.append(Surface.ROTATION_0, 90);//
        TABLETEORIENTATIONS.append(Surface.ROTATION_90, 270);
        TABLETEORIENTATIONS.append(Surface.ROTATION_180, 90);
        TABLETEORIENTATIONS.append(Surface.ROTATION_270, 270);//checked
    }
    static {
        LOLLIPOPORIENTATIONS.append(Surface.ROTATION_0, 270);//
        LOLLIPOPORIENTATIONS.append(Surface.ROTATION_90, 90);
        LOLLIPOPORIENTATIONS.append(Surface.ROTATION_180, 270);
        LOLLIPOPORIENTATIONS.append(Surface.ROTATION_270, 90);//checked
    }

    private boolean isCurrentOrientationPortrait = true;
    public void setCurrentOrientationPortrait(boolean isPortrait) {
        isCurrentOrientationPortrait = isPortrait;
    }
    private int rotation;
    private SensorManager sensorManager;
    private Sensor sensorAccelerometer;
    private Sensor sensorMagneticField;

    public int getFrontCameraOrientation(){
        Log.d(TAG, "front camera orientation with model: " + Build.MODEL);
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            if(Build.MODEL.equals("Nexus 6P"))
                return 90;
            else
                return 270;
        else
            return 270;
    }

    public int getCameraOrientation(){
        if(isFrontCamera)
            return getFrontCameraOrientation();
        else
            return getBackCameraOrientation();
    }

    public int getBackCameraOrientation(){
        Log.d(TAG, "back camera orientation");
        return 90;
    }

    private void initSensor(){
        sensorManager = (SensorManager) mActivity.getSystemService(Context.SENSOR_SERVICE);
        sensorAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorMagneticField = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        sensorManager.registerListener(sensorEventListener, sensorAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(sensorEventListener, sensorMagneticField, SensorManager.SENSOR_DELAY_NORMAL);
    }

    private SensorEventListener sensorEventListener = new SensorEventListener(){

        /*
        * temporary still need to check more in detail but it does do the job in
        * getting portrait and landscape orientation on portrait lock screen
        */
        public int mOrientationDeg; //last rotation in degrees
        private static final int _DATA_X = 0;
        private static final int _DATA_Y = 1;
        private static final int _DATA_Z = 2;
        private int ORIENTATION_UNKNOWN = -1;
        @Override
        public void onSensorChanged(SensorEvent event) {
            float[] values = event.values;
            int orientation = ORIENTATION_UNKNOWN;
            float X = -values[_DATA_X];
            float Y = -values[_DATA_Y];
            float Z = -values[_DATA_Z];
            float magnitude = X*X + Y*Y;
            // Don't trust the angle if the magnitude is small compared to the y value
            if (magnitude * 4 >= Z*Z) {
                float OneEightyOverPi = 57.29577957855f;
                float angle = (float)Math.atan2(-Y, X) * OneEightyOverPi;
                orientation = 90 - (int)Math.round(angle);
                // normalize to 0 - 359 range
                while (orientation >= 360) {
                    orientation -= 360;
                }
                while (orientation < 0) {
                    orientation += 360;
                }
            }

            //now we must figure out which orientation based on the degrees
            if (orientation != mOrientationDeg) {
                mOrientationDeg = orientation;
                if(orientation == -1)
                {}                  //basically flat
                else if(orientation <= 45 || orientation > 315)
                    rotation = 0;   //round to 0
                else if(orientation > 45 && orientation <= 135)
                    rotation = 1;   //round to 90
                else if(orientation > 135 && orientation <= 225)
                    rotation = 2;   //round to 180
                else if(orientation > 225 && orientation <= 315)
                    rotation = 3;   //round to 270
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };

    public MediaRecorder getRecorder() {

        //list supported method
        boolean flag = false;
        mVideoSizeList =  mCamera.getParameters().getSupportedVideoSizes();
        if(mVideoSizeList == null || mVideoSizeList.isEmpty()){
            flag = true;        //google developer guides says for some phones there is no list of video supported and the size is te same of preview.
        }

        //release resources
        mCamera.stopPreview();
        mCamera.unlock();

        //setup recorder
        if(recorder == null)
            recorder = new MediaRecorder();
        recorder.setCamera(mCamera);
        recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        recorder.setAudioEncodingBitRate(16);
        recorder.setAudioSamplingRate(44100);
        recorder.setAudioEncodingBitRate(96000);
        recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);

        //set parameters (switch height->width if it's in portrait)
        Camera.Size optimalPreviewSize = getOptimalPreviewSize(mPreviewSizeList, CameraHelper.CameraSettings.getFrameSizeHeight(), CameraHelper.CameraSettings.getFrameSizeWidth());
        Camera.Size optimalVideoSize = getOptimalPreviewSize(mVideoSizeList, CameraHelper.CameraSettings.getFrameSizeHeight(), CameraHelper.CameraSettings.getFrameSizeWidth());
        recorder.setOrientationHint(getCameraOrientation());
        recorder.setVideoEncodingBitRate(CameraHelper.CameraSettings.getVideoBitRate());
        recorder.setVideoFrameRate(CameraHelper.CameraSettings.getFrameRate());

        //set video size
        if(!flag) {
            Log.d(TAG, "video record size - w" + optimalVideoSize.width + " h" + optimalVideoSize.height);
            recorder.setVideoSize(optimalVideoSize.width, optimalVideoSize.height);
        }
        else {
            recorder.setVideoSize(mPreviewSize.width, mPreviewSize.height);   //this is with the size of the screen
            Log.d(TAG, "video record size - w" + mPreviewSize.width + " h" + mPreviewSize.height);
        }
        //set preview size
        recorder.setPreviewDisplay(getHolder().getSurface());
        cameraParams.setPreviewSize(optimalPreviewSize.width, optimalPreviewSize.height);

        return recorder;
    }

    //return the closest size to the desidered (always selected from the list of supported)
    private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int w, int h) {

        //aspect tolerance
        final double ASPECT_TOLERANCE = 0.1;
        double targetRatio = (double) w / h;

        if (sizes == null)
            return null;

        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        int targetHeight = h;

        // Try to find an size match aspect ratio and size
        for (Camera.Size size : sizes) {
            Log.d("Camera", "Checking size " + size.width + "w " + size.height + "h");
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE)
                continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }

        // Cannot find the one match the aspect ratio, ignore the requirement
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        return optimalSize;
    }

    public interface PreviewReadyCallback {
        void onPreviewReady();
    }

    /**
     * State flag: true when surface's layout size is set and surfaceChanged()
     * process has not been completed.
     */
    protected boolean mSurfaceConfiguring = false;
    public CameraPreview(Activity activity, Camera pCamera, Size.LayoutMode mode) {
        super(activity);    // Always necessary
        mActivity = activity;
        initSensor();

        mLayoutMode = mode;
        mHolder = getHolder();
        mHolder.addCallback(this);

        mCamera = pCamera;
        mCamera.setDisplayOrientation(90);
        cameraParams = mCamera.getParameters();
        mPreviewSizeList = cameraParams.getSupportedPreviewSizes();
        mPictureSizeList = cameraParams.getSupportedPictureSizes();
        mVideoSizeList = cameraParams.getSupportedVideoSizes();

        isFrontCamera = true;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.e(TAG, "Surface created");
        if (mCamera == null) {
            mCamera = Camera.open();
            try {
                mCamera.setPreviewDisplay(holder);
                mCamera.startPreview();
            } catch (IOException e) {
                mCamera.release();
                mCamera = null;
            }
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        mSurfaceChangedCallDepth++;
        doSurfaceChanged(width, height);
        mSurfaceChangedCallDepth--;
    }

    private void doSurfaceChanged(int width, int height) {
        if (mHolder.getSurface() == null) {
            // preview surface does not exist
            return;
        }

        // stop preview before making changes
        try {
            mCamera.stopPreview();
        } catch (Exception e) {
            // ignore: tried to stop a non-existent preview
        }

        boolean portrait = isPortrait();

        if (!mSurfaceConfiguring) {
            Camera.Size previewSize = getOptimalPreviewSize(mPreviewSizeList, CameraHelper.CameraSettings.getFrameSizeHeight(), CameraHelper.CameraSettings.getFrameSizeWidth());//, CameraHelper.CameraSettings.getFrameSizeHeight());
            Camera.Size pictureSize = getOptimalPreviewSize(mPreviewSizeList, CameraHelper.CameraSettings.getFrameSizeHeight(), CameraHelper.CameraSettings.getFrameSizeWidth());//, CameraHelper.CameraSettings.getFrameSizeHeight());

            if (DEBUGGING) { Log.v(LOG_TAG, "Maximum Preview Size - w: " + width + ", h: " + height); }
            mPreviewSize = previewSize;
            mPictureSize = pictureSize;
            mSurfaceConfiguring = adjustSurfaceLayoutSize(previewSize, portrait, width, height);
            // Continue executing this method if this method is called recursively.
            // Recursive call of surfaceChanged is very special case, which is a path from
            // the catch clause at the end of this method.
            // The later part of this method should be executed as well in the recursive
            // invocation of this method, because the layout change made in this recursive
            // call will not trigger another invocation of this method.
            if (mSurfaceConfiguring && (mSurfaceChangedCallDepth <= 1)) {
                return;
            }
        }

        configureCameraParameters(cameraParams, isFrontCamera);
        mSurfaceConfiguring = false;

        try {
            mCamera.setPreviewDisplay(mHolder);
            mCamera.startPreview();
        } catch (Exception e) {
            Log.w(LOG_TAG, "Failed to start preview: " + e.getMessage());

            // Remove failed size
            mPreviewSizeList.remove(mPreviewSize);
            mPreviewSize = null;

            // Reconfigure
            if (mPreviewSizeList.size() > 0) {          // prevent infinite loop
                surfaceChanged(null, 0, width, height);
            } else {
                Log.w(LOG_TAG, "Gave up starting preview");
            }
        }

        if (null != mPreviewReadyCallback) {
            mPreviewReadyCallback.onPreviewReady();
        }
        if (answerRecordHandler == null) {}  //The case when we are in the question mode
        else
            answerRecordHandler.sendMessage(new Message());
    }

    protected boolean adjustSurfaceLayoutSize(Camera.Size previewSize, boolean portrait, int availableWidth, int availableHeight) {
        float tmpLayoutHeight, tmpLayoutWidth;
        if (portrait) {
            tmpLayoutHeight = previewSize.width;
            tmpLayoutWidth = previewSize.height;
        } else {
            tmpLayoutHeight = previewSize.height;
            tmpLayoutWidth = previewSize.width;
        }

        int layoutHeight,layoutWidth;
        float factH, factW, fact, decidedratio, availableratio;
        decidedratio = tmpLayoutWidth/tmpLayoutHeight;
        availableratio = (float) availableWidth / (float) availableHeight;
        factH = availableHeight / tmpLayoutHeight;
        factW = availableWidth / tmpLayoutWidth;
        if (mLayoutMode == Size.LayoutMode.FitToParent) {
            // Select smaller factor, because the surface cannot be set to the size larger than display metrics.
            if (factH < factW)
                fact = factH;
            else
                fact = factW;

            layoutHeight = (int) (tmpLayoutHeight * fact);
            layoutWidth =  (int) (tmpLayoutWidth * fact);
            if (DEBUGGING) {
                Log.v(LOG_TAG, "Preview Layout Size - w: " + layoutWidth + ", h: " + layoutHeight);
                Log.v(LOG_TAG, "Scale factor: " + fact);
            }

        } else {
            int delta;
            if (decidedratio > availableratio) {
                layoutHeight =  availableHeight;
                layoutWidth = (int) (availableHeight*decidedratio);
                delta = layoutWidth - availableWidth;
                mCenterPosX = mCenterPosX - delta/2;
            } else {
                layoutWidth = availableWidth;
                layoutHeight = (int) (availableWidth/decidedratio);
                delta = layoutHeight - availableHeight;
                mCenterPosY = mCenterPosY - delta/2;
            }
        }

        RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams)this.getLayoutParams();

        boolean layoutChanged;
        if ((layoutWidth != this.getWidth()) || (layoutHeight != this.getHeight())) {
            layoutParams.height = layoutHeight;
            layoutParams.width = layoutWidth;
            if (mCenterPosX >= 0) {
                layoutParams.topMargin = mCenterPosY - (layoutHeight / 2);
                layoutParams.leftMargin = mCenterPosX - (layoutWidth / 2);
            }
            layoutParams.height = layoutHeight;
            layoutParams.width = layoutWidth;
            this.setLayoutParams(layoutParams);     // this will trigger another surfaceChanged invocation.
            layoutChanged = true;
        } else {
            layoutChanged = false;
        }

        return layoutChanged;
    }

    /**
     * @param x X coordinate of center position on the screen. Set to negative value to unset.
     * @param y Y coordinate of center position on the screen.
     */
    public void setCenterPosition(int x, int y) {
        mCenterPosX = x;
        mCenterPosY = y;
    }

    public Camera switchCamera(int cameraid, DoneCallback doneCallback) {

        surfaceDestroyed(mHolder);

        if(cameraid == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            mCamera = getFrontFacingCamera();
            isFrontCamera = true;
        } else {
            mCamera = getBackFacingCamera();
            isFrontCamera = false;
        }

        if(mCamera != null) {
            configureCameraParameters(mCamera.getParameters(), isFrontCamera);

            try {
                mCamera.setPreviewDisplay(mHolder);
                mCamera.startPreview();
                doneCallback.done(new String[]{"COMPLETED", "Camera switched."});
            } catch (IOException e) {
                Log.e(TAG, "exception during switch camera " + e.getMessage());
                surfaceDestroyed(mHolder);
                e.printStackTrace();
            }
        } else {
            if(isFrontCamera)
                return getBackFacingCamera();
            else
                return getFrontFacingCamera();
        }
        return mCamera;
    }

    protected void configureCameraParameters(Camera.Parameters cameraParams, boolean isFrontCamera) {

        int rotationFromInfo = 90;

        if(isFrontCamera) {
            try {
                android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
                android.hardware.Camera.getCameraInfo(Camera.CameraInfo.CAMERA_FACING_FRONT, info);
                rotationFromInfo = info.orientation;
            } catch (Exception ex) {
                Log.d(TAG, "exception. impossible to access to camera.");
            }
        } else {
            try {
                android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
                android.hardware.Camera.getCameraInfo(Camera.CameraInfo.CAMERA_FACING_BACK, info);
                rotationFromInfo = info.orientation;
            } catch (Exception ex) {
                Log.d(TAG, "exception. impossible to access to camera.");
            }
        }

        int rotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }
        Log.d(TAG, "degrees: " + degrees);

        int result = (rotationFromInfo + degrees) % 360;
        List<String> focusModes = cameraParams.getSupportedFocusModes();

        if(isFrontCamera)
            result = (360 - result) % 360;           // compensate the mirror
        else {
            if(focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                cameraParams.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
                Log.d(TAG, "FOCUS MODE CONTINUOUS VIDEO");
            } else if(focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                cameraParams.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                Log.d(TAG, "FOCUS MODE CONTINUOUS PICTURE");
            } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                cameraParams.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                mCamera.autoFocus(null);
                Log.d(TAG, "FOCUS MODE AUTO");
            }
        }

        mCamera.setDisplayOrientation(result);
        cameraParams.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
        cameraParams.setPictureSize(mPictureSize.width, mPictureSize.height);
        if (DEBUGGING) {
            Log.v(LOG_TAG, "Preview Actual Size - w: " + mPreviewSize.width + ", h: " + mPreviewSize.height);
            Log.v(LOG_TAG, "Picture Actual Size - w: " + mPictureSize.width + ", h: " + mPictureSize.height);
        }

        mCamera.setParameters(cameraParams);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        this.getHolder().removeCallback(this);
        if (mCamera != null) {
            Log.e(TAG, "Surface destroyed");
            mCamera.stopPreview();
            mCamera.setPreviewCallback(null);
            mCamera.release();
            mCamera = null;
        }
    }

    public boolean isPortrait() {
        return (mActivity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT);
    }


    public static Camera getFrontFacingCamera() throws NoSuchElementException {
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        for (int cameraIndex = 0; cameraIndex < Camera.getNumberOfCameras(); cameraIndex++) {
            Camera.getCameraInfo(cameraIndex, cameraInfo);
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                try {
                    return Camera.open(cameraIndex);
                } catch (RuntimeException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    public static Camera getBackFacingCamera() throws NoSuchElementException {
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        for (int cameraIndex = 0; cameraIndex < Camera.getNumberOfCameras(); cameraIndex++) {
            Camera.getCameraInfo(cameraIndex, cameraInfo);
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                try {
                    return Camera.open(cameraIndex);
                } catch (RuntimeException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    public void doTouchFocus(final Rect tfocusRect, final DoneCallback<String> doneCallback) {
        try {
            Camera.Parameters params = mCamera.getParameters();
            List<String> supportedFocusModes = params.getSupportedFocusModes();
            if (supportedFocusModes != null && supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                List<Camera.Area> focusList = new ArrayList<>();
                Camera.Area focusArea = new Camera.Area(tfocusRect, 1000);
                params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                focusList.add(focusArea);
                params.setFocusAreas(focusList);
                params.setMeteringAreas(focusList);
                mCamera.setParameters(params);
                mCamera.autoFocus(new Camera.AutoFocusCallback() {
                    @Override
                    public void onAutoFocus(boolean success, Camera camera) {
                        doneCallback.done("DONE");
                    }
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.i(TAG, "Unable to autofocus");
        }
    }

    public boolean setFocusBound(float x, float y) {
        int left   = (int) (x - FOCUS_SQR_SIZE / 2);
        int right  = (int) (x + FOCUS_SQR_SIZE / 2);
        int top    = (int) (y - FOCUS_SQR_SIZE / 2);
        int bottom = (int) (y + FOCUS_SQR_SIZE / 2);

        if (FOCUS_MIN_BOUND > left   || left   > FOCUS_MAX_BOUND) return false;
        if (FOCUS_MIN_BOUND > right  || right  > FOCUS_MAX_BOUND) return false;
        if (FOCUS_MIN_BOUND > top    || top    > FOCUS_MAX_BOUND) return false;
        if (FOCUS_MIN_BOUND > bottom || bottom > FOCUS_MAX_BOUND) return false;

        return true;
    }

} //end