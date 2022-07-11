package com.example.vrsystemclient;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES30;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;


public class OpenGLThread extends HandlerThread {
    public Handler handler;

    private int nDecoders;
    private static SurfaceTexture displaySurfaceTexture;
    private Context context;
    private Timer displayTimer;


    private static boolean setupdelay = true;
    // clocking thread pause/resume variables
    private static Object mPauseLock;
    private static boolean mPaused;
    private static boolean mFinished;


    // clock thread, fire display signal
    /*static private class ClockThread implements Runnable {
        Handler handler;
        ClockThread(Handler h) {
            this.handler = h;
        }
        public void run() {
            int sleepInterval = (int)(1000/(double)(Config.TARGET_FPS) - 1);
            while (!mFinished) {
                // fire signal
                try {
                    // do not fire MSG_FRAME_SYNC immediately
                    if(setupdelay) {
                        // wait until receive the first packet from the server
                        while (!Utils.startDisplay){
                            Thread.sleep(1);
                        }
                        //Thread.sleep(Config.SETUP_DELAY);
                        Thread.sleep(sleepInterval); // sleep two slots before the first display
                        setupdelay = false;
                    }
                    Thread.sleep(sleepInterval); // TODO: drift of the time clock?
                    Utils.timeSlot++;
                    handler.sendEmptyMessage(Config.MSG_FRAME_SYNC);
                } catch (Exception e) { }

                // check if paused
                synchronized (mPauseLock) {
                    while (mPaused) {
                        try {
                            mPauseLock.wait();
                        } catch (InterruptedException e) { }
                    }
                }
            }
        }
        // call this to pause clock thread
        public static void pause() {
            synchronized (mPauseLock) {
                mPaused = true;
            }
        }
        // call this to resume clock thread
        public static void resume() {
            synchronized (mPauseLock) {
                mPaused = false;
                mPauseLock.notifyAll();
            }
        }
    }*/

    void setTimer(){
        displayTimer = new Timer();
        int sleepInterval = (int) (1000 / (double) (Config.TARGET_FPS) - 1);
        // wait for receiving the first RTP packet
        while (!Utils.startDisplay){
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        displayTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Utils.timeSlot++;
                handler.sendEmptyMessage(Config.MSG_FRAME_SYNC);
            }

        }, sleepInterval*2, sleepInterval);
    }

    public OpenGLThread(String name, int nDecoders, SurfaceTexture surfaceTexture, Context context) {
        super(name);
        this.nDecoders = nDecoders;
        this.context = context;
        displaySurfaceTexture = surfaceTexture;
    }




    private static synchronized void updateDisplay() {
        // clear display surface texture
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);

        // draw tiles
        String frame = System.currentTimeMillis() + " ";
        for(int videoID : Utils.displayVideoIDs) {
            FrameCache.drawSingleTile(videoID, displaySurfaceTexture, Config.COLOR);
            Stats.tileDispd.add(videoID);
            frame += (videoID + " ");
        }
        Stats.frameDisp.add(frame);

        sendDispACK(Utils.displayVideoIDs.get(0));

        // display what have been drawn
        OpenGLHelper.display();

        // update camera matrix according to sensor reading
        OpenGLHelper.updateCamera();

        // calculate FPS
        if(Stats.nDisplayed == 0) {
            Stats.startTS = System.currentTimeMillis();
        }
        Stats.nDisplayed++;
        if(Stats.nDisplayed == Config.FPS_CAL_INTERVAL) {
            float thisFPS = 1000.0f/((float)(System.currentTimeMillis() - Stats.startTS)/(Config.FPS_CAL_INTERVAL - 1));
            Stats.FPSs.add(System.currentTimeMillis() + " " + thisFPS);
            Stats.nDisplayed = 0;
            Log.e(Config.GLOBAL_TAG, "thisFPS = " + thisFPS);
            MainActivity.connText.setText("FPS: "+(int) thisFPS);
        }

        Stats.nTotalDisplayed++;
    }

    private static void sendDispACK(int videoID) {
        // let the functional thread send the display ack, only send one ACK each slot
        Message msg = MainActivity.funcNet.handler.obtainMessage(Config.SEND_DISP_ACK);
        Bundle bundle = new Bundle();
        bundle.putString(Config.MSG_KEY, 0+","+videoID+","+Utils.timeSlot+","+Utils.coorX+","+Utils.coorY);
        msg.setData(bundle);
        MainActivity.funcNet.handler.sendMessage(msg);
    }


    /** Entry point. */
    public void start() {
        super.start();

        handler = new Handler(getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case Config.MSG_SETUP: {
                        // init opengl
                        OpenGLHelper.oneTimeSetup(displaySurfaceTexture);

                        // init frameCache (surfaceTextures, surfaces, extTextures, myGLTextures...)
                        FrameCache.init(Config.FBO_CACHE_SIZE, nDecoders);

                        // init template header for decoding
                        Utils.initMegaTemplate();

                        // start decoders
                        Utils.mFrameDecoders = new FrameDecoder[nDecoders];
                        for(int i = 0; i < nDecoders; i++) {
                            Utils.mFrameDecoders[i] = new FrameDecoder(i); // one decoding thread instance
                            FrameDecoderWrapper.runExtractor(Utils.mFrameDecoders[i]);
                        }

                        // connect to server, request initial list (step 0)
                        Utils.naviIdx = 0;

                        // start clock thread to fire MSG_FRAME_SYNC every 1/FPS second
                        mPauseLock = new Object();
                        mPaused = false;
                        mFinished = false;


                        //new Thread(new ClockThread(handler)).start();
                        setTimer();

                        break;
                    }

                    case Config.MSG_ON_FRAME_AVAILABLE: {
                        int decoderID = msg.arg1;
                        int videoID = Utils.mFrameDecoders[decoderID].videoID;
                        //System.out.println("video id: "+videoID+" has been decoded, send to frame buffer");

                        if(videoID < 0) throw new RuntimeException("MSG_ON_FRAME_AVAILABLE error");

                        // latch the data
                        FrameCache.mSurfaceTextures[decoderID].updateTexImage();
                        // cache to FBO, upper half is color lower half is depth
                        FrameCache.cache2FBO(videoID, FrameCache.mSurfaceTextures[decoderID], FrameCache.extTextures[decoderID]);
                        // release decoder awaitNewImage() lock
                        Utils.mFrameDecoders[decoderID].bFrameCached = true;

                        break;
                    }

                    case Config.MSG_FRAME_SYNC: {
                        /*if(Utils.dispBuffer.isEmpty())
                            break;

                        String posMsg = Utils.dispBuffer.get(0);
                        String pos = Utils.getPosFromMsg(posMsg);
                        Utils.naviPos = pos;
                        System.out.println("pose want to display: "+posMsg);

                        // release the display buffer once it is full
                        if((double)Utils.dispBuffer.size() > 0.8 * (double)Config.DISPLAY_CACHE_LIMIT){
                            int i;
                            for(i=0;i<(int)(0.6*(double)Config.DISPLAY_CACHE_LIMIT);i++){
                                // System.out.println("Release video msg: "+Utils.dispBuffer.get(0));
                                Utils.dispBuffer.remove(0);
                            }
                            Log.e(Config.GLOBAL_TAG, "Display CACHE RELEASED: " + i);
                        }

                        // set orientation
                        float[] ori = Utils.getOriFromMsg(posMsg);
                        Utils.coorX = Utils.calAngle(ori[0]);
                        Utils.coorY = Utils.calAngle(ori[1]);
                        Utils.naviLat = -Utils.coorX;
                        Utils.naviLon = 90 - Utils.coorY;*/

                        // check if current visible tiles are cached in FBO
                        Utils.updateDisplayPose();
                        Utils.displayVideoIDs = Utils.getCurVisibleTiles();
                        if(!FrameCache.FBOcacheContainsCurVisibleTiles()) {
                            Log.e(Config.GLOBAL_TAG, "FATAL: tiles not available for step " + Utils.naviIdx);
                            Stats.missedStep.add(Utils.naviIdx);
                            //if(Stats.lastMissTS != 0) Utils.dispBuffer.remove(0);
                            if(Stats.lastMissTS != 0) Stats.totalStall += System.currentTimeMillis() - Stats.lastMissTS;
                            Stats.lastMissTS = System.currentTimeMillis();
                            // per trace idx num of stalls
                            if(Stats.traceIdxStalls.containsKey(Utils.naviIdx))
                                Stats.traceIdxStalls.put(Utils.naviIdx, Stats.traceIdxStalls.get(Utils.naviIdx) + 1);
                            else
                                Stats.traceIdxStalls.put(Utils.naviIdx, 1);

                            // send display ACK with videoID -1 to show missed frame
                            sendDispACK(-1);
                        } else {

                            Stats.lastMissTS = 0;
                            //Utils.dispBuffer.remove(0);

                            //System.out.println("pose to display: "+Utils.naviPos + "," + Utils.coorX + "," + Utils.coorY);

                            // update display
                            updateDisplay();


                        }

                        break;
                    }

                    case Config.MSG_REPORT_STATUS: {
                        // ClockThread.pause();
                        displayTimer.purge();
                        displayTimer.cancel();
                        reportStats();
                        break;
                    }

                    default: {
                        Log.e(Config.GLOBAL_TAG, "handler thread receive unknown msg");
                        break;
                    }
                }
            }
        };
    }

    private static void reportStats(){
        Log.e(Config.GLOBAL_TAG, "Statistics report start.");

        File reportFile = new File(
                MainActivity.rootDir,
                "FPS.csv");
        try {
            reportFile.createNewFile();
            FileWriter reportFwt = new FileWriter(reportFile);
            for(String item : Stats.FPSs) {
                float fps = Float.parseFloat(item.split(" ")[1]);
                if (fps > 65.0f) fps = 65.0f;
                // fw.write(item.split(" ")[0] + " " + fps + "\n");
                reportFwt.write(fps + ",\n");
            }
            reportFwt.flush();
            reportFwt.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
