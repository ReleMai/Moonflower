package haven.botcontrol;

import org.json.JSONObject;

import java.awt.AWTException;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Insets;
import java.awt.Robot;
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
        int x = payload.optInt("x", frame.getWidth() / 2);
        int y = payload.optInt("y", frame.getHeight() / 2);
        Insets insets = frame.getInsets();
        int screenX = frame.getX() + insets.left + x;
        int screenY = frame.getY() + insets.top + y;
        switch (type) {
            case "MOUSE_MOVE" -> robot.mouseMove(screenX, screenY);
            case "LEFT_CLICK" -> click(robot, screenX, screenY, InputEvent.BUTTON1_DOWN_MASK);
            case "RIGHT_CLICK" -> click(robot, screenX, screenY, InputEvent.BUTTON3_DOWN_MASK);
            case "MIDDLE_CLICK" -> click(robot, screenX, screenY, InputEvent.BUTTON2_DOWN_MASK);
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

    private void click(Robot robot, int screenX, int screenY, int buttonMask) {
        robot.mouseMove(screenX, screenY);
        robot.mousePress(buttonMask);
        robot.mouseRelease(buttonMask);
    }

    private Frame findMainFrame() {
        for (Frame frame : Frame.getFrames()) {
            if (frame.isVisible() && "Haven & Hearth".equals(frame.getTitle())) {
                return frame;
            }
        }
        Frame largestVisibleFrame = null;
        for (Frame frame : Frame.getFrames()) {
            if (frame.isVisible() && ((largestVisibleFrame == null)
                    || ((long) frame.getWidth() * frame.getHeight()
                    > (long) largestVisibleFrame.getWidth() * largestVisibleFrame.getHeight()))) {
                largestVisibleFrame = frame;
            }
        }
        return largestVisibleFrame;
    }
}
