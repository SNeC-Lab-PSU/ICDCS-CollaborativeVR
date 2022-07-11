package com.example.vrsystemclient;//class RTPpacket


/**
 * Write a description of RTPpacket here.
 * use videoID to replace the pos and ori info
 */
public class RTPpacket {
    //size of the RTP header:
    static int HEADER_SIZE = 32;

    //Fields that compose the RTP header
    public int Version;
    public int Padding;
    public int Extension;
    public int CC;
    public int Marker;
    public int PayloadType;
    public int SequenceNumber;
    public int TimeStamp;
    public int Ssrc;

    //Fields that corresponds to extension header
    public int TileLength;
    public int PktId;
    public int VideoId;
    public int TileEnd;
    public int PktEnd;

    public float OriX;
    public float OriY;

    //Bitstream of the RTP header
    public byte[] header;

    //size of the RTP payload
    public int payload_size;
    //Bitstream of the RTP payload
    public byte[] payload;



    //--------------------------
    //Constructor of an RTPpacket object from header fields and payload bitstream
    //--------------------------
    public RTPpacket(int PType, int Framenb, int Time, int ssrc, byte[] data, int data_length, int data_offset,
                     int tile_length, int video_id,  int tile_end, int pkt_id, int pkt_end, float ori_x, float ori_y ){
        //fill by default header fields:
        Version = 2;
        Padding = 0;
        Extension = 1;
        CC = 0;
        Marker = 0;
        Ssrc = ssrc;

        //fill changing header fields:
        SequenceNumber = Framenb;
        TimeStamp = Time;
        PayloadType = PType;

        //fill extension header fields:
        TileLength = tile_length;
        VideoId = video_id;
        PktId = pkt_id;
        TileEnd = tile_end;
        PktEnd = pkt_end;
        OriX = ori_x;
        OriY = ori_y;

        //build the header bistream:
        //--------------------------
        header = new byte[HEADER_SIZE];

        //.............
        //TO COMPLETE
        //.............
        //fill the header array of byte with RTP header fields

        header[0] = (byte) (header[0] | Version << (7-1));
        header[0] = (byte) (header[0] | Padding << (7-2));
        header[0] = (byte) (header[0] | Extension << (7-3));
        header[0] = (byte) (header[0] | CC << (7-7));

        header[1] = (byte) (header[1] | Marker << (7-0));
        header[1] = (byte) (header[1] | PayloadType << (7-7));

        header[2] = (byte) (SequenceNumber >> 8);
        header[3] = (byte) (SequenceNumber & 0xFF);

        header[4] = (byte) (TimeStamp >> 24);
        header[5] = (byte) (TimeStamp >> 16);
        header[6] = (byte) (TimeStamp >> 8);
        header[7] = (byte) (TimeStamp & 0xFF);

        header[8] = (byte) (Ssrc >> 24);
        header[9] = (byte) (Ssrc >> 16);
        header[10] = (byte) (Ssrc >> 8);
        header[11] = (byte) (Ssrc & 0xFF);

        //fill the extension header
        header[12] = (byte) (TileLength >> 24);
        header[13] = (byte) (TileLength >> 16);
        header[14] = (byte) (TileLength >> 8);
        header[15] = (byte) (TileLength & 0xFF);

        header[16] = (byte) (header[16] | TileEnd << (7-0));
        header[16] = (byte) (header[16] | VideoId >> 24);
        header[17] = (byte) (VideoId >> 16);
        header[18] = (byte) (VideoId >> 8);
        header[19] = (byte) (VideoId & 0xFF);

        header[20] = (byte) (header[20] | PktEnd << (7-0));
        header[20] = (byte) (header[20] | PktId >> 24);
        header[21] = (byte) (PktId >> 16);
        header[22] = (byte) (PktId >> 8);
        header[23] = (byte) (PktId & 0xFF);

        int intBits = Float.floatToIntBits(OriX);
        header[24] = (byte) (intBits >> 24);
        header[25] = (byte) (intBits >> 16);
        header[26] = (byte) (intBits >> 8);
        header[27] = (byte) (intBits & 0xFF);

        intBits = Float.floatToIntBits(OriY);
        header[28] = (byte) (intBits >> 24);
        header[29] = (byte) (intBits >> 16);
        header[30] = (byte) (intBits >> 8);
        header[31] = (byte) (intBits & 0xFF);

        //fill the payload bitstream:
        //--------------------------
        payload_size = data_length;
        payload = new byte[data_length];

        //fill payload array of byte from data (given in parameter of the constructor)
        System.arraycopy(data, data_offset, payload, 0, data_length);

        // ! Do not forget to uncomment method printheader() below !

    }

    //--------------------------
    //Constructor of an RTPpacket object from the packet bistream
    //--------------------------
    public RTPpacket(byte[] packet, int packet_size)
    {
        //fill default fields:
        Version = 2;
        Padding = 0;
        Extension = 1;
        CC = 0;
        Marker = 0;

        //check if total packet size is lower than the header size
        if (packet_size >= HEADER_SIZE)
        {
            //get the header bitsream:
            header = new byte[HEADER_SIZE];
            for (int i=0; i < HEADER_SIZE; i++)
                header[i] = packet[i];

            //get the payload bitstream:
            payload_size = packet_size - HEADER_SIZE;
            payload = new byte[payload_size];
            for (int i=HEADER_SIZE; i < packet_size; i++)
                payload[i-HEADER_SIZE] = packet[i];

            //interpret the changing fields of the header:
            PayloadType = header[1] & 127;
            SequenceNumber = unsigned_int(header[3]) + 256*unsigned_int(header[2]);
            TimeStamp = unsigned_int(header[7]) + 256*unsigned_int(header[6]) + 65536*unsigned_int(header[5]) + 16777216*unsigned_int(header[4]);
            Ssrc = unsigned_int(header[11]) + 256*unsigned_int(header[10]) + 65536*unsigned_int(header[9]) + 16777216*unsigned_int(header[8]);

            //interpret the extension header fields
            TileLength = unsigned_int(header[15]) + 256*unsigned_int(header[14]) + 65536*unsigned_int(header[13]) + 16777216*unsigned_int(header[12]);
            TileEnd = header[16] >> 7 & 1;
            VideoId =  unsigned_int(header[19]) + 256*unsigned_int(header[18]) + 65536*unsigned_int(header[17]) + 16777216*unsigned_int(header[16] & 127);
            PktEnd = header[20] >> 7 & 1;
            PktId =  unsigned_int(header[23]) + 256*unsigned_int(header[22]) + 65536*unsigned_int(header[21]) + 16777216*unsigned_int(header[20] & 127);

            int intBits = header[24] << 24 | (header[25] & 0xFF) << 16 | (header[26] & 0xFF) << 8 | (header[27] & 0xFF);
            OriX = Float.intBitsToFloat(intBits);
            intBits = header[28] << 24 | (header[29] & 0xFF) << 16 | (header[30] & 0xFF) << 8 | (header[31] & 0xFF);
            OriY = Float.intBitsToFloat(intBits);
        }
    }

    //--------------------------
    //getpayload: return the payload bistream of the RTPpacket and its size
    //--------------------------
    public int getpayload(byte[] data) {

        for (int i=0; i < payload_size; i++)
            data[i] = payload[i];

        return(payload_size);
    }

    //--------------------------
    //getpayload_length: return the length of the payload
    //--------------------------
    public int getpayloadlength() {
        return(payload_size);
    }

    //--------------------------
    //getlength: return the total length of the RTP packet
    //--------------------------
    public int getlength() {
        return(payload_size + HEADER_SIZE);
    }

    //--------------------------
    //getpacket: returns the packet bitstream and its length
    //--------------------------
    public int getpacket(byte[] packet)
    {
        //construct the packet = header + payload
        for (int i=0; i < HEADER_SIZE; i++)
            packet[i] = header[i];
        for (int i=0; i < payload_size; i++)
            packet[i+HEADER_SIZE] = payload[i];

        //return total size of the packet
        return(payload_size + HEADER_SIZE);
    }

    //--------------------------
    //gettile_length: return the total length of current tile
    //--------------------------
    public int gettilelength() {
        return TileLength;
    }

    //--------------------------
    //gettile_id: return the id of current tile
    //--------------------------
    public int getvideoid() {
        return VideoId;
    }

    //--------------------------
    //getpkt_id: return the packet id of current tile
    //--------------------------
    public int getpktid() {
        return PktId;
    }

    //--------------------------
    //getEndOfTile: return the indicator of the end of tile
    //--------------------------
    public int getendoftile() {
        return TileEnd;
    }

    //--------------------------
    //getEndOfPkt: return the indicator of the end of packets
    //--------------------------
    public int getendofpkt() {
        return PktEnd;
    }

    //--------------------------
    //getorix: return the orientation of x axis
    //--------------------------
    public float getorix() {
        return OriX;
    }

    //--------------------------
    //getoriy: return the orientation of y axis
    //--------------------------
    public float getoriy() {
        return OriY;
    }

    //--------------------------
    //getori: return a float array of the orientation of two axis
    //--------------------------
    public float[] getori() {
        float [] ori = new float[2];
        ori[0] = OriX;
        ori[1] = OriY;
        return ori;
    }

    //--------------------------
    //getssrc
    //--------------------------
    public int getssrc() {
        return(Ssrc);
    }

    //--------------------------
    //gettimestamp
    //--------------------------
    public int gettimestamp() {
        return(TimeStamp);
    }

    //--------------------------
    //getsequencenumber
    //--------------------------
    public int getsequencenumber() {
        return(SequenceNumber);
    }

    //--------------------------
    //getpayloadtype
    //--------------------------
    public int getpayloadtype() {
        return(PayloadType);
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