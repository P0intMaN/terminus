package io.terminus.core;

import io.terminus.core.render.RenderPipeline;
import io.terminus.core.terminal.EventDispatcher;
import io.terminus.core.terminal.EventLoop;
import io.terminus.core.terminal.Terminal;

/**
 * The entry point for building Terminus applications.
 *
 * Usage:
 *   TerminusApp.run(myRootComponent);
 *
 * That's it. Terminus handles raw mode, the event loop,
 * rendering, and terminal cleanup.
 *
 * PATTERN: Facade
 * Hides EventLoop, RenderPipeline, Terminal, EventDispatcher.
 * App developers shouldn't need to know these exist.
 */
public final class TerminusApp {

    private TerminusApp() {}

    public static void run(Component root) {
        // Fail fast with a clear message rather than a cryptic JNA error
        if (!Terminal.isRealTerminal()) {
            System.err.println("""
                [Terminus] Not running in an interactive terminal.
                
                If you used: ./gradlew :terminus-demo:run
                Use instead: java --enable-preview -jar terminus-demo/build/libs/terminus-demo.jar
                
                Terminus requires direct TTY access. Gradle wraps stdin in a pipe.
                """);
            System.exit(1);
        }

        int[] size = Terminal.getSize();
        RenderPipeline  pipeline   = new RenderPipeline(size[0], size[1]);
        EventDispatcher dispatcher = new EventDispatcher();
        EventLoop       loop       = new EventLoop(pipeline, dispatcher);

        try {
            loop.start(root);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}