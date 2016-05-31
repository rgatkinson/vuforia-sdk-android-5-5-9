/*===============================================================================
Copyright (c) 2016 PTC Inc. All Rights Reserved.

Copyright (c) 2012-2015 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other 
countries.
===============================================================================*/


package com.vuforia.samples.SampleApplication;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.AsyncTask;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.WindowManager;

import com.vuforia.CameraCalibration;
import com.vuforia.CameraDevice;
import com.vuforia.Matrix44F;
import com.vuforia.Renderer;
import com.vuforia.State;
import com.vuforia.Tool;
import com.vuforia.Vec2I;
import com.vuforia.VideoBackgroundConfig;
import com.vuforia.VideoMode;
import com.vuforia.Vuforia;
import com.vuforia.Vuforia.UpdateCallbackInterface;
import com.vuforia.samples.VuforiaSamples.R;


public class SampleApplicationSession implements UpdateCallbackInterface
    {
    //----------------------------------------------------------------------------------------------
    // State
    //----------------------------------------------------------------------------------------------

    private static final String LOGTAG = "Vuforia_Sample_Apps";

    // Reference to the current activity
    private Activity                    activity;
    private SampleApplicationControl    sessionControl;

    // Flags
    private boolean                     isStarted = false;
    private boolean                     isCameraRunning = false;

    // Display size of the device:
    private int                         screenWidth = 0;
    private int                         screenHeight = 0;

    // The async tasks to initialize the Vuforia SDK:
    private InitVuforiaTask             initVuforiaTask;
    private LoadTrackerTask             loadTrackerTask;

    // An object used for synchronizing Vuforia initialization, dataset loading
    // and the Android onDestroy() life cycle event. If the application is
    // destroyed while a data set is still being loaded, then we wait for the
    // loading operation to finish before shutting down Vuforia:
    private final Object                shutdownLock = new Object();

    // Vuforia initialization flags:
    private int vuforiaFlags = 0;

    // Holds the camera configuration to use upon resuming
    private int                         mCamera = CameraDevice.CAMERA_DIRECTION.CAMERA_DIRECTION_DEFAULT;

    // Stores the projection matrix to use for rendering purposes
    private Matrix44F                   projectionMatrix;

    // Stores viewport to be used for rendering purposes
    private int[]                       viewport;

    // Stores orientation
    private boolean                     isPortrait = false;

    //----------------------------------------------------------------------------------------------
    // Construction
    //----------------------------------------------------------------------------------------------

    public SampleApplicationSession(SampleApplicationControl sessionControl)
        {
        this.sessionControl = sessionControl;
        }

    // Initializes Vuforia and sets up preferences.
    public void initAR(Activity activity, int screenOrientation)
        {
        SampleApplicationException vuforiaException = null;
        this.activity = activity;

        if ((screenOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR) && (Build.VERSION.SDK_INT > Build.VERSION_CODES.FROYO))
            screenOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR;

        // Use an OrientationChangeListener here to capture all orientation changes.  Android
        // will not send an Activity.onConfigurationChanged() callback on a 180 degree rotation,
        // ie: Left Landscape to Right Landscape.  Vuforia needs to react to this change and the
        // SampleApplicationSession needs to update the Projection Matrix.
        OrientationEventListener orientationEventListener = new OrientationEventListener(SampleApplicationSession.this.activity)
            {
            @Override
            public void onOrientationChanged(int i)
                {
                int activityRotation = SampleApplicationSession.this.activity.getWindowManager().getDefaultDisplay().getRotation();
                if (mLastRotation != activityRotation)
                    {
                    // Signal the ApplicationSession to refresh the projection matrix
                    setProjectionMatrix();
                    mLastRotation = activityRotation;
                    }
                }

            int mLastRotation = -1;
            };

        if (orientationEventListener.canDetectOrientation())
            orientationEventListener.enable();

        // Apply screen orientation
        this.activity.setRequestedOrientation(screenOrientation);

        updateActivityOrientation();

        // Query display dimensions:
        storeScreenDimensions();

        // As long as this window is visible to the user, keep the device's screen turned on and bright:
        this.activity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        vuforiaFlags = Vuforia.GL_20;

        // Initialize Vuforia SDK asynchronously to avoid blocking the main (UI) thread.
        //
        // NOTE: This task instance must be created and invoked on the UI thread and it can be executed only once!
        if (initVuforiaTask != null)
            {
            String logMessage = "Cannot initialize SDK twice";
            vuforiaException = new SampleApplicationException(SampleApplicationException.VUFORIA_ALREADY_INITIALIZATED, logMessage);
            Log.e(LOGTAG, logMessage);
            }

        if (vuforiaException == null)
            {
            try
                {
                initVuforiaTask = new InitVuforiaTask();
                initVuforiaTask.execute();
                }
            catch (Exception e)
                {
                String logMessage = "Initializing Vuforia SDK failed";
                vuforiaException = new SampleApplicationException(SampleApplicationException.INITIALIZATION_FAILURE, logMessage);
                Log.e(LOGTAG, logMessage);
                }
            }

        if (vuforiaException != null)
            sessionControl.onInitARDone(vuforiaException);
        }


    // Starts Vuforia, initialize and starts the camera and start the trackers
    public void startAR(int camera) throws SampleApplicationException
        {
        String error;
        if (isCameraRunning)
            {
            error = "Camera already running, unable to open again";
            Log.e(LOGTAG, error);
            throw new SampleApplicationException(SampleApplicationException.CAMERA_INITIALIZATION_FAILURE, error);
            }

        mCamera = camera;
        if (!CameraDevice.getInstance().init(camera))
            {
            error = "Unable to open camera device: " + camera;
            Log.e(LOGTAG, error);
            throw new SampleApplicationException(SampleApplicationException.CAMERA_INITIALIZATION_FAILURE, error);
            }

        if (!CameraDevice.getInstance().selectVideoMode(CameraDevice.MODE.MODE_DEFAULT))
            {
            error = "Unable to set video mode";
            Log.e(LOGTAG, error);
            throw new SampleApplicationException(SampleApplicationException.CAMERA_INITIALIZATION_FAILURE, error);
            }

        // Configure the rendering of the video background
        configureVideoBackground();

        if (!CameraDevice.getInstance().start())
            {
            error = "Unable to start camera device: " + camera;
            Log.e(LOGTAG, error);
            throw new SampleApplicationException(SampleApplicationException.CAMERA_INITIALIZATION_FAILURE, error);
            }

        setProjectionMatrix();

        sessionControl.doStartTrackers();

        isCameraRunning = true;

        if (!CameraDevice.getInstance().setFocusMode(CameraDevice.FOCUS_MODE.FOCUS_MODE_CONTINUOUSAUTO))
            {
            if (!CameraDevice.getInstance().setFocusMode(CameraDevice.FOCUS_MODE.FOCUS_MODE_TRIGGERAUTO))
                CameraDevice.getInstance().setFocusMode(CameraDevice.FOCUS_MODE.FOCUS_MODE_NORMAL);
            }
        }

    // Stops any ongoing initialization, stops Vuforia
    public void stopAR() throws SampleApplicationException
        {
        // Cancel potentially running tasks
        if (initVuforiaTask != null && initVuforiaTask.getStatus() != InitVuforiaTask.Status.FINISHED)
            {
            initVuforiaTask.cancel(true);
            initVuforiaTask = null;
            }

        if (loadTrackerTask != null && loadTrackerTask.getStatus() != LoadTrackerTask.Status.FINISHED)
            {
            loadTrackerTask.cancel(true);
            loadTrackerTask = null;
            }

        initVuforiaTask = null;
        loadTrackerTask = null;

        isStarted = false;

        stopCamera();

        // Ensure that all asynchronous operations to initialize Vuforia
        // and loading the tracker datasets do not overlap:
        synchronized (shutdownLock)
            {
            boolean unloadTrackersResult;
            boolean deinitTrackersResult;

            // Destroy the tracking data set:
            unloadTrackersResult = sessionControl.doUnloadTrackersData();

            // Deinitialize the trackers:
            deinitTrackersResult = sessionControl.doDeinitTrackers();

            // Deinitialize Vuforia SDK:
            Vuforia.deinit();

            if (!unloadTrackersResult)
                throw new SampleApplicationException(SampleApplicationException.UNLOADING_TRACKERS_FAILURE, "Failed to unload trackers\' data");

            if (!deinitTrackersResult)
                throw new SampleApplicationException(SampleApplicationException.TRACKERS_DEINITIALIZATION_FAILURE, "Failed to deinitialize trackers");
            }
        }

    // Resumes Vuforia, restarts the trackers and the camera
    public void resumeAR() throws SampleApplicationException
        {
        // Vuforia-specific resume operation
        Vuforia.onResume();

        if (isStarted)
            {
            startAR(mCamera);
            }
        }

    // Pauses Vuforia and stops the camera
    public void pauseAR() throws SampleApplicationException
        {
        if (isStarted)
            {
            stopCamera();
            }

        Vuforia.onPause();
        }

    // Gets the projection matrix to be used for rendering
    public Matrix44F getProjectionMatrix()
        {
        return projectionMatrix;
        }

    // Gets the viewport to be used fo rendering
    public int[] getViewport()
        {
        return viewport;
        }

    // Callback called every cycle. "Called by the SDK right after tracking finishes"
    @Override
    public void Vuforia_onUpdate(State s)
        {
        sessionControl.onVuforiaUpdate(s);
        }

    // Manages the configuration changes
    public void onConfigurationChanged()
        {
        updateActivityOrientation();

        storeScreenDimensions();

        if (isARRunning())
            {
            // configure video background
            configureVideoBackground();

            // Update projection matrix:
            setProjectionMatrix();
            }
        }

    // Methods to be called to handle lifecycle
    public void onResume()
        {
        Vuforia.onResume();
        }

    public void onPause()
        {
        Vuforia.onPause();
        }

    public void onSurfaceChanged(int width, int height)
        {
        Vuforia.onSurfaceChanged(width, height);
        }

    public void onSurfaceCreated()
        {
        Vuforia.onSurfaceCreated();
        }

    // An async task to initialize Vuforia asynchronously.
    private class InitVuforiaTask extends AsyncTask<Void, Integer, Boolean>
        {
        // Initialize with invalid value:
        private int initProgress = -1;

        protected Boolean doInBackground(Void... params)
            {
            // Prevent the onDestroy() method to overlap with initialization:
            synchronized (shutdownLock)
                {
                Vuforia.setInitParameters(activity, vuforiaFlags, activity.getString(R.string.vuforia_license_key));

                do  {
                    // Vuforia.init() blocks until an initialization step is
                    // complete, then it proceeds to the next step and reports
                    // progress in percents (0 ... 100%).
                    // If Vuforia.init() returns -1, it indicates an error.
                    // Initialization is done when progress has reached 100%.
                    initProgress = Vuforia.init();

                    // Publish the progress value:
                    publishProgress(initProgress);

                    // We check whether the task has been canceled in the
                    // meantime (by calling AsyncTask.cancel(true)).
                    // and bail out if it has, thus stopping this thread.
                    // This is necessary as the AsyncTask will run to completion
                    // regardless of the status of the component that
                    // started is.
                    }
                while (!isCancelled() && initProgress >= 0 && initProgress < 100);

                return (initProgress > 0);
                }
            }

        protected void onProgressUpdate(Integer... values)
            {
            // Do something with the progress value "values[0]", e.g. update splash screen, progress bar, etc.
            }

        protected void onPostExecute(Boolean successfulInit)
            {
            // Done initializing Vuforia, proceed to next application initialization status:
            SampleApplicationException vuforiaException = null;

            if (successfulInit)
                {
                Log.d(LOGTAG, "InitVuforiaTask.onPostExecute: Vuforia " + "initialization successful");

                boolean initTrackersResult;
                initTrackersResult = sessionControl.doInitTrackers();

                if (initTrackersResult)
                    {
                    try
                        {
                        loadTrackerTask = new LoadTrackerTask();
                        loadTrackerTask.execute();
                        }
                    catch (Exception e)
                        {
                        String logMessage = "Loading tracking data set failed";
                        vuforiaException = new SampleApplicationException(SampleApplicationException.LOADING_TRACKERS_FAILURE, logMessage);
                        Log.e(LOGTAG, logMessage);
                        sessionControl.onInitARDone(vuforiaException);
                        }

                    }
                else
                    {
                    vuforiaException = new SampleApplicationException(SampleApplicationException.TRACKERS_INITIALIZATION_FAILURE, "Failed to initialize trackers");
                    sessionControl.onInitARDone(vuforiaException);
                    }
                }
            else
                {
                String logMessage;

                // NOTE: Check if initialization failed because the device is
                // not supported. At this point the user should be informed
                // with a message.
                logMessage = getInitializationErrorString(initProgress);

                // Log error:
                Log.e(LOGTAG, "InitVuforiaTask.onPostExecute: " + logMessage + " Exiting.");

                // Send Vuforia Exception to the application and call initDone to stop initialization process
                vuforiaException = new SampleApplicationException(SampleApplicationException.INITIALIZATION_FAILURE, logMessage);
                sessionControl.onInitARDone(vuforiaException);
                }
            }
        }

    // An async task to load the tracker data asynchronously.
    private class LoadTrackerTask extends AsyncTask<Void, Integer, Boolean>
        {
        protected Boolean doInBackground(Void... params)
            {
            // Prevent the onDestroy() method to overlap:
            synchronized (shutdownLock)
                {
                // Load the tracker data set:
                return sessionControl.doLoadTrackersData();
                }
            }

        protected void onPostExecute(Boolean result)
            {
            SampleApplicationException vuforiaException = null;

            Log.d(LOGTAG, "LoadTrackerTask.onPostExecute: execution " + (result ? "successful" : "failed"));

            if (!result)
                {
                String logMessage = "Failed to load tracker data."; // Error loading dataset
                Log.e(LOGTAG, logMessage);
                vuforiaException = new SampleApplicationException(SampleApplicationException.LOADING_TRACKERS_FAILURE, logMessage);
                }
            else
                {
                // Hint to the virtual machine that it would be a good time to
                // run the garbage collector:
                //
                // NOTE: This is only a hint. There is no guarantee that the
                // garbage collector will actually be run.
                System.gc();

                Vuforia.registerCallback(SampleApplicationSession.this);

                isStarted = true;
                }

            // Done loading the tracker, update application status, send the
            // exception to check errors
            sessionControl.onInitARDone(vuforiaException);
            }
        }


    // Returns the error message for each error code
    private String getInitializationErrorString(int code)
        {
        if (code == Vuforia.INIT_DEVICE_NOT_SUPPORTED)
            return activity.getString(R.string.INIT_ERROR_DEVICE_NOT_SUPPORTED);
        if (code == Vuforia.INIT_NO_CAMERA_ACCESS)
            return activity.getString(R.string.INIT_ERROR_NO_CAMERA_ACCESS);
        if (code == Vuforia.INIT_LICENSE_ERROR_MISSING_KEY)
            return activity.getString(R.string.INIT_LICENSE_ERROR_MISSING_KEY);
        if (code == Vuforia.INIT_LICENSE_ERROR_INVALID_KEY)
            return activity.getString(R.string.INIT_LICENSE_ERROR_INVALID_KEY);
        if (code == Vuforia.INIT_LICENSE_ERROR_NO_NETWORK_TRANSIENT)
            return activity.getString(R.string.INIT_LICENSE_ERROR_NO_NETWORK_TRANSIENT);
        if (code == Vuforia.INIT_LICENSE_ERROR_NO_NETWORK_PERMANENT)
            return activity.getString(R.string.INIT_LICENSE_ERROR_NO_NETWORK_PERMANENT);
        if (code == Vuforia.INIT_LICENSE_ERROR_CANCELED_KEY)
            return activity.getString(R.string.INIT_LICENSE_ERROR_CANCELED_KEY);
        if (code == Vuforia.INIT_LICENSE_ERROR_PRODUCT_TYPE_MISMATCH)
            return activity.getString(R.string.INIT_LICENSE_ERROR_PRODUCT_TYPE_MISMATCH);
        else
            {
            return activity.getString(R.string.INIT_LICENSE_ERROR_UNKNOWN_ERROR);
            }
        }

    // Stores screen dimensions
    private void storeScreenDimensions()
        {
        // Query display dimensions:
        DisplayMetrics metrics = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        }


    // Stores the orientation depending on the current resources configuration
    private void updateActivityOrientation()
        {
        Configuration config = activity.getResources().getConfiguration();

        switch (config.orientation)
            {
            case Configuration.ORIENTATION_PORTRAIT:
                isPortrait = true;
                break;
            case Configuration.ORIENTATION_LANDSCAPE:
                isPortrait = false;
                break;
            case Configuration.ORIENTATION_UNDEFINED:
            default:
                break;
            }

        Log.i(LOGTAG, "Activity is in " + (isPortrait ? "PORTRAIT" : "LANDSCAPE"));
        }

    // Method for setting / updating the projection matrix for AR content rendering
    public void setProjectionMatrix()
        {
        CameraCalibration camCal = CameraDevice.getInstance().getCameraCalibration();
        projectionMatrix = Tool.getProjectionGL(camCal, 10.0f, 5000.0f);
        }

    public void stopCamera()
        {
        if (isCameraRunning)
            {
            sessionControl.doStopTrackers();
            CameraDevice.getInstance().stop();
            CameraDevice.getInstance().deinit();
            isCameraRunning = false;
            }
        }

    // Configures the video mode and sets offsets for the camera's image
    private void configureVideoBackground()
        {
        CameraDevice cameraDevice = CameraDevice.getInstance();
        VideoMode vm = cameraDevice.getVideoMode(CameraDevice.MODE.MODE_DEFAULT);

        VideoBackgroundConfig config = new VideoBackgroundConfig();
        config.setEnabled(true);
        config.setPosition(new Vec2I(0, 0));

        int xSize = 0, ySize = 0;
        if (isPortrait)
            {
            xSize = (int) (vm.getHeight() * (screenHeight / (float) vm.getWidth()));
            ySize = screenHeight;

            if (xSize < screenWidth)
                {
                xSize = screenWidth;
                ySize = (int) (screenWidth * (vm.getWidth() / (float) vm.getHeight()));
                }
            }
        else
            {
            xSize = screenWidth;
            ySize = (int) (vm.getHeight() * (screenWidth / (float) vm.getWidth()));

            if (ySize < screenHeight)
                {
                xSize = (int) (screenHeight * (vm.getWidth() / (float) vm.getHeight()));
                ySize = screenHeight;
                }
            }

        config.setSize(new Vec2I(xSize, ySize));

        // The Vuforia VideoBackgroundConfig takes the position relative to the
        // centre of the screen, where as the OpenGL glViewport call takes the
        // position relative to the lower left corner
        viewport = new int[4];
        viewport[0] = ((screenWidth - xSize) / 2) + config.getPosition().getData()[0];
        viewport[1] = ((screenHeight - ySize) / 2) + config.getPosition().getData()[1];
        viewport[2] = xSize;
        viewport[3] = ySize;

        Log.i(LOGTAG, "Configure Video Background : Video (" + vm.getWidth()
                + " , " + vm.getHeight() + "), Screen (" + screenWidth + " , "
                + screenHeight + "), mSize (" + xSize + " , " + ySize + ")");

        Renderer.getInstance().setVideoBackgroundConfig(config);
        }

    // Returns true if Vuforia is initialized, the trackers started and the tracker data loaded
    private boolean isARRunning()
        {
        return isStarted;
        }
    }
