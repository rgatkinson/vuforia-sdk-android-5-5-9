/*===============================================================================
Copyright (c) 2016 PTC Inc. All Rights Reserved.


Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other 
countries.
===============================================================================*/

package com.vuforia.samples.VuforiaSamples.app.ImageTargets;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.AndroidException;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.CheckBox;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.Toast;

import com.vuforia.CameraDevice;
import com.vuforia.DataSet;
import com.vuforia.ObjectTracker;
import com.vuforia.State;
import com.vuforia.STORAGE_TYPE;
import com.vuforia.Trackable;
import com.vuforia.Tracker;
import com.vuforia.TrackerManager;
import com.vuforia.Vuforia;
import com.vuforia.samples.SampleApplication.SampleApplicationControl;
import com.vuforia.samples.SampleApplication.SampleApplicationException;
import com.vuforia.samples.SampleApplication.SampleApplicationSession;
import com.vuforia.samples.SampleApplication.utils.LoadingDialogHandler;
import com.vuforia.samples.SampleApplication.utils.SampleApplicationGLView;
import com.vuforia.samples.SampleApplication.utils.Texture;
import com.vuforia.samples.VuforiaSamples.R;
import com.vuforia.samples.VuforiaSamples.ui.SampleAppMenu.SampleAppMenu;
import com.vuforia.samples.VuforiaSamples.ui.SampleAppMenu.SampleAppMenuGroup;
import com.vuforia.samples.VuforiaSamples.ui.SampleAppMenu.SampleAppMenuInterface;


public class ImageTargets extends Activity implements SampleApplicationControl, SampleAppMenuInterface
    {
    //----------------------------------------------------------------------------------------------
    // State
    //----------------------------------------------------------------------------------------------

    private static final String LOGTAG = "ImageTargets";

    SampleApplicationSession            vuforiaAppSession;

    private DataSet                     currentDataset;
    private int                         mCurrentDatasetSelectionIndex = 0;
    private int                         mStartDatasetsIndex = 0;
    private int                         mDatasetsNumber = 0;

    // Our OpenGL view:
    private SampleApplicationGLView glView;

    // Our renderer:
    private ImageTargetRenderer         imageTargetRenderer;

    private GestureDetector             mGestureDetector;

    private boolean                     switchDatasetAsap = false;
    private boolean                     mFlash              = false;
    private boolean                     continuousAutoFocus = false;
    private boolean                     mExtendedTracking   = false;
    private boolean                     mDisplayEnabled     = true;
    private View                        mFlashOptionView;
    private RelativeLayout uiLayout;
    private SampleAppMenu               mSampleAppMenu;
    LoadingDialogHandler                loadingDialogHandler = new LoadingDialogHandler(this);

    // Alert Dialog used to display SDK errors
    private AlertDialog                 errorDialog;

    boolean                             isDroidDevice = false;

    // The textures we will use for rendering:
    private Vector<Texture>             textures;
    private ArrayList<String>           datasetFileNames = new ArrayList<String>();
    private String[]                    datasetRoots = new String[] {"StonesAndChips", "Tarmac", "FTC"};
    private List<String>                textureNames = Arrays.asList("building", "stones", "chips", "tarmac", "first");


    //----------------------------------------------------------------------------------------------
    // Life cycle
    //----------------------------------------------------------------------------------------------

    // Called when the activity first starts or the user navigates back to an activity.
    @Override protected void onCreate(Bundle savedInstanceState)
        {
        Log.d(LOGTAG, "onCreate");
        super.onCreate(savedInstanceState);

        vuforiaAppSession = new SampleApplicationSession(this);

        startLoadingAnimation();
        for (String s : datasetRoots)
            {
            datasetFileNames.add(s + ".xml");
            }

        vuforiaAppSession.initAR(this, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        mGestureDetector = new GestureDetector(this, new GestureListener());

        // Load any sample specific textures:
        textures = new Vector<Texture>();
        loadTextures();

        isDroidDevice = android.os.Build.MODEL.toLowerCase().startsWith("droid");
        }

    // Process Single Tap event to trigger autofocus
    private class GestureListener extends GestureDetector.SimpleOnGestureListener
        {
        // Used to set autofocus one second after a manual focus is triggered
        private final Handler autofocusHandler = new Handler();

        @Override public boolean onDown(MotionEvent e)
            {
            return true;
            }

        @Override public boolean onSingleTapUp(MotionEvent e)
            {
            // Generates a Handler to trigger autofocus // after 1 second
            autofocusHandler.postDelayed(new Runnable()
                {
                public void run()
                    {
                    boolean result = CameraDevice.getInstance().setFocusMode(CameraDevice.FOCUS_MODE.FOCUS_MODE_TRIGGERAUTO);

                    if (!result)
                        Log.e("SingleTapUp", "Unable to trigger focus");
                    }
                }, 1000L);

            return true;
            }
        }

    // We want to load specific textures from the APK, which we will later use for rendering.
    private void loadTextures()
        {
        textures.add(Texture.loadTextureFromApk("ImageTargets/Buildings.jpeg", getAssets()));
        textures.add(Texture.loadTextureFromApk("TextureTeapotBrass.png", getAssets()));
        textures.add(Texture.loadTextureFromApk("TextureTeapotBlue.png", getAssets()));
        textures.add(Texture.loadTextureFromApk("TextureTeapotRed.png", getAssets()));
        textures.add(Texture.loadTextureFromApk("TextureTeapotBrass.png", getAssets()));
        }

    // Called when the activity will start interacting with the user.
    @Override protected void onResume()
        {
        Log.d(LOGTAG, "onResume");
        super.onResume();

        // This is needed for some Droid devices to force portrait
        if (isDroidDevice)
            {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            }

        try
            {
            vuforiaAppSession.resumeAR();
            }
        catch (SampleApplicationException e)
            {
            Log.e(LOGTAG, e.getString());
            }

        // Resume the GL view:
        if (glView != null)
            {
            glView.setVisibility(View.VISIBLE);
            glView.onResume();
            }
        }

    // Callback for configuration changes the activity handles itself
    @Override
    public void onConfigurationChanged(Configuration config)
        {
        Log.d(LOGTAG, "onConfigurationChanged");
        super.onConfigurationChanged(config);

        vuforiaAppSession.onConfigurationChanged();
        }


    // Called when the system is about to start resuming a previous activity.
    @Override
    protected void onPause()
        {
        Log.d(LOGTAG, "onPause");
        super.onPause();

        if (glView != null)
            {
            glView.setVisibility(View.INVISIBLE);
            glView.onPause();
            }

        // Turn off the flash
        if (mFlashOptionView != null && mFlash)
            {
            // OnCheckedChangeListener is called upon changing the checked state
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
                {
                ((Switch) mFlashOptionView).setChecked(false);
                }
            else
                {
                ((CheckBox) mFlashOptionView).setChecked(false);
                }
            }

        try
            {
            vuforiaAppSession.pauseAR();
            }
        catch (SampleApplicationException e)
            {
            Log.e(LOGTAG, e.getString());
            }
        }


    // The final call you receive before your activity is destroyed.
    @Override
    protected void onDestroy()
        {
        Log.d(LOGTAG, "onDestroy");
        super.onDestroy();

        try
            {
            vuforiaAppSession.stopAR();
            }
        catch (SampleApplicationException e)
            {
            Log.e(LOGTAG, e.getString());
            }

        // Unload texture:
        textures.clear();
        textures = null;

        System.gc();
        }

    // Initializes AR application components.
    private void initApplicationAR()
        {
        // Create OpenGL ES view:
        int depthSize = 16;
        int stencilSize = 0;
        boolean translucent = Vuforia.requiresAlpha();

        glView = new SampleApplicationGLView(this);  // xyzzy
        glView.init(translucent, depthSize, stencilSize);

        imageTargetRenderer = new ImageTargetRenderer(this, vuforiaAppSession);

        imageTargetRenderer.setTextures(textures, textureNames);
        glView.setRenderer(imageTargetRenderer);
        }

    private void startLoadingAnimation()
        {
        uiLayout = (RelativeLayout) View.inflate(this, R.layout.camera_overlay, null);

        uiLayout.setVisibility(View.VISIBLE);
        uiLayout.setBackgroundColor(Color.BLACK);

        // Gets a reference to the loading dialog
        loadingDialogHandler.loadingDialogContainer = uiLayout.findViewById(R.id.loading_indicator);

        // Shows the loading indicator at start
        loadingDialogHandler.sendEmptyMessage(LoadingDialogHandler.SHOW_LOADING_DIALOG);

        // Adds the inflated layout to the view
        addContentView(uiLayout, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        }

    // Methods to load and destroy tracking data.
    @Override
    public boolean doLoadTrackersData()
        {
        TrackerManager tManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) tManager.getTracker(ObjectTracker.getClassType());
        if (objectTracker == null)
            return false;

        if (currentDataset == null)
            currentDataset = objectTracker.createDataSet();

        if (currentDataset == null)
            return false;

        if (!currentDataset.load(datasetFileNames.get(mCurrentDatasetSelectionIndex), STORAGE_TYPE.STORAGE_APPRESOURCE))
            return false;

        if (!objectTracker.activateDataSet(currentDataset))
            return false;

        int numTrackables = currentDataset.getNumTrackables();
        for (int count = 0; count < numTrackables; count++)
            {
            Trackable trackable = currentDataset.getTrackable(count);
            if (isExtendedTrackingActive())
                {
                trackable.startExtendedTracking();
                }

            String name = "Current Dataset : " + trackable.getName();
            trackable.setUserData(name);
            Log.d(LOGTAG, "UserData:Set the following user data " + (String) trackable.getUserData());
            }

        return true;
        }


    @Override
    public boolean doUnloadTrackersData()
        {
        // Indicate if the trackers were unloaded correctly
        boolean result = true;

        TrackerManager tManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) tManager.getTracker(ObjectTracker.getClassType());
        if (objectTracker == null)
            return false;

        if (currentDataset != null && currentDataset.isActive())
            {
            if (objectTracker.getActiveDataSet().equals(currentDataset) && !objectTracker.deactivateDataSet(currentDataset))
                {
                result = false;
                }
            else if (!objectTracker.destroyDataSet(currentDataset))
                {
                result = false;
                }

            currentDataset = null;
            }

        return result;
        }


    @Override
    public void onInitARDone(SampleApplicationException exception)
        {
        if (exception == null)
            {
            initApplicationAR();

            imageTargetRenderer.isActive = true;

            // Now add the GL surface view. It is important
            // that the OpenGL ES surface view gets added
            // BEFORE the camera is started and video
            // background is configured.
            addContentView(glView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

            // Sets the UILayout to be drawn in front of the camera
            uiLayout.bringToFront();

            // Sets the layout background to transparent
            uiLayout.setBackgroundColor(Color.TRANSPARENT);

            try
                {
                vuforiaAppSession.startAR(CameraDevice.CAMERA_DIRECTION.CAMERA_DIRECTION_DEFAULT);
                }
            catch (SampleApplicationException e)
                {
                Log.e(LOGTAG, e.getString());
                }

            boolean result = CameraDevice.getInstance().setFocusMode(CameraDevice.FOCUS_MODE.FOCUS_MODE_CONTINUOUSAUTO);

            if (result)
                continuousAutoFocus = true;
            else
                Log.e(LOGTAG, "Unable to enable continuous autofocus");

            mSampleAppMenu = new SampleAppMenu(this, this, "Image Targets", glView, uiLayout, null);
            setSampleAppMenuSettings();
            }
        else
            {
            Log.e(LOGTAG, exception.getString());
            showInitializationErrorMessage(exception.getString());
            }
        }


    // Shows initialization error messages as System dialogs
    public void showInitializationErrorMessage(String message)
        {
        final String errorMessage = message;
        runOnUiThread(new Runnable()
            {
            public void run()
                {
                if (errorDialog != null)
                    {
                    errorDialog.dismiss();
                    }

                // Generates an Alert Dialog to show the error message
                AlertDialog.Builder builder = new AlertDialog.Builder(
                        ImageTargets.this);
                builder
                        .setMessage(errorMessage)
                        .setTitle(getString(R.string.INIT_ERROR))
                        .setCancelable(false)
                        .setIcon(0)
                        .setPositiveButton(getString(R.string.button_OK),
                                new DialogInterface.OnClickListener()
                                    {
                                    public void onClick(DialogInterface dialog, int id)
                                        {
                                        finish();
                                        }
                                    });

                errorDialog = builder.create();
                errorDialog.show();
                }
            });
        }

    @Override
    public void onVuforiaUpdate(State state)
        {
        // TODO: report tracking

        if (switchDatasetAsap)
            {
            switchDatasetAsap = false;
            TrackerManager tm = TrackerManager.getInstance();
            ObjectTracker ot = (ObjectTracker) tm.getTracker(ObjectTracker.getClassType());
            if (ot == null || currentDataset == null || ot.getActiveDataSet() == null)
                {
                Log.d(LOGTAG, "Failed to swap datasets");
                return;
                }

            doUnloadTrackersData();
            doLoadTrackersData();
            }
        }

    @Override
    public boolean doInitTrackers()
        {
        // Indicate if the trackers were initialized correctly
        boolean result = true;

        TrackerManager tManager = TrackerManager.getInstance();
        Tracker tracker;

        // Trying to initialize the image tracker
        tracker = tManager.initTracker(ObjectTracker.getClassType());
        if (tracker == null)
            {
            Log.e(LOGTAG, "Tracker not initialized. Tracker already initialized or the camera is already started");
            result = false;
            }
        else
            {
            Log.i(LOGTAG, "Tracker successfully initialized");
            }
        return result;
        }


    @Override
    public boolean doStartTrackers()
        {
        // Indicate if the trackers were started correctly
        boolean result = true;

        Tracker objectTracker = TrackerManager.getInstance().getTracker(ObjectTracker.getClassType());
        if (objectTracker != null)
            objectTracker.start();

        return result;
        }


    @Override
    public boolean doStopTrackers()
        {
        // Indicate if the trackers were stopped correctly
        boolean result = true;

        Tracker objectTracker = TrackerManager.getInstance().getTracker(ObjectTracker.getClassType());
        if (objectTracker != null)
            objectTracker.stop();

        return result;
        }


    @Override
    public boolean doDeinitTrackers()
        {
        // Indicate if the trackers were deinitialized correctly
        boolean result = true;

        TrackerManager tManager = TrackerManager.getInstance();
        tManager.deinitTracker(ObjectTracker.getClassType());

        return result;
        }


    @Override
    public boolean onTouchEvent(MotionEvent event)
        {
        // Process the Gestures
        if (mSampleAppMenu != null && mSampleAppMenu.processEvent(event))
            return true;

        return mGestureDetector.onTouchEvent(event);
        }


    boolean isExtendedTrackingActive()
        {
        return mExtendedTracking;
        }

    final public static int CMD_BACK = -1;
    final public static int CMD_EXTENDED_TRACKING = 1;
    final public static int CMD_AUTOFOCUS = 2;
    final public static int CMD_FLASH = 3;
    final public static int CMD_CAMERA_FRONT = 4;
    final public static int CMD_CAMERA_REAR = 5;
    final public static int CMD_ENABLE_DISPLAY = 6;
    final public static int CMD_DATASET_START_INDEX = 7;


    // This method sets the menu's settings
    private void setSampleAppMenuSettings()
        {
        SampleAppMenuGroup group;

        group = mSampleAppMenu.addGroup("", false);
        group.addTextItem(getString(R.string.menu_back), -1);

        group = mSampleAppMenu.addGroup("", true);
        group.addSelectionItem(getString(R.string.menu_extended_tracking),  CMD_EXTENDED_TRACKING,  false);
        group.addSelectionItem(getString(R.string.menu_contAutofocus),      CMD_AUTOFOCUS, continuousAutoFocus);
        group.addSelectionItem(getString(R.string.menu_enable_display),     CMD_ENABLE_DISPLAY,     mDisplayEnabled);
        mFlashOptionView = group.addSelectionItem(getString(R.string.menu_flash), CMD_FLASH, false);

        CameraInfo ci = new CameraInfo();
        boolean deviceHasFrontCamera = false;
        boolean deviceHasBackCamera = false;
        for (int i = 0; i < Camera.getNumberOfCameras(); i++)
            {
            Camera.getCameraInfo(i, ci);
            if (ci.facing == CameraInfo.CAMERA_FACING_FRONT)
                deviceHasFrontCamera = true;
            else if (ci.facing == CameraInfo.CAMERA_FACING_BACK)
                deviceHasBackCamera = true;
            }

        if (deviceHasBackCamera && deviceHasFrontCamera)
            {
            int numberOfCameras = Camera.getNumberOfCameras();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                {
                try {
                    CameraManager cameraManager = (CameraManager)this.getSystemService(CAMERA_SERVICE);
                    numberOfCameras = cameraManager.getCameraIdList().length;
                    }
                catch (AndroidException ignored)
                    {
                    }
                }

            group = mSampleAppMenu.addGroup(String.format("%s (#=%d)", getString(R.string.menu_camera), numberOfCameras), true);
            group.addRadioItem(getString(R.string.menu_camera_front), CMD_CAMERA_FRONT, false);
            group.addRadioItem(getString(R.string.menu_camera_back), CMD_CAMERA_REAR, true);
            }

        group = mSampleAppMenu.addGroup(getString(R.string.menu_datasets), true);
        mStartDatasetsIndex = CMD_DATASET_START_INDEX;
        mDatasetsNumber = datasetFileNames.size();

        group.addRadioItem("Stones & Chips", mStartDatasetsIndex, true);
        group.addRadioItem("Tarmac",         mStartDatasetsIndex + 1, false);
        group.addRadioItem("FIRST",          mStartDatasetsIndex + 2, false);

        mSampleAppMenu.attachMenu();
        }

    @Override
    public boolean menuProcess(int command)
        {
        boolean result = true;

        switch (command)
            {
            case CMD_BACK:
                finish();
                break;

            case CMD_FLASH:
                result = CameraDevice.getInstance().setFlashTorchMode(!mFlash);

                if (result)
                    {
                    mFlash = !mFlash;
                    }
                else
                    {
                    showToast(getString(mFlash ? R.string.menu_flash_error_off : R.string.menu_flash_error_on));
                    Log.e(LOGTAG, getString(mFlash ? R.string.menu_flash_error_off : R.string.menu_flash_error_on));
                    }
                break;

            case CMD_AUTOFOCUS:

                if (continuousAutoFocus)
                    {
                    result = CameraDevice.getInstance().setFocusMode(CameraDevice.FOCUS_MODE.FOCUS_MODE_NORMAL);

                    if (result)
                        {
                        continuousAutoFocus = false;
                        }
                    else
                        {
                        showToast(getString(R.string.menu_contAutofocus_error_off));
                        Log.e(LOGTAG, getString(R.string.menu_contAutofocus_error_off));
                        }
                    }
                else
                    {
                    result = CameraDevice.getInstance().setFocusMode(CameraDevice.FOCUS_MODE.FOCUS_MODE_CONTINUOUSAUTO);

                    if (result)
                        {
                        continuousAutoFocus = true;
                        }
                    else
                        {
                        showToast(getString(R.string.menu_contAutofocus_error_on));
                        Log.e(LOGTAG, getString(R.string.menu_contAutofocus_error_on));
                        }
                    }

                break;

            case CMD_ENABLE_DISPLAY:
                {
                if (mDisplayEnabled)
                    {
                    if (imageTargetRenderer.setDisplayEnabled(false))
                        {
                        mDisplayEnabled = false;
                        }
                    }
                else
                    {
                    if (imageTargetRenderer.setDisplayEnabled(true))
                        {
                        mDisplayEnabled = true;
                        }
                    }
                }
                break;


            case CMD_CAMERA_FRONT:
            case CMD_CAMERA_REAR:

                // Turn off the flash
                if (mFlashOptionView != null && mFlash)
                    {
                    // OnCheckedChangeListener is called upon changing the checked state
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
                        {
                        ((Switch) mFlashOptionView).setChecked(false);
                        }
                    else
                        {
                        ((CheckBox) mFlashOptionView).setChecked(false);
                        }
                    }

                vuforiaAppSession.stopCamera();

                try
                    {
                    vuforiaAppSession.startAR(command == CMD_CAMERA_FRONT
                            ? CameraDevice.CAMERA_DIRECTION.CAMERA_DIRECTION_FRONT
                            : CameraDevice.CAMERA_DIRECTION.CAMERA_DIRECTION_BACK);
                    }
                catch (SampleApplicationException e)
                    {
                    showToast(e.getString());
                    Log.e(LOGTAG, e.getString());
                    result = false;
                    }
                doStartTrackers();
                break;

            case CMD_EXTENDED_TRACKING:
                for (int tIdx = 0; tIdx < currentDataset.getNumTrackables(); tIdx++)
                    {
                    Trackable trackable = currentDataset.getTrackable(tIdx);

                    if (!mExtendedTracking)
                        {
                        if (!trackable.startExtendedTracking())
                            {
                            Log.e(LOGTAG, "Failed to start extended tracking target");
                            result = false;
                            }
                        else
                            {
                            Log.d(LOGTAG, "Successfully started extended tracking target");
                            }
                        }
                    else
                        {
                        if (!trackable.stopExtendedTracking())
                            {
                            Log.e(LOGTAG, "Failed to stop extended tracking target");
                            result = false;
                            }
                        else
                            {
                            Log.d(LOGTAG, "Successfully started extended tracking target");
                            }
                        }
                    }

                if (result)
                    mExtendedTracking = !mExtendedTracking;

                break;

            default:
                if (command >= mStartDatasetsIndex && command < mStartDatasetsIndex + mDatasetsNumber)
                    {
                    switchDatasetAsap = true;
                    mCurrentDatasetSelectionIndex = command - mStartDatasetsIndex;
                    }
                break;
            }

        return result;
        }

    private void showToast(String text)
        {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
        }
    }
