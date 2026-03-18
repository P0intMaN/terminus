package io.terminus.core.terminal;

import io.terminus.core.Component;
import io.terminus.core.event.*;
import io.terminus.core.render.RenderPipeline;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The heartbeat of a Terminus application.
 *
 * The EventLoop does three things, forever, until stopped:
 *   1. Read raw bytes from stdin and convert them to Events
 *   2. Dispatch Events to the component tree
 *   3. Render a new frame if any component is dirty
 *
 * CONCURRENCY MODEL: Single UI thread + virtual thread for stdin.
 *
 * All component state mutations, event dispatching, and rendering
 * happen on ONE thread — the EventLoop thread. This eliminates the
 * need for locks on any component state. Components are NOT
 * thread-safe and should never be touched from another thread directly.
 *
 * Background work (data loading, network calls) runs on separate
 * threads and communicates back via StateChangeEvent posted to the
 * queue. The EventLoop drains the queue on each tick, processes the
 * events on the UI thread, and re-renders.
 *
 * This is the same model used by:
 *   - JavaScript's event loop (browser + Node.js)
 *   - Android's Looper/Handler system
 *   - Flutter's dart:ui isolate
 *
 * WHY VIRTUAL THREADS FOR STDIN?
 * Stdin reads are blocking — they park the thread until bytes arrive.
 * With a platform thread, this wastes an OS thread. With a virtual
 * thread (Java 21 Project Loom), the cost is ~few KB of memory
 * instead of ~1MB stack. We can afford to block freely.
 */
public class EventLoop {

    /**
     * The event queue — the ONLY communication channel between
     * the stdin reader thread and the UI thread.
     *
     * WHY LinkedBlockingQueue?
     * Thread-safe, unbounded, FIFO. The stdin reader produces events,
     * the UI thread consumes them. LinkedBlockingQueue's poll(timeout)
     * method lets the UI thread wait efficiently without spinning.
     */
    private final BlockingQueue<Event> eventQueue =
        new LinkedBlockingQueue<>();

    private final RenderPipeline renderPipeline;
    private final EventDispatcher dispatcher;
    private final KeyParser keyParser = new KeyParser();

    private final AtomicBoolean running = new AtomicBoolean(false);

    /** Target frame duration in milliseconds — 60fps = ~16ms per frame. */
    private static final long FRAME_MS = 16;

    public EventLoop(RenderPipeline renderPipeline, EventDispatcher dispatcher) {
        this.renderPipeline = renderPipeline;
        this.dispatcher     = dispatcher;
    }

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Start the event loop. Blocks until stop() is called.
     *
     * Call this from your application's main thread.
     * The stdin reader runs on a virtual thread — this method
     * owns the current thread as the UI thread.
     *
     * @param root the root component of your UI tree
     */
    public void start(Component root) throws InterruptedException {
        running.set(true);

        Terminal.enterRawMode();
        Terminal.enableMouseTracking();
        renderPipeline.clearScreen(); // Clear terminal before starting

        // Spawn a virtual thread to read stdin continuously.
        // This thread's only job: read bytes, parse events, enqueue them.
        // WHY A SEPARATE THREAD?
        // Reading stdin is blocking. If we read on the UI thread, we
        // can't render while waiting for input. The queue decouples
        // the two concerns cleanly.
        Thread stdinReader = Thread.ofVirtual()
            .name("terminus-stdin-reader")
            .start(() -> readStdinLoop());

        try {
            runUiLoop(root);
        } finally {
            running.set(false);
            stdinReader.interrupt();
            Terminal.disableMouseTracking();
            Terminal.exitRawMode();
            renderPipeline.shutdown();
        }
    }

    /**
     * Stop the event loop gracefully.
     * Safe to call from any thread.
     */
    public void stop() {
        running.set(false);
    }

    /**
     * Post an event from a background thread.
     *
     * This is the ONLY safe way for non-UI threads to interact
     * with the component tree. Post a StateChangeEvent, then update
     * your component's state in onEvent() on the UI thread.
     *
     * Example:
     *   eventLoop.post(new StateChangeEvent(now, "data.loaded", myData));
     */
    public void post(Event event) {
        eventQueue.offer(event);
    }

    // ── Private loops ─────────────────────────────────────────────────────

    /**
     * The UI thread loop.
     *
     * On each tick:
     *   1. Drain all pending events from the queue
     *   2. Dispatch each event to the component tree
     *   3. Render a new frame if anything is dirty
     *   4. Sleep for the remainder of the 16ms frame budget
     */
    private void runUiLoop(Component root) throws InterruptedException {
        // Force first render
        renderPipeline.renderFrame(root);

        while (running.get()) {
            long frameStart = System.currentTimeMillis();

            // Drain all events that arrived since the last frame
            // poll() with timeout: wait up to FRAME_MS for first event,
            // then drain the rest without waiting
            Event first = eventQueue.poll(FRAME_MS, TimeUnit.MILLISECONDS);

            if (first != null) {
                handleEvent(first, root);

                // Drain any additional events that arrived while we processed
                Event next;
                while ((next = eventQueue.poll()) != null) {
                    handleEvent(next, root);
                }
            }

            // Render if the tree is dirty after event processing
            if (root.isDirty()) {
                renderPipeline.renderFrame(root);
            }

            // Frame rate cap: sleep for any remaining time in the 16ms budget
            long elapsed = System.currentTimeMillis() - frameStart;
            long sleep   = FRAME_MS - elapsed;
            if (sleep > 0) {
                Thread.sleep(sleep);
            }
        }
    }

    /**
     * Route an event to the appropriate handler.
     */
    private void handleEvent(Event event, Component root) {
        switch (event) {
            case ResizeEvent r -> {
                renderPipeline.resize(r.cols(), r.rows());
                root.markDirty();
            }
            case KeyEvent k -> {
                // Check if the app wants to quit on Ctrl+C / Ctrl+Q
                if (isQuitKey(k)) {
                    stop();
                    return;
                }
                dispatcher.dispatch(k, root);
            }
            case MouseEvent m    -> dispatcher.dispatch(m, root);
            case StateChangeEvent s -> dispatcher.dispatch(s, root);
            default              -> {} // future event types
        }
    }

    /**
     * Returns true if the key event should trigger application quit.
     * Ctrl+C and Ctrl+Q are the standard quit keys.
     */
    private boolean isQuitKey(KeyEvent k) {
        return k.ctrl() && (k.key().equals("C") || k.key().equals("Q"));
    }

    /**
     * The stdin reader loop — runs on a virtual thread.
     *
     * Reads bytes from System.in and passes them through KeyParser.
     * Posts resulting KeyEvents to the queue.
     *
     * WHY READ RAW System.in INSTEAD OF A BufferedReader?
     * BufferedReader reads in chunks and adds newline-based line
     * ending logic. In raw mode we need every byte immediately.
     * System.in.read() returns exactly what the terminal sends.
     */
    private void readStdinLoop() {
        InputStream stdin = System.in;
        byte[] buf = new byte[64]; // read up to 64 bytes at a time

        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                int n = stdin.read(buf);
                if (n < 0) break; // EOF — stdin closed

                for (int i = 0; i < n; i++) {
                    // Check for mouse sequence prefix before key parsing
                    // Mouse sequences start with ESC[< — we detect this
                    // by looking ahead when we see ESC[
                    KeyEvent evt = keyParser.process(buf[i]);
                    if (evt != null) {
                        eventQueue.offer(evt);
                    }
                }

            } catch (IOException e) {
                if (running.get()) {
                    // Unexpected IO error — log and stop
                    System.err.println("[Terminus] stdin read error: " + e.getMessage());
                    stop();
                }
                break;
            }
        }
    }
}