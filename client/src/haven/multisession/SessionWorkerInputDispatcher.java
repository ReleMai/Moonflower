package haven.multisession;

import haven.HeadlessClient;
import haven.UI;
import haven.Widget;

import java.awt.Canvas;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/** Converts parent viewport events into the normal Haven UI event pipeline. */
final class SessionWorkerInputDispatcher implements HeadlessClient.InputDispatcher {
    private static final int QUEUE_SIZE = 256;
    private final DataInputStream control;
    private final Runnable shutdown;
    private final BlockingQueue<SessionWorkerProtocol.Input> events =
            new ArrayBlockingQueue<>(QUEUE_SIZE);
    private final Canvas source = new Canvas();
    private volatile boolean running = true;
    private final Thread reader;

    SessionWorkerInputDispatcher(DataInputStream control, Runnable shutdown) {
        this.control = control;
        this.shutdown = shutdown;
        reader = new Thread(this::readLoop, "MoonFlower worker input");
        reader.setDaemon(true);
        reader.start();
    }

    private void readLoop() {
        try {
            while(running) {
                SessionWorkerProtocol.Message message = SessionWorkerProtocol.read(control);
                if(message.type == SessionWorkerProtocol.INPUT) {
                    SessionWorkerProtocol.Input input =
                            SessionWorkerProtocol.decodeInput(message.payload);
                    /* Keep the newest pointer position when the visible client
                     * is resizing or the OS briefly floods move events. */
                    if(!events.offer(input)) {
                        events.poll();
                        events.offer(input);
                    }
                } else if(message.type == SessionWorkerProtocol.SHUTDOWN) {
                    break;
                }
            }
        } catch(IOException ignored) {
            /* Parent teardown and a closed pipe are normal worker exits. */
        } finally {
            running = false;
            shutdown.run();
        }
    }

    @Override
    public void dispatch(UI ui) {
        if(!running)
            return;
        for(int i = 0; i < 64; i++) {
            SessionWorkerProtocol.Input input = events.poll();
            if(input == null)
                return;
            dispatch(ui, input);
        }
    }

    private void dispatch(UI ui, SessionWorkerProtocol.Input input) {
        long now = System.currentTimeMillis();
        int mods = awtModifiers(input.modifiers);
        switch(input.type) {
        case MOUSE_DOWN:
            ui.mousedown(new java.awt.event.MouseEvent(source,
                    java.awt.event.MouseEvent.MOUSE_PRESSED, now, mods,
                    input.x, input.y, 1, false, input.button),
                    new haven.Coord(input.x, input.y), input.button);
            break;
        case MOUSE_UP:
            ui.mouseup(new java.awt.event.MouseEvent(source,
                    java.awt.event.MouseEvent.MOUSE_RELEASED, now, mods,
                    input.x, input.y, 1, false, input.button),
                    new haven.Coord(input.x, input.y), input.button);
            break;
        case MOUSE_MOVE:
            ui.mousemove(new java.awt.event.MouseEvent(source,
                    java.awt.event.MouseEvent.MOUSE_MOVED, now, mods,
                    input.x, input.y, 0, false),
                    new haven.Coord(input.x, input.y));
            break;
        case MOUSE_WHEEL:
            ui.mousewheel(new java.awt.event.MouseWheelEvent(source,
                    java.awt.event.MouseEvent.MOUSE_WHEEL, now, mods,
                    input.x, input.y, 0, false,
                    java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, input.wheel),
                    new haven.Coord(input.x, input.y), input.wheel, input.wheel);
            break;
        case KEY_DOWN:
            ui.keydown(new KeyEvent(source, KeyEvent.KEY_PRESSED, now, mods,
                    input.keyCode, keyChar(input.keyChar)));
            break;
        case KEY_UP:
            ui.keyup(new KeyEvent(source, KeyEvent.KEY_RELEASED, now, mods,
                    input.keyCode, keyChar(input.keyChar)));
            break;
        default:
            break;
        }
    }

    private static char keyChar(char value) {
        return(value == 0 ? KeyEvent.CHAR_UNDEFINED : value);
    }

    private static int awtModifiers(int mods) {
        int ret = 0;
        if((mods & UI.MOD_SHIFT) != 0)
            ret |= InputEvent.SHIFT_DOWN_MASK;
        if((mods & UI.MOD_CTRL) != 0)
            ret |= InputEvent.CTRL_DOWN_MASK;
        if((mods & UI.MOD_META) != 0)
            ret |= InputEvent.ALT_DOWN_MASK;
        return(ret);
    }

    @Override
    public void close() {
        running = false;
        reader.interrupt();
        events.clear();
    }
}
