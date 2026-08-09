package com.winlator.cmod.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.winlator.cmod.renderer.GLRenderer;
import com.winlator.cmod.renderer.HostRenderer;
import com.winlator.cmod.renderer.vulkan.VulkanRenderer;
import com.winlator.cmod.xserver.XServer;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SuppressLint("ViewConstructor")
public class XServerView extends FrameLayout {
    private HostRenderer renderer;
    private SurfaceView vulkanSurfaceView;
    private GLSurfaceView glSurfaceView;
    private final ExecutorService eventExecutor = Executors.newSingleThreadExecutor();
    private XServer xServer;

    private float lastFrameRate = 0f;
    private int lastFrameRateCompat = 0;
    private static final boolean FRAME_RATE_SEAMLESS_ONLY = false;

    public XServerView(Context context, XServer xServer) {
        super(context);
        this.xServer = xServer;
        setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    public void initRenderer(boolean vulkan) {
        initRenderer(vulkan ? "vulkan" : "opengl_es");
    }

    public void initRenderer(String rendererType) {
        boolean vulkan = "vulkan".equalsIgnoreCase(rendererType);
        if (vulkan) {
            vulkanSurfaceView = new SurfaceView(getContext());
            vulkanSurfaceView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            addView(vulkanSurfaceView);
            renderer = new VulkanRenderer(this, xServer);
            vulkanSurfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(SurfaceHolder holder) {
                    ((VulkanRenderer)renderer).onSurfaceCreated(holder.getSurface());
                }
                @Override
                public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                    ((VulkanRenderer)renderer).onSurfaceChanged(width, height);
                    reassertFrameRate();
                }
                @Override
                public void surfaceDestroyed(SurfaceHolder holder) {
                    ((VulkanRenderer)renderer).onSurfaceDestroyed();
                }
            });
        } else {
            glSurfaceView = new GLSurfaceView(getContext());
            glSurfaceView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            glSurfaceView.setEGLContextClientVersion(3);
            glSurfaceView.setEGLConfigChooser(8, 8, 8, 8, 0, 0);
            glSurfaceView.setPreserveEGLContextOnPause(true);
            final GLRenderer glRenderer = new GLRenderer(this, xServer);
            renderer = glRenderer;
            glSurfaceView.setRenderer(glRenderer);
            glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
            glSurfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(SurfaceHolder holder) {}
                @Override
                public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                    reassertFrameRate();
                }
                @Override
                public void surfaceDestroyed(SurfaceHolder holder) {
                    glRenderer.onSurfaceDestroyed();
                }
            });
            addView(glSurfaceView);
        }
    }

    public HostRenderer getRenderer() {
        return renderer;
    }

    public SurfaceHolder getHolder() {
        return vulkanSurfaceView != null ? vulkanSurfaceView.getHolder() : null;
    }

    public void requestRender() {
        if (glSurfaceView != null) glSurfaceView.requestRender();
        else if (renderer != null) renderer.requestRender();
    }

    public void queueEvent(Runnable r) {
        if (glSurfaceView != null) glSurfaceView.queueEvent(r);
        else eventExecutor.execute(r);
    }

    public void onPause() {
        if (glSurfaceView != null) glSurfaceView.onPause();
    }

    public void onResume() {
        if (glSurfaceView != null) glSurfaceView.onResume();
    }

    public void setApexMode(boolean active) {
        if (glSurfaceView != null) {
            glSurfaceView.setRenderMode(active ? GLSurfaceView.RENDERMODE_CONTINUOUSLY : GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        }
    }

    public Object getSurfaceControl() {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            if (vulkanSurfaceView != null) return vulkanSurfaceView.getSurfaceControl();
            if (glSurfaceView != null) return glSurfaceView.getSurfaceControl();
        }
        return null;
    }

    public void setDisplayFrameRate(float fps, int compatibility) {
        lastFrameRate = fps;
        lastFrameRateCompat = compatibility;
        applyFrameRateToSurface(fps, compatibility);
    }

    private void reassertFrameRate() {
        applyFrameRateToSurface(lastFrameRate, lastFrameRateCompat);
    }

    private void applyFrameRateToSurface(float fps, int compatibility) {
        if (Build.VERSION.SDK_INT < 30) return;
        SurfaceHolder holder = null;
        if (vulkanSurfaceView != null) holder = vulkanSurfaceView.getHolder();
        else if (glSurfaceView != null) holder = glSurfaceView.getHolder();
        if (holder == null) return;
        Surface surface = holder.getSurface();
        if (surface == null || !surface.isValid()) return;
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                int strategy = FRAME_RATE_SEAMLESS_ONLY
                        ? Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS
                        : Surface.CHANGE_FRAME_RATE_ALWAYS;
                surface.setFrameRate(fps, compatibility, strategy);
            } else {
                surface.setFrameRate(fps, compatibility);
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
        }
    }
}
