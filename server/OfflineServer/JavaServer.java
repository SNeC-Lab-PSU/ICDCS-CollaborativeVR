import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.HashMap; 
import java.nio.file.Files;

/**
 * This program demonstrates a simple TCP/IP socket server that echoes every
 * message from the client in reversed form.
 * This server is single-threaded.
 *
 */
public class JavaServer {

    static void prepareBuffer(String folderName){
        File folder = new File(folderName);
        File[] listOfFiles = folder.listFiles();
        int filenum = 0;
        long startPrepTime = System.currentTimeMillis();
        for (File f : listOfFiles){
            String fileName = f.getName();
            byte[] curFrame = null;
            try{
                curFrame = Files.readAllBytes(f.toPath());
            }catch (IOException e) {
                System.out.println("Main thread: Frame Buffer exception: " + e.getMessage());
                e.printStackTrace();
            }
            String pos = fileName.substring(fileName.indexOf("(")+1,fileName.indexOf(")"));
            int tileID = Integer.parseInt(fileName.substring(fileName.indexOf("tile")+4,fileName.indexOf("crf")));
            int crf = Integer.parseInt(fileName.substring(fileName.indexOf("crf")+3,fileName.indexOf(".264")));
            int videoID = Utils.getVideoID(pos,tileID,crf);
	        if (videoID != -1) {
	        	Utils.map.put(videoID,curFrame);
	        	Utils.id2size.put(videoID, curFrame.length);
	        }
	        long curTime = System.currentTimeMillis();
	        if (filenum % 1000 == 0) System.out.println("read "+filenum+" files, time used: "+(curTime-startPrepTime)+" ms");
	        filenum++;
        }
        // save the id2size table
        /*File file = new File("./id2size.txt");
        try {
			BufferedWriter bw = new BufferedWriter(new FileWriter(file));
			for(int videoID : Utils.id2size.keySet()) {
				bw.write(videoID + "," + Utils.id2size.get(videoID) + "\n");
			}
			bw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}*/
        
        if(!Utils.notFoundFrames.isEmpty()) {
        	for(int i=0;i<Utils.notFoundFrames.size();i++) {
        		System.out.println("Main thread: cannot find id of the pose: "+Utils.notFoundFrames.get(i));
        	}
        }
        System.out.println("Buffer Ready");
    }

    static void readIDTable(String filename, HashMap<Integer,String> id2pose, HashMap<String,Integer>pose2id) 
    {
    	File file = new File(filename);
    	id2pose.clear();
    	pose2id.clear();
        try{
            BufferedReader br = new BufferedReader(new FileReader(file));

            String st;
            int cnt = 0;
            while ((st = br.readLine()) != null) {
                String[] strs = st.split(" ");
                int videoID = Integer.parseInt(strs[0]);
                String pose = strs[1];
                id2pose.put(videoID, pose);
                pose2id.put(pose, videoID);
                cnt++;
            }
            br.close();
            // print the hashmap
            //System.out.println(table);
            System.out.println("Tile ID Table read done, total id count:" + cnt);
        }
        catch(IOException e){
            e.printStackTrace();
        }
    }
    
    static HashMap<String,ArrayList<Integer>> readTileTable(String filename){
    	HashMap<String,ArrayList<Integer>> table = new HashMap<>();
    	File file = new File(filename);
        try{
            BufferedReader br = new BufferedReader(new FileReader(file));

            String st;
            int cnt = 0;
            String coor = null;
            while ((st = br.readLine()) != null) {
                if(cnt%2 == 0){
                    coor = st;
                }
                else{
                    ArrayList<Integer> tiles = new ArrayList<>();
                    String[] strTiles = st.split(",");
                    for(int i=0;i<strTiles.length;i++){
                        tiles.add((int)Double.parseDouble(strTiles[i]));
                    }
                    table.put(coor,tiles);
                }
                cnt++;
            }
            br.close();
            // print the hashmap
            //System.out.println(table);
            System.out.println(filename + "Tile Orientation Table read done.");
        }
        catch(IOException e){
            e.printStackTrace();
        }
        return table;
    }

    public static void main(String[] args) {
    	// TODO: estimate the throughput by calculate the time from first pkt to the last pkt of a tile
        // TODO: startup, wait for a long enough time such that all clients have connected to the server, set the start time as receiving the first pkt
    	final int MovePort = 8848;
        final int FuncPort = 8080;
        final int RTSPPort = 8088;

        long startTime = System.currentTimeMillis();
        Utils.predTileTable = readTileTable("./tile_table_1row_4col_120_150.txt");
        Utils.reqTileTable = readTileTable("./tile_table_1row_4col_90_100.txt");
        readIDTable("./id2pose.txt",Utils.id2pose,Utils.pose2id);
        //prepareBuffer("D:\\UnityDemo\\VRServerOffice\\tiles1x4\\tiles");
        //prepareBuffer("D:\\jiangong\\dataset\\tiles");
        //new CacheThread("D:\\dataset\\tiles").start();
        int gap = 10000;
        for(int i=0;i<=Utils.id2pose.size()/gap;i++) {
        	new CacheThread("D:\\dataset\\tiles",i*gap,(i+1)*gap).start();
        }
        long prepEndTime = System.currentTimeMillis();
        System.out.println("Main thread: preparation done, time used: "+(prepEndTime - startTime)+" ms");
        
        ServerThread moveServerThread = new ServerThread("Move Server thread", MovePort, 1);
        moveServerThread.start();
        
        ServerThread funcServerThread = new ServerThread("ACK Server thread", FuncPort, 2);
        funcServerThread.start();

        ServerThread rtspServerThread = new ServerThread("RTSP Server thread", RTSPPort, 0);
        rtspServerThread.start();


    }

    public static class ServerThread extends Thread {
        private int PORT;
        private int Type;

        public ServerThread(String name, int port, int type){
        	super(name);
            PORT = port;
            Type = type; // 1, movement server; 2, ACK server
        }

        @Override
        public void run(){
            try (ServerSocket serverSocket = new ServerSocket(PORT)) {
             
                System.out.println(Thread.currentThread().getName()+" ready, listening on port " + PORT);
                while (true) {
                    Socket socket = serverSocket.accept();
                    if (socket == null) {
                    	System.out.println(Thread.currentThread().getName()+" accept socket failed.");
                    	break;
                    }
                    System.out.println(Thread.currentThread().getName()+"Receive connection from: "+socket.getInetAddress());
                    // at the initialization, receive internal IP address from client
                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    String clientAddr = reader.readLine();
                    System.out.println(Thread.currentThread().getName()+"Receive IP from: "+clientAddr);
                    // reader.close(); // will close on each sub thread
                    // String clientAddr = socket.getInetAddress().toString() + " " + socket.getPort();
                    if (Type == 0) {
                    	// start the RTSP thread
                    	if (!Utils.clientStats.containsKey(clientAddr)) {
	                        Utils.clientStats.put(clientAddr,new Stats());
	                        //if (Stats.nextNum == 0){
	                        //    Utils.teacherAddr = clientAddr;
	                        //}
	        	            Stats.nextNum++;
                    	}
                        
        	            // new thread for a client
        	            //new TranThread(map, socket).start();
        	            new RTPThread("RTP thread "+clientAddr,socket, Stats.nextNum, clientAddr, reader).start();
        	            
                    }
                    if (Type == 1) {
                    	// start the prediction thread if the IP belongs to the teacher
                    	//if (Utils.teacherAddr.equals(socket.getInetAddress().toString())) {
                    	Utils.teacherAddr = clientAddr;
                    	PredictThread predThread = new PredictThread("Prediction Thread",socket,reader);
                        predThread.start();
                    	//}
                    	// else, store the socket into the list
                    	/*else {
                    		// wait for the statistic object initialization of the client
                    		while(!Utils.clientStats.containsKey(clientAddr)){
                                try{
                                    Thread.sleep(1);
                                }
                                catch (InterruptedException e){
                                	e.printStackTrace();
                                }
                            }
                    		Stats statistics = Utils.clientStats.get(clientAddr);
                    		statistics.moveSocket = socket;
                    		statistics.moveOutput = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                    	}*/
                    }
                    else if (Type == 2) {
                    	// wait for the statistic object initialization of the client
                    	while(!Utils.clientStats.containsKey(clientAddr)){
                            try{
                                Thread.sleep(1);
                            }
                            catch (InterruptedException e){
                            	e.printStackTrace();
                            }
                        }
                    	// start the functional thread
	                    FuncThread funcThread = new FuncThread("Functional thread"+clientAddr,socket,clientAddr,reader);
	                    funcThread.start();
                    }
                }
            } catch (IOException ex) {
                System.out.println(Thread.currentThread().getName()+" exception: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
    }
}