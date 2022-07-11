package com.example.vrsystemclient;

import android.graphics.SurfaceTexture;
import android.hardware.SensorManager;
import android.opengl.GLES30;
import android.opengl.Matrix;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;


public class OpenGLHelper {

    public final static String VERTEX_SHADER =
            "uniform mat4 uMVPMatrix;\n" +
            "uniform mat4 uTextureMatrix;\n" +
            "attribute vec4 aPosition;\n" +
            "attribute vec4 aTextureCoord;\n" +
            "varying vec2 vTextureCoord;\n" +
            "void main() {\n" +
            "    gl_Position = uMVPMatrix * aPosition;\n" +    // aPosition is the local positions of model, times uMVPMatrix will turn it into world/camera position
            "    vTextureCoord = (uTextureMatrix * aTextureCoord).xy;\n" +
            "}\n";

    public final static String VERTEX_SHADER_360 =
            "uniform mat4 uMVPMatrix;\n" +
            "uniform mat4 uTextureMatrix;\n" +
            "attribute vec4 aPosition;\n" +
            "attribute vec4 aTextureCoord;\n" +
            "varying vec2 vTextureCoord;\n" +
            "void main() {\n" +
            "    gl_Position = uMVPMatrix * aPosition * vec4(-1, -1, 1, 1);\n" +
            "    vTextureCoord = (uTextureMatrix * aTextureCoord).xy;\n" +
            "}\n";

    public final static String FRAGMENT_SHADER =
            "precision mediump float;\n" +      // highp here doesn't seem to matter
            "varying vec2 vTextureCoord;\n" +
            "uniform sampler2D sTexture;\n" +
            "void main() {\n" +
            "    gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
            "}\n";


    public final static String FRAGMENT_SHADER_FBO =
            //"#extension GL_EXT_frag_depth : require\n" +
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +      // highp here doesn't seem to matter
            "varying vec2 vTextureCoord;\n" +
            "uniform samplerExternalOES sTexture;\n" +
            "uniform float depthAttr;\n" +
            "void main() {\n" +
            "    gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
            //"    gl_FragDepthEXT = 0.0;\n" +
            "}\n";

    public final static String FRAGMENT_SHADER_FBO_OVERLAY =
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +      // highp here doesn't seem to matter
            "varying vec2 vTextureCoord;\n" +
            "uniform samplerExternalOES sTexture;\n" +
            "uniform vec4 colormod;\n" +
            "void main() {\n" +
            "    gl_FragColor =  texture2D(sTexture, vTextureCoord) * colormod;\n" +
            "}\n";

    public final static float INITIAL_PITCH_DEGREES = 90.f;
    public final static float Z_NEAR = 1.0f;
    public final static float Z_FAR = 100.0f;

    public final static int FLOAT_SIZE_BYTES = 4;
    public final static int TRIANGLE_VERTICES_DATA_STRIDE_BYTES = 5 * FLOAT_SIZE_BYTES;
    public final static int TRIANGLE_VERTICES_DATA_POS_OFFSET = 0;
    public final static int TRIANGLE_VERTICES_DATA_UV_OFFSET = 3;

    public static EGL10 mEgl = null;
    public static EGLDisplay mEGLDisplay = EGL10.EGL_NO_DISPLAY;
    public static EGLContext mEGLContext = EGL10.EGL_NO_CONTEXT;
    public static EGLSurface mEGLSurface = EGL10.EGL_NO_SURFACE;
    public static int mProgram; // two shader linked together
    public static int muMVPMatrixHandle;
    public static int muTextureMatrixHandle;
    public static int maPositionHandle;
    public static int maTextureHandle;
    public static int mProgramFBO;
    public static int muMVPMatrixHandleFBO;
    public static int muSTMatrixHandleFBO;
    public static int maPositionHandleFBO;
    public static int maTextureHandleFBO;
    public static int maColorFBO;
    public static float[] projectionMatrix = new float[16];
    public static float[] viewMatrix = new float[16];
    public static float[] modelMatrix = new float[16];
    public static float[] mMVPMatrix = new float[16];
    public static float[] mTextureMatrix = new float[16]; // store displaySurfaceTexture transform
    public static MDQuaternion mViewQuaternion = new MDQuaternion();

    public static FloatBuffer mTriangleVertices; // screen object, 2d display
    public static FloatBuffer mTriangleVerticesForFBO;
    public static MySphere[] mSpheres; // screen object, panoramic display

    public static FloatBuffer mTriangleVerticesForColor;
    public static FloatBuffer mTriangleVerticesForDepth;

    // for camera update
    public static float[] camera = new float[3];
    private static float[] tmp16 = new float[16];
    private static float[] mSensorMatrix = new float[16];
    private static float[] remappedPhoneMatrix = new float[16];

    static public void prepareNormalRender() {
        GLES30.glViewport(0, 0, Config.screenWidth, Config.screenHeight);  //screen
        GLES30.glUseProgram(mProgram);
    }


    // call this after drawFrame() to actually display
    static public void display() {
        mEgl.eglSwapBuffers(mEGLDisplay, mEGLSurface);
    }


    static private void checkEglError(String msg) {
        int error;
        if ((error = mEgl.eglGetError()) != EGL10.EGL_SUCCESS) {
            Log.e(Config.GLOBAL_TAG, msg + ": EGL error: 0x" + Integer.toHexString(error));
            throw new RuntimeException(msg + ": EGL error: 0x" + Integer.toHexString(error));
        }
    }

    static public void checkGlError(String op) {
        int error;
        while ((error = GLES30.glGetError()) != GLES30.GL_NO_ERROR) {
            Log.e(Config.GLOBAL_TAG, op + ": glError " + error);
            throw new RuntimeException(op + ": glError " + error);
        }
    }

    static public void checkLocation(int location, String label) {
        if (location < 0) {
            throw new RuntimeException("Unable to locate '" + label + "' in program");
        }
    }

    static public void oneTimeSetup(SurfaceTexture displaySurfaceTexture) {

        initOpenGL(displaySurfaceTexture);

        // init display
        GLES30.glViewport(0, 0, Config.screenWidth, Config.screenHeight);
        OpenGLHelper.checkGlError("glViewport");

        //float aspectRatio = (float) Config.screenWidth / Config.screenHeight;
        float aspectRatio =  (float) (
                Math.tan(Math.toRadians(Config.FOVX / 2.0f))  /
                        Math.tan(Math.toRadians(Config.FOVY / 2.0f)));

        Matrix.perspectiveM(projectionMatrix, 0, Config.FOVY, aspectRatio, Z_NEAR, Z_FAR);
        Matrix.setIdentityM(viewMatrix, 0);
        // Apply initial rotation
        Matrix.setRotateM(modelMatrix, 0, INITIAL_PITCH_DEGREES, 1, 0, 0);

        updateCamera();

        float[] mTriangleVerticesData = {
                // X, Y, Z, U, V
                -1.0f, -1.0f, 0, 0.f, 0.f,
                1.0f, -1.0f, 0, 1.f, 0.f,
                -1.0f, 1.0f, 0, 0.f, 1.f,
                1.0f, 1.0f, 0, 1.f, 1.f,
        };

        float[] mTriangleVerticesDataFBO = {
                // X, Y, Z, U, V
                -1.0f, -1.0f, 0, 0.f, 1.f,
                1.0f, -1.0f, 0, 1.f, 1.f,
                -1.0f, 1.0f, 0, 0.f, 0.f,
                1.0f, 1.0f, 0, 1.f, 0.f,
        };

        float[] mTriangleVerticesColor = {
                // X, Y, Z, U, V
                -1.0f, -1.0f, 0, 0.f, 1.f,
                1.0f, -1.0f, 0, 1.f, 1.f,
                -1.0f, 1.0f, 0, 0.f, 0f,
                1.0f, 1.0f, 0, 1.f, 0f,
        };

        float[] mTriangleVerticesDepth = {
                // X, Y, Z, U, V
                -1.0f, -1.0f, 0, 0.f, 0.5f,
                1.0f, -1.0f, 0, 1.f, 0.5f,
                -1.0f, 1.0f, 0, 0.f, 0.f,
                1.0f, 1.0f, 0, 1.f, 0.f,
        };

        mTriangleVerticesForColor = ByteBuffer.allocateDirect(
                mTriangleVerticesColor.length * FLOAT_SIZE_BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        mTriangleVerticesForColor.put(mTriangleVerticesColor).position(0);

        mTriangleVerticesForDepth = ByteBuffer.allocateDirect(
                mTriangleVerticesDepth.length * FLOAT_SIZE_BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        mTriangleVerticesForDepth.put(mTriangleVerticesDepth).position(0);



        mTriangleVerticesForFBO = ByteBuffer.allocateDirect(
                mTriangleVerticesDataFBO.length * FLOAT_SIZE_BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        mTriangleVerticesForFBO.put(mTriangleVerticesDataFBO).position(0);

        mTriangleVertices = ByteBuffer.allocateDirect(
                mTriangleVerticesData.length * FLOAT_SIZE_BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        mTriangleVertices.put(mTriangleVerticesData).position(0);

        if (Config.bProjection) {
            mSpheres = new MySphere[Config.nTiles];

            for (int i = 0; i < Config.nTiles; i++) {  //each tile ID
                int row = i / Config.nColumns;
                int col = i % Config.nColumns;
                mSpheres[i] = new MySphere(Config.SPHERE_SLICES, Config.nRows, Config.nColumns, row, col, 0.0f, 0.0f, 0.0f,
                        Config.SPHERE_RADIUS, Config.SPHERE_INDICES_PER_VERTEX);
            }

            if(Config.SENSOR_ROTATION) {
                float[] dummy = new float[16];
                Matrix.setIdentityM(dummy, 0);
                mViewQuaternion.fromMatrix(dummy);
                Utils.naviLat = mViewQuaternion.getPitch();
                Utils.naviLon = mViewQuaternion.getYaw();
            }
        }
    }

    static private void initOpenGL(SurfaceTexture displaySurfaceTexture) {
        final int EGL_OPENGL_ES2_BIT = 0x0004;
        final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
        final int EGL_OPENGL_ES3_BIT = 0x00000040;

        mEgl = (EGL10) EGLContext.getEGL();
        mEGLDisplay = mEgl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
        if (mEGLDisplay == EGL10.EGL_NO_DISPLAY) {
            throw new RuntimeException("unable to get EGL14 display");
        }
        int[] version = new int[2];
        if (!mEgl.eglInitialize(mEGLDisplay, version)) {
            mEGLDisplay = null;
            throw new RuntimeException("unable to initialize EGL14");
        }

        // Configure EGL for pbuffer and OpenGL ES 3.0, 24-bit RGB.
        int[] attribList = {
                EGL10.EGL_RED_SIZE, 8,
                EGL10.EGL_GREEN_SIZE, 8,
                EGL10.EGL_BLUE_SIZE, 8,
                EGL10.EGL_ALPHA_SIZE, 0,
                EGL10.EGL_DEPTH_SIZE, 0,        //from 360 project
                EGL10.EGL_STENCIL_SIZE, 0,      //from 360 project
                EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
                EGL10.EGL_SURFACE_TYPE, EGL10.EGL_WINDOW_BIT,   //from 360 project
                //EGL10.EGL_SURFACE_TYPE, EGL10.EGL_PBUFFER_BIT,            //imgsave proj
                EGL10.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        if (!mEgl.eglChooseConfig(mEGLDisplay, attribList, configs, configs.length,
                numConfigs)) {
            throw new RuntimeException("unable to find RGB888+recordable ES2 EGL config");
        }

        // Configure context for OpenGL ES 3.0.
        int[] attrib_list = {
                EGL_CONTEXT_CLIENT_VERSION, 3,
                EGL10.EGL_NONE
        };
        mEGLContext = mEgl.eglCreateContext(mEGLDisplay, configs[0], EGL10.EGL_NO_CONTEXT,
                attrib_list);
        checkEglError("eglCreateContext");
        if (mEGLContext == null) {
            throw new RuntimeException("null context");
        }

        mEGLSurface = mEgl.eglCreateWindowSurface(mEGLDisplay, configs[0], displaySurfaceTexture, null);
        checkEglError("eglCreateWindowSurface");
        if (mEGLSurface == null || mEGLSurface == EGL10.EGL_NO_SURFACE) {
            throw new RuntimeException("surface was null");
        }

        if (!mEgl.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext)) {
            throw new RuntimeException("eglMakeCurrent failed");
        }

        //initialize program
        if (Config.bProjection) {
            mProgram = OpenGLHelper.createProgram(VERTEX_SHADER_360, FRAGMENT_SHADER);
        } else {
            mProgram = OpenGLHelper.createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        }
        if (mProgram == 0) {
            throw new RuntimeException("failed creating program");
        }

        maPositionHandle = GLES30.glGetAttribLocation(mProgram, "aPosition");
        OpenGLHelper.checkLocation(maPositionHandle, "aPosition");
        maTextureHandle = GLES30.glGetAttribLocation(mProgram, "aTextureCoord");
        OpenGLHelper.checkLocation(maTextureHandle, "aTextureCoord");

        muMVPMatrixHandle = GLES30.glGetUniformLocation(mProgram, "uMVPMatrix");
        OpenGLHelper.checkLocation(muMVPMatrixHandle, "uMVPMatrix");
        muTextureMatrixHandle = GLES30.glGetUniformLocation(mProgram, "uTextureMatrix");
        OpenGLHelper.checkLocation(muTextureMatrixHandle, "uTextureMatrix");

        if (Config.bOverlay == 1) {
            mProgramFBO = OpenGLHelper.createProgram(VERTEX_SHADER, FRAGMENT_SHADER_FBO_OVERLAY);
            maColorFBO = GLES30.glGetUniformLocation(mProgramFBO, "colormod");
            OpenGLHelper.checkLocation(maColorFBO, "colormod");
        } else {
            mProgramFBO = OpenGLHelper.createProgram(VERTEX_SHADER, FRAGMENT_SHADER_FBO);
            maColorFBO = 0;
        }
        if (mProgramFBO == 0) {
            throw new RuntimeException("failed creating program for FBO");
        }

        maPositionHandleFBO = GLES30.glGetAttribLocation(mProgramFBO, "aPosition");
        OpenGLHelper.checkLocation(maPositionHandleFBO, "aPosition");
        maTextureHandleFBO = GLES30.glGetAttribLocation(mProgramFBO, "aTextureCoord");
        OpenGLHelper.checkLocation(maTextureHandleFBO, "aTextureCoord");

        muMVPMatrixHandleFBO = GLES30.glGetUniformLocation(mProgramFBO, "uMVPMatrix");
        OpenGLHelper.checkLocation(muMVPMatrixHandleFBO, "uMVPMatrix");
        muSTMatrixHandleFBO = GLES30.glGetUniformLocation(mProgramFBO, "uTextureMatrix");
        OpenGLHelper.checkLocation(muSTMatrixHandleFBO, "uTextureMatrix");

        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        GLES30.glDisable(GLES30.GL_STENCIL_TEST);
        GLES30.glDisable(GLES30.GL_SCISSOR_TEST);
        GLES30.glDisable(GLES30.GL_DITHER);
        GLES30.glDisable(GLES30.GL_BLEND);

        GLES30.glUseProgram(mProgram);
        OpenGLHelper.checkGlError("glUseProgram");

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
    }

    /**
     * Discard all resources held by this class, notably the EGL context.
     */
    static public void uninitOpenGL() {
        GLES30.glDeleteProgram(mProgram);

        if (mEGLDisplay != EGL10.EGL_NO_DISPLAY) {
            mEgl.eglDestroySurface(mEGLDisplay, mEGLSurface);
            mEgl.eglDestroyContext(mEGLDisplay, mEGLContext);
            //mEgl.eglReleaseThread();
            mEgl.eglMakeCurrent(mEGLDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE,
                    EGL10.EGL_NO_CONTEXT);
            mEgl.eglTerminate(mEGLDisplay);
        }
        mEGLDisplay = EGL10.EGL_NO_DISPLAY;
        mEGLContext = EGL10.EGL_NO_CONTEXT;
        mEGLSurface = EGL10.EGL_NO_SURFACE;
    }


    static public int createProgram(String vertexSource, String fragmentSource) {
        int vertexShader = loadShader(GLES30.GL_VERTEX_SHADER, vertexSource);
        if (vertexShader == 0) {
            return 0;
        }
        int pixelShader = loadShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource);
        if (pixelShader == 0) {
            return 0;
        }

        int program = GLES30.glCreateProgram();
        if (program == 0) {
            Log.e(Config.GLOBAL_TAG, "Could not create program");
        }
        GLES30.glAttachShader(program, vertexShader);
        checkGlError("glAttachShader");
        GLES30.glAttachShader(program, pixelShader);
        checkGlError("glAttachShader");
        GLES30.glLinkProgram(program);
        int[] linkStatus = new int[1];
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] != GLES30.GL_TRUE) {
            Log.e(Config.GLOBAL_TAG, "Could not link program: ");
            Log.e(Config.GLOBAL_TAG, GLES30.glGetProgramInfoLog(program));
            GLES30.glDeleteProgram(program);
            program = 0;
        }
        return program;
    }


    public static int loadShader(int shaderType, String source) {
        int shader = GLES30.glCreateShader(shaderType);
        checkGlError("glCreateShader type=" + shaderType);
        GLES30.glShaderSource(shader, source);
        GLES30.glCompileShader(shader);
        int[] compiled = new int[1];
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            Log.e(Config.GLOBAL_TAG, "Could not compile shader " + shaderType + ":");
            Log.e(Config.GLOBAL_TAG, " " + GLES30.glGetShaderInfoLog(shader));
            GLES30.glDeleteShader(shader);
            shader = 0;
        }
        return shader;
    }



    public static void updateCamera() {
        if (Config.SENSOR_ROTATION) {
            //from sensor
            SensorManager.getRotationMatrixFromVector(tmp16, Config.sensor_rotation);
            SensorManager.remapCoordinateSystem(tmp16, SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X, mSensorMatrix);
            Matrix.rotateM(mSensorMatrix, 0, 90.0F, 1.0F, 0.0F, 0.0F);

            float _t;
            _t = mSensorMatrix[0]; mSensorMatrix[0] = mSensorMatrix[8]; mSensorMatrix[8] = -_t;
            _t = mSensorMatrix[1]; mSensorMatrix[1] = mSensorMatrix[9]; mSensorMatrix[9] = -_t;
            _t = mSensorMatrix[2]; mSensorMatrix[2] = mSensorMatrix[10]; mSensorMatrix[10] = -_t;
            _t = mSensorMatrix[3]; mSensorMatrix[3] = mSensorMatrix[11]; mSensorMatrix[11] = -_t;
            mViewQuaternion.fromMatrix(mSensorMatrix);

            float lat = mViewQuaternion.getPitch();
            Utils.naviLat = Math.max(-85, Math.min(85, lat));
            Utils.naviLon = mViewQuaternion.getYaw();
        }

        float phi = (float) Math.toRadians(90 - Utils.naviLat);
        float theta = (float) Math.toRadians(Utils.naviLon);

        camera[0] = (float) (Math.sin(phi) * Math.cos(theta));
        camera[1] = (float) (Math.cos(phi));
        camera[2] = (float) (Math.sin(phi) * Math.sin(theta));

        Matrix.setLookAtM(
                viewMatrix, 0,
                0, 0, 0,
                -camera[0], -camera[1], -camera[2],
                0, 1, 0
        );
    }
}

