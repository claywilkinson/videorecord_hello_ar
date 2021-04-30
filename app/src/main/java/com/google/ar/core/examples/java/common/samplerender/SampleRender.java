/*
 * Copyright 2020 Google LLC
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
package com.google.ar.core.examples.java.common.samplerender;

import android.content.res.AssetManager;
import android.opengl.EGL14;
import android.opengl.EGLDisplay;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.os.Environment;
import android.util.Log;

import com.google.ar.core.Frame;
import com.google.ar.core.examples.java.helloar.video.VideoRecorder;

import java.io.File;
import java.io.IOException;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.opengles.GL10;

/** A SampleRender context. */
public class SampleRender {
  private static final String TAG = SampleRender.class.getSimpleName();

  private final AssetManager assetManager;

  private int viewportWidth = 1;
  private int viewportHeight = 1;

  // VideoRecording support
  private VideoRecorder mRecorder;
  private android.opengl.EGLConfig mAndroidEGLConfig;

  /**
   * Constructs a SampleRender object and instantiates GLSurfaceView parameters.
   *
   * @param glSurfaceView Android GLSurfaceView
   * @param renderer Renderer implementation to receive callbacks
   * @param assetManager AssetManager for loading Android resources
   */
  public SampleRender(GLSurfaceView glSurfaceView, Renderer renderer, AssetManager assetManager) {
    this.assetManager = assetManager;
    glSurfaceView.setPreserveEGLContextOnPause(true);
    glSurfaceView.setEGLContextClientVersion(3);
    glSurfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
    glSurfaceView.setRenderer(
        new GLSurfaceView.Renderer() {
          @Override
          public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            GLES30.glEnable(GLES30.GL_BLEND);
            GLError.maybeThrowGLException("Failed to enable blending", "glEnable");
            renderer.onSurfaceCreated(SampleRender.this);
            // Initialize video support.
            initializeVideoSupport(config);
          }

          @Override
          public void onSurfaceChanged(GL10 gl, int w, int h) {
            viewportWidth = w;
            viewportHeight = h;
            renderer.onSurfaceChanged(SampleRender.this, w, h);
          }

          @Override
          public void onDrawFrame(GL10 gl) {
            clear(/*framebuffer=*/ null, 0f, 0f, 0f, 1f);
            Frame frame = renderer.onDrawFrame(SampleRender.this,null);
            if (frame != null&& mRecorder!= null && mRecorder.isRecording()) {
              VideoRecorder.CaptureContext ctx = mRecorder.startCapture();
              if (ctx != null) {
                // draw again
               renderer.onDrawFrame(SampleRender.this, frame);

                // restore the context
                mRecorder.stopCapture(ctx, frame.getTimestamp());
              }
            }
          }
        });
    glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    glSurfaceView.setWillNotDraw(false);
  }

  void initializeVideoSupport(EGLConfig config) {
    EGL10 egl10 =  (EGL10) EGLContext.getEGL();
    javax.microedition.khronos.egl.EGLDisplay display = egl10.eglGetCurrentDisplay();
    int v[] = new int[2];
    egl10.eglGetConfigAttrib(display,config, EGL10.EGL_CONFIG_ID, v);

    EGLDisplay androidDisplay = EGL14.eglGetCurrentDisplay();
    int attribs[] = {EGL14.EGL_CONFIG_ID, v[0], EGL14.EGL_NONE};
    android.opengl.EGLConfig myConfig[] = new android.opengl.EGLConfig[1];
    EGL14.eglChooseConfig(androidDisplay, attribs, 0, myConfig, 0, 1, v, 1);
    mAndroidEGLConfig = myConfig[0];
  }

  /** Draw a {@link Mesh} with the specified {@link Shader}. */
  public void draw(Mesh mesh, Shader shader) {
    draw(mesh, shader, /*framebuffer=*/ null);
  }

  /**
   * Draw a {@link Mesh} with the specified {@link Shader} to the given {@link Framebuffer}.
   *
   * <p>The {@code framebuffer} argument may be null, in which case the default framebuffer is used.
   */
  public void draw(Mesh mesh, Shader shader, Framebuffer framebuffer) {
    useFramebuffer(framebuffer);
    shader.lowLevelUse();
    mesh.lowLevelDraw();
  }

  /**
   * Clear the given framebuffer.
   *
   * <p>The {@code framebuffer} argument may be null, in which case the default framebuffer is
   * cleared.
   */
  public void clear(Framebuffer framebuffer, float r, float g, float b, float a) {
    useFramebuffer(framebuffer);
    GLES30.glClearColor(r, g, b, a);
    GLError.maybeThrowGLException("Failed to set clear color", "glClearColor");
    GLES30.glDepthMask(true);
    GLError.maybeThrowGLException("Failed to set depth write mask", "glDepthMask");
    GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);
    GLError.maybeThrowGLException("Failed to clear framebuffer", "glClear");
  }

  /** Interface to be implemented for rendering callbacks. */
  public static interface Renderer {
    /**
     * Called by {@link SampleRender} when the GL render surface is created.
     *
     * <p>See {@link GLSurfaceView.Renderer#onSurfaceCreated}.
     */
    public void onSurfaceCreated(SampleRender render);

    /**
     * Called by {@link SampleRender} when the GL render surface dimensions are changed.
     *
     * <p>See {@link GLSurfaceView.Renderer#onSurfaceChanged}.
     */
    public void onSurfaceChanged(SampleRender render, int width, int height);

    /**
     * Called by {@link SampleRender} when a GL frame is to be rendered.
     *
     * <p>See {@link GLSurfaceView.Renderer#onDrawFrame}.
     *
     * Returns the timestamp of the frame.
     */
    public Frame onDrawFrame(SampleRender render, Frame currentFrame);
  }

  /* package-private */
  AssetManager getAssets() {
    return assetManager;
  }

  private void useFramebuffer(Framebuffer framebuffer) {
    int framebufferId;
    int viewportWidth;
    int viewportHeight;
    if (framebuffer == null) {
      framebufferId = 0;
      viewportWidth = this.viewportWidth;
      viewportHeight = this.viewportHeight;
    } else {
      framebufferId = framebuffer.getFramebufferId();
      viewportWidth = framebuffer.getWidth();
      viewportHeight = framebuffer.getHeight();
    }
    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebufferId);
    GLError.maybeThrowGLException("Failed to bind framebuffer", "glBindFramebuffer");
    GLES30.glViewport(0, 0, viewportWidth, viewportHeight);
    GLError.maybeThrowGLException("Failed to set viewport dimensions", "glViewport");
  }

  public boolean toggleRecording() {
    if (mRecorder == null) {
      File outputFile = new File(Environment.getExternalStoragePublicDirectory(
              Environment.DIRECTORY_PICTURES) + "/HelloAR",
              "fbo-gl-" + Long.toHexString(System.currentTimeMillis()) + ".mp4");
      File dir = outputFile.getParentFile();
      if (!dir.exists()) {
        dir.mkdirs();
      }

      try {
        mRecorder = new VideoRecorder(viewportWidth,
                viewportHeight,
                VideoRecorder.DEFAULT_BITRATE, outputFile, new VideoRecorder.VideoRecorderListener() {
          @Override
          public void onVideoRecorderEvent(VideoRecorder.VideoEvent videoEvent) {
              Log.d(TAG, "VideoEvent: " + videoEvent);
              if (videoEvent == VideoRecorder.VideoEvent.RecordingStopped) {
                mRecorder = null;
              }
          }
        });
        mRecorder.setEglConfig(mAndroidEGLConfig);
      } catch (IOException e) {
        Log.e(TAG,"Exception starting recording", e);
      }
    }
    mRecorder.toggleRecording();
    return mRecorder != null && mRecorder.isRecording();
  }

}
