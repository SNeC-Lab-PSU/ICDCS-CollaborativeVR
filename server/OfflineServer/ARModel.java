import java.util.*;
import Jama.Matrix;
/**
 * Write a description of ARModel here.
 * 
 * @author (your name) 
 * @version (a version number or a date)
 */
public class ARModel {

    private int predWind;
    private int dim;
    
    private ArrayList<Double> hist;
    private ArrayList<Double> histSin;
    private ArrayList<Double> histCos;
    private ArrayList<Double> realDataList;
    private ArrayList<Double> predDataList;
    
    public ARModel(int window, int p){
        predWind = window;
        dim = p;
        hist = new ArrayList<Double>();
        histSin = new ArrayList<Double>();
        histCos = new ArrayList<Double>();
        realDataList = new ArrayList<Double>();
        predDataList = new ArrayList<Double>();
    }
    
    public ArrayList<Double> getRealList(){
        return realDataList;
    }
    
    public ArrayList<Double> getPredList(){
        return predDataList;
    }
    
    public double posStep(double newValue){
        updateArrayList(hist,newValue);
        double predValue = predict(hist);
        realDataList.add(newValue);
        predDataList.add(predValue);
        return predValue;
    }
    
    public double oriStep(double newValue){
        updateArrayList(histSin,Math.sin(newValue*Math.PI/180));
        updateArrayList(histCos,Math.cos(newValue*Math.PI/180));
        double predSin = predict(histSin);
        double predCos = predict(histCos);
        double predValue = Math.atan2(predSin,predCos)*180/Math.PI;
        realDataList.add(newValue);
        predDataList.add(predValue);
        return predValue;
    }
    
    public void updateArrayList(ArrayList<Double> input, double newValue ){
        input.add(newValue);
        while (input.size() > predWind){
            input.remove(input.get(0));
        }
    }
    
    private Matrix toeplitz(double[] inputArray, int mDim){
        double[][] array = new double[mDim][mDim];
        for(int i=0;i<mDim;i++){
            for(int j=i;j<mDim;j++){
                array[i][j] = inputArray[j-i];
            }
            for(int j=0;j<i;j++){
                array[i][j] = array[j][i];
            }
        }
        return new Matrix(array);
    }

    public double predict(ArrayList<Double> input){
        double result;
        if (input.size() < predWind) {
            result = input.get(input.size()-1);
            return result;
        }
        
        ArrayList<Double> temp = new ArrayList<Double>();
        for (int i=0;i<input.size();i++){
            temp.add(input.get(i));
        }
        try{
            double[] rxx = new double[dim+1];
            for(int j=0;j<dim+1;j++){
                for(int k=0;k<predWind-j;k++){
                    rxx[j] = rxx[j] + temp.get(k).doubleValue()*temp.get(k+j).doubleValue();
                }
                rxx[j] = rxx[j]/predWind;
            }
            Matrix mA = toeplitz(rxx,dim);
            double[][] mbArray = new double[dim][1];
            for(int j=0;j<dim;j++){
                mbArray[j][0] = -1*rxx[1+j];
            }
            Matrix mb = new Matrix(mbArray);
            Matrix maEst = mA.solve(mb);
            double[][] xRevArray = new double[dim][1];
            for(int j=0;j<dim;j++){
                xRevArray[j][0] = temp.get(temp.size()-1-j);
            }
            Matrix xRev = new Matrix(xRevArray);
            //maEst.times(-1).transpose().times(xRev).print(10,10);
            result = maEst.times(-1).transpose().times(xRev).get(0,0);
        } catch(RuntimeException e){
            result = input.get(input.size()-1);
        }
        return result;
    }

    
}
