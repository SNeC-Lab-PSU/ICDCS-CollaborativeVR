import java.util.*;

public class Utils {
	// handle tiles map
	public static HashMap<Integer,String> id2pose = new HashMap<>();
	public static HashMap<String,Integer> pose2id = new HashMap<>();
	public static HashMap<Integer,Integer> id2size = new HashMap<>();
	public static HashMap<Integer,byte[]> map = new HashMap<>();
    public static HashMap<String,ArrayList<Integer>> predTileTable = new HashMap<>();
    public static HashMap<String,ArrayList<Integer>> reqTileTable = new HashMap<>();

    // locate the statistics for each client by IP
    public static HashMap<String, Stats> clientStats = new HashMap<>(); 

    // handler the general statistics
    public static volatile String curPos = null;
    public static volatile String[] predPos = null;
    public static HashMap<Integer,String> realPoses = new HashMap<>();
    public static String teacherAddr = null; //"/192.168.1.6";//"/172.20.241.88";
    //public static boolean netStartFlag = false; // triggered by the first received teacher's trace
    public static boolean netEndFlag = false; // triggered by the teacher's last trace
    public static float granular = 5f;
    public static int traceSendInterval = 3;
    public final static int qualityLevel = 6;
    public final static int[] availQuality = {15,19,23,27,31,35};
    // map quality from 19,23,27,31 to 4,3,2,1
    public final static HashMap<Integer,Integer> qualityMap = new HashMap<Integer,Integer>() {{
        put(15, 6);
    	put(19, 5);
        put(23, 4);
        put(27, 3);
        put(31, 2);
        put(35, 1);
    }};
    public static int timeSlot = 0;
	public static int TARGET_FPS = 60;
	public static int FRAME_PERIOD = 1000 / Utils.TARGET_FPS - 1; // Frame period of the video to stream, in ms
	
    // port forwarding 
	public final static HashMap<String,Integer> portForward = new HashMap<String,Integer>() {{
        put("192.168.1.6", 65506);
        put("192.168.1.10", 65510);
        put("192.168.1.13", 65513);
        put("192.168.1.9", 65509);
        put("192.168.1.11", 65511);
        put("192.168.1.15", 65515);
        put("192.168.1.16", 65516);
        put("192.168.1.17", 65517);
        put("192.168.1.19", 65519);
    }};
    
    // statistics to optimization
    public static float ALPHA = 0.1f;
    public static float GAMMA = 0.5f;
    public static float RATE_LIMIT_SERVER = (float) 800/8; // unit: MB/s
    public static int policy = 0;
    // given the throughput limitation
    public final static HashMap<String,Float> throughputMap = new HashMap<String,Float>() {{
        put("/192.168.1.6", 40/8f);
        put("/192.168.1.10", 20/8f);
        put("/192.168.1.13", 60/8f);
        put("/192.168.1.9", 75/8f);
        put("/192.168.1.11", 50/8f);
    }};
    // probability estimation
    public static float estProb = 1;
    
	// handle the problems met during transmission
    // if have pose, cannot find id, the pose will be stored in this array
    // if have id, cannot find frame, the video id will be stored in this array
	public static ArrayList<String> notFoundFrames = new ArrayList<>();

	public static float calBandwidth(String indexPos, ArrayList<Integer> tiles, int quality) {
		quality = Utils.getCRF(quality); //convert from quality level to CRF
		int totalSize = 0;
    	for(int tile_id : tiles) {
    		int videoID =  Utils.getVideoID(indexPos, tile_id, quality);
    		if (Utils.id2size.containsKey(videoID))
    			totalSize += Utils.id2size.get(videoID);
    	}
    	// considering the reservation for decoding
    	return (float) totalSize / ((Utils.FRAME_PERIOD)*1000); // unit: MB/s
	}
	
	public static int getCRF(int qualityLevel) {
		return Utils.availQuality[Utils.qualityLevel-qualityLevel];
	}
	
	public static int getPredResult(String predPose, String realPose, int flag) {
		int result = 1;
		
		// get requested tiles use real pose
        ArrayList<Integer> requestTiles = new ArrayList<>();
        String indexPos = Utils.getPosIndex(realPose);
		float[] ori = Utils.getOri(realPose);
		String coor = "(" + (int) Utils.calAngle(ori[0]) + "," + (int) Utils.calAngle(ori[1]) + "," + 0 + ")";
		ArrayList<Integer> tiles = Utils.reqTileTable.get(coor);
		for (int tile_id : tiles) {
			int videoID = Utils.getVideoID(indexPos, tile_id, 19);
			requestTiles.add(videoID);
		}
		
		// get transmitted tiles use predicted pose
        ArrayList<Integer> transTiles = new ArrayList<>();
		indexPos = Utils.getPosIndex(predPose);
		ori = Utils.getOri(predPose);
		coor = "(" + (int) Utils.calAngle(ori[0]) + "," + (int) Utils.calAngle(ori[1]) + "," + 0 + ")";
		if (flag == 1) // for transmission
			tiles = Utils.predTileTable.get(coor);
		else if (flag == 0) // for display validation
			tiles = Utils.reqTileTable.get(coor);
		for (int tile_id : tiles) {
			int videoID = Utils.getVideoID(indexPos, tile_id, 19);
			transTiles.add(videoID);
		}
		
		//detect whether all requested tiles are transmitted 
		for(int tileID : requestTiles){
            if(!transTiles.contains(tileID)){
                result = 0;
                break;
            }
        }
		return result;
	}
	
	public static int getVideoID(String indexPos, int tileID, int quality) {
		String pose = indexPos+","+tileID+","+quality;
		if (!Utils.pose2id.containsKey(pose)) 
		{
			Utils.notFoundFrames.add(pose);
			return -1;
		}
		int videoID = Utils.pose2id.get(pose);
		return videoID;
	}
	
	public static double calPos(double pos, float granularity){
        return (int)(pos/granularity)*granularity;
    }
	
	  public static String getPosIndex(String recvStr){
		  String[] posStr = null;
	        if (recvStr.contains(","))
	            posStr = recvStr.split(",");
	        else {
	            posStr = recvStr.split(" ");
	        }
	      double[] positions = new double[2];
	      for(int i=0;i<2;i++){
	          positions[i] = calPos(Float.parseFloat(posStr[i]),granular);
	      }
	      //String posX = recvStr.substring(0,recvStr.indexOf(",")).trim();
	      //String posY = recvStr.substring(recvStr.indexOf(",")+1).trim();
	      String pos = (int) positions[0]+","+(int) positions[1];
	      return pos;
	  }
	  
	  public static float calAngle(float degree){
	      float result =  (degree+180.f)%360.f - 180.f;
	      return result < -180? result + 360.f : result;
	  }
	  
	  public static float[] getPos(String recvStr){
		  String[] posStr = null;
		  if (recvStr.contains(","))
	            posStr = recvStr.split(",");
	        else {
	            posStr = recvStr.split(" ");
	        }
	      float[] positions = new float[2];
	      
	      for(int i=0;i<2;i++){
	    	  positions[i] = Float.parseFloat(posStr[i]);
	      }
          
	      return positions;
	  }

	  public static float[] getOri(String recvStr){
	      String[] coor = recvStr.split(" ");
	      float[] orientations = new float[2];
	      for(int i=0;i<2;i++){
	          orientations[i] = Float.parseFloat(coor[i+2]);
	      }
	      return orientations;
	  }
}
