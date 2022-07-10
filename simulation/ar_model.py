# -*- coding: utf-8 -*-
"""
Created on Tue May  4 11:24:36 2021
Autoregression model
@author: chenj
"""
import numpy as np
from scipy import linalg

class AR_model:
    def __init__(self, wind_size, dim):
        self.window_len = wind_size
        self.p = dim
        self.hist = []
        self.hist_sin = []
        self.hist_cos = []
        self.real_data_list = []
        self.pred_data_list = []
    
    def update_list(self,input_list,new_value):
        input_list.append(new_value)
        while(len(input_list)>self.window_len):
            input_list.remove(input_list[0])
            
    def predict(self,input_value):
        # use previous value when the history length is smaller than prediction window
        if len(input_value) < self.window_len:
            return input_value[-1]
        try:
            temp = input_value.copy()        
            rxx = np.zeros(self.p+1) #% rxx0--rxx(1), rxx1--rxx(2),..., rxxp--rxx(p+1)    
            for i in range(self.p+1):
                for j in range(self.window_len-i): 
                    rxx[i] = rxx[i] + temp[j]*temp[j+i]                
                rxx[i] = rxx[i]/self.window_len                
            ma = linalg.toeplitz(rxx[:self.p])    
            mb = -1 * rxx[1:]    
            ma_est = np.linalg.solve(ma,mb.T)    
            result = (-1 * ma_est).T @ np.flip(temp[-self.p:])           
            return result
        except:
            return input_value[-1]
    
    def ori_step(self,new_value):
        self.update_list(self.hist_sin,np.sin(new_value*np.pi/180))
        self.update_list(self.hist_cos,np.cos(new_value*np.pi/180))
        pred_sin = self.predict(self.hist_sin)
        pred_cos = self.predict(self.hist_cos)
        pred_value = np.arctan2(pred_sin,pred_cos)*180/np.pi
        self.real_data_list.append(new_value)
        self.pred_data_list.append(pred_value)
        return pred_value
    
    def get_real_list(self):
        return self.real_data_list
    def get_pred_list(self):
        return self.pred_data_list
    
if __name__ == '__main__':
    predWind = 3
    dim = 3
    
    arOriX = AR_model(predWind,dim)
    arOriY = AR_model(predWind,dim)
    with open('./modiTrace.txt') as f:
        msgLen = 64
            
        print("Start playing the trace")
        lines = f.readlines()
        for line in lines:
            if(len(line)>msgLen) :
                print("error")

            # predict the orientation on axis X and Y
            oriX = float(line.split(",")[3])
            predOriX = arOriX.ori_step(oriX)
            oriY = float(line.split(",")[4])
            predOriY = arOriY.ori_step(oriY)
            
            predLine = str(predOriX)+","+str(predOriY)+","+"0"+"\n"

        
        realOriXList = arOriX.get_real_list()
        predOriXList = arOriX.get_pred_list()
        realOriYList = arOriY.get_real_list()
        predOriYList = arOriY.get_pred_list()

        errorOriX = 0.0
        errorOriY = 0.0
        for i in range(len(realOriXList)-1):
            errorOriX += abs((realOriXList[i+1]-predOriXList[i]+180.)%360. - 180.)
            errorOriY += abs((realOriYList[i+1]-predOriYList[i]+180.)%360. - 180.)
            

        errorOriX = errorOriX / (len(realOriXList) - 1)
        errorOriY = errorOriY / (len(realOriYList) - 1)
        print("prediction error on the orientation axis X: ",errorOriX)
        print("prediction error on the orientation axis Y: ",errorOriY)
    