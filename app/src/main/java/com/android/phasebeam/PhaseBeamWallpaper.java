package com.android.phasebeam;

import android.app.WallpaperColors;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.view.SurfaceHolder;

public class PhaseBeamWallpaper extends GLWallpaperService
{
    private static final WallpaperColors WALLPAPER_COLORS = new WallpaperColors(
            Color.valueOf(0xFF5075C3),
            Color.valueOf(0xFF706DC3),
            Color.valueOf(0xFF7C4DFF),
            WallpaperColors.HINT_SUPPORTS_DARK_THEME);

    @Override
    public Engine onCreateEngine()
    {
        return new PhaseBeamEngine();
    }

    private class PhaseBeamEngine extends GLWallpaperService.GLEngine
    {
        private boolean colorsReported;

        @Override
        public void onCreate(SurfaceHolder surfaceHolder)
        {
            super.onCreate(surfaceHolder);

            setTouchEventsEnabled(false);
            surfaceHolder.setSizeFromLayout();
            surfaceHolder.setFormat(PixelFormat.OPAQUE);

            setEGLContextClientVersion(2);

            setPreserveEGLContextOnPause(true);

            setEGLConfigChooser(8, 8, 8, 8, 0, 0);

            renderer = new PhaseBeamRenderer(PhaseBeamWallpaper.this);
            setRenderer(renderer);

            renderer.setPointScale(getResources().getDisplayMetrics().densityDpi / 240.0f);
        }

        @Override
        public void onSurfaceCreated(SurfaceHolder holder)
        {
            super.onSurfaceCreated(holder);

            if (!colorsReported)
            {
                colorsReported = true;
                notifyColorsChanged();
            }
        }

        @Override
        public WallpaperColors onComputeColors()
        {
            return WALLPAPER_COLORS;
        }

        @Override
        public void onOffsetsChanged(float xOffset, float yOffset, float xOffsetStep,
                                     float yOffsetStep, int xPixelOffset, int yPixelOffset) {
            renderer.setOffset(xOffset, yOffset, xPixelOffset, yPixelOffset);
            boostFrameRate();
        }
    }
}
