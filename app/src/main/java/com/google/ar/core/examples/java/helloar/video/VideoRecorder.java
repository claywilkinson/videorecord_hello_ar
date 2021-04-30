/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.ar.core.examples.java.helloar.video;

import android.graphics.Rect;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES20;

import java.io.File;
import java.io.IOException;


/**
 * Created by wilkinsonclay on 12/28/17.
 */

public class VideoRecorder {
    public static final int DEFAULT_BITRATE = 4000000;   // 4Mbps
    private TextureMovieEncoder2 mVideoEncoder;
    private CaptureContext mEncoderContext;
    private Rect mVideoRect;
    private boolean mRecording;
    private final VideoRecorderListener listener;
    private final VideoEncoderCore mEncoderCore;
    private EGLConfig mEGLConfig;

    public VideoRecorder(int width, int height, int bitrate, File outputFile, VideoRecorderListener listener) throws IOException {
        this.listener = listener;
        mEncoderCore = new VideoEncoderCore(width, height, bitrate, outputFile);
        mVideoRect = new Rect(0,0,width,height);
    }

    public CaptureContext startCapture() {

        if (mVideoEncoder == null) {
            return null;
        }

        if (mEncoderContext == null) {
            mEncoderContext = new CaptureContext();
            mEncoderContext.windowDisplay = EGL14.eglGetCurrentDisplay();

            // Create a window surface, and attach it to the Surface we received.
            int[] surfaceAttribs = {
                    EGL14.EGL_NONE
            };

            mEncoderContext.windowDrawSurface = EGL14.eglCreateWindowSurface(
                    mEncoderContext.windowDisplay,mEGLConfig,mEncoderCore.getInputSurface(),surfaceAttribs,0);
            mEncoderContext.windowReadSurface = mEncoderContext.windowDrawSurface;
        }

        // swap the egl buffers for the current surface
        CaptureContext displayContext = new CaptureContext();
        displayContext.initialize();
        EGL14.eglSwapBuffers(displayContext.windowDisplay, displayContext.windowDrawSurface);

        // Draw for recording, swap.
        mVideoEncoder.frameAvailableSoon();


        // Make the input surface current
        // mInputWindowSurface.makeCurrent();
        EGL14.eglMakeCurrent(mEncoderContext.windowDisplay,
                mEncoderContext.windowDrawSurface, mEncoderContext.windowReadSurface,
                EGL14.eglGetCurrentContext());

        // If we don't set the scissor rect, the glClear() we use to draw the
        // light-grey background will draw outside the viewport and muck up our
        // letterboxing.  Might be better if we disabled the test immediately after
        // the glClear().  Of course, if we were clearing the frame background to
        // black it wouldn't matter.
        //
        // We do still need to clear the pixels outside the scissor rect, of course,
        // or we'll get garbage at the edges of the recording.  We can either clear
        // the whole thing and accept that there will be a lot of overdraw, or we
        // can issue multiple scissor/clear calls.  Some GPUs may have a special
        // optimization for zeroing out the color buffer.
        //
        // For now, be lazy and zero the whole thing.  At some point we need to
        // examine the performance here.
        GLES20.glClearColor(0f, 0f, 0f, 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        GLES20.glViewport(mVideoRect.left, mVideoRect.top,
                mVideoRect.width(), mVideoRect.height());
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        GLES20.glScissor(mVideoRect.left, mVideoRect.top,
                mVideoRect.width(), mVideoRect.height());

        return displayContext;
    }

    public void stopCapture(CaptureContext oldContext, long timeStampNanos) {

        if (oldContext == null) {
            return;
        }
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        EGLExt.eglPresentationTimeANDROID(mEncoderContext.windowDisplay, mEncoderContext.windowDrawSurface, timeStampNanos);
        // mInputWindowSurface.setPresentationTime(timeStampNanos);

        //mInputWindowSurface.swapBuffers();
        EGL14.eglSwapBuffers(mEncoderContext.windowDisplay, mEncoderContext.windowDrawSurface);


        // Restore.
        GLES20.glViewport(0, 0, oldContext.getWidth(), oldContext.getHeight());
        EGL14.eglMakeCurrent(oldContext.windowDisplay,
                oldContext.windowDrawSurface, oldContext.windowReadSurface,
                EGL14.eglGetCurrentContext());
    }

    public boolean isRecording() {
        return mRecording;
    }

    public void toggleRecording() {
        if (isRecording()) {
            stopRecording();
        } else {
            startRecording();
        }
    }

    protected void startRecording() {
        mRecording = true;
        if (mVideoEncoder == null) {
            mVideoEncoder = new TextureMovieEncoder2(mEncoderCore);
        }
        if (listener != null) {
            listener.onVideoRecorderEvent(VideoEvent.RecordingStarted);
        }
    }

    protected void stopRecording() {
        mRecording = false;
        if (mVideoEncoder != null) {
            mVideoEncoder.stopRecording();
        }
        if (listener != null) {
            listener.onVideoRecorderEvent(VideoEvent.RecordingStopped);
        }
    }

    public void setEglConfig(EGLConfig eglConfig) {
        this.mEGLConfig = eglConfig;
    }

    public static class CaptureContext {
        EGLDisplay windowDisplay;
        EGLSurface windowReadSurface;
        EGLSurface windowDrawSurface;
        private int mWidth;
        private int mHeight;

        public void initialize() {
            windowDisplay = EGL14.eglGetCurrentDisplay();
            windowReadSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW);
            windowDrawSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_READ);
            int[] v = new int[1];
            EGL14.eglQuerySurface(windowDisplay, windowDrawSurface, EGL14.EGL_WIDTH, v, 0);
            mWidth = v[0];
            v[0] = -1;
            EGL14.eglQuerySurface(windowDisplay, windowDrawSurface, EGL14.EGL_HEIGHT, v, 0);
            mHeight = v[0];
        }

        /**
         * Returns the surface's width, in pixels.
         * <p>
         * If this is called on a window surface, and the underlying surface is in the process
         * of changing size, we may not see the new size right away (e.g. in the "surfaceChanged"
         * callback).  The size should match after the next buffer swap.
         */
        public int getWidth() {
            if (mWidth < 0) {
                int[] v = new int[1];
                EGL14.eglQuerySurface(windowDisplay, windowDrawSurface, EGL14.EGL_WIDTH, v, 0);
                mWidth = v[0];
            }
            return mWidth;
        }

        /**
         * Returns the surface's height, in pixels.
         */
        public int getHeight() {
            if (mHeight < 0) {
                int[] v = new int[1];
                EGL14.eglQuerySurface(windowDisplay, windowDrawSurface, EGL14.EGL_HEIGHT, v, 0);
                mHeight = v[0];
            }
            return mHeight;
        }

    }

    public enum VideoEvent {
        RecordingStarted,
        RecordingStopped
    }

    public interface VideoRecorderListener {

        void onVideoRecorderEvent(VideoEvent videoEvent);
    }
}
