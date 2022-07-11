package com.example.vrsystemclient;

import java.io.File;

public class Config {
    public static int RTSP_PORT = 8088;
    public static int FUNC_PORT = 8080;
    public static int MOVE_PORT = 8848;
    public static String SERVER_IP = "192.168.0.235"; //"192.168.1.5"; //"131.128.54.85"; //"172.20.255.74";
    public static int PKT_SIZE = 1400;
    public static int RUN_TIMES = 1;

    // RTSP thread msg type
    public final static int RTSP_SETUP = 0x201;
    public final static int RTSP_PLAY = 0x202;
    public final static int RTSP_PAUSE = 0x203;
    public final static int RTSP_TEARDOWN = 0x204;

    // Functional Thread msg type
    public final static int CONNECT = 0x300;
    public final static int SEND_DISP_ACK = 0x301;
    public final static int SEND_PKT_ACK = 0x302;
    public final static int SEND_NACK = 0x303;
    public static final String MSG_KEY = "hi";

    public final static int qualityLevel = 6;
    public final static int[] avaQuality = {15,19,23,27,31,35};
    public final static int FRAME_WIDTH = 2560;
    public final static int FRAME_HEIGHT = 1440;
    public final static String GLOBAL_TAG = "MobileVR";
    public final static int TARGET_FPS = 60;
    public final static int MSG_FRAME_SYNC = 0x101;
    public final static int MSG_SETUP = 0x102;
    public final static int MSG_ON_FRAME_AVAILABLE = 0x103;
    public final static int MSG_REPORT_STATUS = 0x104;
    public final static int SPHERE_SLICES = 180;
    public final static float SPHERE_RADIUS = 100.0f;
    public final static int SPHERE_INDICES_PER_VERTEX = 1;
    public final static int COLOR = -1;
    public final static int DEPTH = -2;

    public static int screenWidth = 0;
    public static int screenHeight = 0;

    public static boolean bProjection = true;
    public static int bOverlay = 0;

    public static int FOVX = 90;
    public static int FOVY = 100;

    public static int nRows = 1;
    public static int nColumns = 4;
    public static int nTiles = 4;

    //sensor data
    public static float[] sensor_rotation = new float[4]; // x_rot, y_rot, z_rot, ang_rot

    // MAP STEP
    public static int STEP = 5;

    // navigation
    public final static int NAVI_FREQ = 15; // only for touching navi
    public static float granular = 5f;

    // navi
    public static boolean traceNavigation = true;

    // decoders
    public static int nDecoders = 5;
    public static int SETUP_DELAY = 500; // do not fire MSG_FRAME_SYNC immediately

    // path
    public static File TRACE_FILE = null;
    public static String TILEMAP_PATH = "MobileVR/office/OfficeTileMap.txt"; // this file maps videoID(global tileID) to mp4 filename
    public static String LOCAL_VIDEOPATH = "MobileVR/office/mega_tile_mp4/crf23/";
    public static String STATS_PATH = "MobileVR/office/stats/";

    // pre-fetch mode
    public final static int FETCH_VISIBLE = 0x200;
    public final static int FETCH_SURROUDING = 0x201;
    public final static int FETCH_PD = 0x202;
    public static int PREFETCH_MODE = FETCH_PD;


    // FPS calculation interval
    public final static int FPS_CAL_INTERVAL = TARGET_FPS*5;

    // camera rotate by sensor or trace reading
    public final static boolean SENSOR_ROTATION = false;


    // note8/s10 config
    // FBO CACHE
    public static double FBO_CACHE_LIFE = 1000; // ms
    public static int FBO_CACHE_SIZE = 150; // depth + color FBO
    // video cache
    public static long VIDEO_CACHE_LIFE = 1000; // ms
    public static int VIDEO_CACHE_LIMIT = 150;
    // Display buffer size
    public static int DISPLAY_CACHE_LIMIT = 5;

}
