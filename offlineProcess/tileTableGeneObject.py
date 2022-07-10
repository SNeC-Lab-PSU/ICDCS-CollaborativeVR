# -*- coding: utf-8 -*-
"""
Created on Sat Jan 30 20:03:04 2021
Generate the tile table
Map rotation angles to requested tiles
@author: chenj
"""

import numpy as np
import time

def coor2angle(coordinate): # unit: rads
    # transform from the 3-dimensional coordinate to the latitude and longtitude
    x = coordinate[0]
    y = coordinate[1]
    z = coordinate[2]
    temp_theta = np.pi/2-abs(np.arctan(np.sqrt(np.square(x)+np.square(y))/z))
    temp_phi = np.arctan(y/x)
    lati = temp_theta*np.sign(z)
    if x<0 and y>0:
        long = np.pi+temp_phi
    elif x<0 and y<0:
        long = temp_phi-np.pi
    else:
        long = temp_phi   
    return -long, lati  #Notice that the longitude is reverse of the value of phi

def angle2coor(long,lati): # unit:rads
    # transform from the latitude and longtitude to the 3-dimensional coordinate
    theta = np.pi/2-abs(lati);
    phi = -long;
    coor = [np.sin(theta)*np.cos(phi),np.sin(theta)*np.sin(phi),np.cos(theta)*np.sign(lati)];
    return coor

def calAngle(degree):
        result =  (degree+180.)%360. - 180.
        return result

class GeneTable():
    def __init__(self, x_num, y_num, fov_x, fov_y, granu):
        # Given the granularity of the tiles
        # Assume that the tile is numbered from the top left and row by row
        self.TILE_X_num = x_num
        self.TILE_Y_num = y_num
        self.TILE_NUM = self.TILE_X_num*self.TILE_Y_num
        
        # Given the size of the FoV and granularity when calculating coordinates
        self.FOV_X = fov_x
        self.FOV_Y = fov_y
        self.GRANULAR = granu
        
        #  calculate the initial coordinates without any rotation
        self.long_list = np.arange(-self.FOV_Y/2,self.FOV_Y/2,self.GRANULAR)
        self.lati_list = np.arange(-self.FOV_X/2,self.FOV_X/2,self.GRANULAR)

    def cal_init_points(self):
        self.init_coors = []
        for i in range(len(self.long_list)):
            for j in range(len(self.lati_list)):
                temp_coor = angle2coor(self.long_list[i]*np.pi/180,self.lati_list[j]*np.pi/180)
                self.init_coors.append(temp_coor)
        self.init_coors = np.array(self.init_coors)
        
        for coor in self.init_coors:
            radius = np.linalg.norm(coor, ord=2) 
            # check whether all the points are on the sphere
            if(not(np.isclose(radius,1))):
                print(coor)
                print(radius)
        
    def rotate(self, x_angle, y_angle, z_angle):      
        # Transform the angle unity from degree to rads
        x_angle = x_angle*np.pi/180
        y_angle = y_angle*np.pi/180
        z_angle = z_angle*np.pi/180
        # Transform the initial coordinates by specific angles
        rotation_x = np.array([[1,0,0],[0,np.cos(z_angle),-np.sin(z_angle)],[0,np.sin(z_angle),np.cos(z_angle)]])
        rotation_y = np.array([[np.cos(x_angle),0,np.sin(x_angle)],[0,1,0],[-np.sin(x_angle),0,np.cos(x_angle)]])
        rotation_z = np.array([[np.cos(y_angle),-np.sin(y_angle),0],[np.sin(y_angle),np.cos(y_angle),0],[0,0,1]])
        tran_coors = self.init_coors.dot(rotation_x).dot(rotation_y).dot(rotation_z)
        return tran_coors

    def cal_tiles(self, tran_coors):
        cur_tiles = []
        max_long = -1e26
        min_long = 1e26
        # calculate the longtitude and latitude from the coordinates after the transformation
        # and get the requested tiles
        for coor in tran_coors:
            long, lati = coor2angle(coor)
            long = long*180/np.pi
            lati = lati*180/np.pi
            '''
            if(long>max_long):
                max_long = long
            if(long<min_long):
                min_long = long'''
            # handle the special points
            '''if(np.isclose(lati+90,180)):
                lati = 89
            if(np.isclose(long+180,360)):
                long = 179'''
            # caculate the tile num which includes current point
            tile_num = (long+180)//(360/self.TILE_Y_num) + (self.TILE_X_num -1 - (lati+90)//(180/self.TILE_X_num))*self.TILE_Y_num
            if tile_num < 0 or tile_num >= self.TILE_NUM:
                if(np.isclose(lati+90,180)):
                    lati = 89
                if(np.isclose(long+180,360)):
                    long = 179
                tile_num = (long+180)//(360/self.TILE_Y_num) + (self.TILE_X_num -1 - (lati+90)//(180/self.TILE_X_num))*self.TILE_Y_num
                if tile_num < 0 or tile_num >= self.TILE_NUM:
                    print("error occurs: ")
                    print(coor)
                    break
            if tile_num not in cur_tiles:
                cur_tiles.append(tile_num)
        #print("max long: ",max_long)
        #print("min long: ",min_long)
        return cur_tiles

#%%
def test_rotate(coor, x_angle, y_angle, z_angle):  
    x_angle = x_angle*np.pi/180
    y_angle = y_angle*np.pi/180
    z_angle = z_angle*np.pi/180     
    # Transform the initial coordinates by specific angles
    rotation_x = np.array([[1,0,0],[0,np.cos(z_angle),-np.sin(z_angle)],[0,np.sin(z_angle),np.cos(z_angle)]])
    rotation_y = np.array([[np.cos(x_angle),0,np.sin(x_angle)],[0,1,0],[-np.sin(x_angle),0,np.cos(x_angle)]])
    rotation_z = np.array([[np.cos(y_angle),-np.sin(y_angle),0],[np.sin(y_angle),np.cos(y_angle),0],[0,0,1]])
    tran_coors = coor.dot(rotation_x).dot(rotation_y).dot(rotation_z)
    long, lati = coor2angle(tran_coors)
    return long*180/np.pi, lati*180/np.pi
    # return tran_coors
#%%
if __name__ == '__main__':
    import sys
    if len(sys.argv) == 5:
        # Get the properties of the tile split
        TILE_X_NUM = int(sys.argv[1].strip())
        TILE_Y_NUM = int(sys.argv[2].strip())
        FOV_X = int(sys.argv[3].strip())
        FOV_Y = int(sys.argv[4].strip())
    else:
        print("format: row_num, col_num, FOV_x, FOV_y")
        sys.exit()

    GRANULAR = 1
    
    # initialization
    gene = GeneTable(TILE_X_NUM,TILE_Y_NUM,FOV_X,FOV_Y,GRANULAR)
    gene.cal_init_points()
    
    # test some points
    tran_coors = gene.rotate(0,40,0)
    cur_tiles = gene.cal_tiles(tran_coors)
    
    
    # Read all orientations from the trajectory, ignore the repetitive one
    coors = []
    tile_table = {}
    '''
    # given a trace
    with open("./banditDemo/modiTrace.txt") as f:
        lines = f.readlines()
        for line in lines:
            a = line.split(",")
            temp_coor = [round(float(a[3]),1),round(float(a[4]),1),round(float(a[5]),1)]
            str_coor = "(%.1f,%.1f,%.1f)"%(float(a[3]),float(a[4]),float(a[5]))
            if str_coor not in tile_table:
                tile_table[str_coor] = temp_coor

    # iterate over all orientations in the trajectory
    for coor in tile_table:
        x = calAngle(tile_table[coor][0])
        y = calAngle(tile_table[coor][1])
        z = calAngle(tile_table[coor][2])
        tran_coors = gene.rotate(x,y,z)
        cur_tiles = gene.cal_tiles(tran_coors)
        tile_table[coor] = cur_tiles'''
    
    print("Start generating offline table "+str(TILE_X_NUM)+" row "+str(TILE_Y_NUM)+" col "+str(FOV_X)+" "+str(FOV_Y))
    start_time = time.time()
    # iterate over all orientations, granularity 1 degree
    for x in range(-90,91,1):
        for y in range(-180,180,1):
            tran_coors = gene.rotate(x,y,0)
            cur_tiles = gene.cal_tiles(tran_coors)
            coor = "(%d,%d,0)"%(x,y)
            tile_table[coor] = cur_tiles
        print("%d/180"%(x+90))

    # Write the dictionary into file
    with open("./tile_table_"+str(TILE_X_NUM)+"row_"+str(TILE_Y_NUM)+"col_"+str(FOV_X)+"_"+str(FOV_Y)+".txt","w") as f:
        for coor in tile_table:
            f.write(coor+"\n")
            f.write(",".join(map(str, tile_table[coor]))+"\n")
            
    end_time = time.time()
    print("time used: %.2f s"%(end_time - start_time))
        
    '''
    # iterate over all degrees
    longs = np.arange(-180,180,0.05)
    latis = np.arange(-90,90,0.05)
    for lati in latis:
        for long in longs:
            tran_coors = gene.rotate(lati,long,0)
            cur_tiles = gene.cal_tiles(tran_coors)'''