import java.util.ArrayList;

public class PAVQ implements RateAlgo {
	int clientNum;
	String label;
	ArrayList<Integer> u_index;
	
	public PAVQ() {
		clientNum = Stats.nextNum;
        label = "PAVQ";
        u_index = new ArrayList<>();
	}
	
	@Override
	public void allocate() {
		float[] bandwidth_clients = new float[clientNum];
        int[] qualities = new int[clientNum];
        String[] IPs = new String[clientNum]; // To locate the Stats
        // get the requested statistics from Stats
        int cnt = 0;
        for (String IP: Utils.clientStats.keySet()) {
			IPs[cnt] = IP;
			Stats statistics = Utils.clientStats.get(IP);
			bandwidth_clients[cnt] = statistics.estThroughput;
			//bandwidth_clients[cnt] = Utils.throughputMap.get(IP);
			qualities[cnt] = 1;
			cnt++;
		}
		// get the requested tiles of current time slot
		String indexPos = Utils.getPosIndex(Utils.predPos[0]);
		float[] ori = Utils.getOri(Utils.predPos[0]);
		String coor = "("+(int)Utils.calAngle(ori[0])+","+(int)Utils.calAngle(ori[1])+","+0+")";
		ArrayList<Integer> tiles = Utils.predTileTable.get(coor);
		
		// PAVQ algorithm
		u_index.clear();
        for (int i=0;i<clientNum;i++){
        	u_index.add(i);
        }
        
        while (!u_index.isEmpty()) {
            float[] mu_n = new float[u_index.size()];
            
            for(int i=0;i<u_index.size();i++) {
                int index = u_index.get(i);
                float rate_high = Utils.calBandwidth(indexPos,tiles, qualities[index]+1);
                float rate_low = Utils.calBandwidth(indexPos,tiles, qualities[index]);
                
                Stats statistics = Utils.clientStats.get(IPs[index]);
                float delay_portion = statistics.predictDelay(rate_high) - statistics.predictDelay(rate_low);
                
                mu_n[i] = Utils.estProb - Utils.ALPHA*delay_portion 
                           - 2*Utils.GAMMA*Utils.estProb*(qualities[index]-statistics.aveQuality) ;
            }
            int max_index = 0;
            float max_value = -10000000f;
            for(int i=0;i<u_index.size();i++) {
            	if (mu_n[i]>max_value) {
            		max_value = mu_n[i];
            		max_index = i;
            	}
            }
            int max_user_index = u_index.get(max_index);
            if (max_value<=0)
                u_index.clear();
            else {
            	qualities[max_user_index] += 1;
                float[] cur_rates = new float[clientNum];
                float total_rate = 0;
                for(int i=0;i<clientNum;i++) {
                	int quality = qualities[i];
                	cur_rates[i] = Utils.calBandwidth(indexPos,tiles,quality);
                	total_rate += cur_rates[i];
                }
                if (cur_rates[max_user_index] >= bandwidth_clients[max_user_index] ||
                    total_rate >= Utils.RATE_LIMIT_SERVER) {
                    qualities[max_user_index] -= 1;
                    u_index.remove(Integer.valueOf(max_user_index));
                }
                else {
                    if (qualities[max_user_index] == Utils.qualityLevel)
                        u_index.remove(Integer.valueOf(max_user_index));
                }
            }
        }
        
        // update the qualities in statistics
        for(int i=0;i<clientNum;i++) {
        	Utils.clientStats.get(IPs[i]).curQuality = qualities[i];
        }
	}

}
