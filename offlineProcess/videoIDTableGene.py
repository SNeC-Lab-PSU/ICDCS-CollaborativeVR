# -*- coding: utf-8 -*-
"""
Created on Thu Jul 15 11:25:55 2021

@author: chenj
"""

import os
import sys

if __name__ == "__main__":
    if len(sys.argv) == 2:
        input_folder = sys.argv[1].strip()
        print('generate video ID from the folder \'%s\''
              %(input_folder))
    else:
        print("format: input_folder")
        sys.exit()
    filenames = os.listdir(input_folder)
    ids = []
    names = []
    cnt = 0
    for filename in filenames:
        posX = int(filename[filename.find('(')+1:filename.find(',')])
        posZ = int(filename[filename.find(',')+1:filename.find(')')])
        tile_num = int(filename[filename.find('tile')+4])
        quality = int(filename[filename.find('crf')+3:filename.find('.264')])
        ids.append(cnt)
        pose = "%d,%d,%d,%d"%(posX,posZ,tile_num,quality)
        names.append(pose)
        cnt+=1
        
    with open('id2pose.txt','w') as f:
        for i in range(cnt):
            f.write(str(ids[i]) + " " + names[i] + "\n")

    