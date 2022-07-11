package com.example.vrsystemclient;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

public class MySphere {

    public static final int FLOAT_SIZE = 4;
    public static final int SHORT_SIZE = 2;
    private FloatBuffer mVertices;
    private ShortBuffer[] mIndices;
    private int[] mNumIndices;
    private int mTotalIndices;

    /*
     * @param nSlices how many slice in horizontal direction.
     *                The same slice for vertical direction is applied.
     *                nSlices should be > 1 and should be <= 180
     * @param x,y,z the origin of the sphere
     * @param r the radius of the sphere
     */
    public MySphere(int nTotSlices, int nRows, int nCols, int row, int col,
                    float x, float y, float z, float r, int numIndexBuffers) {

        int nSlicesX = nTotSlices / nCols;
        int nSlicesY = nTotSlices / nRows;

        int iMaxX = nSlicesX + 1;
        int iMaxY = nSlicesY + 1;
        int nVertices = iMaxX * iMaxY;

        if (nVertices * nRows * nCols > 35000) {    //was Short.MAX_VALUE
            // this cannot be handled in one vertices / indices pair
            throw new RuntimeException("nSlices too big for vertex");
        }

        mTotalIndices = nSlicesX * nSlicesY * 6;
        float angleStepI = ((float) Math.PI / (nSlicesY * nRows));             //Y (T)
        float angleStepJ = ((2.0f * (float) Math.PI) / (nSlicesX * nCols));    //X (S)
        // 3 vertex coords + 2 texture coords
        mVertices = ByteBuffer.allocateDirect(nVertices * 5 * FLOAT_SIZE)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        mIndices = new ShortBuffer[numIndexBuffers];
        mNumIndices = new int[numIndexBuffers];
        // first evenly distribute to n-1 buffers, then put remaining ones to the last one.
        int noIndicesPerBuffer = (mTotalIndices / numIndexBuffers / 6) * 6;
        for (int i = 0; i < numIndexBuffers - 1; i++) {
            mNumIndices[i] = noIndicesPerBuffer;
        }
        mNumIndices[numIndexBuffers - 1] = mTotalIndices - noIndicesPerBuffer *
                (numIndexBuffers - 1);
        for (int i = 0; i < numIndexBuffers; i++) {
            mIndices[i] = ByteBuffer.allocateDirect(mNumIndices[i] * SHORT_SIZE)
                    .order(ByteOrder.nativeOrder()).asShortBuffer();
        }
        // calling put for each float took too much CPU time, so put by line instead
        float[] vLineBuffer = new float[iMaxX * 5];
        for (int i = 0; i < iMaxY; i++) {       //Y
            for (int j = 0; j < iMaxX; j++) {   //X
                int vertexBase = j * 5;

                int baseI = row * nSlicesY;
                int baseJ = col * nSlicesX;

                float sini = (float) Math.sin(angleStepI * (baseI + i));
                float sinj = (float) Math.sin(angleStepJ * (baseJ + j));
                float cosi = (float) Math.cos(angleStepI * (baseI + i));
                float cosj = (float) Math.cos(angleStepJ * (baseJ + j));
                // vertex x,y,z
                vLineBuffer[vertexBase + 0] = x + r * sini * sinj;
                vLineBuffer[vertexBase + 1] = y + r * sini * cosj;
                vLineBuffer[vertexBase + 2] = z + r * cosi;
                // texture s,t
                vLineBuffer[vertexBase + 3] = (float) j / (float) nSlicesX;  //X
                vLineBuffer[vertexBase + 4] = -i / (float) nSlicesY; //Y
            }
            mVertices.put(vLineBuffer, 0, vLineBuffer.length);
        }
        short[] indexBuffer = new short[max(mNumIndices)];
        int index = 0;
        int bufferNum = 0;
        for (int i = 0; i < nSlicesY; i++) {
            for (int j = 0; j < nSlicesX; j++) {
                int i1 = i + 1;
                int j1 = j + 1;
                if (index >= mNumIndices[bufferNum]) {
                    // buffer ready for moving to target
                    mIndices[bufferNum].put(indexBuffer, 0, mNumIndices[bufferNum]);
                    // move to the next one
                    index = 0;
                    bufferNum++;
                }
                indexBuffer[index++] = (short) (i * iMaxX + j);
                indexBuffer[index++] = (short) (i1 * iMaxX + j);
                indexBuffer[index++] = (short) (i1 * iMaxX + j1);
                indexBuffer[index++] = (short) (i * iMaxX + j);
                indexBuffer[index++] = (short) (i1 * iMaxX + j1);
                indexBuffer[index++] = (short) (i * iMaxX + j1);
            }
        }
        mIndices[bufferNum].put(indexBuffer, 0, mNumIndices[bufferNum]);
        mVertices.position(0);
        for (int i = 0; i < numIndexBuffers; i++) {
            mIndices[i].position(0);
        }
    }

    public FloatBuffer getVertices() {
        return mVertices;
    }

    public int getVerticesStride() {
        return 5 * FLOAT_SIZE;
    }

    public ShortBuffer[] getIndices() {
        return mIndices;
    }

    public int[] getNumIndices() {
        return mNumIndices;
    }

    public int getTotalIndices() {
        return mTotalIndices;
    }

    private int max(int[] array) {
        int max = array[0];
        for (int i = 1; i < array.length; i++) {
            if (array[i] > max) max = array[i];
        }
        return max;
    }
}
