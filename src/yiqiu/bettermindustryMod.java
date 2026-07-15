package yiqiu;

import arc.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.mod.*;
import mindustry.ui.*;

/**
 * bettermindustry - 更好的像素工厂 Mod
 *
 * 字体方案说明:
 *   通过 mod 内嵌的 assets/scripts/main.js (类似 kai-font JS 模组),
 *   在 mods.loadScripts() 阶段通过 AssetManager.addAsset() 注入内置楷体字体的
 *   FreeTypeFontGenerator,使得后续 Fonts 加载自动使用楷体。
 *
 *   启用开关为 Core.settings.getBool("bm-kai-font-enabled"),Java 与 JS 端
 *   共享 settings 配置。
 */
public class bettermindustryMod extends Mod{

    private CustomLoadingUI loaderUI;

    public bettermindustryMod(){
        loader_log.start();
        Log.info("[BM] Mod 构造函数完成");
    }

    @Override
    public void init(){
        // 1. 确保 BM 目录结构存在
        FontManager.ensureDirectories();

        // 2. 注入自定义加载界面
        loaderUI = new CustomLoadingUI();
        loaderUI.build();

        // 3. 在 ClientLoadEvent 中注册设置(此时 settings 菜单已构建)
        Events.on(ClientLoadEvent.class, e -> {
            registerBMSettings();
        });

        // 4. 启动科技树控制器
        TechTreeController.hook();

        // 5. 阶段事件
        Events.on(WorldLoadBeginEvent.class, e -> {
            loaderUI.setPhase("[#fbd24d]正在加载世界...[]");
            loaderUI.setCurrentMod("地图数据 / 存档");
        });

        Events.on(WorldLoadEndEvent.class, e -> {
            loaderUI.setPhase("[#7fff7f]世界加载完成[]");
            loaderUI.setCurrentMod("—");
        });

        Events.on(ModContentLoadEvent.class, e -> {
            loaderUI.setPhase("[#7fb6ff]Mod 内容初始化中...[]");
            loaderUI.setCurrentMod(modListSummary());
        });

        Log.info("[BM] 初始化完成 (kai-font 启用=@)", FontManager.isKaiFontEnabled());
    }

    private void registerBMSettings(){
        if(Vars.ui == null || Vars.ui.settings == null) return;

        Vars.ui.settings.addCategory("BM设置", Icon.wrench, table -> {
            table.left();

            // 顶置提示
            var notice = new Label("[#ffaa66]本模组现处于功能暴涨期,更新可能会比较频繁～[]");
            notice.setWrap(true);
            table.add(notice).width(Math.min(500f, Core.graphics.getWidth() / 1.2f / Scl.scl(1f))).padBottom(8f);
            table.row();

            // ============ 字体设置 ============
            addSection(table, "字体设置");

            // 当前字体
            var fontLabel = new Label(FontManager.getCurrentFontDisplayName());
            fontLabel.setWrap(true);
            table.add(fontLabel).width(Math.min(500f, Core.graphics.getWidth() / 1.2f / Scl.scl(1f))).padTop(4f);
            table.row();

            // 启用内置楷体字体开关
            addToggleRow(table, "[#7fff7f]使用内置楷体字体[]", box -> {
                box.update(() -> box.setChecked(FontManager.isKaiFontEnabled()));
                box.changed(() -> {
                    boolean newVal = box.isChecked();
                    FontManager.setKaiFontEnabled(newVal);
                    fontLabel.setText(FontManager.getCurrentFontDisplayName());
                    showRestartDialog(
                        "[#fbd24d]需要重启游戏[]",
                        newVal
                            ? "已启用内置楷体字体。\n字体替换需在游戏下次启动时生效,建议立即重启游戏。"
                            : "已关闭内置楷体字体。\n游戏下次启动时会恢复为默认字体,建议立即重启游戏。",
                        null
                    );
                });
            });

            addHint(table, "[#888888]说明:启用后游戏主字体替换为内置楷体,适合中文显示。\n此功能由 assets/scripts/main.js 在字体加载前注入,需重启游戏生效。[]");

            // ============ 科技树 ============
            addSection(table, "科技树");

            addToggleRow(table, "[#7fff7f]显示未解锁科技[]", box -> {
                box.update(() -> box.setChecked(Core.settings.getBool("bm-showlocked", false)));
                box.changed(() -> Core.settings.put("bm-showlocked", box.isChecked()));
            });

            addHint(table, "[#888888]显示未解锁内容图标(灰色),点击不会解锁,仅预览。\n需重启游戏生效。[]");

            // ============ 时间流速 ============
            addSection(table, "时间流速");

            addToggleRow(table, "[#7fff7f]游戏内时间流速控件[]", box -> {
                box.update(() -> box.setChecked(Core.settings.getBool("bm-timeflow-enabled", false)));
                box.changed(() -> {
                    boolean newVal = box.isChecked();
                    Core.settings.put("bm-timeflow-enabled", newVal);
                    try{ Core.settings.manualSave(); }catch(Throwable t){ Log.err("[BM] manualSave 失败", t); }
                    showRestartDialog(
                        "[#fbd24d]需要重启游戏[]",
                        newVal
                            ? "已启用游戏内时间流速控件。\n游戏会在左下角添加一个时间流速按钮(1x、16x、32x、64x),重启后生效。"
                            : "已关闭游戏内时间流速控件。\n游戏下次启动时移除该按钮。",
                        null
                    );
                });
            });

            addHint(table, "[#888888]启用后在游戏界面左下角显示一个时间流速按钮(1x、16x、32x、64x)。\n仅调整游戏运行速度,不影响单位、规则或胜负判定。\n需重启游戏生效。[]");

            // ============ 核心资源显示 ============
            addSection(table, "核心资源显示");

            addToggleRow(table, "[#7fff7f]游戏内核心资源面板[]", box -> {
                box.update(() -> box.setChecked(Core.settings.getBool("bm-coreitems-enabled", false)));
                box.changed(() -> {
                    boolean newVal = box.isChecked();
                    Core.settings.put("bm-coreitems-enabled", newVal);
                    try{ Core.settings.manualSave(); }catch(Throwable t){ Log.err("[BM] manualSave 失败", t); }
                    showRestartDialog(
                        "[#fbd24d]需要重启游戏[]",
                        newVal
                            ? "已启用核心资源显示。\n游戏会在 HUD 上方显示当前核心所拥有的资源及核心/玩家/采矿单位数量。"
                            : "已关闭核心资源显示。\n游戏下次启动时将不再显示核心资源面板。",
                        null
                    );
                });
            });

            addHint(table, "[#888888]启用后在游戏界面左上角显示一个核心资源面板。\n显示当前核心拥有的资源、核心数量、在线玩家及采矿单位数量。\n面板可拖动。需重启游戏生效。[]");
        });

        Log.info("[BM] BM设置已注册");
    }

    /** 添加段落标题 */
    private void addSection(Table table, String title){
        table.add("[#fbd24d]" + title + "[]").left().padTop(16f).padBottom(4f)
            .width(Math.min(500f, Core.graphics.getWidth() / 1.2f / Scl.scl(1f)));
        table.row();
    }

    /** 添加说明文字 */
    private void addHint(Table table, String text){
        var hint = new Label(text);
        hint.setWrap(true);
        table.add(hint).width(Math.min(500f, Core.graphics.getWidth() / 1.2f / Scl.scl(1f))).padTop(4f).padBottom(4f);
        table.row();
    }

    /** 添加一行开关设置（参考原版 CheckSetting 样式） */
    private void addToggleRow(Table table, String text, ToggleHandler handler){
        var box = new Button(Styles.grayt);
        box.background(Styles.grayPanel);
        box.margin(10f);

        box.add(new Image()).update(i -> i.setDrawable(
            box.isChecked() ? Tex.checkOn : Tex.checkOff
        )).size(32f).padRight(8f).padLeft(-4f);

        box.add(text).left();

        box.left();
        handler.configure(box);
        table.add(box).minWidth(Math.min(500f, Core.graphics.getWidth() / 1.2f / Scl.scl(1f))).fillX().height(45f).left().padTop(7f);
        // 不添加 tooltip，因是非原版设置
        table.row();
    }

    /** 开关配置回调 */
    @FunctionalInterface
    private interface ToggleHandler {
        void configure(Button box);
    }

    /**
     * 显示需要重启游戏的提示对话框,提供"立即重启"和"稍后"两个按钮
     */
    private void showRestartDialog(String title, String body, Runnable onConfirm){
        if(Vars.ui == null) return;

        // showCustomConfirm 的"no"分支会调用 denied.run(),必须为非空
        Runnable noOp = () -> {};

        Vars.ui.showCustomConfirm(
            title,
            body,
            "立即重启",
            "稍后再说",
            () -> {
                try{
                    Core.settings.manualSave();
                }catch(Throwable t){
                    Log.err("[BM] 保存设置失败", t);
                }
                Log.info("[BM] 设置已更改,正在退出游戏...");
                Core.app.exit();
                if(onConfirm != null){
                    try{ onConfirm.run(); }catch(Throwable t){ Log.err("[BM] onConfirm 异常", t); }
                }
            },
            noOp
        );
    }

    private static String modListSummary(){
        Seq<mindustry.mod.Mods.LoadedMod> list = Vars.mods.list();
        if(list.size == 0) return "—";
        StringBuilder sb = new StringBuilder();
        int max = Math.min(list.size, 5);
        for(int i = 0; i < max; i++){
            if(i > 0) sb.append(", ");
            sb.append(list.get(i).meta.displayName);
        }
        if(list.size > 5) sb.append(" 等");
        return sb.toString();
    }

    @Override
    public void loadContent(){
        Log.info("[BM] loadContent");
    }
}
