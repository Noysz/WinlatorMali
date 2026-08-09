package com.winlator.cmod.xserver.extensions;

import static com.winlator.cmod.xserver.XClientRequestHandler.RESPONSE_CODE_SUCCESS;

import android.util.SparseArray;

import com.winlator.cmod.renderer.GPUImage;
import com.winlator.cmod.renderer.Texture;
import com.winlator.cmod.xconnector.XInputStream;
import com.winlator.cmod.xconnector.XOutputStream;
import com.winlator.cmod.xconnector.XStreamLock;
import com.winlator.cmod.xserver.Bitmask;
import com.winlator.cmod.xserver.Drawable;
import com.winlator.cmod.xserver.Pixmap;
import com.winlator.cmod.xserver.Window;
import com.winlator.cmod.xserver.XClient;
import com.winlator.cmod.xserver.XLock;
import com.winlator.cmod.xserver.XServer;
import com.winlator.cmod.xserver.errors.BadImplementation;
import com.winlator.cmod.xserver.errors.BadMatch;
import com.winlator.cmod.xserver.errors.BadPixmap;
import com.winlator.cmod.xserver.errors.BadWindow;
import com.winlator.cmod.xserver.errors.XRequestError;
import com.winlator.cmod.xserver.events.PresentCompleteNotify;
import com.winlator.cmod.xserver.events.PresentIdleNotify;

import java.io.IOException;

public class PresentExtension implements Extension {
    public static final byte MAJOR_OPCODE = -103;
    private static final int FAKE_INTERVAL = 1000000 / 60;
    private static final long FIRE_EARLY_NS = 700_000L; // 0.7 ms
    public enum Kind {PIXMAP, MSC_NOTIFY}
    public enum Mode {COPY, FLIP, SKIP}
    private final SparseArray<Event> events = new SparseArray<>();
    private SyncExtension syncExtension;
    private long nextFrameTime = 0;
    private volatile int frameRateLimit = 0;

    public void setFrameRateLimit(int limit) { this.frameRateLimit = Math.max(0, limit); }

    private static class PendingIdle {
        Window window; Pixmap pixmap; int serial; int idleFence; long targetNs;
        PendingIdle(Window w, Pixmap p, int s, int f, long t) {
            window = w; pixmap = p; serial = s; idleFence = f; targetNs = t;
        }
    }
    private final java.util.concurrent.ConcurrentHashMap<Integer, PendingIdle> pendingIdles =
        new java.util.concurrent.ConcurrentHashMap<>();

    private static class WindowTiming { long nextIdleNs = 0; }
    private final java.util.concurrent.ConcurrentHashMap<Integer, WindowTiming> windowTimings =
        new java.util.concurrent.ConcurrentHashMap<>();

    private volatile android.view.Choreographer choreographer = null;
    private volatile boolean choreographerChecked = false;
    private volatile boolean choreographerPosted = false;
    private final Object choreographerLock = new Object();

    private Thread cpuPacerThread = null;
    private final java.util.concurrent.PriorityBlockingQueue<PendingIdle> cpuQueue =
        new java.util.concurrent.PriorityBlockingQueue<>(11,
            java.util.Comparator.comparingLong(p -> p.targetNs));

    public void close() {
        if (cpuPacerThread != null) { cpuPacerThread.interrupt(); cpuPacerThread = null; }
    }

    private static abstract class ClientOpcodes {
        private static final byte QUERY_VERSION = 0;
        private static final byte PRESENT_PIXMAP = 1;
        private static final byte SELECT_INPUT = 3;
    }

    private static class Event {
        private Window window;
        private XClient client;
        private int id;
        private Bitmask mask;
    }

    @Override
    public String getName() {
        return "Present";
    }

    @Override
    public byte getMajorOpcode() {
        return MAJOR_OPCODE;
    }

    @Override
    public byte getFirstErrorId() {
        return 0;
    }

    @Override
    public byte getFirstEventId() {
        return 0;
    }

    private void emitIdleNotify(Window window, Pixmap pixmap, int serial, int idleFence,
                                 int targetFps, com.winlator.cmod.renderer.vulkan.VulkanRenderer renderer) {
        if (targetFps <= 0) { sendIdleNotify(window, pixmap, serial, idleFence); return; }

        final long frameNs = 1_000_000_000L / targetFps;
        long now = System.nanoTime();
        WindowTiming wt = windowTimings.computeIfAbsent(window.id, k -> new WindowTiming());
        if (wt.nextIdleNs <= now - frameNs) wt.nextIdleNs = now + frameNs;
        else wt.nextIdleNs += frameNs;
        long fireTime = wt.nextIdleNs - FIRE_EARLY_NS;

        if (tryGetChoreographer(renderer) != null) {
            pendingIdles.put(window.id, new PendingIdle(window, pixmap, serial, idleFence, fireTime));
            postChoreographerCallback();
        } else {
            cpuQueue.offer(new PendingIdle(window, pixmap, serial, idleFence, fireTime));
        }
    }

    private android.view.Choreographer tryGetChoreographer(com.winlator.cmod.renderer.vulkan.VulkanRenderer renderer) {
        if (choreographerChecked) return choreographer;
        synchronized (choreographerLock) {
            if (choreographerChecked) return choreographer;
            choreographerChecked = true;
            try {
                choreographer = android.view.Choreographer.getInstance();
            } catch (Exception ignored) {
                android.util.Log.w("PresentExtension", "Choreographer unavailable, using CPU pacer");
            }
            if (choreographer == null) startCpuPacer();
            return choreographer;
        }
    }

    private final android.view.Choreographer.FrameCallback vsyncCallback = frameTimeNs -> {
        choreographerPosted = false;
        boolean anyRemaining = false;
        for (java.util.Iterator<java.util.Map.Entry<Integer, PendingIdle>> it =
                pendingIdles.entrySet().iterator(); it.hasNext(); ) {
            PendingIdle p = it.next().getValue();
            if (frameTimeNs >= p.targetNs) {
                it.remove();
                sendIdleNotify(p.window, p.pixmap, p.serial, p.idleFence);
            } else anyRemaining = true;
        }
        if (anyRemaining) postChoreographerCallback();
    };

    private void postChoreographerCallback() {
        if (choreographer == null || choreographerPosted) return;
        choreographerPosted = true;
        choreographer.postFrameCallback(vsyncCallback);
    }

    private void startCpuPacer() {
        if (cpuPacerThread != null) return;
        cpuPacerThread = new Thread(() -> {
            while (!Thread.interrupted()) {
                PendingIdle p = cpuQueue.peek();
                if (p == null) { java.util.concurrent.locks.LockSupport.parkNanos(500_000L); continue; }
                long now = System.nanoTime();
                if (now >= p.targetNs) {
                    cpuQueue.poll();
                    pendingIdles.remove(p.window.id, p);
                    sendIdleNotify(p.window, p.pixmap, p.serial, p.idleFence);
                } else {
                    long diff = p.targetNs - now;
                    if (diff > 2_000_000L) java.util.concurrent.locks.LockSupport.parkNanos(1_000_000L);
                    else Thread.yield();
                }
            }
        }, "PresentPacer-CPU");
        cpuPacerThread.setDaemon(true);
        cpuPacerThread.setPriority(Thread.MAX_PRIORITY);
        cpuPacerThread.start();
    }

    private void sendIdleNotify(Window window, Pixmap pixmap, int serial, int idleFence) {
        if (idleFence != 0 && syncExtension != null) syncExtension.setTriggered(idleFence);

        synchronized (events) {
            for (int i = 0; i < events.size(); i++) {
                Event event = events.valueAt(i);
                if (event.window == window && event.mask.isSet(PresentIdleNotify.getEventMask())) {
                    event.client.sendEvent(new PresentIdleNotify(event.id, window, pixmap, serial, idleFence));
                }
            }
        }
    }

    private void sendCompleteNotify(Window window, int serial, Kind kind, Mode mode, long ust, long msc) {
        synchronized (events) {
            for (int i = 0; i < events.size(); i++) {
                Event event = events.valueAt(i);
                if (event.window == window && event.mask.isSet(PresentCompleteNotify.getEventMask())) {
                    event.client.sendEvent(new PresentCompleteNotify(event.id, window, serial, kind, mode, ust, msc));
                }
            }
        }
    }

    private static void queryVersion(XClient client, XInputStream inputStream, XOutputStream outputStream) throws IOException, XRequestError {
        inputStream.skip(8);

        try (XStreamLock lock = outputStream.lock()) {
            outputStream.writeByte(RESPONSE_CODE_SUCCESS);
            outputStream.writeByte((byte)0);
            outputStream.writeShort(client.getSequenceNumber());
            outputStream.writeInt(0);
            outputStream.writeInt(1);
            outputStream.writeInt(0);
            outputStream.writePad(16);
        }
    }

    private void presentPixmap(XClient client, XInputStream inputStream, XOutputStream outputStream) throws IOException, XRequestError {
        int windowId = inputStream.readInt();
        int pixmapId = inputStream.readInt();
        int serial = inputStream.readInt();
        inputStream.skip(8);
        short xOff = inputStream.readShort();
        short yOff = inputStream.readShort();
        inputStream.skip(8);
        int idleFence = inputStream.readInt();
        inputStream.skip(client.getRemainingRequestLength());

        final Window window = client.xServer.windowManager.getWindow(windowId);
        if (window == null) throw new BadWindow(windowId);

        final Pixmap pixmap = client.xServer.pixmapManager.getPixmap(pixmapId);
        if (pixmap == null) throw new BadPixmap(pixmapId);

        Drawable content = window.getContent();
        int contentDepth = content.visual.depth;
        int pixmapDepth = pixmap.drawable.visual.depth;
        boolean depthCompat = (contentDepth == pixmapDepth) ||
            ((contentDepth == 24 || contentDepth == 32) && (pixmapDepth == 24 || pixmapDepth == 32));
        if (!depthCompat) throw new BadMatch();

        final com.winlator.cmod.renderer.vulkan.VulkanRenderer renderer =
            client.xServer.getRenderer() instanceof com.winlator.cmod.renderer.vulkan.VulkanRenderer ?
                (com.winlator.cmod.renderer.vulkan.VulkanRenderer) client.xServer.getRenderer() : null;

        int targetFps = this.frameRateLimit;
        if (targetFps <= 0 && renderer != null) targetFps = renderer.getFpsLimit();

        long ust = System.nanoTime() / 1000;
        long msc = ust / FAKE_INTERVAL;

        synchronized (content.renderLock) {
            if (renderer != null && window.attributes.isMapped()
                    && pixmap.drawable.getTexture() instanceof GPUImage
                    && ((GPUImage) pixmap.drawable.getTexture()).getHardwareBufferPtr() != 0) {
                sendCompleteNotify(window, serial, Kind.PIXMAP, Mode.COPY, ust, msc);
                renderer.onUpdateWindowContentDirect(window, pixmap.drawable, xOff, yOff);
                emitIdleNotify(window, pixmap, serial, idleFence, targetFps, renderer);
            } else {
                content.copyArea((short)0, (short)0, xOff, yOff, pixmap.drawable.width, pixmap.drawable.height, pixmap.drawable);
                sendCompleteNotify(window, serial, Kind.PIXMAP, Mode.COPY, ust, msc);
                emitIdleNotify(window, pixmap, serial, idleFence, targetFps, renderer);
            }
        }
    }

    private void selectInput(XClient client, XInputStream inputStream, XOutputStream outputStream) throws IOException, XRequestError {
        int eventId = inputStream.readInt();
        int windowId = inputStream.readInt();
        Bitmask mask = new Bitmask(inputStream.readInt());

        Window window = client.xServer.windowManager.getWindow(windowId);
        if (window == null) throw new BadWindow(windowId);

        if (GPUImage.isSupported() && !mask.isEmpty()) {
            com.winlator.cmod.renderer.HostRenderer hr = client.xServer.getRenderer();
            if (hr != null) {
                Drawable content = window.getContent();
                final Texture oldTexture = content.getTexture();
                if (oldTexture != null && hr instanceof com.winlator.cmod.renderer.GLRenderer) {
                    ((com.winlator.cmod.renderer.GLRenderer)hr).getXServerView().queueEvent(oldTexture::destroy);
                }
                content.setTexture(new GPUImage(content.width, content.height));
            }
        }

        synchronized (events) {
            Event event = events.get(eventId);
            if (event != null) {
                if (event.window != window || event.client != client) throw new BadMatch();

                if (!mask.isEmpty()) {
                    event.mask = mask;
                }
                else events.remove(eventId);
            }
            else {
                event = new Event();
                event.id = eventId;
                event.window = window;
                event.client = client;
                event.mask = mask;
                events.put(eventId, event);
            }
        }
    }

    private void enforceAbsoluteFramerate(com.winlator.cmod.renderer.GLRenderer renderer) {
        if (renderer == null) return;

        int targetFps = renderer.getFpsLimit();
        if (targetFps <= 0) {
            nextFrameTime = 0;
            return;
        }

        long targetFrameTime = 1000000000L / targetFps;
        long now = System.nanoTime();

        if (nextFrameTime == 0 || now > nextFrameTime) nextFrameTime = now;

        long sleepTime = nextFrameTime - now;
        if (sleepTime > 0) {
            long sleepMs = (sleepTime - 1500000L) / 1000000L;
            if (sleepMs > 0) {
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException e) {}
            }
            while (System.nanoTime() < nextFrameTime);
        }
        nextFrameTime += targetFrameTime;
    }

    @Override
    public void handleRequest(XClient client, XInputStream inputStream, XOutputStream outputStream) throws IOException, XRequestError {
        int opcode = client.getRequestData();
        if (syncExtension == null) syncExtension = client.xServer.getExtension(SyncExtension.MAJOR_OPCODE);

        switch (opcode) {
            case ClientOpcodes.QUERY_VERSION :
                queryVersion(client, inputStream, outputStream);
                break;
            case ClientOpcodes.PRESENT_PIXMAP:
                try (XLock lock = client.xServer.lock(XServer.Lockable.WINDOW_MANAGER, XServer.Lockable.PIXMAP_MANAGER)) {
                    presentPixmap(client, inputStream, outputStream);
                }
                if (client.xServer.getRenderer() instanceof com.winlator.cmod.renderer.GLRenderer) enforceAbsoluteFramerate((com.winlator.cmod.renderer.GLRenderer) client.xServer.getRenderer());
                break;
            case ClientOpcodes.SELECT_INPUT:
                try (XLock lock = client.xServer.lock(XServer.Lockable.WINDOW_MANAGER)) {
                    selectInput(client, inputStream, outputStream);
                }
                break;
            default:
                throw new BadImplementation();
        }
    }
}
