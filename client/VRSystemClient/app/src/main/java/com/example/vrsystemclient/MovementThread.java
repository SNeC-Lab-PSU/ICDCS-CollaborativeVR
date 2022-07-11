package com.example.vrsystemclient;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class MovementThread extends Thread {
    // file reader
    BufferedReader br;
    ArrayList<String> allLines;
    int lineNum = 0;

    // socket
    Socket sock;
    BufferedReader input;
    BufferedWriter output;
    Timer timer;
    public MovementThread(String name){
        super(name);

    }

    public void run(){
        System.out.println(Thread.currentThread().getName()+ ": connecting to server");
        try {
            sock = new Socket(Config.SERVER_IP, Config.MOVE_PORT);
            sock.setTcpNoDelay(true);
            input = new BufferedReader(new InputStreamReader(sock.getInputStream()));
            output = new BufferedWriter(new OutputStreamWriter(sock.getOutputStream()));
            // send private ip to the server
            output.write(sock.getLocalAddress().getHostAddress()+"\r\n");
            output.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if(Utils.teacherFlag){
            // teacher reads trace from the file and sends frame to the server in a period
            // read the trace from the file
            allLines = new ArrayList<>();
            br = new BufferedReader(new InputStreamReader(MainActivity.traceInput));
            try {
                int cnt=0;
                String curLine = br.readLine();
                while(curLine!=null) {
                    if (cnt%1==0)
                        allLines.add(curLine);
                    cnt++;
                    curLine = br.readLine();
                }
                allLines.add(null);
                br.close();
                System.out.println(Thread.currentThread().getName()+": Trace read done, in total "+(allLines.size()-1)+"lines");
            } catch (IOException e) {
                e.printStackTrace();
            }
            setTimer();
        }
        else {
            // student receive packet from the RTPpacket, do nothing

            // student always receives the trace from the server until the transmission is done
            /*try {
                while (!Utils.endTransmission) {
                    String pose = input.readLine();
                    System.out.println(Thread.currentThread().getName()+" get pose: "+pose);
                    analyPose(pose);
                }
                sock.close();
                input.close();
                output.close();
            } catch (IOException e) {
                e.printStackTrace();
            }*/
        }
    }

    //------------------------
    //Handler for timer
    //------------------------
    public void setTimer(){
        // periodically send the pose
        timer = new Timer();
        int sleepInterval = (int)(1000/(double)(Config.TARGET_FPS) - 1);

        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                sendPose();
            }
        }, 0, sleepInterval);
    }

    private void sendPose() {
        // send the pose to the server as the teacher
        String line = null;
        line = allLines.get(lineNum);
        lineNum++;
        try {
            if (line == null) {
                Config.RUN_TIMES--;
                lineNum = 0;
                if (Config.RUN_TIMES == 0) {
                    Utils.endTransmission = true;
                    timer.purge();
                    timer.cancel();
                    input.close();
                    output.close();
                    sock.close();
                    System.out.println(Thread.currentThread().getName() + ": The transmission is done");
                    // report status
                    MainActivity.mOpenGLThread.handler.sendEmptyMessage(Config.MSG_REPORT_STATUS);
                }
            } else {
                output.write(line+"\r\n");
                output.flush();
                analyPose(line);
                // System.out.println(Thread.currentThread().getName() + ": Teacher send the pose: "+line);
            }
        } catch (IOException e){
            e.printStackTrace();
        }
    }

    void analyPose(String poseLine){
        // get the navigation position and orientation from the pose message
        String indexPos = Utils.getPosFromMsg(poseLine);
        float[] orientations = Utils.getOriFromMsg(poseLine);
        float oriX = Utils.calAngle(orientations[0]);
        float oriY = Utils.calAngle(orientations[1]);
        //Utils.decodePos = indexPos;
        //Utils.decodeOriX = oriX;
        //Utils.decodeOriY = oriY;
        // predicted trace slot preceeds the real trace
        Utils.dispBuffer.put(lineNum-1,indexPos+"_"+oriX+"_"+oriY);
        //Utils.naviPos = indexPos;
        //Utils.coorX = oriX;
        //Utils.coorY = oriY;
        //Utils.naviLat = -Utils.coorX;
        //Utils.naviLon = 90 - Utils.coorY;
    }

}
