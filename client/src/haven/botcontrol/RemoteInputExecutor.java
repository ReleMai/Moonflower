package haven.botcontrol;

import haven.MainFrame;
import org.json.JSONObject;

import java.awt.AWTException;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Robot;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

public class RemoteInputExecutor {
    public void execute(JSONObject payload) throws AWTException {
        Frame frame = findMainFrame();
        if (frame == null) {
            throw new IllegalStateException("Main client window not found.");
        }
        Robot robot = new Robot();
        String type = payload.getString("inputType");
        int x = payload.optInt("x", frame.getX() + frame.getWidth() / 2);
        int y = payload.optInt("y", frame.getY() + frame.getHeight() / 2);
        switch (type) {
            case "MOUSE_MOVE" -> robot.mouseMove(frame.getX() + x, frame.getY() + y);
            case "LEFT_CLICK" -> click(robot, frame, x, y, InputEvent.BUTTON1_DOWN_MASK);
            case "RIGHT_CLICK" -> click(robot, frame, x, y, InputEvent.BUTTON3_DOWN_MASK);
            case "MIDDLE_CLICK" -> click(robot, frame, x, y, InputEvent.BUTTON2_DOWN_MASK);
            case "KEY_PRESS" -> robot.keyPress(payload.getInt("keyCode"));
            case "KEY_RELEASE" -> robot.keyRelease(payload.getInt("keyCode"));
            case "KEY_TAP" -> {
                int keyCode = payload.optInt("keyCode", KeyEvent.VK_SPACE);
                robot.keyPress(keyCode);
                robot.keyRelease(keyCode);
            }
            default -> throw new IllegalArgumentException("Unsupported remote input type: " + type);
        }
    }

    public void focusClientWindow() {
        Frame frame = findMainFrame();
        if (frame == null) {
            throw new IllegalStateException("Main client window not found.");
        }
        EventQueue.invokeLater(() -> {
            if ((frame.getExtendedState() & Frame.ICONIFIED) != 0) {
                frame.setExtendedState(Frame.NORMAL);
            }
            frame.setVisible(true);
            try {
                Robot robot = new Robot();
                robot.keyPress(KeyEvent.VK_ALT);
                robot.keyRelease(KeyEvent.VK_ALT);
            } catch (AWTException ignored) {
            }
            frame.toFront();
            frame.requestFocus();
            frame.requestFocusInWindow();
            frame.setAlwaysOnTop(true);
            frame.setAlwaysOnTop(false);
        });
    }

    private void click(Robot robot, Frame frame, int x, int y, int buttonMask) {
        robot.mouseMove(frame.getX() + x, frame.getY() + y);
        robot.mousePress(buttonMask);
        robot.mouseRelease(buttonMask);
    }

    private Frame findMainFrame() {
        for (Frame frame : Frame.getFrames()) {
            if (frame instanceof MainFrame && frame.isVisible()) {
                return frame;
            }
        }
        for (Window window : Window.getWindows()) {
            if (window instanceof Frame) {
                Frame frame = (Frame) window;
                if (frame.isVisible()) {
                    return frame;
                }
            }
        }
        return null;
    }
}
