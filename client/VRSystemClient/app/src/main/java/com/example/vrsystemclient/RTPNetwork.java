package com.example.vrsystemclient;

import android.os.Bundle;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.ArrayList;
import java.util.HashMap;

public class RTPNetwork extends Thread{
    DatagramPacket rcvdp; //UDP packet received from the server
    DatagramSocket RTPsocket; //socket to be used to send and receive UDP packets

    byte[] buf;
    int videoID;
    int recvSeqNum = 0;
    int prevTs = -1;
    int ts;

    //variables to handle packets
    int tileLen = 0;
    int recvSize = 0;
    //ArrayList<byte[]> pktBuffer = new ArrayList<>();
    HashMap<Integer,HashMap<Integer,byte[]>> tileBuffer = new HashMap<>();

    // throughput estimation
    long calBandBeginTime;
    long calBandEndTime;
    long calBandTileLen;
    //long calPktBeginTime;

    public RTPNetwork(DatagramSocket sock){
        RTPsocket = sock;
        /*try {
            System.out.println("default buffer size: "+RTPsocket.getReceiveBufferSize());
            RTPsocket.setReceiveBufferSize(1000000);
            System.out.println("current buffer size: "+RTPsocket.getReceiveBufferSize());
        }
        catch(SocketException e){
            System.out.println("set receive buffer "+e);
            e.printStackTrace();
        }*/
        //allocate enough memory for the buffer used to receive data from the server
        buf = new byte[65536];
    }
    @Override
    public void run(){
        //Construct a DatagramPacket to receive data from the UDP socket
        rcvdp = new DatagramPacket(buf, buf.length);
        // receive the first packet from the server
        try {
            //System.out.println("Default timeout value: "+RTPsocket.getSoTimeout());
            RTPsocket.setSoTimeout(0); // set infinite timeout, avoid timeout before the first packet, default value: 5
            //System.out.println("Modified timeout value: "+RTPsocket.getSoTimeout());
            RTPsocket.receive(rcvdp);
            Utils.startDisplay = true;
        } catch (IOException e) {
            e.printStackTrace();
        }

        while(!Utils.endTransmission) {
            try {
                long t1 = System.nanoTime();
                recvSeqNum++;
                //System.out.println("total recv packet number: "+recvSeqNum);
                //create an RTPpacket object from the DP
                RTPpacket rtp_packet = new RTPpacket(rcvdp.getData(), rcvdp.getLength());

                //print important header fields of the RTP packet received:
                //System.out.println("Got RTP packet with pkt id: "+rtp_packet.getpktid()+" SeqNum # " + rtp_packet.getsequencenumber() + " TimeStamp " + rtp_packet.gettimestamp() + " ms, of type " + rtp_packet.getpayloadtype());

                //print header bitstream:
                //rtp_packet.printheader();

                //get the information of the packet
                tileLen = rtp_packet.gettilelength();
                videoID = rtp_packet.getvideoid();
                float[] ori = rtp_packet.getori();
                ts = rtp_packet.gettimestamp();

                //detect whether this is next pose in the trace through time stamp
                if (ts > prevTs ) {
                    prevTs = ts;
                    //if (!Utils.teacherFlag)
                    analyPose(videoID,ori);
                    calBandBeginTime = System.nanoTime();
                    calBandTileLen = 0;
                    // handle tileBuffer is too large
                    if(tileBuffer.size()>30) {
                        tileBuffer.clear();
                        Log.d(Config.GLOBAL_TAG,"Release the RTP tile buffer");
                    }
                }

                //if tile length is 0, only RTP header is received, continue to receive next packet
                if(tileLen != 0) {

                    //get the payload bitstream from the RTPpacket object
                    int payload_length = rtp_packet.getpayloadlength();
                    byte[] payload = new byte[payload_length];
                    rtp_packet.getpayload(payload);
                    //pktBuffer.add(payload);

                    // count a packet to calculate the estimated throughput, do not count header, conservative estimation
                    calBandTileLen += payload_length;

                    //check the packet id, combine packets into a tile
                    recvSize += payload_length;
                    int pktId = rtp_packet.getpktid();
                    int endPkt = rtp_packet.getendofpkt();
                    int endTile = rtp_packet.getendoftile();
                    HashMap<Integer, byte[]> buffer = null;


                    //if (pktId == 0) calPktBeginTime = System.nanoTime();

                    //whether the buffer contains the tile
                    if (!tileBuffer.containsKey(videoID)) {
                        buffer = new HashMap<>();
                    } else {
                        buffer = tileBuffer.get(videoID);
                    }
                    //whether the current packet of the tile has been received
                    if (!buffer.containsKey(pktId)) {
                        buffer.put(pktId, payload);
                        tileBuffer.put(videoID, buffer);
                        //System.out.println("tile length:" +tileLen+", required packets: "+(int)(tileLen/Config.PKT_SIZE+1));
                        //System.out.println("number of received packets: "+buffer.size());


                        //detect whether a tile is ready
                        if ((buffer.size() - 1) * Config.PKT_SIZE <= tileLen && buffer.size() * Config.PKT_SIZE >= tileLen) {
                            String ACKmsg = null;
                            if (endTile == 1) {
                                // if all tiles of current time slot is delivered, end the estimated throughput duration
                                calBandEndTime = System.nanoTime();
                                // estimate the throughput, if it is lower than 0, invalid
                                float estThroughput = (float) (calBandTileLen - Config.PKT_SIZE) * 1000 / (calBandEndTime - calBandBeginTime); // unit: MB/s
                                float delay = (float) (System.nanoTime() - calBandBeginTime) / 1000000; // unit: ms
                                ACKmsg = 1 + "," + videoID + "," + ts + "," + endTile + "," + delay + "," + estThroughput;
                            } else
                                ACKmsg = 1 + "," + videoID + "," + ts + "," + endTile;

                            // once the tile is successfully received, let the functional thread send the packet ack of the tile
                            Message msg = MainActivity.funcNet.handler.obtainMessage(Config.SEND_PKT_ACK);
                            Bundle bundle = new Bundle();
                            bundle.putString(Config.MSG_KEY, ACKmsg);
                            msg.setData(bundle);
                            MainActivity.funcNet.handler.sendMessage(msg);

                            //System.out.println("Successfully recv tile: " + videoID);
                            byte[] tile = new byte[tileLen];
                            for (Integer tempId : buffer.keySet()) {
                                byte[] pktBits = buffer.get(tempId);
                                System.arraycopy(pktBits, 0, tile, tempId * Config.PKT_SIZE, pktBits.length);
                            }
                            tileBuffer.remove(videoID);
                        /*if (!Utils.pose2VideoID.containsKey(pos+tileID)) {
                            Utils.pose2VideoID.put(pos + tileID, videoID);
                            Utils.videoID2tileID.put(videoID, tileID);
                            System.out.println("Add video id: "+pos+","+tileID+" "+videoID);
                            sendVideoToBuffer(tile);
                        }*/
                            sendVideoToBuffer(tile);
                        }
                    }
                }
                //send ACK to the server
                /*Message msg = new Message();
                msg.what = Config.SEND_ACK;
                Bundle bundle = new Bundle();
                bundle.putString(Config.MSG_KEY, pos+"_"+tileID+"_"+pktId);
                msg.setData(bundle);
                MainActivity.funcNet.handler.sendMessage(msg);*/

                /*recvSize = 0;
                for(Integer tempId : buffer.keySet()){
                    recvSize+=buffer.get(tempId).length;
                }
                if(pktId == 0){
                    recvSize = payload_length;
                    pktBuffer.clear();
                    pktBuffer.add(payload);
                }
                else if (pktId == 63){
                    //if only one packet, no packet id 0
                    if(recvSize == tileLen) {
                        System.out.println("Successfully recv tile, tile size: " + tileLen);
                        byte[] tile = new byte[tileLen];
                        int offset = 0;
                        for (byte[] pktBits : pktBuffer){
                            System.arraycopy(pktBits,0,tile,offset,pktBits.length);
                            offset+=pktBits.length;
                        }

                        Utils.dispBuffer.add(pos+","+ori);
                        if (!Utils.pose2VideoID.containsKey(pos+tileID)) {
                            Utils.pose2VideoID.put(pos + tileID, videoID);
                            Utils.videoID2tileID.put(videoID, tileID);
                            System.out.println("Add video id: "+pos+tileID+" "+videoID);
                            sendVideoToBuffer(tile);
                        }
                    }
                    recvSize = 0;
                    pktBuffer.clear();
                }*/
                long t2 = System.nanoTime();
                long timeDiff = t2 - t1;
                //System.out.println("RTP operation time: "+timeDiff);

                //Construct a DatagramPacket to receive data from the UDP socket
                rcvdp = new DatagramPacket(buf, buf.length);

                //receive the DP from the socket:
                RTPsocket.receive(rcvdp);
            } catch (InterruptedIOException iioe) {
                //System.out.println("Nothing to read");
            } catch (IOException ioe) {
                System.out.println("Exception caught: " + ioe);
            }
        }
        RTPsocket.close();
    }

    void sendVideoToBuffer(byte[] buf_rec){
        // cache received video
        NetworkBufferPool.VideoBuffer vb = new NetworkBufferPool.VideoBuffer(videoID, buf_rec);
        NetworkBufferPool.videoCache.put(videoID, vb);
        NetworkBufferPool.videoReceived.add(videoID);
        if (!Utils.videoToDecode.contains(videoID))
            Utils.videoToDecode.add(videoID);
        //Utils.latestVideoId = videoID;
        //System.out.println(NetworkBufferPool.videoCache.size());

        // video cache limits reached, save some video into disk
        if (NetworkBufferPool.videoCache.size() > Config.VIDEO_CACHE_LIMIT) {
            NetworkBufferPool.releaseVideoCache();
        }
        //videoID++;

    }

    void analyPose(int videoID, float[] orientations){
        // get the navigation position and orientation from the pose message
        String indexPos = Utils.getPosFromMsg(Utils.videoID2pose.get(videoID));
        float oriX = Utils.calAngle(orientations[0]);
        float oriY = Utils.calAngle(orientations[1]);

        Utils.decodePos = indexPos;
        Utils.decodeOriX =oriX;
        Utils.decodeOriY =oriY;

        Utils.dispBuffer.put(ts, indexPos+"_"+oriX+"_"+oriY);
        //System.out.println("Receive the time slot: "+ts+" pose: "+Utils.dispBuffer.get(ts));

        //Utils.naviPos = indexPos;
        //Utils.coorX =oriX;
        //Utils.coorY =oriY;
        //Utils.naviLat = -Utils.coorX;
        //Utils.naviLon = 90 - Utils.coorY;


    }

}
