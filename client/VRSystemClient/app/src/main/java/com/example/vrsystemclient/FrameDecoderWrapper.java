package com.example.vrsystemclient;

import android.util.Log;

public class FrameDecoderWrapper implements Runnable {
    private FrameDecoder fExtractor;
    private FrameDecoderWrapper(FrameDecoder fExtractor) {
        this.fExtractor = fExtractor;
    }

    @Override
    public void run() {
        try {
            fExtractor.extract();
        } catch (Exception e) {
            Log.e(Config.GLOBAL_TAG, e.getMessage() );
        }
    }

    /** Entry point. */
    public static void runExtractor(FrameDecoder fExtractor) {
        FrameDecoderWrapper wrapper = new FrameDecoderWrapper(fExtractor);
        try {
            Thread th = new Thread(wrapper, "codec test");
            th.start();
            // th.join();
        } catch (Exception e) {
            Log.e(Config.GLOBAL_TAG, e.getMessage());
        }
    }
}
