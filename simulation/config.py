# -*- coding: utf-8 -*-
"""
Created on Mon Apr 26 21:49:52 2021

@author: chenj
"""

import numpy as np

# parameter for AR model
PRED_WIND = 20
DIM = 3

CLIENT_NUM = 5
TOTAL_TRACE_NUM = 100
DECISION_INTERVAL = 1
BUFFER_LIMIT = 5000
BITRATE_LEVELS = 6
TARGET_FPS = 60
TIME_INTERVAL = int(1000/TARGET_FPS) - 1
T = 2e4
PKT_SIZE = 1400
RATE_LIMIT_CLIENT = [4.51,5.41,3.81,8.01,3.21] # unit: MB/s
RATE_LIMIT_CLIENT_EST = [0.9*rate for rate in RATE_LIMIT_CLIENT]
RATE_LIMIT_SERVER = CLIENT_NUM*4.5
# available tile combination
TILES_POOL = [[0,1],[1,2],[2,3],[3,0],[0,1,2],[1,2,3],[2,3,0],[3,0,1]]

# size for each tile in each quality, unit: byte
TILE_BASE = np.array([3300,4000,2500,1500])
TILE_SIZE = [TILE_BASE*(i) for i in range(BITRATE_LEVELS+1)]
SLOT_SIZE = [0 for i in range(BITRATE_LEVELS)]

# base delay for each client, consider the transmission and propogation delay and application-level processing
# different delay for each client
np.random.seed(10)
DELAY_BASE = np.zeros(CLIENT_NUM) #np.random.randint(5,size=CLIENT_NUM) # np.array([2,4,1,3,5])

DELAY_PRED = False

# hyperparameters
ALPHA = 0.02
BETA = 0 #0.15
GAMMA = 0.1