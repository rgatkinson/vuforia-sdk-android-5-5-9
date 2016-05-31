/*===============================================================================
Copyright (c) 2016 PTC Inc. All Rights Reserved.

Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other 
countries.
===============================================================================*/

package com.vuforia.samples.VuforiaSamples.app.ImageTargets;

import java.io.IOException;
import java.util.List;
import java.util.Vector;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;

import com.vuforia.Matrix34F;
import com.vuforia.Matrix44F;
import com.vuforia.Renderer;
import com.vuforia.State;
import com.vuforia.Tool;
import com.vuforia.Trackable;
import com.vuforia.TrackableResult;
import com.vuforia.VIDEO_BACKGROUND_REFLECTION;
import com.vuforia.Vuforia;
import com.vuforia.samples.SampleApplication.SampleApplicationSession;
import com.vuforia.samples.SampleApplication.utils.CubeShaders;
import com.vuforia.samples.SampleApplication.utils.LoadingDialogHandler;
import com.vuforia.samples.SampleApplication.utils.SampleApplication3DModel;
import com.vuforia.samples.SampleApplication.utils.SampleUtils;
import com.vuforia.samples.SampleApplication.utils.Teapot;
import com.vuforia.samples.SampleApplication.utils.Texture;


// The renderer class for the ImageTargets sample. 
public class ImageTargetRenderer implements GLSurfaceView.Renderer
    {
    //----------------------------------------------------------------------------------------------
    // State
    //----------------------------------------------------------------------------------------------

    private static final String LOGTAG = "ImageTargetRenderer";

    private SampleApplicationSession    vuforiaAppSession;
    private ImageTargets                activity;
    private Vector<Texture>             textures;
    private List<String>                textureNames;
    private int                         shaderProgramID;
    private int                         vertexHandle;
    private int                         normalHandle;
    private int                         textureCoordHandle;
    private int                         mvpMatrixHandle;
    private int                         texSampler2DHandle;
    private Teapot                      teapot;
    private float                       kBuildingScale = 12.0f;
    private SampleApplication3DModel    buildingsModel;
    private Renderer                    renderer;
    boolean                             isActive = false;
    boolean                             displayEnabled = true;

    private static final float OBJECT_SCALE_FLOAT = 3.0f;

    //----------------------------------------------------------------------------------------------
    // Construction
    //----------------------------------------------------------------------------------------------

    public ImageTargetRenderer(ImageTargets activity, SampleApplicationSession session)
        {
        this.activity = activity;
        vuforiaAppSession = session;
        }

    //----------------------------------------------------------------------------------------------
    // Operations
    //----------------------------------------------------------------------------------------------

    // Called to draw the current frame.
    @Override
    public void onDrawFrame(GL10 gl)
        {
        if (!isActive)
            return;

        // Call our function to render content
        renderFrame();
        }


    // Called when the surface is created or recreated.
    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config)
        {
        Log.d(LOGTAG, "GLRenderer.onSurfaceCreated");

        initRendering();

        // Call Vuforia function to (re)initialize rendering after first use
        // or after OpenGL ES context was lost (e.g. after onPause/onResume):
        vuforiaAppSession.onSurfaceCreated();
        }


    // Called when the surface changed size.
    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height)
        {
        Log.d(LOGTAG, "GLRenderer.onSurfaceChanged");

        // Call Vuforia function to handle render surface size changes:
        vuforiaAppSession.onSurfaceChanged(width, height);
        }

    // Function for initializing the renderer.
    private void initRendering()
        {
        teapot = new Teapot();

        renderer = Renderer.getInstance();

        GLES20.glClearColor(0.0f, 0.0f, 0.0f, Vuforia.requiresAlpha() ? 0.0f : 1.0f);

        for (Texture t : textures)
            {
            GLES20.glGenTextures(1, t.mTextureID, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, t.mTextureID[0]);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, t.mWidth, t.mHeight, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, t.mData);
            }

        shaderProgramID = SampleUtils.createProgramFromShaderSrc(CubeShaders.CUBE_MESH_VERTEX_SHADER, CubeShaders.CUBE_MESH_FRAGMENT_SHADER);

        vertexHandle        = GLES20.glGetAttribLocation(shaderProgramID, "vertexPosition");
        normalHandle        = GLES20.glGetAttribLocation(shaderProgramID, "vertexNormal");
        textureCoordHandle  = GLES20.glGetAttribLocation(shaderProgramID, "vertexTexCoord");
        mvpMatrixHandle     = GLES20.glGetUniformLocation(shaderProgramID, "modelViewProjectionMatrix");
        texSampler2DHandle  = GLES20.glGetUniformLocation(shaderProgramID, "texSampler2D");

        try
            {
            buildingsModel = new SampleApplication3DModel();
            buildingsModel.loadModel(activity.getResources().getAssets(), "ImageTargets/Buildings.txt");
            }
        catch (IOException e)
            {
            Log.e(LOGTAG, "Unable to load buildings");
            }

        // Hide the Loading Dialog
        activity.loadingDialogHandler.sendEmptyMessage(LoadingDialogHandler.HIDE_LOADING_DIALOG);
        }

    // The render function.
    private void renderFrame()
        {
        if (!this.displayEnabled)
            {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

            State state = renderer.begin();

            for (int tIdx = 0; tIdx < state.getNumTrackableResults(); tIdx++)
                {
                TrackableResult trackableResult = state.getTrackableResult(tIdx);
                printUserData(trackableResult);
                }

            renderer.end();
            }
        else
            {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

            State state = renderer.begin();
            renderer.drawVideoBackground();

            GLES20.glEnable(GLES20.GL_DEPTH_TEST);

            // Set the viewport
            int[] viewport = vuforiaAppSession.getViewport();
            GLES20.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);

            // handle face culling, we need to detect if we are using reflection
            // to determine the direction of the culling
            GLES20.glEnable(GLES20.GL_CULL_FACE);
            GLES20.glCullFace(GLES20.GL_BACK);
            if (Renderer.getInstance().getVideoBackgroundConfig().getReflection() == VIDEO_BACKGROUND_REFLECTION.VIDEO_BACKGROUND_REFLECTION_ON)
                GLES20.glFrontFace(GLES20.GL_CW); // Front camera
            else
                GLES20.glFrontFace(GLES20.GL_CCW); // Back camera

            // did we find any trackables this frame?
            for (int tIdx = 0; tIdx < state.getNumTrackableResults(); tIdx++)
                {
                TrackableResult trackableResult = state.getTrackableResult(tIdx);
                Trackable trackable = trackableResult.getTrackable();
                printUserData(trackableResult);

                Matrix44F modelViewMatrix_Vuforia = Tool.convertPose2GLMatrix(trackableResult.getPose());
                float[] modelViewMatrixData = modelViewMatrix_Vuforia.getData();

                // deal with the modelview and projection matrices
                float[] modelViewProjectionData = new float[16];

                if (!activity.isExtendedTrackingActive())
                    {
                    Matrix.translateM(modelViewMatrixData, 0, 0.0f, 0.0f, OBJECT_SCALE_FLOAT);
                    Matrix.scaleM(modelViewMatrixData, 0, OBJECT_SCALE_FLOAT, OBJECT_SCALE_FLOAT, OBJECT_SCALE_FLOAT);
                    }
                else
                    {
                    Matrix.rotateM(modelViewMatrixData, 0, 90.0f, 1.0f, 0, 0);
                    Matrix.scaleM(modelViewMatrixData, 0, kBuildingScale, kBuildingScale, kBuildingScale);
                    }

                Matrix.multiplyMM(modelViewProjectionData, 0, vuforiaAppSession.getProjectionMatrix().getData(), 0, modelViewMatrixData, 0);

                // activate the shader program and bind the vertex/normal/tex coords
                GLES20.glUseProgram(shaderProgramID);

                if (!activity.isExtendedTrackingActive())
                    {
                    int textureIndex = textureNames.indexOf(trackable.getName().toLowerCase());

                    GLES20.glVertexAttribPointer(vertexHandle,       3, GLES20.GL_FLOAT, false, 0, teapot.getVertices());
                    GLES20.glVertexAttribPointer(normalHandle,       3, GLES20.GL_FLOAT, false, 0, teapot.getNormals());
                    GLES20.glVertexAttribPointer(textureCoordHandle, 2, GLES20.GL_FLOAT, false, 0, teapot.getTexCoords());

                    GLES20.glEnableVertexAttribArray(vertexHandle);
                    GLES20.glEnableVertexAttribArray(normalHandle);
                    GLES20.glEnableVertexAttribArray(textureCoordHandle);

                    GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures.get(textureIndex).mTextureID[0]);
                    GLES20.glUniform1i(texSampler2DHandle, 0);

                    // pass the model view matrix to the shader
                    GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, modelViewProjectionData, 0);

                    // finally draw the teapot
                    GLES20.glDrawElements(GLES20.GL_TRIANGLES, teapot.getNumObjectIndex(), GLES20.GL_UNSIGNED_SHORT, teapot.getIndices());

                    // disable the enabled arrays
                    GLES20.glDisableVertexAttribArray(vertexHandle);
                    GLES20.glDisableVertexAttribArray(normalHandle);
                    GLES20.glDisableVertexAttribArray(textureCoordHandle);
                    }
                else
                    {
                    int textureIndex = textureNames.indexOf("building");

                    GLES20.glDisable(GLES20.GL_CULL_FACE);
                    GLES20.glVertexAttribPointer(vertexHandle, 3, GLES20.GL_FLOAT, false, 0, buildingsModel.getVertices());
                    GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, 0, buildingsModel.getNormals());
                    GLES20.glVertexAttribPointer(textureCoordHandle, 2, GLES20.GL_FLOAT, false, 0, buildingsModel.getTexCoords());

                    GLES20.glEnableVertexAttribArray(vertexHandle);
                    GLES20.glEnableVertexAttribArray(normalHandle);
                    GLES20.glEnableVertexAttribArray(textureCoordHandle);

                    GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures.get(textureIndex).mTextureID[0]);
                    GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, modelViewProjectionData, 0);
                    GLES20.glUniform1i(texSampler2DHandle, 0);
                    GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, buildingsModel.getNumObjectVertex());

                    SampleUtils.checkGLError("Renderer DrawBuildings");
                    }

                SampleUtils.checkGLError("Render Frame");
                }

            GLES20.glDisable(GLES20.GL_DEPTH_TEST);

            renderer.end();
            }
        }

    private void printUserData(TrackableResult trackableResult)
        {
        // http://www.opengl-tutorial.org/beginners-tutorials/tutorial-3-matrices/
        // https://developer.vuforia.com/library/articles/Solution/Get-the-Camera-Position

        // http://stackoverflow.com/questions/15022630/how-to-calculate-the-angle-from-roational-matrix
        // http://math.stackexchange.com/questions/82602/how-to-find-camera-position-and-rotation-from-a-4x4-matrix

        // "The top 3x3 block is the rotation, and the right column is the translation"
        Matrix34F pose = trackableResult.getPose();

        // modelViewMatrixData is manipulable with android.opengl.Matrix
        Matrix44F modelViewMatrix_Vuforia = Tool.convertPose2GLMatrix(pose);
        float[] modelViewMatrixData = modelViewMatrix_Vuforia.getData();


        Trackable trackable = trackableResult.getTrackable();
        String userData = (String)trackable.getUserData();
        Log.d(LOGTAG, "UserData:Retreived User Data	\"" + userData + "\"");
        }

    public void setTextures(Vector<Texture> textures, List<String> textureNames)
        {
        this.textures = textures;
        this.textureNames = textureNames;
        }

    public boolean setDisplayEnabled(boolean displayEnabled)
        {
        this.displayEnabled = displayEnabled;
        return true;
        }
    }
