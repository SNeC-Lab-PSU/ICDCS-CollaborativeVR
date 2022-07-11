package com.example.vrsystemclient;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;

public class RTSPNetwork extends HandlerThread {
    //DatagramPacket rcvdp; //UDP packet received from the server
    DatagramSocket RTPsocket; //socket to be used to send and receive UDP packets
    static int RTP_RCV_PORT = 25000; //port where the client will receive the RTP packets

    public Handler handler;
    //byte[] buf; //buffer used to store data received from the server

    //RTSP variables
    //----------------
    //rtsp states
    final static int INIT = 0;
    final static int READY = 1;
    final static int PLAYING = 2;
    static int state; //RTSP state == INIT or READY or PLAYING
    Socket RTSPsocket; //socket used to send/receive RTSP messages
    RTPNetwork recvRTP;
    boolean done = false;
    //input and output stream filters
    static BufferedReader RTSPBufferedReader;
    static BufferedWriter RTSPBufferedWriter;
    static String VideoFileName; //video file to request to the server
    int RTSPSeqNb = 0; //Sequence number of RTSP messages within the session
    int RTSPid = 0; //ID of the RTSP session (given by the RTSP Server)

    final static String CRLF = "\r\n";

    //Video constants:
    //------------------
    static int MJPEG_TYPE = 26; //RTP payload type for MJPEG video

    //--------------------------
    //Constructor
    //--------------------------
    public RTSPNetwork(String name) {
        super(name);
        //allocate enough memory for the buffer used to receive data from the server
        //buf = new byte[65536];
        VideoFileName = "vr";
    }

    public void start() {
        super.start();

        handler = new Handler(getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    // handle the message received from the main thread
                    case Config.RTSP_SETUP: {
                        setup();
                        break;
                    }

                    case Config.RTSP_PLAY: {
                        playback();
                        break;
                    }

                    case Config.RTSP_PAUSE: {
                        pause();
                        break;
                    }

                    case Config.RTSP_TEARDOWN: {
                        tearDown();
                        break;
                    }

                    default: {
                        Log.e("vr", "handler thread receive unknown msg");
                        break;
                    }
                }
            }
        };
    }

    public void setup(){
        //get server RTSP port and IP address from the command line
        //------------------
        int RTSP_server_port = Config.RTSP_PORT;
        String ServerHost = Config.SERVER_IP;
        try {
            InetAddress ServerIPAddr = InetAddress.getByName(ServerHost);

            //Establish a TCP connection with the server to exchange RTSP messages
            //------------------
            System.out.println("Connect to the server: "+ServerHost+" port: "+RTSP_server_port);
            RTSPsocket = new Socket(ServerIPAddr, RTSP_server_port);
            //System.out.println("Local IP address: " + RTSPsocket.getLocalAddress().getHostAddress());

            //Set input and output stream filters:
            RTSPBufferedReader = new BufferedReader(new InputStreamReader(RTSPsocket.getInputStream()) );
            RTSPBufferedWriter = new BufferedWriter(new OutputStreamWriter(RTSPsocket.getOutputStream()) );

            // send private ip to the server
            RTSPBufferedWriter.write(RTSPsocket.getLocalAddress().getHostAddress()+"\r\n");
            RTSPBufferedWriter.flush();

            //init RTSP state:
            state = INIT;
            initRTSP();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void initRTSP(){
        if (state == INIT)
        {
            //Init non-blocking RTPsocket that will be used to receive data
            try{
                //construct a new DatagramSocket to receive RTP packets from the server, on port RTP_RCV_PORT
                RTPsocket = new DatagramSocket(RTP_RCV_PORT);

                //set TimeOut value of the socket to 5msec.
                RTPsocket.setSoTimeout(5);

            }
            catch (SocketException se)
            {
                System.out.println("Socket exception: "+se);
                System.exit(0);
            }

            //init RTSP sequence number
            RTSPSeqNb = 1;

            //Send SETUP message to the server
            send_RTSP_request("SETUP");

            //Wait for the response
            if (parse_server_response() != 200)
                System.out.println("Invalid Server Response");
            else
            {
                //change RTSP state and print new state
                state = READY;
                playback();
                System.out.println("New RTSP state: READY");
            }
        }//else if state != INIT then do nothing
    }

    public void playback(){
        if (state == READY)
        {
            //increase RTSP sequence number
            RTSPSeqNb++;


            //Send PLAY message to the server
            send_RTSP_request("PLAY");

            //Wait for the response
            if (parse_server_response() != 200)
                System.out.println("Invalid Server Response");
            else
            {
                //change RTSP state and print out new state
                state = PLAYING;
                System.out.println("New RTSP state: PLAYING");

                //start receiving data
                if(!done) {
                    recvData();
                    done = true;
                }
            }
        }//else if state != READY then do nothing
    }

    public void pause(){
        System.out.println("Pause the transmission !");

        if (state == PLAYING)
        {
            //increase RTSP sequence number
            RTSPSeqNb++;

            //Send PAUSE message to the server
            send_RTSP_request("PAUSE");

            //Wait for the response
            if (parse_server_response() != 200)
                System.out.println("Invalid Server Response");
            else
            {
                //change RTSP state and print out new state
                state = READY;
                System.out.println("New RTSP state: READY");

                //stop the timer

            }
        }
        //else if state != PLAYING then do nothing
    }

    public void tearDown(){
        System.out.println("Teardown the transmission !");

        //increase RTSP sequence number
        RTSPSeqNb++;


        //Send TEARDOWN message to the server
        send_RTSP_request("TEARDOWN");

        //Wait for the response
        if (parse_server_response() != 200)
            System.out.println("Invalid Server Response");
        else
        {
            //change RTSP state and print out new state
            state = INIT;
            System.out.println("New RTSP state: INIT");

            //stop the transmission and report status
            MainActivity.mOpenGLThread.handler.sendEmptyMessage(Config.MSG_REPORT_STATUS);
            Utils.endTransmission = true;
        }
    }

    void recvData(){
        recvRTP = new RTPNetwork(RTPsocket);
        recvRTP.start();
    }

    //------------------------------------
    //Parse Server Response
    //------------------------------------
    private int parse_server_response()
    {
        int reply_code = 0;

        try{
            //parse status line and extract the reply_code:
            String StatusLine = RTSPBufferedReader.readLine();
            //System.out.println("RTSP Client - Received from Server:");
            System.out.println(StatusLine);

            String[] splitStatus = StatusLine.split("\\s");
            reply_code = Integer.parseInt(splitStatus[1]);

            //if reply code is OK get and print the 2 other lines
            if (reply_code == 200)
            {
                String SeqNumLine = RTSPBufferedReader.readLine();
                System.out.println(SeqNumLine);

                String SessionLine = RTSPBufferedReader.readLine();
                System.out.println(SessionLine);

                //if state == INIT gets the Session Id from the SessionLine
                String[] splitSession = SessionLine.split("\\s");
                RTSPid = Integer.parseInt(splitSession[1]);
            }
        }
        catch(Exception ex)
        {
            System.out.println("Parse Server Exception caught: "+ex);
            System.exit(0);
        }

        return(reply_code);
    }

    //------------------------------------
    //Send RTSP Request
    //------------------------------------
    private void send_RTSP_request(String request_type)
    {
        try{
            //Use the RTSPBufferedWriter to write to the RTSP socket
            System.out.println("prepare to send message");
            //write the request line:
            RTSPBufferedWriter.write(request_type+" "+VideoFileName+" RTSP/1.0"+CRLF);

            //write the CSeq line:
            RTSPBufferedWriter.write("CSeq: "+RTSPSeqNb+CRLF);

            //check if request_type is equal to "SETUP" and in this case write the Transport: line advertising to the server the port used to receive the RTP packets RTP_RCV_PORT
            if (request_type.equals("SETUP"))
                RTSPBufferedWriter.write("Transport: RTP/UDP; client_port= "+RTP_RCV_PORT+CRLF);
                //otherwise, write the Session line from the RTSPid field
            else
                RTSPBufferedWriter.write("Session: "+RTSPid+CRLF);

            RTSPBufferedWriter.flush();
        }
        catch(Exception ex)
        {
            System.out.println("Send RTSP message Exception caught: "+ex);
            System.exit(0);
        }
    }
}
