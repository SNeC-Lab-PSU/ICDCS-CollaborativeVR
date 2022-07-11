package com.example.vrsystemclient;

import android.content.Context;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.opengl.GLES30;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;

import static android.opengl.GLU.gluErrorString;

/** GL utility methods. */
public class Utils {

    public static MediaExtractor megaExtractor;
    public static MediaFormat megaFmtTemplate;
    public static FrameDecoder[] mFrameDecoders; // last one is depth decoder
    public static ArrayList<String> mvrpdTrace = new ArrayList<>();
    public static HashMap<String, ArrayList<Integer>> tileTable = new HashMap<>();
    public static ArrayList<Integer> avatorTrace = new ArrayList<>();
    public static HashMap<Integer, String> videoID2mp4Name = new HashMap<>(); // videoID(global tileID) -> mp4 filename(frameID_tileID)
    public static int traceID = -1; // indicates which trace we are running
    public final static String CRLF = "\r\n";

    public static HashMap<String, Integer> pose2VideoID = new HashMap<>();
    //public static HashMap<Integer, Integer> videoID2tileID = new HashMap<>();
    public static HashMap<Integer, String> videoID2pose = new HashMap<>();
    //public static int latestVideoId = 0;
    public static int timeSlot = 0;
    public static int lastDisplayQuality = 0;
    public static ArrayList<String> notFoundFrames = new ArrayList<>();

    //public static ArrayList<String> dispBuffer = new ArrayList<>();
    public static HashMap<Integer,String> dispBuffer = new HashMap<>();
    public static ArrayList<Integer> displayVideoIDs = new ArrayList<>();

    public static ArrayList<Integer> videoToDecode = new ArrayList<>();


    // navigation
    public static String naviPos;
    public static int naviIdx;
    public static int naviX;
    public static int naviZ;
    public static float naviLat = 0.0f;
    public static float naviLon = 0.0f;
    public static float coorX = 0.0f;
    public static float coorY = 0.0f;
    public static String decodePos;
    public static float decodeOriX = 0.0f;
    public static float decodeOriY = 0.0f;
    public static boolean endOfTrace = false;
    public static boolean endTransmission = false;
    public static boolean startDisplay = false; // control the time to start display the first frame
    public static boolean teacherFlag;

    public static void updateDisplayPose(){
        if (Utils.dispBuffer.containsKey(Utils.timeSlot)) {
            String pose = Utils.dispBuffer.get(Utils.timeSlot);
            String[] tokens = pose.split("_");
            String indexPos = tokens[0];
            float oriX = Float.parseFloat(tokens[1]);
            float oriY = Float.parseFloat(tokens[2]);
            Utils.naviPos = indexPos;
            Utils.coorX = oriX;
            Utils.coorY = oriY;
            Utils.naviLat = -Utils.coorX;
            Utils.naviLon = 90 - Utils.coorY;
        }
        else{
            Log.e(Config.GLOBAL_TAG,"cannot find corresponding display pose of slot "+Utils.timeSlot);
        }

    }

    public static float calAngle(float degree){
        float result =  (degree+180.f)%360.f - 180.f;
        return result < -180? result + 360.f : result;
    }

    public static int getVideoID(String indexPos, int tileID, int quality) {
        String pose = indexPos+","+tileID+","+quality;
        if (!Utils.pose2VideoID.containsKey(pose))
        {
            Utils.notFoundFrames.add(pose);
            return -1;
        }
        int videoID = Utils.pose2VideoID.get(pose);
        return videoID;
    }

    public static int getTileID(int videoID){
        String pose = Utils.videoID2pose.get(videoID);
        return Integer.parseInt(pose.split(",")[2]);
    }

    public static String getPosIndexFromFloatArray(float[] raw_positions){
        float[] positions = new float[3];
        for(int i=0;i<3;i++){
            positions[i] = (int)(raw_positions[i]/Config.granular)*Config.granular;
        }
        String pos = (int) positions[0]+","+(int) positions[2];
        return pos;
    }

    public static String getPosFromMsg(String recvStr){
        // only pos X and Z
        String[] pose = null;
        if (recvStr.contains(","))
            pose = recvStr.split(",");
        else {
            pose = recvStr.split(" ");
        }
        float[] positions = new float[2];
        for(int i=0;i<2;i++){
            positions[i] = (int)(Float.parseFloat(pose[i])/Config.granular)*Config.granular;
        }
        String pos = (int) positions[0]+","+(int) positions[1];
        return pos;
    }

    public static float[] getOriFromMsg(String recvStr){
        // only ori X and Y
        String[] pose = null;
        if (recvStr.contains(","))
            pose = recvStr.split(",");
        else {
            pose = recvStr.split(" ");
        }
        float[] orientations = new float[2];
        for(int i=0;i<2;i++){
            orientations[i] = Float.parseFloat(pose[i+2]);
        }
        return orientations;
    }

    public static ArrayList<Integer> combineTileList(ArrayList<Integer> al1, ArrayList<Integer> al2) {
        if(endOfTrace) return new ArrayList<>();
        ArrayList<Integer> al3 = new ArrayList<>();
        for(int videoID : al1) al3.add(videoID);
        for(int videoID : al2) al3.add(videoID);
        return al3;
    }


    public static ArrayList<Integer> getDecodeTiles() {

        ArrayList<Integer> al = new ArrayList<>();

        /*for(int i=0;i< Config.nDecoders;i++){
            if (Utils.videoToDecode.size() > i)
                al.add(Utils.videoToDecode.get(Utils.videoToDecode.size()-1-i));
            else
                al.add(-1);
        }*/

        String coor = "(" + (int) Utils.decodeOriX + "," + (int) Utils.decodeOriY + "," + 0 + ")";
        //System.out.println(coor);
        ArrayList<Integer> tiles = Utils.tileTable.get(coor);
        //System.out.println(tiles);

        // add videoID from high quality to low quality
        for (int i = 0; i < Config.qualityLevel; i++) {
            int videoID = -1;
            for (int tileId : tiles) {
                videoID = Utils.getVideoID(Utils.decodePos, tileId, Config.avaQuality[i]);
                al.add(videoID);
            }
        }
        return al;
    }

    public static ArrayList<Integer> getCurVisibleTiles() {

        ArrayList<Integer> al = new ArrayList<>();
        String coor = "("+(int) Utils.coorX+","+(int) Utils.coorY+","+0+")";
        //System.out.println(coor);
        ArrayList<Integer> tiles = Utils.tileTable.get(coor);
        //System.out.println(tiles);

        // list available tiles
        ArrayList<Integer> avaQuality = new ArrayList<>();
        for (int i=0;i<Config.qualityLevel;i++) {
            int videoID = -1;
            for (int tileId : tiles) {
                videoID = Utils.getVideoID(Utils.naviPos, tileId, Config.avaQuality[i]);
                // check whether this tile has been received
                if(!NetworkBufferPool.videoReceived.contains(videoID)){
                    videoID = -1;
                }
                if (videoID == -1) break;
            }
            if (videoID != -1) avaQuality.add(Config.avaQuality[i]);
        }
        int quality = -1;
        if (avaQuality.size() == 1)
            quality = avaQuality.get(0);
        else if (avaQuality.size() > 1){
            int min = 100;
            for (int i = 0; i < avaQuality.size(); i++){
                // choose the quality which is closest to last quality to reduce the variance
                // problem exists, make the same tile with higher quality invalid
                //int diff = Math.abs(quality - Utils.lastDisplayQuality);
                // choose the highest quality
                int diff = quality;
                // since quality is listed from higher to lower, it will choose the higher one if difference is the same
                // if want to choose the lower one, just replacec < by <=
                if (diff < min){
                    min = diff;
                    quality = avaQuality.get(i);
                }
            }
        }

        for(int tileId : tiles) {
            int videoID = -1;
            // choose the quality from the highest to the lowest
            /*for(int i=0;i<Config.qualityLevel;i++){
                videoID = Utils.getVideoID(Utils.naviPos, tileId, Config.avaQuality[i]);
                // check whether this tile has been decoded
                if(FrameCache.FBOcacheContainsMegaTile(videoID)){
                    al.add(videoID);
                    break;
                }
            }
            // cannot find the tile in any quality
            if (videoID == -1)
                al.add(videoID);*/
            videoID = Utils.getVideoID(Utils.naviPos, tileId, quality);
            al.add(videoID);
        }
        //System.out.println(al);
        //System.out.println(coor+"tiles:"+al);
        //System.out.println("Y:"+Utils.coorY);
        //MainActivity.connText.setText("("+ Utils.coorX+","+ Utils.coorY+","+0+")\n"+al);
        return al;
    }
    public static ArrayList<Integer> getCurPDTiles() {
        if(endOfTrace) return new ArrayList<>();
        ArrayList<Integer> al = new ArrayList<>();
        String line = Utils.mvrpdTrace.get(Utils.naviIdx);
        String[] parts = line.split(",");
        if(parts[2].trim().length() == 0)
            return al;
        String part = parts[2];
        String[] items = part.split(" ");
        for(String item : items) {
            if(item.length() > 0)
                al.add(Integer.parseInt(item));
        }
        return al;
    }
    public static ArrayList<Integer> getCurSurroundingTiles() {
        if(endOfTrace) return new ArrayList<>();
        ArrayList<Integer> al = new ArrayList<>();
        String line = Utils.mvrpdTrace.get(Utils.naviIdx);
        String[] parts = line.split(",");
        if(parts[3].trim().length() == 0)
            return al;
        String part = parts[3];
        String[] items = part.split(" ");
        for(String item : items) {
            if(item.length() > 0)
                al.add(Integer.parseInt(item));
        }
        return al;
    }



    public static String getMyIP(Context context) {
        WifiManager wifiMan = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInf = wifiMan.getConnectionInfo();
        int ipAddress = wifiInf.getIpAddress();
        String ip = String.format("%d.%d.%d.%d", (ipAddress & 0xff),(ipAddress >> 8 & 0xff),(ipAddress >> 16 & 0xff),(ipAddress >> 24 & 0xff));
        return ip;
    }

    /** Returns the consumer friendly device name */
    public static String getDeviceName() {
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;
        if (model.startsWith(manufacturer)) {
            return capitalize(model);
        }
        return capitalize(manufacturer) + " " + model;
    }
    private static String capitalize(String str) {
        if (TextUtils.isEmpty(str)) {
            return str;
        }
        char[] arr = str.toCharArray();
        boolean capitalizeNext = true;

        StringBuilder phrase = new StringBuilder();
        for (char c : arr) {
            if (capitalizeNext && Character.isLetter(c)) {
                phrase.append(Character.toUpperCase(c));
                capitalizeNext = false;
                continue;
            } else if (Character.isWhitespace(c)) {
                capitalizeNext = true;
            }
            phrase.append(c);
        }

        return phrase.toString();
    }


    public static final int BYTES_PER_FLOAT = 4;
    /** Debug builds should fail quickly. Release versions of the app should have this disabled. */
    private static final boolean HALT_ON_GL_ERROR = false;
    /** Class only contains static methods. */
    private Utils() {}

    // to generate template raw, ffmpeg -i input.mp4 -t 0 -c:v copy output.mp4
    public static void initMegaTemplate() {
        megaFmtTemplate = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, Config.FRAME_WIDTH/ Config.nColumns, Config.FRAME_HEIGHT/ Config.nRows);
    }


    /** Checks GLES30.glGetError and fails quickly if the state isn't GL_NO_ERROR. */
    public static void checkGlError() {
        int error = GLES30.glGetError();
        int lastError;
        if (error != GLES30.GL_NO_ERROR) {
            do {
                lastError = error;
                Log.e(Config.GLOBAL_TAG, "glError " + gluErrorString(lastError));
                error = GLES30.glGetError();
            } while (error != GLES30.GL_NO_ERROR);

            if (HALT_ON_GL_ERROR) {
                RuntimeException e = new RuntimeException("glError " + gluErrorString(lastError));
                Log.e(Config.GLOBAL_TAG, "Exception: ", e);
                throw e;
            }
        }
    }
}
