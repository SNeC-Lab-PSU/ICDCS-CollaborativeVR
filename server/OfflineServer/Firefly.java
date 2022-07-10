import java.util.ArrayList;

public class Firefly implements RateAlgo {
	float safetyMargin;
	float RESERVE;
	int gap;
    ArrayList<Integer> lruIndex;
    int clientNum;
    String label;
	
	public Firefly() {
        safetyMargin = 0.9f;
        RESERVE = 0 ;// 0.75*total_budget/CLIENT_NUM
        // gap to judge whether the total budget is approximate to 0
        gap = 1;
        clientNum = Stats.nextNum;
    	lruIndex = new ArrayList<>();
        for (int i=0;i<clientNum;i++){
        	lruIndex.add(i);
        }
        label = "FireFly";
	}
	
    // FireFly algorithm
    public void allocate() {
        float total_budget = Utils.RATE_LIMIT_SERVER * safetyMargin;
        float[] bandwidth_clients = new float[clientNum];
        int[] qualities = new int[clientNum];
        String[] IPs = new String[clientNum]; // To locate the Stats
        // get the requested statistics from Stats
        int cnt = 0;
		for (String IP: Utils.clientStats.keySet()) {
			IPs[cnt] = IP;
			Stats statistics = Utils.clientStats.get(IP);
			bandwidth_clients[cnt] = statistics.estThroughput * safetyMargin;
			//bandwidth_clients[cnt] = Utils.throughputMap.get(IP) * safetyMargin;
			qualities[cnt] = statistics.curQuality;
			cnt++;
		}
		// get the requested tiles of current time slot
		String indexPos = Utils.getPosIndex(Utils.predPos[0]);
		float[] ori = Utils.getOri(Utils.predPos[0]);
		String coor = "("+(int)Utils.calAngle(ori[0])+","+(int)Utils.calAngle(ori[1])+","+0+")";
		ArrayList<Integer> tiles = Utils.predTileTable.get(coor);

        // reduce the quality if it is larger than the rate limit of the client
        for (int i=0; i<clientNum;i++) {
            float rate_client = Utils.calBandwidth(indexPos,tiles,qualities[i]);
            while(rate_client>=bandwidth_clients[i] && qualities[i] > 1) {
                qualities[i]--;
                rate_client = Utils.calBandwidth(indexPos,tiles,qualities[i]);
                lruIndex.remove(Integer.valueOf(i));
                lruIndex.add(i);
            }
            total_budget -= rate_client; // min(bandwidth_clients[i],max(self.RESERVE,rate_client))
        }
        if(total_budget<0) {
        	// not enough total budget of the server, LRU reduce the quality
            ArrayList<Integer> tempIndex = new ArrayList<>();
            for (int i=0;i<clientNum;i++){
            	tempIndex.add(i);
            }
            // iterate until there is positive remaining budget or all qualities is 1
            while(total_budget<0 && !tempIndex.isEmpty()) {
                // lru decrease
                int index = lruUpdate(lruIndex);
                if (qualities[index] > 1) {
                    float rate_client = Utils.calBandwidth(indexPos,tiles,qualities[index]);
                    total_budget += rate_client; // min(bandwidth_clients[index],max(self.RESERVE,rate_client))
                    qualities[index] -= 1;
                    rate_client = Utils.calBandwidth(indexPos,tiles,qualities[index]);
                    total_budget -= rate_client; // min(bandwidth_clients[index],max(self.RESERVE,rate_client))
                }
                else if (tempIndex.contains(index)){
                    tempIndex.remove(Integer.valueOf(index));
                }
            }
        }
        else {
            // still remain budget of the server, LRU increase the quality
        	ArrayList<Integer> tempIndex = new ArrayList<>();
            for (int i=0;i<clientNum;i++){
            	tempIndex.add(i);
            }
            // iterate until the budget is approximate to 0 or all qualities are the highest
            while(!tempIndex.isEmpty()) {
                // lru increase
                int index = lruUpdate(lruIndex);
                if (qualities[index] < Utils.qualityLevel && Utils.calBandwidth(indexPos,tiles,qualities[index]+1) <bandwidth_clients[index]) {
                    float rate_client = Utils.calBandwidth(indexPos,tiles,qualities[index]);
                    float rate_client_next = Utils.calBandwidth(indexPos,tiles,qualities[index]+1);     
                    qualities[index] += 1;
                    total_budget += rate_client; // min(bandwidth_clients[index],max(self.RESERVE,rate_client))
                    total_budget -= rate_client_next; // min(bandwidth_clients[index],max(self.RESERVE,rate_client))
                    if (total_budget < 0) {
                        qualities[index] -= 1;
                        total_budget += rate_client_next-rate_client;
                        if (tempIndex.contains(index)) {
                            tempIndex.remove(Integer.valueOf(index));
                        }
                    }
                }
                else if (tempIndex.contains(index)) {
                    tempIndex.remove(Integer.valueOf(index));
                }
            }
        }
        
        // update the qualities in statistics
        for(int i=0;i<clientNum;i++) {
        	Utils.clientStats.get(IPs[i]).curQuality = qualities[i];
        }
	}
    
    int lruUpdate(ArrayList<Integer> lruIndex) {
        int index = lruIndex.get(0);
        lruIndex.remove(0);
        lruIndex.add(index);
        return index;
    }
}
