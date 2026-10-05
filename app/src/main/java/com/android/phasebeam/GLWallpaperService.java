package com.android.phasebeam;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.display.DisplayManager;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.Choreographer;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;

public abstract class GLWallpaperService extends WallpaperService
{
    private static final String TAG = "GLWallpaperService";

    // Half a 120Hz vsync short of the frame interval, so the delayed callback
    // lands on the same vsync on 60, 90 and 120Hz panels
    private static final long FRAME_DELAY_NANOS = 1_000_000_000L / 30 - 4_166_667L;
    private static final long POWER_SAVE_FRAME_DELAY_NANOS = 1_000_000_000L / 20 - 4_166_667L;

    private static final long SCROLL_BOOST_MS = 1000;

    public class GLEngine extends Engine
    {
        private WallpaperGLSurfaceView glSurfaceView;
        private boolean rendererHasBeenSet;
        private boolean visible;
        private long boostUntilMs;
        private Choreographer choreographer;
        private float maxRefreshRate;
        private boolean highFrameRateVoted;
        private boolean powerSaveMode;
        private PowerManager powerManager;
        PhaseBeamRenderer renderer;

        private final BroadcastReceiver powerSaveReceiver = new BroadcastReceiver()
        {
            @Override
            public void onReceive(Context context, Intent intent)
            {
                powerSaveMode = powerManager.isPowerSaveMode();
            }
        };

        private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback()
        {
            @Override
            public void doFrame(long frameTimeNanos)
            {
                if (!visible)
                {
                    return;
                }

                glSurfaceView.requestRender();

                boolean boosted = SystemClock.uptimeMillis() < boostUntilMs;
                voteHighFrameRate(boosted);

                if (boosted)
                {
                    choreographer.postFrameCallback(this);
                }
                else
                {
                    long lateNanos = System.nanoTime() - frameTimeNanos;
                    long frameDelayNanos = powerSaveMode ? POWER_SAVE_FRAME_DELAY_NANOS : FRAME_DELAY_NANOS;
                    long delayMs = Math.max(0, (frameDelayNanos - lateNanos) / 1_000_000L);
                    choreographer.postFrameCallbackDelayed(this, delayMs);
                }
            }
        };

        class WallpaperGLSurfaceView extends GLSurfaceView
        {

            WallpaperGLSurfaceView(Context context)
            {
                super(context);
            }

            public SurfaceHolder getHolder()
            {
                return getSurfaceHolder();
            }

            public void onDestroy()
            {
                super.onDetachedFromWindow();
            }
        }

        @Override
        public void onCreate(SurfaceHolder surfaceHolder)
        {
            super.onCreate(surfaceHolder);

            choreographer = Choreographer.getInstance();
            glSurfaceView = new WallpaperGLSurfaceView(GLWallpaperService.this);

            Display display = getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
            for (Display.Mode mode : display.getSupportedModes())
            {
                maxRefreshRate = Math.max(maxRefreshRate, mode.getRefreshRate());
            }

            powerManager = getSystemService(PowerManager.class);
            powerSaveMode = powerManager.isPowerSaveMode();
            IntentFilter powerSaveFilter = new IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            {
                registerReceiver(powerSaveReceiver, powerSaveFilter, Context.RECEIVER_NOT_EXPORTED);
            }
            else
            {
                registerReceiver(powerSaveReceiver, powerSaveFilter);
            }
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder)
        {
            super.onSurfaceDestroyed(holder);
            highFrameRateVoted = false;
        }

        @Override
        public void onVisibilityChanged(boolean visible)
        {
            super.onVisibilityChanged(visible);

            this.visible = visible;

            if(rendererHasBeenSet)
            {
                choreographer.removeFrameCallback(frameCallback);

                if (visible)
                {
                    glSurfaceView.onResume();
                    glSurfaceView.queueEvent(renderer::resetFrameTime);
                    choreographer.postFrameCallback(frameCallback);
                }
                else
                {
                    voteHighFrameRate(false);
                    glSurfaceView.onPause();
                }
            }
        }

        @Override
        public void onDestroy()
        {
            super.onDestroy();
            visible = false;
            unregisterReceiver(powerSaveReceiver);
            choreographer.removeFrameCallback(frameCallback);
            glSurfaceView.onDestroy();
        }

        protected void boostFrameRate()
        {
            boolean wasBoosted = SystemClock.uptimeMillis() < boostUntilMs;
            boostUntilMs = SystemClock.uptimeMillis() + SCROLL_BOOST_MS;

            if (visible && rendererHasBeenSet && !wasBoosted)
            {
                voteHighFrameRate(true);
                choreographer.removeFrameCallback(frameCallback);
                choreographer.postFrameCallback(frameCallback);
            }
        }

        private void voteHighFrameRate(boolean high)
        {
            if (high == highFrameRateVoted)
            {
                return;
            }

            Surface surface = getSurfaceHolder().getSurface();
            if (surface == null || !surface.isValid())
            {
                return;
            }

            try
            {
                surface.setFrameRate(high ? maxRefreshRate : 0.0f, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT,
                        Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS);
                highFrameRateVoted = high;
            }
            catch (RuntimeException e)
            {
                Log.w(TAG, "Unable to set surface frame rate", e);
            }
        }

        protected void setRenderer(GLSurfaceView.Renderer renderer)
        {
            this.renderer = (PhaseBeamRenderer)renderer;
            glSurfaceView.setRenderer(renderer);
            glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
            rendererHasBeenSet = true;
        }

        protected void setPreserveEGLContextOnPause(boolean preserve)
        {
            glSurfaceView.setPreserveEGLContextOnPause(preserve);
        }

        protected void setEGLContextClientVersion(int version)
        {
            glSurfaceView.setEGLContextClientVersion(version);
        }

        protected void setEGLConfigChooser(int redSize, int greenSize, int blueSize, int alphaSize, int depthSize, int stencilSize)
        {
            glSurfaceView.setEGLConfigChooser(redSize, greenSize, blueSize, alphaSize, depthSize, stencilSize);
        }
    }
}
