import java.util.*;
import org.apache.commons.math3.stat.regression.SimpleRegression;
/**
 * Write a description of LRModelWrap here.
 * 
 * @author (your name) 
 * @version (a version number or a date)
 */
public class LRModelWrap {
    int predWind;
    int timeSlot;
    int histWind;
    private double[] hist;
    private ArrayList<Double> realDataList;
    private ArrayList<Double> predDataList;
    SimpleRegression lr;
    
    public LRModelWrap(int futureWind, int histPredWind){
        predWind = futureWind;
        timeSlot = 0;
        histWind = histPredWind;
        lr = new SimpleRegression();
        hist = new double[histPredWind];
        realDataList = new ArrayList<>();
        predDataList = new ArrayList<>();
    }
    
    public void updateList(double value){
        for (int i=0;i<histWind-1;i++){
            hist[i] = hist[i + 1];
        }
        hist[histWind-1] = value;
    }
    
    public double[] step(double newValue){
        timeSlot++;
        realDataList.add(newValue);
        updateList(newValue);
        double[] results = new double[predWind];
        if (realDataList.size() < histWind){
            for (int i=0;i<predWind;i++)
                results[i] = newValue;
        }
        else{
            /*// train a new LRmodel, get the prediction result
            // prepare the input and output
            double[][] inputData = new double[histWind][lrOrder];
            double[] outputData = new double[histWind];
            for (int i=0;i<histWind;i++){
                inputData[i] = new double[]{timeSlot-histWind+i};
                outputData[i] = hist[i];
            }
            // train the model
            LRModel lr = new LRModel(lrOrder,true,100);
            lr.fit(inputData,outputData);
            
            // get the prediction results
            predValue = lr.predict(new double[]{(double)timeSlot});*/
            
            // train a new LRmodel using apache math library, get the prediction result
            // prepare the input and output
            lr.clear();
            for (int i=0;i<histWind;i++){
                lr.addData(timeSlot-histWind+i, hist[i]);
            }
            // train the model
            lr.regress();
            
            // get the prediction results
            for (int i=0;i<predWind;i++)
                results[i] = lr.predict((double)timeSlot+i);
        }
        for(int i=0;i<predWind;i++)
            predDataList.add(results[i]);
        return results;
    }
    
    public ArrayList<Double> getRealList(){
        return realDataList;
    }
    
    public ArrayList<Double> getPredList(){
        return predDataList;
    }
    
    public void releaseRes(){
        lr.clear();
        hist = null;
        realDataList.clear();
        predDataList.clear();
    }
}
