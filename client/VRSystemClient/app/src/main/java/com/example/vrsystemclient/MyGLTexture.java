package com.example.vrsystemclient;

import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES30;
import android.opengl.Matrix;

import java.nio.IntBuffer;

// FBO class

public class MyGLTexture {
    private int glTexture;
    private int[] frameBuffers = new int[1];


    public MyGLTexture() {
        GLES30.glGenFramebuffers(1, frameBuffers, 0);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, frameBuffers[0]);
        int[] texId = new int[1];
        GLES30.glGenTextures(1, IntBuffer.wrap(texId));
        glTexture = texId[0];
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, glTexture);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGB, Config.FRAME_WIDTH, Config.FRAME_HEIGHT, 0, GLES30.GL_RGB, GLES30.GL_UNSIGNED_SHORT_5_6_5, null);
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, glTexture, 0);
        if (GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException("glCheckFramebufferStatus error");
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0);
    }



    // copy from external android surfaceTexture to this gl texture(fbo)


    public void copyColorTexture(SurfaceTexture surfaceTexture, int extTextureID) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, frameBuffers[0]);
        GLES30.glViewport(0, 0, Config.FRAME_WIDTH, Config.FRAME_HEIGHT);
        GLES30.glUseProgram(OpenGLHelper.mProgramFBO);

        surfaceTexture.getTransformMatrix(OpenGLHelper.mTextureMatrix);

        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, extTextureID);

        OpenGLHelper.mTriangleVerticesForColor.position(OpenGLHelper.TRIANGLE_VERTICES_DATA_POS_OFFSET);
        GLES30.glVertexAttribPointer(OpenGLHelper.maPositionHandleFBO, 3, GLES30.GL_FLOAT, false,
                OpenGLHelper.TRIANGLE_VERTICES_DATA_STRIDE_BYTES, OpenGLHelper.mTriangleVerticesForColor);
        GLES30.glEnableVertexAttribArray(OpenGLHelper.maPositionHandleFBO);
        OpenGLHelper.mTriangleVerticesForColor.position(OpenGLHelper.TRIANGLE_VERTICES_DATA_UV_OFFSET);
        GLES30.glVertexAttribPointer(OpenGLHelper.maTextureHandleFBO, 2, GLES30.GL_FLOAT, false,
                OpenGLHelper.TRIANGLE_VERTICES_DATA_STRIDE_BYTES, OpenGLHelper.mTriangleVerticesForColor);
        GLES30.glEnableVertexAttribArray(OpenGLHelper.maTextureHandleFBO);

        //actually draw it
        Matrix.setIdentityM(OpenGLHelper.mMVPMatrix, 0);
        GLES30.glUniformMatrix4fv(OpenGLHelper.muMVPMatrixHandleFBO, 1, false, OpenGLHelper.mMVPMatrix, 0);
        GLES30.glUniformMatrix4fv(OpenGLHelper.muSTMatrixHandleFBO, 1, false, OpenGLHelper.mTextureMatrix, 0);

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4);
        Utils.checkGlError();

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
        GLES30.glDisableVertexAttribArray(OpenGLHelper.maPositionHandleFBO);
        GLES30.glDisableVertexAttribArray(OpenGLHelper.maTextureHandleFBO);
        Utils.checkGlError();
    }

    public void copyDepthTexture(SurfaceTexture surfaceTexture, int extTextureID) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, frameBuffers[0]);
        GLES30.glViewport(0, 0, Config.FRAME_WIDTH, Config.FRAME_HEIGHT);
        GLES30.glUseProgram(OpenGLHelper.mProgramFBO);

        surfaceTexture.getTransformMatrix(OpenGLHelper.mTextureMatrix);

        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, extTextureID);

        OpenGLHelper.mTriangleVerticesForDepth.position(OpenGLHelper.TRIANGLE_VERTICES_DATA_POS_OFFSET);
        GLES30.glVertexAttribPointer(OpenGLHelper.maPositionHandleFBO, 3, GLES30.GL_FLOAT, false,
                OpenGLHelper.TRIANGLE_VERTICES_DATA_STRIDE_BYTES, OpenGLHelper.mTriangleVerticesForDepth);
        GLES30.glEnableVertexAttribArray(OpenGLHelper.maPositionHandleFBO);
        OpenGLHelper.mTriangleVerticesForDepth.position(OpenGLHelper.TRIANGLE_VERTICES_DATA_UV_OFFSET);
        GLES30.glVertexAttribPointer(OpenGLHelper.maTextureHandleFBO, 2, GLES30.GL_FLOAT, false,
                OpenGLHelper.TRIANGLE_VERTICES_DATA_STRIDE_BYTES, OpenGLHelper.mTriangleVerticesForDepth);
        GLES30.glEnableVertexAttribArray(OpenGLHelper.maTextureHandleFBO);

        //actually draw it
        Matrix.setIdentityM(OpenGLHelper.mMVPMatrix, 0);
        GLES30.glUniformMatrix4fv(OpenGLHelper.muMVPMatrixHandleFBO, 1, false, OpenGLHelper.mMVPMatrix, 0);
        GLES30.glUniformMatrix4fv(OpenGLHelper.muSTMatrixHandleFBO, 1, false, OpenGLHelper.mTextureMatrix, 0);

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4);
        Utils.checkGlError();

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
        GLES30.glDisableVertexAttribArray(OpenGLHelper.maPositionHandleFBO);
        GLES30.glDisableVertexAttribArray(OpenGLHelper.maTextureHandleFBO);
        Utils.checkGlError();
    }


    // draw data from this FBO
    public void draw(int tileID, SurfaceTexture displaySurfaceTexture) {

        displaySurfaceTexture.getTransformMatrix(OpenGLHelper.mTextureMatrix);

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, glTexture);

        if (Config.bProjection) {
            MySphere sphere = OpenGLHelper.mSpheres[tileID];

            GLES30.glEnableVertexAttribArray(OpenGLHelper.maPositionHandle);
            OpenGLHelper.checkGlError("glEnableVertexAttribArray");
            GLES30.glVertexAttribPointer(OpenGLHelper.maPositionHandle, 3,
                    GLES30.GL_FLOAT, false, sphere.getVerticesStride(), sphere.getVertices());
            OpenGLHelper.checkGlError("glVertexAttribPointer");

            GLES30.glEnableVertexAttribArray(OpenGLHelper.maTextureHandle);
            OpenGLHelper.checkGlError("glEnableVertexAttribArray");
            GLES30.glVertexAttribPointer(OpenGLHelper.maTextureHandle, 2,
                    GLES30.GL_FLOAT, false, sphere.getVerticesStride(),
                    sphere.getVertices().duplicate().position(3));
            OpenGLHelper.checkGlError("glVertexAttribPointer");

            //actually draw it
            Matrix.translateM(OpenGLHelper.mTextureMatrix, 0, 0, 1, 0);
            Matrix.multiplyMM(OpenGLHelper.mMVPMatrix, 0, OpenGLHelper.projectionMatrix, 0, OpenGLHelper.viewMatrix, 0);
            float _t;
            _t = OpenGLHelper.mMVPMatrix[4]; OpenGLHelper.mMVPMatrix[4] = OpenGLHelper.mMVPMatrix[8]; OpenGLHelper.mMVPMatrix[8] = -_t;
            _t = OpenGLHelper.mMVPMatrix[5]; OpenGLHelper.mMVPMatrix[5] = OpenGLHelper.mMVPMatrix[9]; OpenGLHelper.mMVPMatrix[9] = -_t;
            _t = OpenGLHelper.mMVPMatrix[6]; OpenGLHelper.mMVPMatrix[6] = OpenGLHelper.mMVPMatrix[10]; OpenGLHelper.mMVPMatrix[10] = -_t;
            _t = OpenGLHelper.mMVPMatrix[7]; OpenGLHelper.mMVPMatrix[7] = OpenGLHelper.mMVPMatrix[11]; OpenGLHelper.mMVPMatrix[11] = -_t;

            GLES30.glUniformMatrix4fv(OpenGLHelper.muMVPMatrixHandle, 1, false, OpenGLHelper.mMVPMatrix, 0);
            GLES30.glUniformMatrix4fv(OpenGLHelper.muTextureMatrixHandle, 1, false, OpenGLHelper.mTextureMatrix, 0);

            for (int j = 0; j < sphere.getNumIndices().length; ++j) {
                GLES30.glDrawElements(GLES30.GL_TRIANGLES,
                        sphere.getNumIndices()[j], GLES30.GL_UNSIGNED_SHORT,
                        sphere.getIndices()[j]);
            }

        } else {
            OpenGLHelper.mTriangleVertices.position(OpenGLHelper.TRIANGLE_VERTICES_DATA_POS_OFFSET);
            GLES30.glVertexAttribPointer(OpenGLHelper.maPositionHandle, 3, GLES30.GL_FLOAT, false,
                    OpenGLHelper.TRIANGLE_VERTICES_DATA_STRIDE_BYTES, OpenGLHelper.mTriangleVertices);
            OpenGLHelper.checkGlError("glVertexAttribPointer maPosition");
            GLES30.glEnableVertexAttribArray(OpenGLHelper.maPositionHandle);
            OpenGLHelper.checkGlError("glEnableVertexAttribArray maPositionHandle");

            OpenGLHelper.mTriangleVertices.position(OpenGLHelper.TRIANGLE_VERTICES_DATA_UV_OFFSET);
            GLES30.glVertexAttribPointer(OpenGLHelper.maTextureHandle, 2, GLES30.GL_FLOAT, false,
                    OpenGLHelper.TRIANGLE_VERTICES_DATA_STRIDE_BYTES, OpenGLHelper.mTriangleVertices);
            OpenGLHelper.checkGlError("glVertexAttribPointer maTextureHandle");
            GLES30.glEnableVertexAttribArray(OpenGLHelper.maTextureHandle);
            OpenGLHelper.checkGlError("glEnableVertexAttribArray maTextureHandle");

            //actually draw it
            Matrix.setIdentityM(OpenGLHelper.mMVPMatrix, 0);
            GLES30.glUniformMatrix4fv(OpenGLHelper.muMVPMatrixHandle, 1, false, OpenGLHelper.mMVPMatrix, 0);
            GLES30.glUniformMatrix4fv(OpenGLHelper.muTextureMatrixHandle, 1, false, OpenGLHelper.mTextureMatrix, 0);

            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4);
            OpenGLHelper.checkGlError("glDrawArrays");
        }

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0);
        GLES30.glDisableVertexAttribArray(OpenGLHelper.maPositionHandleFBO);
        GLES30.glDisableVertexAttribArray(OpenGLHelper.maTextureHandleFBO);
    }



    public int getTextureID() {
        return this.glTexture;
    }
}