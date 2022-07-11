package com.example.vrsystemclient;

import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.Socket;

public class FunctionThread extends HandlerThread {
    public Handler handler;
    public static boolean isConnected = false;
    private static Socket client = null;
    private OutputStream out;
    private DataOutputStream dOut;
    private BufferedWriter writer;
    private InputStream input;
    private DataInputStream dIn;
    private BufferedReader reader;

    public FunctionThread(String name) {
        super(name);
        isConnected = false;
    }

    private void connectToServer() {
        try {
            Log.e(Config.GLOBAL_TAG, "TCP thread: connecting to server");
            client = new Socket(Config.SERVER_IP, Config.FUNC_PORT);
            client.setTcpNoDelay(true);

            out = client.getOutputStream();
            writer = new BufferedWriter(new OutputStreamWriter(out) );
            dOut = new DataOutputStream(out);
            input = client.getInputStream();
            dIn = new DataInputStream(input);
            reader = new BufferedReader(new InputStreamReader(input) );

            // send private ip to the server
            writer.write(client.getLocalAddress().getHostAddress()+"\r\n");
            writer.flush();

            if (client.isConnected()) {
                isConnected = true;
            }

//            int n = 10;
//            for(int i=0;i<n;i++){
//                dIn.readInt();
//                dOut.writeInt(1);
//            }

        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }

        Log.e(Config.GLOBAL_TAG, "Functional TCP thread: server connected, start waiting for msg.");
    }

    public void start() {
        super.start();

        handler = new Handler(getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                // 0: display ACK, 1: packet ACK, 2: video release ACK
                if (msg.what == Config.CONNECT) {
                    if (!isConnected)
                        connectToServer();

                } else if (msg.what == Config.SEND_DISP_ACK) {
                    // receive video ID, time slot
                    Bundle bundle = msg.getData();
                    String packetACK = bundle.getString(Config.MSG_KEY);
                    //String[] tokens = packetACK.split(",");
                    //int videoID = Integer.parseInt(tokens[0]);
                    //int slot = Integer.parseInt(tokens[1]);

                    //Builds an ACKpacket object containing the acknowledgement
                    //ACKpacket ack_packet = new ACKpacket(0,videoID,slot,0,0,Utils.estThroughput);
                    //get to total length of the ack packet to send
                    //int packet_length = ack_packet.getlength();
                    //retrieve the packet bitstream and store it in an array of bytes
                    //byte[] packet_bits = new byte[packet_length];
                    //ack_packet.getpacket(packet_bits);

                    try {
                        //out.write(packet_bits);
                        writer.write(packetACK + Utils.CRLF);
                        writer.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                        MainActivity.funcNet.handler.sendEmptyMessage(1);
                    }
                    //Utils.estThroughput = 0.0f;
                } else if (msg.what == Config.SEND_PKT_ACK) {
                    // receive video ID, time slot and delay
                    Bundle bundle = msg.getData();
                    String packetACK = bundle.getString(Config.MSG_KEY);
                    //String[] tokens = packetACK.split(",");
                    //int videoID = Integer.parseInt(tokens[0]);
                    //int slot = Integer.parseInt(tokens[1]);
                    //float delay = Float.parseFloat(tokens[2]);
                    //int endTile = Integer.parseInt(tokens[3]);

                    //Builds an ACKpacket object containing the acknowledgement
                    //ACKpacket ack_packet = new ACKpacket(1,videoID,slot,delay,endTile,1.0f);
                    //get to total length of the ack packet to send
                    //int packet_length = ack_packet.getlength();
                    //retrieve the packet bitstream and store it in an array of bytes
                    //byte[] packet_bits = new byte[packet_length];
                    //ack_packet.getpacket(packet_bits);

                    try {
                        //byte[] ackMsg =  poseACK.getBytes();
                        //dOut.writeUTF(poseACK);
                        //out.write(packet_bits);
                        writer.write(packetACK + Utils.CRLF);
                        writer.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                        MainActivity.funcNet.handler.sendEmptyMessage(1);
                    }

                } else if (msg.what == Config.SEND_NACK) {
                    // receive video ID, time slot
                    Bundle bundle = msg.getData();
                    String packetACK = bundle.getString(Config.MSG_KEY);
                    //String[] tokens = packetACK.split(",");
                    //int videoID = Integer.parseInt(tokens[0]);
                    //int slot = Integer.parseInt(tokens[1]);

                    //Builds an ACKpacket object containing the acknowledgement
                    //ACKpacket ack_packet = new ACKpacket(2,videoID,slot,0,0,1.0f);
                    //get to total length of the ack packet to send
                    //int packet_length = ack_packet.getlength();
                    //retrieve the packet bitstream and store it in an array of bytes
                    //byte[] packet_bits = new byte[packet_length];
                    //ack_packet.getpacket(packet_bits);

                    try {
                        //byte[] ackMsg =  poseACK.getBytes();
                        //dOut.writeUTF(poseACK);
                        //out.write(packet_bits);
                        writer.write(packetACK + Utils.CRLF);
                        writer.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                        MainActivity.funcNet.handler.sendEmptyMessage(1);
                    }

                } else {
                    try {
                        Log.d(Config.GLOBAL_TAG, "Functional TCP thread: closing TCP connection");
                        out.close();
                        dOut.close();
                        writer.close();
                        client.close();
                        input.close();
                        dIn.close();
                        reader.close();
                        // report status
                        if (!Utils.teacherFlag)
                            MainActivity.mOpenGLThread.handler.sendEmptyMessage(Config.MSG_REPORT_STATUS);
                    } catch (Exception e) {
                        //
                    }
                }
            }
        };
    }

}
