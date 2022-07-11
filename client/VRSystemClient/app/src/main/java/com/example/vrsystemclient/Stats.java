package com.example.vrsystemclient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class Stats {
    // stall
    public static long totalStall = 0;
    public static long lastMissTS = 0;
    public static HashMap<Integer, Integer> traceIdxStalls = new HashMap<>(); // traceIdx -> num of stalls

    // FPS - calculated every 600 display
    public static int nDisplayed = 0;
    public static long startTS = 0;
    public static ArrayList<String> FPSs = new ArrayList<>();

    // accuracy
    public static int nTotalDisplayed = 0;
    public static HashSet<Integer> missedStep = new HashSet<>();

    //
    public static HashSet<Integer> tileRecvd = new HashSet<>();
    public static HashSet<Integer> tileDispd = new HashSet<>();

    // display stats
    public static ArrayList<String> frameDisp = new ArrayList<>();

    // L3 swap
    public static int nSwap = 0;
}
