package com.example.vrsystemclient;

import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES30;
import android.os.Message;
import android.util.Log;
import android.util.Pair;
import android.view.Surface;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Stack;

public class FrameCache {
    public static int[] extTextures;
    public static SurfaceTexture[] mSurfaceTextures;
    public static Surface[] mSurfaces;

    public static MyGLTexture[] mGLTextures; // FBOs

    private static int nDecoders;
    private static int cacheSize;
    private static Stack<Integer> idleFBOs; // idle fbo, store the idx of mGLTextures
    public static HashMap<Integer, Pair<Integer, Long>> colorFrameCache;// this is the occupied FBOs for color frames, idxed by videoID, store the idx of mGLTextures
    //public static HashMap<Integer, Pair<Integer, Long>> depthFrameCache;// this is the occupied FBOs for depth frames, idxed by videoID, store the idx of mGLTextures

    public static void init(int cacheSize, int nDecoders) {
        FrameCache.cacheSize = cacheSize;
        FrameCache.nDecoders = nDecoders;

        idleFBOs = new Stack<>();
        colorFrameCache = new HashMap<>();
        //depthFrameCache = new HashMap<>();

        // android part
        extTextures = new int[nDecoders];
        mSurfaceTextures = new SurfaceTexture[nDecoders];
        mSurfaces = new Surface[nDecoders];

        GLES30.glGenTextures(nDecoders, extTextures, 0);

        // associate extTextures to mySurfaceTexture
        for (int i = 0; i < nDecoders; i++) {
            GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, extTextures[i]);
            Utils.checkGlError();

            GLES30.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER,
                    GLES30.GL_NEAREST);
            GLES30.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER,
                    GLES30.GL_NEAREST /*GL_LINEAR*/);
            GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S,
                    GLES30.GL_CLAMP_TO_EDGE);
            GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T,
                    GLES30.GL_CLAMP_TO_EDGE);
            Utils.checkGlError();

            mSurfaceTextures[i] = new SurfaceTexture(extTextures[i]);
            //Creates a Surface that can be passed to MediaCodec.con figure()
            mSurfaces[i] = new Surface(mSurfaceTextures[i]);
            //System.out.println("setup frame cache");
            // set on frame available listener for this output surface
            final int decoderID = i;
            mSurfaceTextures[i].setOnFrameAvailableListener(
                new SurfaceTexture.OnFrameAvailableListener() {
                    @Override
                    public synchronized void onFrameAvailable(SurfaceTexture surfaceTexture) {
                        Message msg = new Message();
                        msg.what = Config.MSG_ON_FRAME_AVAILABLE;
                        msg.arg1 = decoderID;
                        MainActivity.mOpenGLThread.handler.sendMessage(msg);
                    }
                }
            );
        }

        // opengl part
        mGLTextures = new MyGLTexture[cacheSize];
        for(int i = 0; i < cacheSize; i++) {
            mGLTextures[i] = new MyGLTexture();
            idleFBOs.push(i);
        }
    }


    public static void drawSingleTile(int videoID, SurfaceTexture displaySurfaceTexture, int type) {
        Integer idx = -1;
        if(type == Config.COLOR) idx = colorFrameCache.get(videoID).first; // idx of mGLTextures, MyGLTexture[idx]
        //if(type == Config.DEPTH) idx = depthFrameCache.get(videoID).first;

        if(idx == null) {
            throw new RuntimeException("weird, frame not found when displaySingleFrame");
        }

        // use background drawing shader program
        OpenGLHelper.prepareNormalRender();

        int tileID = Utils.getTileID(videoID);
        FrameCache.mGLTextures[idx].draw(tileID, displaySurfaceTexture);
    }


    // cache contains both the color and depth for this tile
    public static boolean FBOcacheContainsMegaTile(int videoID) {
        //return colorFrameCache.containsKey(videoID) && depthFrameCache.containsKey(videoID);
        return colorFrameCache.containsKey(videoID);
    }


    public static boolean FBOcacheContainsCurVisibleTiles() {
        ArrayList<Integer> al = Utils.displayVideoIDs;
        for(int videoID : al) {
            if(!FBOcacheContainsMegaTile(videoID)) return false;
        }
        return true;
    }


    public static synchronized void cache2FBO(int videoID, SurfaceTexture surfaceTexture, int extTexture) {
        if(FBOcacheContainsMegaTile(videoID)) {
            Log.e(Config.GLOBAL_TAG, "cache already contains mega tile " + videoID);
            return;
        } //else if(colorFrameCache.containsKey(videoID) || depthFrameCache.containsKey(videoID)) {
         //   throw new RuntimeException("weird, cache contains part of the mega tile " + videoID);
        //}

        long curTS = System.currentTimeMillis();

        // cache color
        int idx = getNextAvailableFBO();
        if(idx == -1) throw new RuntimeException("no more available FBO");
        mGLTextures[idx].copyColorTexture(surfaceTexture, extTexture); // surfaceTexture and extTexture are associated with decoder
        colorFrameCache.put(videoID, new Pair<>(idx, curTS));
        //System.out.println(Thread.currentThread().getName() + "put video "+videoID+" into FBO");
        /*
        // cache depth
        idx = getNextAvailableFBO();
        if(idx == -1) throw new RuntimeException("no more available FBO");
        mGLTextures[idx].copyDepthTexture(surfaceTexture, extTexture); // surfaceTexture and extTexture are associated with decoder
        depthFrameCache.put(videoID, new Pair<>(idx, curTS));
        */
        // try to release FBO cache space
        //if((double)colorFrameCache.size() + (double)depthFrameCache.size() > 0.8 * (double)cacheSize) {
        if((double)colorFrameCache.size() > 0.8 * (double)cacheSize) {
            long t1 = System.currentTimeMillis();
            int n = 0;
            // release color cache
            for (Iterator<HashMap.Entry<Integer, Pair<Integer, Long>>> it = colorFrameCache.entrySet().iterator(); it.hasNext();) {
                HashMap.Entry<Integer, Pair<Integer, Long>> entry = it.next();
                double timepast = curTS - entry.getValue().second;
                // if tile expires
                if(timepast > Config.FBO_CACHE_LIFE) {
                    // check if current visible tiles contain this
                    int thisVideoID = entry.getKey();
                    if(Utils.displayVideoIDs.contains(thisVideoID)) {
                        Log.d(Config.GLOBAL_TAG, "stop! do not release this FBO, it contains current visible tile!");
                        continue;
                    }

                    // safe to release ?
                    idleFBOs.push(entry.getValue().first);
                    it.remove();
                    /*
                    // also remove depth cache
                    int depthIdx = depthFrameCache.get(entry.getKey()).first;
                    depthFrameCache.remove(entry.getKey());
                    idleFBOs.push(depthIdx);*/
                    n++;
                }
            }
            long t2 = System.currentTimeMillis();
            Log.e(Config.GLOBAL_TAG, "FBO CACHE RELEASED: " + n + " Time used: "+(t2-t1));
        }

    }



    private static synchronized int getNextAvailableFBO() {
        return idleFBOs.empty() ? -1 : idleFBOs.pop();
    }


    public static void uninit() {
        int[] _textures = new int[1];

        for (int i = 0; i < nDecoders; i++) {
            mSurfaces[i].release();
            mSurfaceTextures[i].release();

            mSurfaces[i] = null;
            mSurfaceTextures[i] = null;

            _textures[0] = extTextures[i];
            GLES30.glDeleteTextures(1, _textures, 0);
        }

        // TODO: cleanup glTextures and FBO
    }
}
