# -*- coding: utf-8 -*-
"""
Created on Mon Apr 26 20:56:10 2021

@author: chenj
"""

import config 

class cons_agent:
    def __init__(self):
        self.CLIENT_NUM = config.CLIENT_NUM
        self.label = 'lowest'
        
    def allocate(self,prev_qualities,tiles,users):
        return [1 for i in range(self.CLIENT_NUM)]