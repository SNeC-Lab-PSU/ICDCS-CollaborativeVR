import java.io.BufferedWriter;
import java.net.Socket;
import java.util.*;
import org.apache.commons.math3.fitting.PolynomialCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoints;
// keep statistics for each client
public class Stats{
	public int clientNum;
	public static int nextNum = 0;
	public double oneWayDelay;
	public boolean delayReady = false;
	public HashSet<Integer> prevPose = new HashSet<>();
	//public HashMap<Integer,ArrayList<Integer>> reTranMap = new HashMap<>();
	//public boolean reTranMapLock = false;
	public HashMap<Integer, Long> videoSendTime = new HashMap<>(); 
    public HashMap<Integer, Long> videoACKTime = new HashMap<>(); 
    public HashMap<Integer, Integer> videoSizeSlot = new HashMap<>();
    public HashMap<Integer, Integer> videoQualitySlot = new HashMap<>();
    public ArrayList<Integer> videoACKReport = new ArrayList<Integer>(); 
    public ArrayList<Long> timeACKReport = new ArrayList<Long>(); 
    //public Socket moveSocket;
    //public BufferedWriter moveOutput;
    
    // related to the rate control part
    public int curQuality = 1;
    public float estThroughput = 0; // unit: MB/s
    //public float[] estProbabilities = new float[Utils.qualityLevel];
    public float aveQuality = 0;
    public float varQuality = 0;
    public float expFactor = 0.25f;
    public int polyDegree = 5;
    PolynomialCurveFitter fitter = PolynomialCurveFitter.create(polyDegree);
    public WeightedObservedPoints obs = new WeightedObservedPoints();
    public double[] delayFitParams = new double[polyDegree];

    public long calDelayStartTime;
    public long calDelayEndTime;

    public Stats(){
    	clientNum = nextNum;
    	// initially, all probabilities are 1
    	//for(int i=0;i<Utils.qualityLevel;i++) {
    	//	estProbabilities[i] = 1;
    	//}
    }
    
    public void fitDelay(float rate, float newDelay) {
    	obs.add(rate, newDelay);
    	delayFitParams = fitter.fit(obs.toList());
    }
    
    public float predictDelay(float rate) {
    	double result = 0;
    	for(int i=0;i<polyDegree;i++) {
    		result += delayFitParams[i] * Math.pow(rate, i); 
    	}
    	return (float) result;
    }
    
}