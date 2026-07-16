package yiqiu;

import arc.*;
import arc.graphics.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.mod.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.Block;
import yiqiu.ui.*;
import yiqiu.block.*;
import yiqiu.item.*;
import yiqiu.pinyin.*;
import yiqiu.util.*;

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

    /** 强化传送带 */
    public static Block strengthenConveyor;
    /** 一般容器 */
    public static Block generalContainer;
    /** 强化铜墙 */
    public static Block strengthenCopperWall;
    /** 大型强化铜墙 */
    public static Block strengthenCopperWallLarge;
    /** 强化机械钻机 */
    public static Block strengthenMechanicalDrill;
    /** 三重合金 */
    public static Item tripleAlloy;
    /** 三重合金压缩机 */
    public static Block tripleAlloyCompressor;
    /** 终极修复投影 */
    public static Block ultimateMendProjector;

    public bettermindustryMod(){
        loader_log.start();
        Log.info("[BM] Mod 构造函数完成");
    }

    @Override
    public void init(){
        // 1. 确保 BM 目录结构存在
        FontManager.ensureDirectories();

        // 2. 安装拼音搜索模块
        PinyinSearchMod.build();

        // 3. 在 ClientLoadEvent 中注册设置(此时 settings 菜单已构建)
        Events.on(ClientLoadEvent.class, e -> {
            registerBMSettings();
        });

        // 4. 启动科技树控制器
        TechTreeController.hook();

        // 5. 注册模组管理到游戏设置
        BetterModManager.hook();

        Log.info("[BM] 初始化完成 (kai-font 启用=@)", FontManager.isKaiFontEnabled());
    }

    // 缩放设置 - 保存原始 minZoom
    private static float originalMinZoom = 1.5f;

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

            // ============ 缩放设置 ============
            addSection(table, "缩放设置");

            addToggleRow(table, "[#7fff7f]极限缩放视野[]", box -> {
                box.update(() -> box.setChecked(Core.settings.getBool("bm-extreme-zoom", false)));
                box.changed(() -> {
                    boolean newVal = box.isChecked();
                    Core.settings.put("bm-extreme-zoom", newVal);
                    if(newVal){
                        // 保存原始值并设为极限，同时强制将相机拉到极限视野
                        originalMinZoom = Vars.renderer.minZoom;
                        Vars.renderer.minZoom = 0.02f;
                        Vars.renderer.minZoomInGame = Vars.renderer.minZoom / Core.settings.getFloat("minzoomingamemultiplier", 1f);
                        Vars.renderer.setScale(Vars.renderer.minScale());
                    }else{
                        // 恢复默认缩放限制并将相机拉回
                        Vars.renderer.minZoom = originalMinZoom;
                        Vars.renderer.minZoomInGame = Vars.renderer.minZoom / Core.settings.getFloat("minzoomingamemultiplier", 1f);
                        Vars.renderer.setScale(4f);
                    }
                });
            });
            addHint(table, "[#888888]开启后自动将视野缩放到极限，可查看几乎整个地图。\n关闭后自动恢复默认缩放级别。即时生效。[]");

            // ============ 拼音搜索 ============
            addSection(table, "拼音搜索");

            addToggleRow(table, "[#7fff7f]启用拼音搜索[]", box -> {
                box.update(() -> box.setChecked(Core.settings.getBool(PinyinSearchMod.keyEnabled, true)));
                box.changed(() -> Core.settings.put(PinyinSearchMod.keyEnabled, box.isChecked()));
            });
            addHint(table, "[#888888]在所有搜索框中支持拼音搜索，输入拼音字母即可匹配中文。\n例如输入 \"cl\" 可搜索到 \"处理\"。[]");

            addToggleRow(table, "[#7fff7f]模糊匹配(zh↔z)[]", box -> {
                box.update(() -> box.setChecked(Core.settings.getBool(PinyinSearchMod.keyFuzzy, true)));
                box.changed(() -> Core.settings.put(PinyinSearchMod.keyFuzzy, box.isChecked()));
            });
            addHint(table, "[#888888]开启后，z/c/s 可匹配 zh/ch/sh。例: \"cangku\"→\"仓库\"。[]");

            addToggleRow(table, "[#7fff7f]首字母匹配[]", box -> {
                box.update(() -> box.setChecked(Core.settings.getBool(PinyinSearchMod.keyInitials, true)));
                box.changed(() -> Core.settings.put(PinyinSearchMod.keyInitials, box.isChecked()));
            });
            addHint(table, "[#888888]开启后，输入 \"mfc\" 可匹配 \"面粉厂\"。[]");

            addToggleRow(table, "[#7fff7f]多音字匹配[]", box -> {
                box.update(() -> box.setChecked(Core.settings.getBool(PinyinSearchMod.keyHeteronym, true)));
                box.changed(() -> Core.settings.put(PinyinSearchMod.keyHeteronym, box.isChecked()));
            });
            addHint(table, "[#888888]开启后，\"银行\" 可通过 \"xing\" 匹配（行=hang/xing）。[]");

            // 延迟滑块
            var delayLabel = new Label("[#888888]搜索延迟: [#ffffff]" + Core.settings.getInt(PinyinSearchMod.keyDelayMs, PinyinSearchMod.defaultDelayMs) + " ms[]");
            delayLabel.setWrap(true);
            var delaySlider = new Slider(0, 500, 10, false);
            delaySlider.setValue(Core.settings.getInt(PinyinSearchMod.keyDelayMs, PinyinSearchMod.defaultDelayMs));
            delaySlider.changed(() -> {
                int val = (int)delaySlider.getValue();
                Core.settings.put(PinyinSearchMod.keyDelayMs, val);
                delayLabel.setText("[#888888]搜索延迟: [#ffffff]" + val + " ms[]");
            });
            table.add(delayLabel).width(Math.min(500f, Core.graphics.getWidth() / 1.2f / Scl.scl(1f))).padTop(8f).left();
            table.row();
            table.add(delaySlider).width(Math.min(400f, Core.graphics.getWidth() / 1.2f / Scl.scl(1f))).padTop(4f).left();
            table.row();
            addHint(table, "[#888888]输入停止后等待多长时间开始搜索。0=即时搜索，推荐 120ms。[]");

            // ============ 关于 ============
            addSection(table, "关于");
            addHint(table, "[#888888]更好的像素工厂 v1.0.0\n作者: 弈秋忘忧白帽\n"
                + "如果遇到 Bug 或功能建议,请在 GitHub 提交 Issue。[]");
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

    /** 添加一个可点击的操作按钮行 */
    private void addActionRow(Table table, String text, Runnable action){
        var box = new Button(Styles.grayt);
        box.background(Styles.grayPanel);
        box.margin(10f);
        box.add(text).left();
        box.left();
        box.clicked(action);
        table.add(box).minWidth(Math.min(500f, Core.graphics.getWidth() / 1.2f / Scl.scl(1f))).fillX().height(45f).left().padTop(7f);
        table.row();
    }

    /** 添加一个禁用状态的行（灰色显示，不可点击） */
    private void addDisableRow(Table table, String text){
        var box = new Button(Styles.grayt);
        box.background(Styles.grayPanel);
        box.margin(10f);
        box.add(text).left().color(Color.gray);
        box.left();
        box.setDisabled(true);
        table.add(box).minWidth(Math.min(500f, Core.graphics.getWidth() / 1.2f / Scl.scl(1f))).fillX().height(45f).left().padTop(7f);
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

    @Override
    public void loadContent(){
        Log.info("[BM] loadContent");

        // 注册强化传送带
        strengthenConveyor = new StrengthenConveyor("strengthen-conveyorbelt");
        Log.info("[BM] 强化传送带已注册: @", strengthenConveyor.name);

        // 挂载科技树：普通传送带 → 强化传送带 → 钛传送带
        var conveyorNode = TechTree.all.find(t -> t.content == Blocks.conveyor);
        var titaniumNode = TechTree.all.find(t -> t.content == Blocks.titaniumConveyor);

        if (conveyorNode != null && titaniumNode != null) {
            // 将我们的节点插入到 conveyor 和 titaniumConveyor 之间
            var strengNode = new TechTree.TechNode(conveyorNode, strengthenConveyor, strengthenConveyor.researchRequirements());

            // 将钛传送带从 conveyor 的子节点移到强化传送带下
            titaniumNode.parent.children.remove(titaniumNode);
            titaniumNode.parent = strengNode;
            strengNode.children.add(titaniumNode);

            Log.info("[BM] 科技树已挂载: 传送带 → 强化传送带 → 钛传送带");
        } else {
            Log.warn("[BM] 无法挂载科技树: conveyorNode=@, titaniumNode=@", conveyorNode, titaniumNode);
        }

        // 注册一般容器
        generalContainer = new GeneralContainer("general-container");

        // 挂载科技树：容器 → 一般容器
        var containerNode = TechTree.all.find(t -> t.content == Blocks.container);
        if (containerNode != null) {
            new TechTree.TechNode(containerNode, generalContainer, generalContainer.researchRequirements());
            Log.info("[BM] 科技树已挂载: 容器 → 一般容器");
        } else {
            Log.warn("[BM] 无法找到容器科技节点");
        }

        // 注册强化铜墙
        strengthenCopperWall = new StrengthenCopperWall("strengthen-copper-wall");
        strengthenCopperWallLarge = new StrengthenCopperWallLarge("strengthen-copper-wall-large");

        // 挂载科技树：铜墙 → 强化铜墙 → 大型强化铜墙 → 钛墙
        var copperWallNode = TechTree.all.find(t -> t.content == Blocks.copperWall);
        var titaniumWallNode = TechTree.all.find(t -> t.content == Blocks.titaniumWall);
        if (copperWallNode != null && titaniumWallNode != null) {
            var smallNode = new TechTree.TechNode(copperWallNode, strengthenCopperWall, strengthenCopperWall.researchRequirements());
            var largeNode = new TechTree.TechNode(smallNode, strengthenCopperWallLarge, strengthenCopperWallLarge.researchRequirements());

            // 将钛墙从铜墙的子节点移到大型强化铜墙下
            titaniumWallNode.parent.children.remove(titaniumWallNode);
            titaniumWallNode.parent = largeNode;
            largeNode.children.add(titaniumWallNode);

            Log.info("[BM] 科技树已挂载: 铜墙 → 强化铜墙 → 大型强化铜墙 → 钛墙");
        } else {
            Log.warn("[BM] 无法挂载铜墙科技树: copperWallNode=@, titaniumWallNode=@", copperWallNode, titaniumWallNode);
        }

        // 注册强化机械钻机
        strengthenMechanicalDrill = new StrengthenMechanicalDrill("strengthen-mechanical-drill");

        // 挂载科技树：机械钻机 → 强化机械钻机 → 石墨压块机 → 气动钻头
        var mdNode = TechTree.all.find(t -> t.content == Blocks.mechanicalDrill);
        var gpNode = TechTree.all.find(t -> t.content == Blocks.graphitePress);
        if (mdNode != null && gpNode != null && gpNode.parent == mdNode) {
            var smNode = new TechTree.TechNode(mdNode, strengthenMechanicalDrill, strengthenMechanicalDrill.researchRequirements());

            // 将石墨压块机从机械钻机的子节点移到强化机械钻机下
            gpNode.parent.children.remove(gpNode);
            gpNode.parent = smNode;
            smNode.children.add(gpNode);

            Log.info("[BM] 科技树已挂载: 机械钻机 → 强化机械钻机 → 石墨压块机");
        } else {
            Log.warn("[BM] 无法挂载机械钻机科技树: mdNode=@, gpNode=@", mdNode, gpNode);
        }

        // 注册三重合金物品
        tripleAlloy = new TripleAlloy();
        Log.info("[BM] 三重合金已注册: @", tripleAlloy.name);

        // 注册三重合金压缩机
        tripleAlloyCompressor = new TripleAlloyCompressor("triple-alloy-compressor");

        // 挂载科技树：巨浪合金炉 → 三重合金压缩机
        var surgeNode = TechTree.all.find(t -> t.content == Blocks.surgeSmelter);
        if (surgeNode != null) {
            new TechTree.TechNode(surgeNode, tripleAlloyCompressor, tripleAlloyCompressor.researchRequirements());
            Log.info("[BM] 科技树已挂载: 巨浪合金炉 → 三重合金压缩机");
        } else {
            Log.warn("[BM] 无法找到巨浪合金炉科技节点");
        }

        // 注册终极修复投影
        ultimateMendProjector = new UltimateMendProjector("ultimate-mend-projector");

        // 挂载科技树：修复投影 → 终极修复投影
        var mendProjectorNode = TechTree.all.find(t -> t.content == Blocks.mendProjector);
        if (mendProjectorNode != null) {
            new TechTree.TechNode(mendProjectorNode, ultimateMendProjector, ultimateMendProjector.researchRequirements());
            Log.info("[BM] 科技树已挂载: 修复投影 → 终极修复投影");
        } else {
            Log.warn("[BM] 无法找到修复投影科技节点");
        }
    }
}
