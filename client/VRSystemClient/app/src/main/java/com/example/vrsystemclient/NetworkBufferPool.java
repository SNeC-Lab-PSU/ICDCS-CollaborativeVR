package com.example.vrsystemclient;

import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.util.Log;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

public class NetworkBufferPool {
    private static final File FILES_DIR = Environment.getExternalStorageDirectory();
    public static final int ALL_FRAME_READ = -1;

    public static class VideoBuffer {
        private byte[] buf;
        private int pos; // cur pos
        private int size; // size in bytes
        private int videoID;
        private long t; // time created
        // network constructor
        public VideoBuffer(int videoID, byte[] buffer) {
            pos = 0;
            this.videoID = videoID;
            this.buf = buffer;
            size = buf.length;
            t = System.currentTimeMillis();
        }
        // local constructor
        /*public VideoBuffer(int videoID) {
            String mp4name = Utils.videoID2mp4Name.get(videoID);
            String path = Config.LOCAL_VIDEOPATH + mp4name + ".record";
            File inputFile = new File(FILES_DIR, path);
            pos = 0;
            this.videoID = videoID;
            try {
                buf = Files.readAllBytes(inputFile.toPath());
            } catch (IOException e) {
                throw new RuntimeException("read video file error");
            }
            size = buf.length;
            if(size <= 0)
                throw new RuntimeException("read video file error 2");
            t = System.currentTimeMillis();
        }*/
        public int getVideoID() {
            return videoID;
        }
        public void resetPos() { pos = 0; }
        public long getT() { return t; }
    }


    // this reads one frame from the mp4, the frame starts with 4 bytes of length info
    public static int readFrame(ByteBuffer inputBuf, VideoBuffer vb) {
        if(vb.pos == vb.size) {
            // all frame already read
            return ALL_FRAME_READ;
        }
        /*
        byte[] tmp = new byte[4];
        System.arraycopy(vb.buf, vb.pos, tmp, 0, 4);
        ByteBuffer wrapped = ByteBuffer.wrap(tmp);
        int frameLen = wrapped.getInt();
        vb.pos += 4;*/
        int frameLen = vb.size;

        if(frameLen > vb.size - vb.pos) {
            Log.e(Config.GLOBAL_TAG, "readFrame: error frameLen, pos=" + vb.pos + " frameLen="+frameLen + " size="+vb.size);
        }

        inputBuf.put(vb.buf, vb.pos, frameLen);
        vb.pos += frameLen;

        return frameLen;
    }


    // decoder utilities
    private static HashSet<Integer> videoInDecoding = new HashSet<>();
    public static HashSet<Integer> videoReceived = new HashSet<>();
    public static HashMap<Integer, VideoBuffer> videoCache = new HashMap<>(); // videoCache in mem

    public static synchronized int nextVideoToDecode() {
        //System.out.println("video cache size:"+videoCache.size());
        if(videoCache.isEmpty()) return -1;

        //decode current display tile
        ArrayList<Integer> al;

        al = Utils.getDecodeTiles();
        //System.out.println("visible tiles size:"+al.size());
        if (al.size() == 0) return -1;

        for(int videoID : al) {
            //System.out.println("video id:"+videoID);
            //System.out.println("contains? "+videoReceived.contains(videoID));
            // video cache or disk contain this GOP && this GOP is not being currently decoded && FBO does not contain this GOP
            if(videoReceived.contains(videoID) && !videoInDecoding.contains(videoID) && !FrameCache.FBOcacheContainsMegaTile(videoID)) {
                videoInDecoding.add(videoID);
                System.out.println("Next video to decode: "+videoID);
                if(Utils.videoToDecode.contains(videoID)) Utils.videoToDecode.remove(Integer.valueOf(videoID));
                //System.out.println("video to decode number: "+videoToDecode.size());
                return videoID;
            }
        }
        // decode only for test
        /*int videoID = Config.nTiles-1;
        //while(videoID>=0){
        for(int i = 0; i< Config.nTiles; i++){
            if(videoReceived.contains(videoID) && !videoInDecoding.contains(videoID) && !FrameCache.FBOcacheContainsMegaTile(videoID)) {
                videoInDecoding.add(videoID);
                return videoID;
            }
            videoID--;
        }*/

        // decode latest received tile
        /*int videoID = Utils.latestVideoId;
        while(videoID>=0){
            if(videoReceived.contains(videoID) && !videoInDecoding.contains(videoID) && !FrameCache.FBOcacheContainsMegaTile(videoID)) {
                videoInDecoding.add(videoID);
                return videoID;
            }
            videoID--;
        }*/

        return -1;
    }

    public static synchronized void finishDecodeVideo(int videoID) {
        videoInDecoding.remove(videoID);
        //Utils.videoToDecode.remove(Integer.valueOf(videoID));
    }

    // move encoded tiles from mem to disk
    public static void releaseVideoCache() {
        int n = 0;
        long curTS = System.currentTimeMillis();
        for (Iterator<HashMap.Entry<Integer, VideoBuffer>> it = NetworkBufferPool.videoCache.entrySet().iterator(); it.hasNext();) {
            HashMap.Entry<Integer, VideoBuffer> entry = it.next();
            int videoID = entry.getValue().getVideoID();
            if(curTS - entry.getValue().getT() > Config.VIDEO_CACHE_LIFE && !videoInDecoding.contains(videoID)) {
                it.remove();

                // release the video cache, send ack to let the server know
                videoReceived.remove(videoID);
                Message msg = MainActivity.funcNet.handler.obtainMessage(Config.SEND_NACK);
                Bundle bundle = new Bundle();
                bundle.putString(Config.MSG_KEY, 2+","+videoID);
                msg.setData(bundle);
                MainActivity.funcNet.handler.sendMessage(msg);

                n++;
            }
        }
        Log.e(Config.GLOBAL_TAG, "VIDEO CACHE RELEASED: " + n);
    }
}
