# -*- coding: utf-8 -*-
"""
Created on Sun Jul 25 22:22:09 2021

@author: chenj
"""

import numpy as np
from sklearn.linear_model import LinearRegression

class lr_model:
    def __init__(self,futureWind,histPredWind):
        self.predWind = futureWind
        self.timeSlot = 0
        self.histWind = histPredWind
        self.lr = LinearRegression()
        self.hist = []
        
    def updateList(self,value):
        if len(self.hist) < self.histWind:
            self.hist.append(value)
        else:
            for i in range(self.histWind-1):
                self.hist[i] = self.hist[i + 1]
            self.hist[self.histWind-1] = value
        
    def step(self,newValue):
        self.timeSlot+=1
        self.updateList(newValue)
        results = np.zeros(self.predWind)
        if len(self.hist) < self.histWind:
            for i in range(self.predWind):
                results[i] = newValue
        
        else:
            X = np.arange(self.timeSlot-self.histWind,self.timeSlot)
            y = self.hist
            # train the model
            self.lr.fit(X[:, np.newaxis],y)
            
            # get the prediction results
            for i in range(self.predWind):
                results[i] = self.lr.predict(np.arange(self.timeSlot,self.timeSlot+self.predWind).reshape(-1,1))
        return results
    
    
if __name__ == '__main__':
    def cal_pos(pos,granular):
        return int(pos/granular)*granular
    # test the linear regression model
    posXs = []
    posZs = []
    oriXs = []
    oriYs = []
    with open('./traces/OfficeTrace_1.txt') as f:
        lines = f.readlines()
        for line in lines:
            tokens = line.split(' ')
            posXs.append(float(tokens[0]))
            posZs.append(float(tokens[1]))
            oriXs.append(float(tokens[2]))
            oriYs.append(float(tokens[3]))
    pred_pos = []     
    lr_posX = lr_model(1,3)
    lr_posZ = lr_model(1,3)
    lr_oriX = lr_model(1,3)
    lr_oriY = lr_model(1,3)
    for i in range(len(posXs)):
        pred_posX = lr_posX.step(posXs[i])
        pred_posZ = lr_posZ.step(posZs[i])
        pred_oriX = lr_oriX.step(oriXs[i])
        pred_oriY = lr_oriY.step(oriYs[i])   
        pred_pos.append("%d %d %f %f"%(cal_pos(pred_posX,5),cal_pos(pred_posZ,5),pred_oriX,pred_oriY))
    
    
    
        