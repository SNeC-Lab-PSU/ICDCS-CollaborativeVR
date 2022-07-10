# -*- coding: utf-8 -*-
"""
Created on Wed Jun  9 16:31:37 2021

@author: Jiangong
"""

import numpy as np

def f_x(x,flag):
    if flag == 0:
        return -(x-3)**2+100 # np.log(a+x)
    else:
        return -0.8*(x-5)**2+100
    
def g_x(x):
    return x**2

quality_levels = [1,2,3,4,5]
user_num = 2

limits = np.arange(4,52)
for limit in limits:
    
# if True:
#     limit = 10
    
    opt_index = []
    opt_value = -1e26
    for i in range(len(quality_levels)):
        for j in range(len(quality_levels)):
            if g_x(quality_levels[i])+g_x(quality_levels[j])<=limit:
                value_sum = f_x(quality_levels[i],0)+f_x(quality_levels[j],1)
                if value_sum > opt_value:
                    opt_index = [quality_levels[i],quality_levels[j]]
                    opt_value = value_sum
                    
    init_qual = [1,1]
    u_index = [0,1]
    remain_rate = limit - g_x(init_qual[0]) - g_x(init_qual[1])
    while True:
        for i in u_index.copy():
            if init_qual[i]+1 > max(quality_levels) or g_x(init_qual[i]+1)-g_x(init_qual[i])>remain_rate:
                u_index.remove(i)
        if not u_index:
            break
        descent = [(f_x(init_qual[i]+1,i)-f_x(init_qual[i],i)) 
                   /(g_x(init_qual[i]+1)-g_x(init_qual[i])) for i in u_index]
        max_index = np.argmax(descent)
        max_user_index = u_index[max_index]
        if descent[max_index]<=0 :
            break
        else:
            remain_rate -= g_x(init_qual[max_user_index]+1)-g_x(init_qual[max_user_index])
            init_qual[max_user_index] += 1
    result = f_x(init_qual[0],0)+f_x(init_qual[1],1)
                
    if result != opt_value:
        print(limit)
        
