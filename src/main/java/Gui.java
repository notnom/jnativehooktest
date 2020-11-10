import com.github.joonasvali.naturalmouse.api.MouseMotionFactory;
import com.github.joonasvali.naturalmouse.support.DefaultOvershootManager;
import com.github.joonasvali.naturalmouse.util.FactoryTemplates;
import lombok.Getter;
import org.jnativehook.GlobalScreen;
import org.jnativehook.NativeHookException;
import org.jnativehook.dispatcher.SwingDispatchService;
import org.jnativehook.keyboard.NativeKeyEvent;
import org.jnativehook.keyboard.NativeKeyListener;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class Gui implements NativeKeyListener {
    private Point clickLocation;
    private int triggerKeyCode;
    private Robot robot;
    private JLabel label = new JLabel("                                    ");
    JComboBox<RSHotkey> hotkeyJComboBox = new JComboBox<>(RSHotkey.values());
    private ExecutorService pool;
    private MouseMotionFactory mmf;
    public static void main(String[] args) {
        new Gui();
    }
    public Gui() {
        try {
            GlobalScreen.registerNativeHook();
            robot = new Robot();
            this.robot.setAutoWaitForIdle(true);
        } catch (NativeHookException | AWTException e) {
            e.printStackTrace();
        }
        mmf = FactoryTemplates.createFastGamerMotionFactory();
        ((DefaultOvershootManager)mmf.getOvershootManager()).setOvershoots(0);
        GlobalScreen.setEventDispatcher(new SwingDispatchService());

        this.pool = Executors.newFixedThreadPool(1);
        GlobalScreen.addNativeKeyListener(this);

        JFrame frame = new JFrame("Library tester");

        JLabel rsHotkeyLabel = new JLabel("Set OSRS tab to press");
        JButton locationButton = new JButton("Set location     ");
        JButton triggerButton = new JButton("Set trigger key");

        locationButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                if (clickLocation == null) {
                    locationButton.setText("Press any key");
                } else {
                    locationButton.setFocusable(true);
                    locationButton.setEnabled(true);
                    clickLocation = null;
                }
            }
        });
        triggerButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                if (triggerKeyCode == 0) {
                    triggerButton.setText("Press any key");
                } else {
                    triggerButton.setFocusable(true);
                    triggerButton.setEnabled(true);
                    triggerKeyCode = 0;
                }
            }
        });

        locationButton.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {

            }

            @Override
            public void keyPressed(KeyEvent e) {
                clickLocation = MouseInfo.getPointerInfo().getLocation();
                locationButton.setText("X: " + clickLocation.x + " Y: " + clickLocation.y);
                locationButton.setFocusable(false);
                locationButton.setEnabled(false);
            }

            @Override
            public void keyReleased(KeyEvent e) {

            }
        });
        triggerButton.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {

            }

            @Override
            public void keyPressed(KeyEvent e) {
                RSHotkey hotkey = hotkeyJComboBox.getItemAt(hotkeyJComboBox.getSelectedIndex());
                if (e.getKeyCode() == hotkey.getKeyCode()) {
                    label.setText("I see what you're trying to do");
                    return;
                }
                triggerKeyCode = e.getKeyCode();
                triggerButton.setText(KeyEvent.getKeyText(e.getKeyCode()));
                triggerButton.setFocusable(false);
                triggerButton.setEnabled(false);
            }

            @Override
            public void keyReleased(KeyEvent e) {

            }
        });

        JPanel main = boxPanel();

        main.add(createJPanel(p -> {
            p.add(rsHotkeyLabel);
            p.add(hotkeyJComboBox);
            p.add(locationButton);
        }));
        main.add(triggerButton);
        main.add(label);
        align(main);

        WindowListener exitListener = new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                System.exit(0);
            }
        };
        frame.addWindowListener(exitListener);
        frame.setContentPane(main);
        frame.setLocationRelativeTo(null);
        frame.pack();
        frame.setVisible(true);
    }

    @Override
    public void nativeKeyTyped(NativeKeyEvent nativeKeyEvent) {

    }

    private long lastTrigger = System.currentTimeMillis();
    @Override
    public void nativeKeyPressed(NativeKeyEvent nativeKeyEvent) {
        label.setText("Pressed " + KeyEvent.getKeyText(HotkeyConverter.keyNativeToAwt(nativeKeyEvent)));
        if (Math.abs(System.currentTimeMillis() - lastTrigger) < 300) {
            return;
        }
        if (HotkeyConverter.keyNativeToAwt(nativeKeyEvent) == triggerKeyCode) {
            lastTrigger = System.currentTimeMillis();
            pool.execute(() -> {
                RSHotkey hotkey = hotkeyJComboBox.getItemAt(hotkeyJComboBox.getSelectedIndex());
                if (hotkey != RSHotkey.NONE) {
                    robot.keyPress(hotkey.keyCode);
                    sleep(10);
                    robot.keyRelease(hotkey.keyCode);
                }
                if (clickLocation != null)
                    try {
                        mmf.build(clickLocation.x, clickLocation.y).move();
                        sleep(5);
                        robot.mouseMove(clickLocation.x, clickLocation.y);
                        sleep(1);
                        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                        sleep(1);
                        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
            });
        }
    }


    public void sleep(int sleep) {
        try {
            Thread.sleep(Math.max(1,sleep));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    @Override
    public void nativeKeyReleased(NativeKeyEvent nativeKeyEvent) {

    }

    /**
     * Hollow factory/decorator for JLabels
     * @param actions
     * @return
     */
    @SafeVarargs
    public static JPanel createJPanel(Consumer<JPanel>... actions) {
        JPanel panel = new JPanel();
        for(Consumer<JPanel> action: actions) {
            action.accept(panel);
        }
        return panel;
    }


    public static void align(JPanel panel) {
        for (Component component : panel.getComponents()) {
            if (component instanceof JPanel) {
                align((JPanel)component);
            } else if (component instanceof JComponent) {
                ((JComponent)component).setAlignmentX(0.5f);
            }
        }
    }

    public static JPanel boxPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        return panel;
    }

    public enum RSHotkey {
        NONE(KeyEvent.VK_UNDEFINED),
        ESC(KeyEvent.VK_ESCAPE),
        F1( KeyEvent.VK_F1),
        F2( KeyEvent.VK_F2),
        F3( KeyEvent.VK_F3),
        F4( KeyEvent.VK_F4),
        F5( KeyEvent.VK_F5),
        F6( KeyEvent.VK_F6),
        F7( KeyEvent.VK_F7),
        F8( KeyEvent.VK_F8),
        F9( KeyEvent.VK_F9),
        F10(KeyEvent.VK_F10),
        F11(KeyEvent.VK_F11),
        F12(KeyEvent.VK_F12),
        ;
        @Getter
        private int keyCode;

        RSHotkey(int keyCode) {
            this.keyCode = keyCode;
        }


        @Override
        public String toString() {
            return super.toString().replace("_","");
        }
    }
}
