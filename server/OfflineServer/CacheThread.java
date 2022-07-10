import java.io.File;
import java.io.IOException;
import java.lang.Thread;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Always run this thread to dynamically add files to buffer
 *
 */

public class CacheThread extends Thread{
	String dirName = null;
	String threName = "Cache Thread";
	String lastPose = null;
	HashMap<Integer,File> files = new HashMap<>();
	HashSet<Integer> readTiles = new HashSet<>();
	HashSet<String> readPoses = new HashSet<>();
	HashSet<String> filePoses = new HashSet<>();
	int range = 0;
	
	boolean flag = false;
	int index = 0;
	int endIndex = 0;
	
	public CacheThread(String folder) {
		dirName = folder;
		Utils.curPos = "0.0 0.0";
	}
	
	public CacheThread(String folder, int startIndex, int getEndIndex) {
		dirName = folder;
		flag = true;
		index = startIndex;
		endIndex = getEndIndex;
		threName = "Cache Thread "+startIndex;
	}
	
	public void run() {
		long startPrepTime = System.currentTimeMillis();
		// read all file names and store in a table
		getFilePaths(dirName);
        long tablePrepTime = System.currentTimeMillis();
        System.out.println(threName + ": file table preparation done, time used: "+(tablePrepTime - startPrepTime) + "ms");
        
		if (flag) {
			// read tiles before the transmission
			int videoID = index;
			while(videoID < endIndex && videoID < files.size()) {
				readTile(videoID);
				long tempPrepTime = System.currentTimeMillis();
				if ((videoID - index) % 1000 == 0) {
					System.out.println(threName + "Read tiles from "+index+" to "+videoID +" time used: "+(tempPrepTime - startPrepTime) + "ms");
				}
				videoID++;
			}
		}
		else {
			// read tiles in real time
	        while(true) {
	        	// end the loop if we have read all tiles
	        	if (readTiles.size() == files.size())	break;
	        	String indexPos = getNextPosToRead();
	        	readAllTilesInAPos(indexPos);
	        }
		}

		long endPrepTime = System.currentTimeMillis();
		System.out.println(threName + ": All tiles have been read, time used: "+(endPrepTime - startPrepTime) + "ms, current total tile num: "+Utils.map.size());
	}
	
	void readTile(int id) {
		if (Utils.map.containsKey(id)) return;
		if (!files.containsKey(id)) return; 
		byte[] curFrame = null;
		File f = files.get(id); 
        try{
            curFrame = Files.readAllBytes(f.toPath());
        }catch (IOException e) {
            System.out.println("Main thread: Frame Buffer exception: " + e.getMessage());
            e.printStackTrace();
        }
        if (id != -1) {
        	Utils.map.put(id,curFrame);
        	Utils.id2size.put(id, curFrame.length);
        	readTiles.add(id);
        }
	}
	
	void readAllTilesInAPos(String indexPos) {
		if (indexPos == null) return;
		if (readPoses.contains(indexPos)) return;
		if (!filePoses.contains(indexPos)) return;
		for (int j=0;j<4;j++) {
			for (int k=1;k<Utils.qualityLevel+1;k++) {
				int id = Utils.getVideoID(indexPos, j, Utils.getCRF(k));
				readTile(id);
			}
		}
		readPoses.add(indexPos); 
    	System.out.println(threName + ": "+readTiles.size()+" tiles have been read.");
	}
	
	String getNextPosToRead() {
		String indexPos = null;
		if (Utils.curPos.equals(lastPose)) {
			range++;
			float[] positions = Utils.getPos(lastPose);
			for (int i=-range;i<=range;i++) {
				positions[0] = positions[0] + i*Utils.granular;
				for (int j=-range;j<=range;j++) {
					positions[1] = positions[1] + i*Utils.granular;
					for(int k=0;k<2;k++){
				          positions[k] = (float) Utils.calPos(positions[k],Utils.granular);
				      }
					indexPos = (int) positions[0]+","+(int) positions[1];
					if (!readPoses.contains(indexPos)) break;
					if (!Utils.curPos.equals(lastPose)) break;
				}
			}
		}
		else {
			lastPose = Utils.curPos;
			indexPos = Utils.getPosIndex(lastPose);
			range = 0;
		}
		return indexPos;
	}
	
	void getFilePaths(String folderName) {
		File folder = new File(folderName);
        File[] listOfFiles = folder.listFiles();
        for (File f : listOfFiles){
            String fileName = f.getName();
            String pos = fileName.substring(fileName.indexOf("(")+1,fileName.indexOf(")"));
            int tileID = Integer.parseInt(fileName.substring(fileName.indexOf("tile")+4,fileName.indexOf("crf")));
            int crf = Integer.parseInt(fileName.substring(fileName.indexOf("crf")+3,fileName.indexOf(".264")));
            int videoID = Utils.getVideoID(pos,tileID,crf);
	        if (!files.containsKey(videoID) && videoID != -1) {
	        	files.put(videoID, f);
	        }
	        if (!filePoses.contains(pos)) {
	        	filePoses.add(pos);
	        }
        }
        // check whether the id list is correct
        if(!Utils.notFoundFrames.isEmpty()) {
        	for(int i=0;i<Utils.notFoundFrames.size();i++) {
        		System.out.println(threName + ": cannot find id of the pose: "+Utils.notFoundFrames.get(i));
        	}
        }
	}
	
	void prepareBuffer(String folderName){
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
}
