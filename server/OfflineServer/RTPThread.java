/* ------------------
   Server
   usage: java Server [RTSP listening port]
   ---------------------- */

import java.io.*;
import java.net.*;
import java.util.*;

public class RTPThread extends Thread {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	//RTP variables:
	// ----------------
	DatagramSocket RTPsocket; // socket to be used to send and receive UDP packets
	DatagramPacket senddp; // UDP packet containing the video frames
	static int RTSP_PORT = 8888;
	int pkt_length = 1400;

	InetAddress exClientIPAddr; // external Client IP address
	String clientAddr; // received IP address to locate the statistics
	int RTP_dest_port = 0; // destination port for RTP packets (given by the RTSP Client)
	int Ssrc;

	// Video variables:
	// ----------------
	int imagenb = 0; // image nb of the image currently transmitted
	static int MJPEG_TYPE = 26; // RTP payload type for MJPEG video
	static int VIDEO_LENGTH = 500; // length of the video in frames
	int slot = 0;
	//long pktGap = 150000;
	Stats statistics = null;
	int quality;
	int lastQuality;

	Timer timer; // timer used to send the images at the video frame rate
	byte[] buf; // buffer used to store the images to send to the client
	long lastSendTime;
	String lastSendPose = null;

	// RTSP variables
	// ----------------
	// rtsp states
	final static int INIT = 0;
	final static int READY = 1;
	final static int PLAYING = 2;
	// rtsp message types
	final static int SETUP = 3;
	final static int PLAY = 4;
	final static int PAUSE = 5;
	final static int TEARDOWN = 6;

	static int state; // RTSP Server state == INIT or READY or PLAY
	Socket RTSPsocket; // socket used to send/receive RTSP messages
	// input and output stream filters
	static BufferedReader RTSPBufferedReader;
	static BufferedWriter RTSPBufferedWriter;
	static String VideoFileName; // video file requested from the client
	static int RTSP_ID = 123456; // ID of the RTSP session
	int RTSPSeqNb = 0; // Sequence number of RTSP messages within the session

	final static String CRLF = "\r\n";

	// --------------------------------
	// Constructor
	// --------------------------------
	public RTPThread(String name, Socket sock, int ssrc, String IP, BufferedReader prereader) {
		super(name);
		Ssrc = ssrc;
		// allocate memory for the sending buffer
		buf = new byte[65536];

		RTSPsocket = sock;
		clientAddr = IP;

		// Get Client IP address
		exClientIPAddr = RTSPsocket.getInetAddress();
		// Get the Stats class for current client
		statistics = Utils.clientStats.get(clientAddr);
		
		RTSPBufferedReader = prereader;
	}

	byte[] getFrame(int videoID) {
		// System.out.print("Client " + index + " Try to get frame:"+pos);
		byte[] wholeImage = null;
		if (Utils.map.containsKey(videoID)) {
			wholeImage = Utils.map.get(videoID);
			// System.out.print(" Successful ");
		} else {
			System.out.println(Thread.currentThread().getName() + " Cannot find tile: " + Utils.id2pose.get(videoID));
			Utils.notFoundFrames.add(String.valueOf(videoID)); // add video ID which cannot be found corresponding frame
		}

		return wholeImage;
	}

	// ------------------------------------
	// main
	// ------------------------------------
	public void run() {


		// Initiate RTSPstate
		state = INIT;

		try {
			// Set input and output stream filters: input get from main thread
			RTSPBufferedWriter = new BufferedWriter(new OutputStreamWriter(RTSPsocket.getOutputStream()));
		} catch (IOException e1) {
			e1.printStackTrace();
		}

		// Wait for the SETUP message from the client
		int request_type;
		boolean done = false;
		while (!done) {
			request_type = parse_RTSP_request(); // blocking

			if (request_type == SETUP) {
				done = true;

				// update RTSP state
				state = READY;
				System.out.println(Thread.currentThread().getName() + " New RTSP state: READY");

				// Send response
				send_RTSP_response();
				try {
					// init RTP socket
					RTPsocket = new DatagramSocket();
				} catch (SocketException e) {
					e.printStackTrace();
				}
			}
		}

		// loop to handle RTSP requests
		while (true) {
			// parse the request
			request_type = parse_RTSP_request(); // blocking

			if ((request_type == PLAY) && (state == READY)) {
				// send back response
				send_RTSP_response();
				try {
					// start transmitting video when receive the first trace from the teacher
					//while (!Utils.netStartFlag) {
					//	Thread.sleep(1);
					//}
					//setTimer();
					while (true) {
						// wait for receiving new trace from the teacher
						if (slot == Utils.timeSlot) {
							Thread.sleep(1);
							continue;
						}
						if (Utils.netEndFlag) break;
						slot = Utils.timeSlot;
						arrangeSend();
					}
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				// update state
				state = PLAYING;
				System.out.println(Thread.currentThread().getName() + " New RTSP state: PLAYING");
				//always send frame once there are available bandwidth
				//while (true) {
				//	arrangeSend();
				//	if (Utils.netEndFlag) break;
				//}
			} else if ((request_type == PAUSE) && (state == PLAYING)) {
				// send back response
				send_RTSP_response();
				// pause transmitting
				timer.cancel();
				// update state
				state = READY;
				System.out.println(Thread.currentThread().getName() + " New RTSP state: READY");
			} else if (request_type == TEARDOWN) {
				// send back response
				send_RTSP_response();
				// stop transmitting
				timer.cancel();
				// close sockets
				try {
					RTSPsocket.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
				RTPsocket.close();

				//System.exit(0);
			}
		}
	}

	// ------------------------
	// Handler for timer
	// ------------------------
	public void setTimer() {
		timer = new Timer();
		int sleepInterval = (int) (1000 / (double) (Utils.TARGET_FPS) - 1);

		timer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				arrangeSend();
				if (Utils.netEndFlag) {
					timer.cancel();
					timer.purge();
					System.out.println(
							Thread.currentThread().getName() + ": RTP transmission has been closed.");
				}
			}

		}, 0, sleepInterval);
	}

	private void arrangeSend() {
		long curTime = System.currentTimeMillis();
		//System.out.println(Thread.currentThread().getName() + " Frame Send Interval: " + (curTime - lastSendTime));
		
		for (int i=0;i<1;i++) {
			// send the tiles corresponding to the predicted pose to the clients
			String line = Utils.predPos[i];
			// start time to calculate the delay
			statistics.calDelayStartTime = System.nanoTime();
			int totalTileSize = 0;
			// transmit current frame
		
			//System.out.println(Thread.currentThread().getName() + " Send pose: " + line);
			// the quality is determined by the rate control algorithm, convert from quality
			// level to CRF
			quality = Utils.getCRF(statistics.curQuality);
			String indexPos = Utils.getPosIndex(line);
			float[] ori = Utils.getOri(line);
			String coor = "(" + (int) Utils.calAngle(ori[0]) + "," + (int) Utils.calAngle(ori[1]) + "," + 0 + ")";
			ArrayList<Integer> tiles = Utils.predTileTable.get(coor);
			for (int tile_id : tiles) {
				int endTile = 0;
				if (tiles.indexOf(tile_id) == tiles.size() - 1)
					endTile = 1;
				// tempSendTiles.add(indexPos+tile_id);
				// send empty packet with only header when the tile has already been transmitted
				int videoID = Utils.getVideoID(indexPos, tile_id, quality);
				if (videoID == -1) {
					System.out.println(Thread.currentThread().getName() + " Cannot find the pose: " + indexPos + ","
							+ tile_id + "," + quality);
					continue;
				}

				int tileLen = sendFrame(videoID, endTile, ori);
				totalTileSize += tileLen;
			}
			//timeLine++;
		
			// add total tile size to statistics
			statistics.videoSendTime.put(slot, statistics.calDelayStartTime);
			statistics.videoSizeSlot.put(slot, totalTileSize);
			statistics.videoQualitySlot.put(slot, Utils.qualityMap.get(quality));
			lastSendTime = curTime;
			lastSendPose = line;
			lastQuality = quality;
			
			// if enter new time slot, restart the loop and transmit new predicted tiles
			//if(slot!=Utils.timeSlot) {
			//	System.out.println("Slot: "+slot+" send "+i+"frames");
			//	break;
			//}
		}
	}

	public int sendFrame(int videoID, int endTile, float[] ori) {
		try {
			// once the tile is ACKed, only send the header to synchronize the pose
			if (statistics.prevPose.contains(videoID)) {
				imagenb++;
				byte[] emptyBytes = new byte[1];
				RTPpacket rtp_packet = new RTPpacket(MJPEG_TYPE, imagenb, slot, Ssrc, emptyBytes, 0, 0, 
						0, videoID, endTile, 0, 0, ori[0], ori[1]);
				//get to total length of the full rtp packet to send
				int packet_length = rtp_packet.getlength();
		
				//retrieve the packet bitstream and store it in an array of bytes
				byte[] packet_bits = new byte[packet_length];
				rtp_packet.getpacket(packet_bits);
		
				//send the packet as a DatagramPacket over the UDP socket 
				senddp = new DatagramPacket(packet_bits, packet_length, exClientIPAddr, RTP_dest_port);
				RTPsocket.send(senddp);
				//System.out.println(Thread.currentThread().getName() + " Tile Already Transmitted");
				return 0;
			}
			
			// System.out.println(Thread.currentThread().getName()+" seq num:
			// "+imagenb+"Send tile: "+videoID);
			byte[] wholeImage = getFrame(videoID);
			if (wholeImage == null) return 0;
			int image_length = wholeImage.length;

			int pkt_id = 0;
			int sentPktSize = 0;
			// calculate the time to send a tile
			long t1 = System.currentTimeMillis();
			while (sentPktSize < image_length) {

				// update current imagenb
				imagenb++;
				int endPkt = 0;
				int curPkt_length = 0;
				if (sentPktSize + pkt_length < image_length)
					curPkt_length = pkt_length;
				else {
					curPkt_length = image_length - sentPktSize;
					// pkt_id = 63;
					endPkt = 1;
				}
				// Builds an RTPpacket object containing the frame
				RTPpacket rtp_packet = new RTPpacket(MJPEG_TYPE, imagenb, slot, Ssrc, wholeImage,
						curPkt_length, sentPktSize, image_length, videoID, endTile, pkt_id, endPkt,ori[0],ori[1]);

				// get to total length of the full rtp packet to send
				int packet_length = rtp_packet.getlength();

				// retrieve the packet bitstream and store it in an array of bytes
				byte[] packet_bits = new byte[packet_length];
				rtp_packet.getpacket(packet_bits);

				// send the packet as a DatagramPacket over the UDP socket
				senddp = new DatagramPacket(packet_bits, packet_length, exClientIPAddr, RTP_dest_port);
				RTPsocket.send(senddp);

				// System.out.println("Send frame #"+imagenb);
				// print the header bitstream
				// rtp_packet.printheader();

				// update packet related variables
				pkt_id++;
				sentPktSize += curPkt_length;
				
				//long initTime = System.nanoTime();
				//while (System.nanoTime() - initTime < pktGap)
				//	;
			}
			long t2 = System.currentTimeMillis();
			long diff = t2 - t1;
			//System.out.println(Thread.currentThread().getName() + " tile quality: " + quality + "size:" + image_length
			//		+ " packet num: " + pkt_id + " send tile time used: " + diff);
			// wait for a short time after stream a tile
			// Thread.sleep(1);

			/*
			 * //retransmission if(lastSendPose!=null ) { String indexPos =
			 * Utils.getPosIndex(lastSendPose); float[] ori = Utils.getOri(lastSendPose);
			 * String coor =
			 * "("+(int)Utils.calAngle(ori[0])+","+(int)Utils.calAngle(ori[1])+","+0+")";
			 * ArrayList<Integer> tiles = Utils.tileTable.get(coor);
			 * 
			 * for(int tile_id : tiles) { int videoID = Utils.getVideoID(indexPos, tile_id,
			 * lastQuality); if (videoID == -1) {
			 * System.out.println(Thread.currentThread().getName()+" Cannot find the pose: "
			 * +indexPos+","+tile_id+","+quality); continue; }
			 * if(statistics.reTranMap.containsKey(videoID)&&!statistics.reTranMap.get(
			 * videoID).isEmpty()) {
			 * System.out.println(Thread.currentThread().getName()+" Retransmit pose: "
			 * +indexPos+" tile: "+tile_id); byte[] wholeImage = getFrame(videoID); int
			 * image_length = wholeImage.length;
			 * 
			 * statistics.reTranMapLock = true; ArrayList<Integer> packets = new
			 * ArrayList<>(statistics.reTranMap.get(videoID)); statistics.reTranMapLock =
			 * false; if (packets.isEmpty()) continue; for(int pktID : packets) { //update
			 * current imagenb imagenb++;
			 * 
			 * System.out.println(Thread.currentThread().getName()+" retransmit packet: "
			 * +pktID);
			 * 
			 * //get packet related parameters int curPkt_length = 0; int sentPktSize =
			 * pktID * pkt_length; if (sentPktSize + pkt_length < image_length )
			 * curPkt_length = pkt_length; else { curPkt_length = image_length -
			 * sentPktSize; //pkt_id = 63; } //Builds an RTPpacket object containing the
			 * frame RTPpacket rtp_packet = new RTPpacket(MJPEG_TYPE, imagenb,
			 * (timeLine-2)*FRAME_PERIOD, Ssrc, wholeImage, curPkt_length, sentPktSize,
			 * image_length, videoID, 0, pktID, 0); //get to total length of the full rtp
			 * packet to send int packet_length = rtp_packet.getlength();
			 * 
			 * //retrieve the packet bitstream and store it in an array of bytes byte[]
			 * packet_bits = new byte[packet_length]; rtp_packet.getpacket(packet_bits);
			 * 
			 * //send the packet as a DatagramPacket over the UDP socket senddp = new
			 * DatagramPacket(packet_bits, packet_length, ClientIPAddr, RTP_dest_port);
			 * RTPsocket.send(senddp);
			 * 
			 * long initTime = System.nanoTime(); while(System.nanoTime()-initTime <
			 * pktGap); } } } }
			 */
			return image_length;
		} catch (Exception ex) {
			System.out.println(Thread.currentThread().getName() + " Send Frame Exception caught: " + ex);
			ex.printStackTrace();
			//System.exit(0);
		}
		return 0;
	}

	// ------------------------------------
	// Parse RTSP Request
	// ------------------------------------
	private int parse_RTSP_request() {
		int request_type = -1;
		try {
			// parse request line and extract the request_type:
			String RequestLine = RTSPBufferedReader.readLine();
			// System.out.println("RTSP Server - Received from Client:");
			System.out.println(Thread.currentThread().getName() + RequestLine);

			String[] splitRequest = RequestLine.split("\\s");
			String request_type_string = splitRequest[0];

			// convert to request_type structure:
			if ((new String(request_type_string)).compareTo("SETUP") == 0)
				request_type = SETUP;
			else if ((new String(request_type_string)).compareTo("PLAY") == 0)
				request_type = PLAY;
			else if ((new String(request_type_string)).compareTo("PAUSE") == 0)
				request_type = PAUSE;
			else if ((new String(request_type_string)).compareTo("TEARDOWN") == 0)
				request_type = TEARDOWN;

			if (request_type == SETUP) {
				// extract VideoFileName from RequestLine
				VideoFileName = splitRequest[1];
			}

			// parse the SeqNumLine and extract CSeq field
			String SeqNumLine = RTSPBufferedReader.readLine();
			System.out.println(Thread.currentThread().getName() + SeqNumLine);
			String[] splitSeqNum = SeqNumLine.split("\\s");
			RTSPSeqNb = Integer.parseInt(splitSeqNum[1]);

			// get LastLine
			String LastLine = RTSPBufferedReader.readLine();
			System.out.println(Thread.currentThread().getName() + LastLine);

			if (request_type == SETUP) {
				// extract RTP_dest_port from LastLine
				String[] splitLast = LastLine.split("\\s");
				
				if (Utils.portForward.containsKey(clientAddr))
					RTP_dest_port = Utils.portForward.get(clientAddr);
				else
					RTP_dest_port = Integer.parseInt(splitLast[3]);
			}
			// else LastLine will be the SessionId line ... do not check for now.
		} catch (Exception ex) {
			System.out.println(Thread.currentThread().getName() + " Parse RTSP message Exception caught: " + ex);
			ex.printStackTrace();
			//System.exit(0);
		}
		return (request_type);
	}

	// ------------------------------------
	// Send RTSP Response
	// ------------------------------------
	private void send_RTSP_response() {
		try {
			RTSPBufferedWriter.write("RTSP/1.0 200 OK" + CRLF);
			RTSPBufferedWriter.write("CSeq: " + RTSPSeqNb + CRLF);
			RTSPBufferedWriter.write("Session: " + RTSP_ID + CRLF);
			RTSPBufferedWriter.flush();
			// System.out.println("RTSP Server - Sent response to Client.");
		} catch (Exception ex) {
			System.out.println(Thread.currentThread().getName() + " Send RTSP message Exception caught: " + ex);
			ex.printStackTrace();
			//System.exit(0);
		}
	}
}