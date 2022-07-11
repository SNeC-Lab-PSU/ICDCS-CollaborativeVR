package com.example.vrsystemclient;


import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;

import java.nio.ByteBuffer;


public class FrameDecoder {

    private int decoderID;

    private MediaCodec decoder;

    private boolean outputDone;
    private boolean inputDone;

    public int videoID;
    public int ninput;
    public boolean bFrameCached;
    public NetworkBufferPool.VideoBuffer vb;


    public FrameDecoder(int decoderID) {
        bFrameCached = false;
        this.decoderID = decoderID;
        decoder = null;
        setDecoderIdle();
    }


    public boolean idle() {
        return inputDone && outputDone && videoID == -1;
    }


    public boolean setDecoderBusySucc() {
        videoID = NetworkBufferPool.nextVideoToDecode();
        if(videoID == -1) return false;
        //System.out.println("next video to decode: "+videoID);
        if(NetworkBufferPool.videoCache.containsKey(videoID)) {
            vb = NetworkBufferPool.videoCache.get(videoID);
            //System.out.println("Feed video "+videoID+" into codec");
        } else {
            // already released to disk
            //vb = new NetworkBufferPool.VideoBuffer(videoID);
            //Log.e(Config.GLOBAL_TAG, "getVideoFromDisk: " + videoID );
            //Stats.nSwap += 1;
            Log.e(Config.GLOBAL_TAG, "No video needs to be decoded.");
        }

        ninput = 0;
        inputDone = false;
        outputDone = false;

        // decode latency
        // Stats.decodeStats.put(videoID, System.currentTimeMillis());

        return true;
    }


    public void setDecoderIdle() {
        videoID = -1;
        inputDone = true;
        outputDone = true;
    }


    public void extract() {
        try {
            String mime = Utils.megaFmtTemplate.getString(MediaFormat.KEY_MIME);
            decoder = MediaCodec.createDecoderByType(mime);
            decoder.configure(Utils.megaFmtTemplate, FrameCache.mSurfaces[decoderID], null, 0);

            decoder.start();
            doExtract(decoder);

        } catch (Exception e) {
            Log.e(Config.GLOBAL_TAG, "videoID=" + videoID);
            Log.e(Config.GLOBAL_TAG, NetworkBufferPool.videoCache.containsKey(videoID) + "");
            Log.e(Config.GLOBAL_TAG, (NetworkBufferPool.videoCache.get(videoID) == null) + "");
            throw new RuntimeException("decoder " + decoderID + " decodes video " + videoID + " error:" + e.getLocalizedMessage());

        } finally {
            // release everything we grabbed
            if (decoder != null) {
                decoder.stop();
                decoder.release();
            }
        }
    }


    private void doExtract(MediaCodec decoder) throws InterruptedException {
        final int TIMEOUT_USEC = 10000;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        System.out.println("decoder start to work");
        while (true) {
            if(idle()) {
                // try to assign work to decoder
                if(!setDecoderBusySucc()) {
                    Thread.sleep(1); // prevent busy spin
                    continue;
                }
            }
            //System.out.println("decoder works");
            // Feed data to the decoder.
            if (!inputDone) {
                int inputBufIndex = decoder.dequeueInputBuffer(TIMEOUT_USEC);
                if (inputBufIndex >= 0) {
                    ByteBuffer inputBuf = decoder.getInputBuffer(inputBufIndex);

                    // copy one frame from vb and write into inputBuf
                    int frameSize = NetworkBufferPool.readFrame(inputBuf, vb);
                    // System.out.println("decoder input: "+frameSize);
                    if (frameSize == NetworkBufferPool.ALL_FRAME_READ) {
                        // reset vb pos, since hashmap.get() return a reference this will also update the vb in hashmap
                        vb.resetPos();
                        // End of stream -- send empty frame with EOS flag set.
                        decoder.queueInputBuffer(inputBufIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputDone = true;
                        // Log.d(Config.GLOBAL_TAG, "Input EOS");
                    } else {
                        decoder.queueInputBuffer(inputBufIndex, 0, frameSize, 0, 0 /*flags*/);
                        ninput++;
                    }
                } else {
                    // Log.e(Config.GLOBAL_TAG, "input buffer not available:" + inputBufIndex);
                }
            }


            // try to dequeueOutputBuffer
            if (!outputDone) {
                int decoderStatus = decoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
                // System.out.println("decoder status: "+decoderStatus);
                if (decoderStatus >= 0) {
                    boolean doRender = (info.size != 0);
                    // System.out.println("buffer info size: "+info.size);
                    decoder.releaseOutputBuffer(decoderStatus, doRender);

                    if (doRender) {
                        // As soon as we call releaseOutputBuffer, the buffer will be forwarded
                        // to SurfaceTexture to convert to a texture.  The API doesn't guarantee
                        // that the texture will be available before the call returns, so we
                        // need to wait for the onFrameAvailable callback to fire.
                        awaitNewImage();
                        // Log.e(Config.GLOBAL_TAG, "decoder " + decoderID + " finish caching video " + videoID);
                    }

                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        // Log.e(Config.GLOBAL_TAG, "decoder " + decoderID + " finish decoding video " + videoID);
                        outputDone = true;
                        decoder.flush();
                        // unlock this video
                        System.out.println("finish decode video: "+videoID);
                        NetworkBufferPool.finishDecodeVideo(videoID);
                        // release this decoder
                        setDecoderIdle();
                    }

                } else if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // Log.e(Config.GLOBAL_TAG, "INFO_TRY_AGAIN_LATER");
                } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // Log.e(Config.GLOBAL_TAG, "INFO_OUTPUT_FORMAT_CHANGED");
                } else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    // Log.e(Config.GLOBAL_TAG, "INFO_OUTPUT_BUFFERS_CHANGED");
                } else {
                    Log.e(Config.GLOBAL_TAG, "unexpected result from decoder.dequeueOutputBuffer: " + decoderStatus);
                    throw new RuntimeException("unexpected result from decoder.dequeueOutputBuffer: " + decoderStatus);
                }
            }
        }
    }


    private void awaitNewImage() {
        while(!bFrameCached) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                throw new RuntimeException("decoder " + decoderID + " - sleep error");
            }
        }
        bFrameCached = false;
    }
}
