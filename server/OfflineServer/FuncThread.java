import java.lang.Thread;
import java.net.*;
import java.io.*;
import java.util.*;
import java.time.Instant;



public class FuncThread extends Thread {
	private Socket socket;
	private String clientAddr;
	BufferedReader reader;
	//private Stats curStat;
	private FileWriter fwt;
	private String filename;
	private double oneWayDelayNoData;
    private int ackSize;
    private int clientNum;
    private Timer timer;

	private ArrayList<Integer> videoACKReport; 
    private ArrayList<Integer> timeACKReport; 
    private HashMap<Integer,Float> delayACKReport;
    private HashMap<Integer,Float> throughputReport;
    private ArrayList<String> poseACKReport;
    
    Stats statistics = null;
    
	public FuncThread(String name, Socket sock, String IP, BufferedReader prereader){
		// get the name of the thread
		super(name);
		
		socket = sock;
		clientAddr = IP;
        
        statistics = Utils.clientStats.get(clientAddr);
        clientNum = statistics.clientNum;
		oneWayDelayNoData = 0.0;
        ackSize = ACKpacket.HEADER_SIZE;
		videoACKReport = new ArrayList<>(); 
		timeACKReport = new ArrayList<>(); 
		poseACKReport = new ArrayList<>(); 
		delayACKReport = new HashMap<>();
		throughputReport = new HashMap<>();
		
		reader = prereader;
	}

	private void reportStats(){
        System.out.println(Thread.currentThread().getName()+" start writing the functional report");
        
        String fileName = null;
        try {
	        if (clientAddr.equals(Utils.teacherAddr)){
	            fileName = Utils.policy + "_teacher.txt";
	            System.out.println("estimated prediction probability: "+Utils.estProb);
	            // error report
	            /*File errfile = new File("./report/"+Utils.policy+"_error.txt");
	            errfile.createNewFile();
	            FileWriter errfwt = new FileWriter(errfile);
	            errfwt.write("video id that cannot be found\n");
	            for(int i=0; i< Utils.notFoundFrames.size();i++){
	            	errfwt.write(Utils.notFoundFrames.get(i)+ "\n");
	            	errfwt.flush();
	            }
	            errfwt.close();*/
	    	}
	        else{
	            fileName = Utils.policy + "_student_"+clientNum+".txt";
	        }
            String path = "report";
            File folder = new File(path);
            if (!folder.exists()) {
                System.out.print("No Folder");
                folder.mkdir();
                System.out.print("Folder created");
            }
            File file = new File("./report/"+fileName);
            file.createNewFile();
            FileWriter fwt = new FileWriter(file);
            fwt.write("slot, allocate quality, display quality,pose,delay, throughput, video size\n");
            for(int i=0; i< videoACKReport.size();i++){
                // minus the estimated one way RTT
                //long dispTime = timeACKReport.get(i) - (long) oneWayDelayNoData;
                //String dispPos = Utils.id2pose.get(videoACKReport.get(i));
                //fwt.write(dispTime + ", " + dispPos + " " + timeACKReport.get(i) + "\n");
                
            	int slot = timeACKReport.get(i);
            	int quality = videoACKReport.get(i);
            	float delay = 0;
            	if (delayACKReport.containsKey(slot)) {
            		delay = delayACKReport.get(slot);
            	}
            	int targetQuality = -1;
            	if (statistics.videoQualitySlot.containsKey(slot)) {
            		targetQuality = statistics.videoQualitySlot.get(slot);
            	}
            	//int videoSize = 0;
            	//if (statistics.videoSizeSlot.containsKey(slot))
            	//	videoSize = statistics.videoSizeSlot.get(slot);
            	float throughput = 0;
            	if (throughputReport.containsKey(slot))
            		throughput = throughputReport.get(slot);
            	int videoSize = 0;
            	if (statistics.videoSizeSlot.containsKey(slot))
            		videoSize = statistics.videoSizeSlot.get(slot);
            	String pose = poseACKReport.get(i);
            	fwt.write(slot + "," + targetQuality + "," + quality + "," + pose + "," + delay + "," + throughput + "," + videoSize + "\n");
            	fwt.flush();
            }
            fwt.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("estimated throughput: "+statistics.estThroughput);
        System.out.println(Thread.currentThread().getName()+": Report of "+fileName+" has been done");
    
    }
	
	/*private void setTimer(){
        // periodically send the pose
        timer = new Timer();
        int sleepInterval = Utils.traceSendInterval;

        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                sendPose();
            }
        }, 0, sleepInterval);
    }
	
	 private void sendPose() {
        try {
            if (Utils.netStartFlag && Utils.curPos!=null) {
        		// send the pose to the client
            	statistics.moveOutput.write(Utils.curPos+" \r\n");
            	statistics.moveOutput.flush();
            }
            if (Utils.netEndFlag) {
            	// end the timer and close the socket
            	timer.purge();
            	timer.cancel();
            	statistics.moveOutput.close();
            	statistics.moveSocket.close();
            	System.out.println(Thread.currentThread().getName() + clientAddr + ": The pose synchronization is closed.");
            }
        } catch (IOException e){
            e.printStackTrace();
        }
    }*/
	 
	@Override
	public void run(){
        System.out.println(Thread.currentThread().getName()+" New client connected, from: "+clientAddr);
        
        /*if (!clientAddr.equals(Utils.teacherAddr)) {
	        // for students, wait for the movement socket initialization
	        try {
				while (statistics.moveOutput == null) {
					Thread.sleep(1);
				}
				// start a timer to periodically send the movement to the client
				setTimer();
	        } catch (InterruptedException e) {
	        	e.printStackTrace();
	        }
        }*/
        
		try {
			socket.setTcpNoDelay(true);
			
//            int n = 10;
//            for(int i=0;i<n;i++){
//            	long t1 = System.currentTimeMillis();
//            	dOut.writeInt(1);
//            	dIn.readInt();
//            	long t2 = System.currentTimeMillis();
//            	if (i>=1) oneWayDelayNoData += t2 - t1;
//            }
//            oneWayDelayNoData = oneWayDelayNoData/(2*(n-1));
//            JavaServer.clientStats.get(clientAddr).oneWayDelay = oneWayDelayNoData;
//            JavaServer.clientStats.get(clientAddr).delayReady = true;
//            System.out.println(clientAddr+" one way delay without data: "+oneWayDelayNoData+"ms");


            while (socket != null && !Utils.netEndFlag){
            	
                //byte[] ackBytes = new byte[ackSize];
                // Receive displayed ACK or packet ACK from the client
                //input.read(ackBytes);
        		String ackLine = reader.readLine();
                long t = System.currentTimeMillis();
                
                //ACKpacket ack = new ACKpacket(ackBytes,ackSize);
                String[] tokens = null;
                if (ackLine.contains(","))
                	tokens = ackLine.split(",");
                else
                	tokens = ackLine.split(" ");
                
                int ackType = Integer.parseInt(tokens[0]);
                //int ackType = ack.getacktype();
                //int videoID = ack.gettileid();
                //int slot = ack.gettimeslot(); // use slot to determine the relationship between selected quality and displayed quality
                // also check the drift problem of client's clock
                
                if (ackType == 0) {
                	// display ACK
                	int videoID = Integer.parseInt(tokens[1]);
                    int slot = Integer.parseInt(tokens[2]);
                    String pose = null;
                	int quality = 0;
                	if (videoID != -1) {
                		//System.out.println(videoID + " " Utils.id2pose.get(videoID));
                		quality = Integer.parseInt(Utils.id2pose.get(videoID).split(",")[3]);
                		quality = Utils.qualityMap.get(quality); //convert from CRF to quality

                        float oriX = Float.parseFloat(tokens[3]);
                        float oriY = Float.parseFloat(tokens[4]);
                        String posX = Utils.id2pose.get(videoID).split(",")[0];
                        String posZ = Utils.id2pose.get(videoID).split(",")[1];
                        pose = posX+" "+posZ+" "+oriX+" "+oriY;
                        
                        // double check for students, since they do not know the prediction result
                        if (!clientAddr.equals(Utils.teacherAddr) && Utils.realPoses.containsKey(slot)){
                        	String realPose = Utils.realPoses.get(slot);
                        	int result = Utils.getPredResult(pose, realPose,0);
                        	if (result == 0) {
                        		System.out.println("Correct the quality of "+clientAddr+" to 0");
                        		quality = 0;
                        	}
                        }
                	}
                	
            		// update the prediction probability
            		//if (statistics.videoQualitySlot.containsKey(slot)) {
            		//	int selectedQuality = statistics.videoQualitySlot.get(slot);
            			//selectedQuality = Utils.qualityMap.get(selectedQuality); //convert from CRF to quality
            		//	statistics.estProbabilities[selectedQuality-1] = (Math.signum(selectedQuality) + slot * statistics.estProbabilities[selectedQuality-1])/(slot+1);
            		//}
                	
                	// calculate mean and variance of display quality
                    statistics.varQuality = (float) ((slot-1)*statistics.varQuality/slot+Math.pow(quality-statistics.aveQuality,2)/(slot+1));
                    statistics.aveQuality = (quality + slot * statistics.aveQuality)/(slot + 1);
                	
                    poseACKReport.add("("+pose+")");
                    videoACKReport.add(quality);
                    timeACKReport.add(slot);
                }
                else if (ackType == 1) {
                	// packet ACK, refer to a tile is successfully received
                	int videoID = Integer.parseInt(tokens[1]);
                    int slot = Integer.parseInt(tokens[2]);
                	//int endTile = ack.getendoftile();
                	int endTile = Integer.parseInt(tokens[3]);
                	if(endTile == 1) {
                		// all tiles in this time slot have been ACked
                		statistics.calDelayEndTime = System.nanoTime();
                        
                        // delay prediction
                        //float delay = ack.getdelay();
                		float delay = Float.parseFloat(tokens[4]);
                		// ignore invalid delay
                        if (delay > 0 && delay < 100 && statistics.videoSizeSlot.containsKey(slot)) {
                        	int size = statistics.videoSizeSlot.get(slot);
                        	//float RTT = (statistics.calDelayEndTime - statistics.videoSendTime.get(slot))/1000000.f - delay; //unit:ms
                            //delay = delay + RTT/2;
                        	float rate = (float) size / (Utils.FRAME_PERIOD*1000); // unit: MB/s
                        	statistics.fitDelay(rate, delay);
                        	//System.out.println(Thread.currentThread().getName()+" add delay: "+delay);
                        }
                    	delayACKReport.put(slot, delay);
                        //System.out.println(Thread.currentThread().getName()+" receive delay "+delay+" in time slot :"+slot );
                	
                        float estThroughput = Float.parseFloat(tokens[5]);
                        // throughput estimation
                		if (estThroughput > 0)
                			statistics.estThroughput = statistics.estThroughput*(1-statistics.expFactor) + estThroughput*statistics.expFactor;
                        throughputReport.put(slot, estThroughput);
	                    //System.out.println(Thread.currentThread().getName()+" receive estimated throughput "+estThroughput);
                	}
                    statistics.prevPose.add(videoID);
                    //System.out.println(Thread.currentThread().getName()+" receive packet ACK of videoID :"+videoID );
                }
                else if (ackType == 2) {
                	// video release ACK
                    int videoID = Integer.parseInt(tokens[1]);
                	if (statistics.prevPose.contains(videoID))
                		statistics.prevPose.remove(videoID);
                	//System.out.println(Thread.currentThread().getName()+" release video: "+videoID);
                }
                    

            }
            Utils.netEndFlag = true; // triggered multiple times
            reader.close(); // will close all underlying stream
        	System.out.println(Thread.currentThread().getName()+": client from: "+clientAddr+" has been closed.");
        } catch (IOException e) {
        	System.out.println(Thread.currentThread().getName()+" exception: " + e.getMessage());
            e.printStackTrace();
        } finally {
        	reportStats();
        }
	}

}


	

