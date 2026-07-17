package yiqiu.util;

import arc.*;
import arc.input.*;
import arc.math.geom.*;
import arc.scene.ui.*;
import arc.util.*;
import mindustry.game.EventType.*;

import java.io.*;
import java.lang.reflect.*;

public class Fcitx5Support{
    private static boolean enabled;
    /** true 表示当前运行时可以使用 SDL 反射方法（仅 SDL IME 模式需要） */
    private static boolean sdlAvailable;

    private static Method sdlStartTextInput;
    private static Method sdlStopTextInput;
    private static Method sdlSetTextInputRect;
    private static Method sdlSetHint;

    private static boolean textInputActive;
    private static boolean updateHookAdded;
    private static boolean dialogShowing;

    /** 对话框模式：使用 AWT/Swing 原生对话框（绕开有问题的 SDL IME 后端） */
    private static boolean dialogMode = true;

    /** 是否运行在桌面环境 */
    private static boolean isDesktop;

    public static void init(){
        Events.on(ClientLoadEvent.class, e -> {
            isDesktop = Core.app != null && Core.app.getType() == arc.Application.ApplicationType.desktop;
            // 尝试解析 SDL 方法（失败不影响对话框模式）
            try{
                Class<?> clazz = Class.forName("arc.backend.sdl.SdlApplication");
                sdlStartTextInput = clazz.getDeclaredMethod("SDL_StartTextInput");
                sdlStartTextInput.setAccessible(true);
                sdlStopTextInput = clazz.getDeclaredMethod("SDL_StopTextInput");
                sdlStopTextInput.setAccessible(true);
                sdlSetTextInputRect = clazz.getDeclaredMethod("SDL_SetTextInputRect", int.class, int.class, int.class, int.class);
                sdlSetTextInputRect.setAccessible(true);
                sdlSetHint = clazz.getDeclaredMethod("SDL_SetHint", String.class, String.class);
                sdlSetHint.setAccessible(true);
                sdlAvailable = true;
                Log.info("[BM] fcitx5 SDL native methods resolved");
            }catch(Exception ex){
                sdlAvailable = false;
                Log.info("[BM] fcitx5 SDL native methods unavailable (Java 25+ module restrictions) — dialog mode still works");
            }
        });
    }

    public static void setEnabled(boolean value){
        if(enabled == value) return;
        enabled = value;

        if(!isDesktop) return;

        if(enabled){
            if(dialogMode){
                addInputProcessor();
                Log.info("[BM] fcitx5 dialog mode ENABLED " + diagString());
            }else if(sdlAvailable){
                try{
                    sdlSetHint.invoke(null, "SDL_IME_SHOW_UI", "1");
                }catch(Exception ignored){}
                ensureTextInputActive();
                if(!updateHookAdded){
                    addUpdateHook();
                    updateHookAdded = true;
                }
                Log.info("[BM] fcitx5 SDL IME mode ENABLED " + diagString());
            }else{
                Log.warn("[BM] fcitx5 SDL IME mode unavailable — switch to dialog mode");
            }
        }else{
            deactivateTextInput();
            closeDialog();
            Log.info("[BM] fcitx5 DISABLED");
        }
    }

    public static void setDialogMode(boolean value){
        if(dialogMode == value) return;
        dialogMode = value;
        if(enabled){
            setEnabled(false);
            setEnabled(true);
        }
    }

    public static boolean isEnabled(){
        return enabled;
    }

    public static boolean isDialogMode(){
        return dialogMode;
    }

    public static boolean isAvailable(){
        return isDesktop;
    }

    public static String diagString(){
        if(!isDesktop) return "(not desktop)";
        StringBuilder sb = new StringBuilder();
        sb.append("[mode=").append(dialogMode ? "dialog" : "sdl-ime");
        sb.append(", SDL_IM_MODULE=").append(System.getenv("SDL_IM_MODULE"));
        sb.append(", XMODIFIERS=").append(System.getenv("XMODIFIERS"));
        sb.append(", GTK_IM_MODULE=").append(System.getenv("GTK_IM_MODULE"));
        sb.append(", QT_IM_MODULE=").append(System.getenv("QT_IM_MODULE"));
        sb.append(", fcitx5-remote=").append(checkFcitx5Remote());
        sb.append("]");
        return sb.toString();
    }

    // ============ 对话框回退模式 ============

    private static final Fcitx5InputHandler inputHandler = new Fcitx5InputHandler();

    private static void addInputProcessor(){
        Core.input.addProcessor(inputHandler);
    }

    private static class Fcitx5InputHandler implements InputProcessor{
        @Override
        public boolean keyDown(KeyCode keycode){ return false; }

        @Override
        public boolean keyUp(KeyCode keycode){
            if(!enabled) return false;

            if(keycode == KeyCode.f2){
                TextField tf = focusedField();
                if(tf != null){
                    showInputDialog(tf);
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean keyTyped(char c){ return false; }

        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, KeyCode button){ return false; }

        @Override
        public boolean touchUp(int screenX, int screenY, int pointer, KeyCode button){ return false; }

        @Override
        public boolean touchDragged(int screenX, int screenY, int pointer){ return false; }

        @Override
        public boolean mouseMoved(int screenX, int screenY){ return false; }

        @Override
        public boolean scrolled(float amountX, float amountY){ return false; }
    }

    private static TextField focusedField(){
        if(Core.scene == null) return null;
        var focus = Core.scene.getKeyboardFocus();
        return focus instanceof TextField ? (TextField)focus : null;
    }

    /** 通过反射使用 AWT/Swing 打开原生输入对话框，避免编译期依赖 java.desktop 模块 */
    private static void showInputDialog(TextField gameField){
        if(dialogShowing) return;
        dialogShowing = true;

        String currentText = gameField.getText();
        String placeholder = gameField.getMessageText();

        // 在独立线程中反射加载 AWT/Swing，不阻塞主线程也不影响 ModClassLoader
        Threads.daemon(() -> {
            try{
                ClassLoader cl = ClassLoader.getSystemClassLoader();

                Class<?> jDialogCls = cl.loadClass("javax.swing.JDialog");
                Class<?> jPanelCls = cl.loadClass("javax.swing.JPanel");
                Class<?> jTextFieldCls = cl.loadClass("javax.swing.JTextField");
                Class<?> jButtonCls = cl.loadClass("javax.swing.JButton");
                Class<?> borderFactoryCls = cl.loadClass("javax.swing.BorderFactory");
                Class<?> borderLayoutCls = cl.loadClass("java.awt.BorderLayout");
                Class<?> flowLayoutCls = cl.loadClass("java.awt.FlowLayout");
                Class<?> keyStrokeCls = cl.loadClass("javax.swing.KeyStroke");
                Class<?> jComponentCls = cl.loadClass("javax.swing.JComponent");
                Class<?> swingUtilitiesCls = cl.loadClass("javax.swing.SwingUtilities");
                Class<?> graphicsEnvCls = cl.loadClass("java.awt.GraphicsEnvironment");
                Class<?> graphicsDeviceCls = cl.loadClass("java.awt.GraphicsDevice");
                Class<?> displayModeCls = cl.loadClass("java.awt.DisplayMode");

                // 切换到 EDT 线程创建和显示对话框
                Method invokeLater = swingUtilitiesCls.getMethod("invokeLater", Runnable.class);
                invokeLater.invoke(null, (Runnable)() -> {
                    try{
                        Object dialog = jDialogCls.getConstructor().newInstance();
                        jDialogCls.getMethod("setTitle", String.class).invoke(dialog, "fcitx5 中文输入");
                        jDialogCls.getMethod("setModal", boolean.class).invoke(dialog, true);
                        jDialogCls.getMethod("setAlwaysOnTop", boolean.class).invoke(dialog, true);
                        jDialogCls.getMethod("setResizable", boolean.class).invoke(dialog, false);

                    Object layoutMgr = borderLayoutCls.getConstructor(int.class, int.class).newInstance(8, 8);
                    Object panel = jPanelCls.getConstructor(Class.forName("java.awt.LayoutManager")).newInstance(layoutMgr);
                        Object emptyBorder = borderFactoryCls.getMethod("createEmptyBorder", int.class, int.class, int.class, int.class)
                            .invoke(null, 12, 12, 12, 12);
                        jPanelCls.getMethod("setBorder", Class.forName("javax.swing.border.Border")).invoke(panel, emptyBorder);

                        Object field = jTextFieldCls.getConstructor(int.class).newInstance(30);
                        if(currentText != null && !currentText.isEmpty()){
                            jTextFieldCls.getMethod("setText", String.class).invoke(field, currentText);
                        }
                        jTextFieldCls.getMethod("selectAll").invoke(field);
                        jTextFieldCls.getMethod("requestFocusInWindow").invoke(field);

                        jPanelCls.getMethod("add", Class.forName("java.awt.Component"), Object.class).invoke(panel, field, "Center");

                        Object btnPanel = jPanelCls.getConstructor(java.awt.LayoutManager.class).newInstance(
                            flowLayoutCls.getConstructor(int.class, int.class, int.class).newInstance(
                                flowLayoutCls.getField("RIGHT").getInt(null), 8, 0
                            )
                        );

                        Object okBtn = jButtonCls.getConstructor(String.class).newInstance("确定 (Enter)");
                        Object cancelBtn = jButtonCls.getConstructor(String.class).newInstance("取消 (Esc)");

                        // okBtn.addActionListener
                        Class<?> actionListenerCls = Class.forName("java.awt.event.ActionListener");
                        Object okListener = Proxy.newProxyInstance(
                            actionListenerCls.getClassLoader(),
                            new Class[]{actionListenerCls},
                            (proxy, method, args) -> {
                                if(method.getName().equals("actionPerformed")){
                                    String result = (String)jTextFieldCls.getMethod("getText").invoke(field);
                                    jDialogCls.getMethod("setVisible", boolean.class).invoke(dialog, false);
                                    jDialogCls.getMethod("dispose").invoke(dialog);
                                    if(result != null){
                                        String finalResult = result;
                                        Core.app.post(() -> insertText(gameField, finalResult));
                                    }
                                    dialogShowing = false;
                                }
                                return null;
                            }
                        );
                        jButtonCls.getMethod("addActionListener", actionListenerCls).invoke(okBtn, okListener);

                        Object cancelListener = Proxy.newProxyInstance(
                            actionListenerCls.getClassLoader(),
                            new Class[]{actionListenerCls},
                            (proxy, method, args) -> {
                                if(method.getName().equals("actionPerformed")){
                                    jDialogCls.getMethod("setVisible", boolean.class).invoke(dialog, false);
                                    jDialogCls.getMethod("dispose").invoke(dialog);
                                    dialogShowing = false;
                                }
                                return null;
                            }
                        );
                        jButtonCls.getMethod("addActionListener", actionListenerCls).invoke(cancelBtn, cancelListener);

                        // field.addActionListener -> okBtn.doClick()
                        Object fieldListener = Proxy.newProxyInstance(
                            actionListenerCls.getClassLoader(),
                            new Class[]{actionListenerCls},
                            (proxy, method, args) -> {
                                if(method.getName().equals("actionPerformed")){
                                    jButtonCls.getMethod("doClick").invoke(okBtn);
                                }
                                return null;
                            }
                        );
                        jTextFieldCls.getMethod("addActionListener", actionListenerCls).invoke(field, fieldListener);

                        Class<?> compCls = Class.forName("java.awt.Component");
                        jPanelCls.getMethod("add", compCls, Object.class).invoke(btnPanel, okBtn);
                        jPanelCls.getMethod("add", compCls, Object.class).invoke(btnPanel, cancelBtn);
                        jPanelCls.getMethod("add", compCls, Object.class).invoke(panel, btnPanel, "South");

                        // dialog.getContentPane().add(panel)
                        Object contentPane = jDialogCls.getMethod("getContentPane").invoke(dialog);
                        contentPane.getClass().getMethod("add", Class.forName("java.awt.Component")).invoke(contentPane, panel);

                        jDialogCls.getMethod("pack").invoke(dialog);

                        // 居中显示
                        Object gEnv = graphicsEnvCls.getMethod("getLocalGraphicsEnvironment").invoke(null);
                        Object gd = graphicsEnvCls.getMethod("getDefaultScreenDevice").invoke(gEnv);
                        Object dm = graphicsDeviceCls.getMethod("getDisplayMode").invoke(gd);
                        int sw = (int)displayModeCls.getMethod("getWidth").invoke(dm);
                        int sh = (int)displayModeCls.getMethod("getHeight").invoke(dm);
                        int dw = (int)jDialogCls.getMethod("getWidth").invoke(dialog);
                        int dh = (int)jDialogCls.getMethod("getHeight").invoke(dialog);
                        jDialogCls.getMethod("setLocation", int.class, int.class).invoke(dialog, sw / 2 - dw / 2, sh / 2 - dh / 2);

                        // Enter 键确认
                        Object rootPane = jDialogCls.getMethod("getRootPane").invoke(dialog);
                        rootPane.getClass().getMethod("setDefaultButton", Class.forName("javax.swing.JButton")).invoke(rootPane, okBtn);

                        // Esc 键取消
                        int vkEscape = Class.forName("java.awt.event.KeyEvent").getField("VK_ESCAPE").getInt(null);
                        Object escapeKey = keyStrokeCls.getMethod("getKeyStroke", int.class, int.class)
                            .invoke(null, vkEscape, 0);
                        rootPane.getClass().getMethod("registerKeyboardAction", actionListenerCls, keyStrokeCls, int.class)
                            .invoke(rootPane, cancelListener, escapeKey,
                                jComponentCls.getField("WHEN_IN_FOCUSED_WINDOW").getInt(null));

                        jDialogCls.getMethod("setVisible", boolean.class).invoke(dialog, true);
                    }catch(Throwable t){
                        Log.err("[BM] fcitx5 dialog error: @", Strings.getSimpleMessage(t));
                        dialogShowing = false;
                    }
                });
            }catch(Throwable t){
                Log.err("[BM] fcitx5 dialog init error: @", Strings.getSimpleMessage(t));
                dialogShowing = false;
            }
        });
    }

    private static void closeDialog(){
        try{
            ClassLoader cl = ClassLoader.getSystemClassLoader();
            Class<?> windowCls = cl.loadClass("java.awt.Window");
            Object[] windows = (Object[])windowCls.getMethod("getWindows").invoke(null);
            for(Object w : windows){
                if(w != null){
                    String title = (String)w.getClass().getMethod("getTitle").invoke(w);
                    if("fcitx5 中文输入".equals(title)){
                        w.getClass().getMethod("setVisible", boolean.class).invoke(w, false);
                        w.getClass().getMethod("dispose").invoke(w);
                    }
                }
            }
        }catch(Throwable ignored){}
        dialogShowing = false;
    }

    private static void insertText(TextField field, String text){
        if(field == null || field.getScene() == null) return;
        field.setText(text);
        if(text != null) field.setCursorPosition(text.length());
    }

    // ============ SDL IME 模式（原有实现） ============

    private static void addUpdateHook(){
        Events.run(Trigger.update, () -> {
            if(!enabled || !sdlAvailable || dialogMode) return;

            if(Core.scene != null && Core.scene.getKeyboardFocus() instanceof TextField tf){
                ensureTextInputActive();
                updateInputRect(tf);
            }
        });
    }

    private static void ensureTextInputActive(){
        if(textInputActive) return;
        try{
            sdlStartTextInput.invoke(null);
            textInputActive = true;
            Log.debug("[BM] fcitx5 SDL_StartTextInput called");
        }catch(Exception e){
            Log.err("[BM] Failed to start text input", e);
        }
    }

    private static void deactivateTextInput(){
        if(!textInputActive) return;
        try{
            sdlStopTextInput.invoke(null);
            textInputActive = false;
            Log.debug("[BM] fcitx5 SDL_StopTextInput called");
        }catch(Exception e){
            Log.err("[BM] Failed to stop text input", e);
        }
    }

    private static void updateInputRect(TextField field){
        try{
            Vec2 pos = field.localToStageCoordinates(Tmp.v1.setZero());
            int screenH = Core.graphics.getHeight();
            int x = (int)(pos.x);
            int y = screenH - 1 - (int)(pos.y + field.getHeight());
            int w = (int)field.getWidth();
            int h = (int)field.getHeight();
            sdlSetTextInputRect.invoke(null, x, y, w, h);
        }catch(Exception e){
            Log.err("[BM] Failed to update input rect", e);
        }
    }

    // ============ 工具方法 ============

    private static String checkFcitx5Remote(){
        try{
            Process p = new ProcessBuilder("fcitx5-remote", "-s").redirectErrorStream(true).start();
            boolean ok = p.waitFor() == 0;
            p.destroy();
            return ok ? "running" : "not-running";
        }catch(Exception e){
            return "not-found";
        }
    }
}
