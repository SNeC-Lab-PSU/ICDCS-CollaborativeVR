import java.util.ArrayList;

public class TwoApprox implements RateAlgo{
	int clientNum;
	String label;
	ArrayList<Integer> u_index;
	public TwoApprox() {
		clientNum = Stats.nextNum;
        label = "2 approximation";
        u_index = new ArrayList<>();
	}
	
	public void allocate() {
        float[] bandwidth_clients = new float[clientNum];
        int[] d_qualities = new int[clientNum];
        int[] v_qualities = new int[clientNum];
        int[] qualities = new int[clientNum];
        String[] IPs = new String[clientNum]; // To locate the Stats
        // get the requested statistics from Stats
        int cnt = 0;
        int slot = Utils.timeSlot + 1;
		for (String IP: Utils.clientStats.keySet()) {
			IPs[cnt] = IP;
			Stats statistics = Utils.clientStats.get(IP);
			bandwidth_clients[cnt] = statistics.estThroughput;
			//bandwidth_clients[cnt] = Utils.throughputMap.get(IP);
			d_qualities[cnt] = 1;
			v_qualities[cnt] = 1;
			cnt++;
		}
		// get the requested tiles of current time slot
		String indexPos = Utils.getPosIndex(Utils.predPos[0]);
		float[] ori = Utils.getOri(Utils.predPos[0]);
		String coor = "("+(int)Utils.calAngle(ori[0])+","+(int)Utils.calAngle(ori[1])+","+0+")";
		ArrayList<Integer> tiles = Utils.predTileTable.get(coor);
		
		// density greedy algorithm
        u_index.clear();
        for (int i=0;i<clientNum;i++){
        	u_index.add(i);
        }
        
        float d_improve = 0;
        while (!u_index.isEmpty()) {
            float[] obj_incre = new float[u_index.size()];
            float[] density = new float[u_index.size()];

            for(int i=0;i<u_index.size();i++) {
                int index = u_index.get(i);
                float rate_high = Utils.calBandwidth(indexPos,tiles, d_qualities[index]+1);
                float rate_low = Utils.calBandwidth(indexPos,tiles, d_qualities[index]);
                
                Stats statistics = Utils.clientStats.get(IPs[index]);
                float delay_portion = statistics.predictDelay(rate_high) - statistics.predictDelay(rate_low);
                
                float old_mean = statistics.aveQuality;
                float var_portion = Utils.estProb*(Utils.timeSlot-1)*(float) (Math.pow(d_qualities[index]+1 - old_mean,2)-Math.pow(d_qualities[index]-old_mean, 2))/Utils.timeSlot;
                //float qualityHighWithProb = (d_qualities[index]+1)*Utils.estProb;
                //float new_mean_high = old_mean + (qualityHighWithProb - old_mean)/slot;
                //float qualityLowWithProb = d_qualities[index] * Utils.estProb;
                //float new_mean_low = old_mean + (qualityLowWithProb - old_mean)/slot;
                //float var_portion = (qualityHighWithProb - new_mean_high)*(qualityHighWithProb - old_mean)
                //        - (qualityLowWithProb - new_mean_low)*(qualityLowWithProb - old_mean);
                //obj_incre[i] = statistics.estProbabilities[d_qualities[index]]*(1 - Utils.ALPHA*delay_portion 
                //              - Utils.GAMMA*var_portion);
                obj_incre[i] = (Utils.estProb - Utils.ALPHA*delay_portion 
                        - Utils.GAMMA*var_portion);
                density[i] = obj_incre[i]/(rate_high-rate_low);
            }
            int max_index = 0;
            float max_value = -10000000f;
            for(int i=0;i<u_index.size();i++) {
            	if (density[i]>max_value) {
            		max_value = density[i];
            		max_index = i;
            	}
            }
            int max_user_index = u_index.get(max_index);
            if (max_value<=0)
                u_index.clear();
            else {
                d_qualities[max_user_index] += 1;
                float[] cur_rates = new float[clientNum];
                float total_rate = 0;
                for(int i=0;i<clientNum;i++) {
                	int quality = d_qualities[i];
                	cur_rates[i] = Utils.calBandwidth(indexPos,tiles,quality);
                	total_rate += cur_rates[i];
                }
                if (cur_rates[max_user_index] >= bandwidth_clients[max_user_index] ||
                    total_rate >= Utils.RATE_LIMIT_SERVER) {
                    d_qualities[max_user_index] -= 1;
                    u_index.remove(Integer.valueOf(max_user_index));
                }
                else {
                    d_improve += obj_incre[max_index];
                    if (d_qualities[max_user_index] == Utils.qualityLevel)
                        u_index.remove(Integer.valueOf(max_user_index));
                }
            }
        }       
        
        // value greedy algorithm
        u_index.clear();
        for (int i=0;i<clientNum;i++){
        	u_index.add(i);
        }
        
        float v_improve = 0; 
        while (!u_index.isEmpty()) {
        	float[] obj_incre = new float[u_index.size()];
        	for(int i=0;i<u_index.size();i++) {
                int index = u_index.get(i);
                float rate_high = Utils.calBandwidth(indexPos,tiles, v_qualities[index]+1);
                float rate_low = Utils.calBandwidth(indexPos,tiles, v_qualities[index]);
                
                Stats statistics = Utils.clientStats.get(IPs[index]);
                float delay_portion = statistics.predictDelay(rate_high) - statistics.predictDelay(rate_low);

                float old_mean = statistics.aveQuality;
                float var_portion = Utils.estProb*(Utils.timeSlot-1)*(float) (Math.pow(v_qualities[index]+1 - old_mean,2)-Math.pow(v_qualities[index]-old_mean, 2))/Utils.timeSlot;
                //float qualityHighWithProb = (v_qualities[index]+1)*Utils.estProb;
                //float new_mean_high = old_mean + (qualityHighWithProb - old_mean)/slot;
                //float qualityLowWithProb = v_qualities[index] * Utils.estProb;
                //float new_mean_low = old_mean + (qualityLowWithProb - old_mean)/slot;
                //float var_portion = (qualityHighWithProb - new_mean_high)*(qualityHighWithProb - old_mean)
                //    - (qualityLowWithProb - new_mean_low)*(qualityLowWithProb - old_mean);
                //obj_incre[i] = statistics.estProbabilities[v_qualities[index]]*(1 - Utils.ALPHA*delay_portion 
                //              - Utils.GAMMA*var_portion);
                obj_incre[i] = (Utils.estProb - Utils.ALPHA*delay_portion 
                        - Utils.GAMMA*var_portion);
        	}

    		int max_index = 0;
            float max_value = -10000000f;
            for(int i=0;i<u_index.size();i++) {
            	if (obj_incre[i]>max_value) {
            		max_value = obj_incre[i];
            		max_index = i;
            	}
            }		
            int max_user_index = u_index.get(max_index);
            if (max_value <= 0)
                u_index.clear();
            else {
                v_qualities[max_user_index] += 1;
                float[] cur_rates = new float[clientNum];
                float total_rate = 0;
                for(int i=0;i<clientNum;i++) {
                	int quality = v_qualities[i];
                	cur_rates[i] = Utils.calBandwidth(indexPos,tiles,quality);
                	total_rate += cur_rates[i];
                }
                if (cur_rates[max_user_index] >= bandwidth_clients[max_user_index] ||
                    total_rate >= Utils.RATE_LIMIT_SERVER) {
                    v_qualities[max_user_index] -= 1;
                    u_index.remove(Integer.valueOf(max_user_index));
                }
                else {
                    v_improve += obj_incre[max_index];
                    if (v_qualities[max_user_index] == Utils.qualityLevel)
                        u_index.remove(Integer.valueOf(max_user_index));
                }
            }
        }
        
        if (v_improve>d_improve)
            // value greedy better
            qualities = v_qualities;
        else
            // density greedy better
            qualities = d_qualities;
        
        // update the qualities in statistics
        for(int i=0;i<clientNum;i++) {
        	Utils.clientStats.get(IPs[i]).curQuality = qualities[i];
        }
	}

}
