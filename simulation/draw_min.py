# -*- coding: utf-8 -*-
"""
Created on Sat May 15 14:42:41 2021

@author: chenj
"""

import numpy as np
import matplotlib.pyplot as plt

class delay_func():
    def __init__(self,cap):
        self.capacity = cap
    def output(self,input_x):
        return 1/(self.capacity-input_x)

d1 = delay_func(5)    
x = np.arange(-5,5,0.1)
y1 = d1.output(x)

d2 = delay_func(6)
y2 = d2.output(x)

alpha = 1
beta = 1

y = (alpha+beta)*(y1 + y2) - 2*beta*np.min([y1,y2],axis=0)
plt.plot(x,y)
