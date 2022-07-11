package com.example.vrsystemclient;


public class ACKpacket {

    //size of the ACK header:
    static int HEADER_SIZE = 12;

    //Fields that compose the ACK header
    public int AckType; // 0: display ACK, 1: packet ACK, 2: video release ACK
    public int VideoId;
    public int VideoIdNegFlag;
    public int Slot;
    public float Delay;
    public int TileEnd;
    public float Throughput;

    //Bitstream of the ACK header
    public byte[] header;

    //--------------------------
    //Constructor of an ACKpacket object from header fields and payload bitstream
    //--------------------------
    public ACKpacket(int ackType, int videoID, int TimeSlot, float delay, int tileEnd, float throughput){
        //fill header fields:
        AckType = ackType;
        VideoId = videoID;
        Slot = TimeSlot;
        Delay = delay;
        TileEnd = tileEnd;
        Throughput = throughput;

        //build the header bistream:
        //--------------------------
        header = new byte[HEADER_SIZE];

        //.............
        //TO COMPLETE
        //.............
        //fill the header array of byte with RTP header fields

        header[0] = (byte) (header[0] | AckType << (7-1));
        if (VideoId < 0) {
            VideoIdNegFlag = 1;
        }
        else{
            VideoIdNegFlag = 0;
        }
        header[0] = (byte) (header[0] | VideoIdNegFlag << (7-2));
        header[0] = (byte) (header[0] | VideoId >> 24 & 0x1F);

        header[1] = (byte) (VideoId >> 16);
        header[2] = (byte) (VideoId >> 8);
        header[3] = (byte) (VideoId & 0xFF);

        header[4] = (byte) (header[4] | TileEnd << (7-0));
        header[4] = (byte) (header[4] | Slot >> 24 & 0x7F);
        header[5] = (byte) (Slot >> 16);
        header[6] = (byte) (Slot >> 8);
        header[7] = (byte) (Slot & 0xFF);

        // if packet acknowledgement
        if (AckType == 1) {
            int intBits = Float.floatToIntBits(delay);
            header[8] = (byte) (intBits >> 24);
            header[9] = (byte) (intBits >> 16);
            header[10] = (byte) (intBits >> 8);
            header[11] = (byte) (intBits & 0xFF);
        }
        // if display acknowledgement
        else if (AckType == 0) {
            int intBits = Float.floatToIntBits(throughput);
            header[8] = (byte) (intBits >> 24);
            header[9] = (byte) (intBits >> 16);
            header[10] = (byte) (intBits >> 8);
            header[11] = (byte) (intBits & 0xFF);
        }


    }

    //--------------------------
    //Constructor of an RTPpacket object from the packet bistream
    //--------------------------
    public ACKpacket(byte[] packet, int packet_size)
    {
        //check if total packet size is equal to the header size
        if (packet_size == HEADER_SIZE)
        {
            //get the header bitsream:
            header = new byte[HEADER_SIZE];
            for (int i=0; i < HEADER_SIZE; i++)
                header[i] = packet[i];

            //interpret the fields of the header:
            AckType = header[0] >> 6 & 3;
            VideoIdNegFlag = header[0] >> 5 & 1;
            if (VideoIdNegFlag == 1) VideoId = -1;
            else VideoId = unsigned_int(header[3]) + 256*unsigned_int(header[2]) + 65536*unsigned_int(header[1]) + 16777216*unsigned_int(header[0] & 31);
            TileEnd = header[4] >> 7 & 1;
            Slot = unsigned_int(header[7]) + 256*unsigned_int(header[6]) + 65536*unsigned_int(header[5]) + 16777216*unsigned_int(header[4] & 127);

            if (AckType == 1) {
                int intBits = header[8] << 24 | (header[9] & 0xFF) << 16 | (header[10] & 0xFF) << 8 | (header[11] & 0xFF);
                Delay = Float.intBitsToFloat(intBits);
            }
            else if (AckType == 0) {
                int intBits = header[8] << 24 | (header[9] & 0xFF) << 16 | (header[10] & 0xFF) << 8 | (header[11] & 0xFF);
                Throughput = Float.intBitsToFloat(intBits);
            }
        }
    }

    //--------------------------
    //getlength: return the total length of the ACK packet
    //--------------------------
    public int getlength() {
        return(HEADER_SIZE);
    }

    //--------------------------
    //getpacket: returns the packet bitstream and its length
    //--------------------------
    public int getpacket(byte[] packet)
    {
        //construct the packet = header + payload
        for (int i=0; i < HEADER_SIZE; i++)
            packet[i] = header[i];

        //return total size of the packet
        return(HEADER_SIZE);
    }


    //--------------------------
    //gettile_id: return the id of current tile
    //--------------------------
    public int gettileid() {
        return VideoId;
    }

    //--------------------------
    //getendoftile: return the indicator of the end of tile transmission in slot
    //--------------------------
    public int getendoftile() {
        return TileEnd;
    }

    //--------------------------
    //getdelay: return the delay when reach the end of the tile
    //--------------------------
    public float getdelay() {
        return Delay;
    }

    //--------------------------
    //getthroughput: return the estimated throughput
    //--------------------------
    public float getthroughput() {
        return Throughput;
    }

    //--------------------------
    //gettimeslot
    //--------------------------
    public int gettimeslot() {
        return(Slot);
    }

    //--------------------------
    //getacktype
    //--------------------------
    public int getacktype() {
        return(AckType);
    }

    //--------------------------
    //print headers without the SSRC
    //--------------------------
    public void printheader()
    {
        //TO DO: uncomment

        for (int i=0; i < (HEADER_SIZE); i++)
        {
            for (int j = 7; j>=0 ; j--)
                if (((1<<j) & header[i] ) != 0)
                    System.out.print("1");
                else
                    System.out.print("0");
            System.out.print(" ");
        }

        System.out.println();

    }

    //return the unsigned value of 8-bit integer nb
    static int unsigned_int(int nb) {
        if (nb >= 0)
            return(nb);
        else
            return(256+nb);
    }


}

