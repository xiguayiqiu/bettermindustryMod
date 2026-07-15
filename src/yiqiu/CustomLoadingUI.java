package yiqiu;

import arc.*;
import arc.graphics.*;
import arc.math.*;
import arc.scene.*;
import arc.scene.style.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import mindustry.*;
import mindustry.graphics.*;
import mindustry.ui.*;

/**
 * 自定义加载界面
 *
 * 通过反射清空原版 LoadingFragment.table 并替换
 * nameLabel/bar/button 字段,使原版 API 在新控件上生效。
 *
 * BM设置可控制:
 * - bm-customloading: 启用/禁用自定义界面
 * - bm-showmemory: 显示/隐藏内存用量
 * - bm-showphase: 显示/隐藏阶段文字
 * - bm-bar-speed: 进度条动画速度
 */
public class CustomLoadingUI{

    private Label phaseLabel;
    private Label modLabel;
    private Label memLabel;
    private Bar customBar;
    private TextButton cancelBtn;
    private boolean built;
    private boolean originalRestored;

    private String pendingPhase = "[#bbbbbb]等待加载...[]";
    private String pendingMod = "—";

    /** 构建并注入自定义 UI,仅执行一次 */
    public void build(){
        if(!Core.settings.getBool("bm-customloading", true)) return;
        if(built) return;
        built = true;
        originalRestored = false;

        try{
            var lf = Vars.ui.loadfrag;
            Class<?> cls = lf.getClass();

            var tf = cls.getDeclaredField("table");
            tf.setAccessible(true);
            Table t = (Table) tf.get(lf);
            if(t == null) return;

            t.clearChildren();

            // === 标题 ===
            t.add("[#fbd24d]BETTER MINDUSTRY[] [#888888] 加载中").padTop(120f).row();
            t.add().height(20f).row();

            // === 阶段文字 ===
            phaseLabel = new Label(pendingPhase);
            phaseLabel.setWrap(true);
            phaseLabel.setAlignment(Align.center);
            phaseLabel.visible(() -> Core.settings.getBool("bm-showphase", true));
            t.add(phaseLabel).growX().padLeft(60f).padRight(60f).row();
            t.add().height(10f).row();

            // === 当前 Mod ===
            modLabel = new Label(pendingMod);
            modLabel.setWrap(true);
            modLabel.visible(() -> Core.settings.getBool("bm-showmodlist", true));
            t.add(modLabel).growX().padLeft(60f).padRight(60f).row();
            t.add().height(20f).row();

            // === 进度条 ===
            var pct = new Label("--");
            customBar = new Bar(
                () -> pct.getText().toString(),
                () -> Pal.accent,
                () -> 0.1f + (Mathf.sinDeg(Time.time * (Core.settings.getInt("bm-bar-speed", 10) / 4f)) + 1f) * 0.4f
            );
            Table br = new Table();
            br.add(customBar).growX().height(36f);
            br.add(pct).padLeft(10f).width(50f);
            t.add(br).growX().padLeft(60f).padRight(60f).row();
            t.add().height(14f).row();

            // === 内存 ===
            memLabel = new Label("内存: --");
            memLabel.visible(() -> Core.settings.getBool("bm-showmemory", true));
            t.add(memLabel).growX().padLeft(60f).padRight(60f).row();
            t.add().height(20f).row();

            // === 取消按钮 ===
            cancelBtn = new TextButton("@cancel", Styles.defaultt);
            cancelBtn.visible = false;
            t.add(cancelBtn).size(200f, 50f).row();

            t.add().growY().row();
            t.add(new WarningBar()).growX().height(24f).row();

            // 反射替换字段
            setField(cls, lf, "nameLabel", phaseLabel);
            setField(cls, lf, "bar", customBar);
            setField(cls, lf, "button", cancelBtn);

            Log.info("[BM] 自定义加载 UI 已注入");

            Core.app.post(this::updateLoop);

        }catch(Throwable ex){
            Log.err("[BM] UI 注入失败", ex);
        }
    }

    /** 恢复原版加载界面(当用户禁用自定义界面时调用) */
    public void restoreOriginal(){
        if(!built || originalRestored) return;
        originalRestored = true;
        built = false;

        try{
            var lf = Vars.ui.loadfrag;
            Class<?> cls = lf.getClass();

            // 重新构建原版布局
            var tf = cls.getDeclaredField("table");
            tf.setAccessible(true);
            Table t = (Table) tf.get(lf);
            if(t == null) return;

            t.clearChildren();

            t.add().height(133f).row();
            t.add(new WarningBar()).growX().height(24f);
            t.row();

            Label nameLabel = t.add("@loading").pad(10f).style(Styles.outlineLabel).get();
            t.row();

            t.add(new WarningBar()).growX().height(24f);
            t.row();

            Bar bar = t.add(new Bar()).pad(3).padTop(6).size(500f, 40f).visible(false).get();
            t.row();

            TextButton btn = t.button("@cancel", () -> {}).pad(20).size(250f, 70f).visible(false).get();

            setField(cls, lf, "nameLabel", nameLabel);
            setField(cls, lf, "bar", bar);
            setField(cls, lf, "button", btn);

            Log.info("[BM] 已恢复原版加载界面");
        }catch(Throwable ex){
            Log.err("[BM] 恢复原版界面失败", ex);
        }
    }

    private static void setField(Class<?> cls, Object target, String name, Object value){
        try{
            var f = cls.getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        }catch(ReflectiveOperationException ignored){}
    }

    private void updateLoop(){
        try{
            if(Vars.ui != null && Vars.ui.loadfrag != null && Vars.ui.loadfrag.shown()){
                try{
                    var tf = Vars.ui.loadfrag.getClass().getDeclaredField("table");
                    tf.setAccessible(true);
                    Table t = (Table) tf.get(Vars.ui.loadfrag);
                    if(t != null) t.toFront();
                }catch(Throwable ignored){}
            }

            if(memLabel != null && Core.settings.getBool("bm-showmemory", true)){
                long used = Core.app.getJavaHeap();
                long total = Runtime.getRuntime().totalMemory();
                long max = Runtime.getRuntime().maxMemory();
                memLabel.setText("[#a0ffa0]内存:[] " + fmt(used) + " / " + fmt(total) + " (上限 " + fmt(max) + ")");
            }
        }catch(Throwable ignored){}
        Core.app.post(this::updateLoop);
    }

    private static String fmt(long b){
        if(b < 1024) return b + "B";
        if(b < 1048576L) return (b >> 10) + "KB";
        if(b < 1073741824L) return String.format("%.1fMB", b / 1048576f);
        return String.format("%.2fGB", b / 1073741824f);
    }

    public void setPhase(String s){
        pendingPhase = s;
        if(phaseLabel != null) phaseLabel.setText(s);
    }

    public void setCurrentMod(String s){
        pendingMod = s;
        if(modLabel != null) modLabel.setText(s);
    }
}
