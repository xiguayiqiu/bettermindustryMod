package yiqiu.ui;

import arc.*;
import arc.files.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.scene.style.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.ctype.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.mod.Mods.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;

import static mindustry.Vars.*;

/**
 * 更好的模组管理 — 直接嵌入游戏设置页面。
 *
 * 已启用/未启用模组分左右双栏，按 Java/JavaScript/JS 分类。
 * 每个 Mod 带箭头切换启用/禁用，关闭设置时提示重启。
 */
public class BetterModManager {

    private static ObjectMap<String, Boolean> originalStates = new ObjectMap<>();
    private static Table enabledList, disabledList;
    private static String currentSearch = "";
    private static TextField searchField;

    /** 注册到游戏设置：添加「模组管理」分类页 */
    public static void hook() {
        Events.on(ClientLoadEvent.class, e -> {
            Vars.ui.settings.addCategory("模组管理", Icon.book, table -> {
                // 进入模组管理页面时记录各 Mod 的原始启用状态
                snapshotOriginalStates();
                buildSettingsPage(table);
            });

            // 设置页关闭时检查是否有真实的变更
            Vars.ui.settings.hidden(() -> {
                if (hasRealChanges()) {
                    ui.showConfirm("@mod.reloadrequired", "@mod.reloadrequired", () -> {
                        if (mods.requiresReload()) mods.reload();
                    });
                }
                originalStates.clear();
            });

            Log.info("[BM] 模组管理已添加到游戏设置");
        });
    }

    /** 记录所有 Mod 的原始启用状态 */
    private static void snapshotOriginalStates() {
        originalStates.clear();
        for (LoadedMod m : mods.list()) {
            originalStates.put(m.name, m.enabled());
        }
    }

    /** 检查是否有 Mod 的启用状态与原始状态不同 */
    private static boolean hasRealChanges() {
        for (LoadedMod m : mods.list()) {
            Boolean original = originalStates.get(m.name);
            if (original != null && original != m.enabled()) {
                return true;
            }
        }
        return false;
    }

    /** 构建设置页内容 */
    private static void buildSettingsPage(SettingsMenuDialog.SettingsTable table) {
        table.top().left();
        table.margin(8f);

        boolean isPortrait = mobile && Core.graphics.isPortrait();

        // 工具栏
        table.table(toolbar -> {
            toolbar.defaults().height(50f).pad(4f);
            toolbar.button("@mods.browser", Icon.menu, Styles.flatBordert, () -> Vars.ui.mods.browser.show())
                .width(140f);
            toolbar.button("@mod.import.file", Icon.file, Styles.flatBordert, () -> {
                FileChooser.open("zip", "jar").submitMulti(files -> {
                    for (var file : files) {
                        try { mods.importMod(file); } catch (Exception ex) {
                            ui.showException(ex.getMessage() != null && ex.getMessage().toLowerCase(java.util.Locale.ROOT).contains("writable dex") ? "@error.moddex" : "", ex);
                        }
                    }
                    refreshModLists();
                });
            }).width(140f);
            toolbar.button("@mod.import.github", Icon.github, Styles.flatBordert, () -> {
                ui.showTextInput("@mod.import.github", "", 64, Core.settings.getString("lastmod", ""), text -> {
                    text = text.trim().replace(" ", "");
                    if (text.startsWith("https://github.com/")) text = text.substring("https://github.com/".length());
                    Core.settings.put("lastmod", text);
                    githubImportMod(text, false, null, true);
                });
            }).width(140f);
        }).growX().padBottom(isPortrait ? 2f : 6f);

        table.row();

        // 搜索框
        table.table(search -> {
            search.image(Icon.zoom).padRight(8f).size(32f);
            searchField = search.field(currentSearch, text -> {
                currentSearch = text;
                refreshModLists();
            }).growX().get();
            searchField.setMessageText("@players.search");
        }).growX().pad(2f);

        table.row();

        // 双栏
        if (isPortrait) {
            // 竖屏：上下
            table.table(top -> {
                top.top().left();
                top.add("[accent]" + Core.bundle.get("bm.bettermodmanager.enabled") + "[]").left().padBottom(2f);
                top.row();
                top.table(inner -> { inner.top().left(); enabledList = inner; }).grow();
            }).grow().padBottom(2f);

            table.row();
            table.image(Tex.whiteui, Pal.gray).height(2f).growX().padLeft(10f).padRight(10f);
            table.row();

            table.table(bottom -> {
                bottom.top().left();
                bottom.add("[accent]" + Core.bundle.get("bm.bettermodmanager.disabled") + "[]").left().padBottom(2f);
                bottom.row();
                bottom.table(inner -> { inner.top().left(); disabledList = inner; }).grow();
            }).grow().padTop(2f);
        } else {
            // 横屏/桌面：左右
            table.table(main -> {
                main.table(leftWrap -> {
                    leftWrap.top().left();
                    leftWrap.add("[accent]" + Core.bundle.get("bm.bettermodmanager.enabled") + "[]").left().padBottom(4f);
                    leftWrap.row();
                    leftWrap.table(inner -> { inner.top().left(); enabledList = inner; }).grow();
                }).grow().padRight(4f);

                main.image(Tex.whiteui, Pal.gray).width(2f).growY().padTop(20f).padBottom(20f);

                main.table(rightWrap -> {
                    rightWrap.top().left();
                    rightWrap.add("[accent]" + Core.bundle.get("bm.bettermodmanager.disabled") + "[]").left().padBottom(4f);
                    rightWrap.row();
                    rightWrap.table(inner -> { inner.top().left(); disabledList = inner; }).grow();
                }).grow().padLeft(4f);
            }).grow();
        }

        refreshModLists();
    }

    /** 刷新模组列表（不重建整个页面） */
    private static void refreshModLists() {
        if (enabledList == null || disabledList == null) return;
        enabledList.clear();
        disabledList.clear();
        fillMods(enabledList, true, currentSearch);
        fillMods(disabledList, false, currentSearch);
    }

    /** 填充一侧的 Mod 列表 */
    private static void fillMods(Table container, boolean enabled, String search) {
        Seq<LoadedMod> javaMods = new Seq<>(), jsMods = new Seq<>(), scriptMods = new Seq<>();

        for (LoadedMod m : mods.getMods()) {
            if (m.enabled() != enabled) continue;
            String name = m.meta.displayName != null ? m.meta.displayName : m.name;
            if (!search.isEmpty() && !name.toLowerCase().contains(search.toLowerCase())) continue;

            if (m.isJava()) javaMods.add(m);
            else if (m.file.extension().equalsIgnoreCase("js")) jsMods.add(m);
            else scriptMods.add(m);
        }

        if (javaMods.any()) {
            container.add("[#fbd24d]Java 模组[]").padTop(6f).padBottom(4f); container.row();
            container.image(Tex.whiteui, Pal.gray).height(1f).growX().padLeft(4f).padRight(4f);
            container.row();
            for (LoadedMod m : javaMods) { addModCard(container, m); container.row(); container.add().height(4f); container.row(); }
        }
        if (jsMods.any()) {
            container.add("[#7fff7f]JavaScript 模组[]").padTop(6f).padBottom(4f); container.row();
            container.image(Tex.whiteui, Pal.gray).height(1f).growX().padLeft(4f).padRight(4f);
            container.row();
            for (LoadedMod m : jsMods) { addModCard(container, m); container.row(); container.add().height(4f); container.row(); }
        }
        if (scriptMods.any()) {
            container.add("[#ffaa66]JS 模组[]").padTop(6f).padBottom(4f); container.row();
            container.image(Tex.whiteui, Pal.gray).height(1f).growX().padLeft(4f).padRight(4f);
            container.row();
            for (LoadedMod m : scriptMods) { addModCard(container, m); container.row(); container.add().height(4f); container.row(); }
        }
        if (!javaMods.any() && !jsMods.any() && !scriptMods.any()) {
            container.add("@none.found").color(Color.lightGray).padTop(10f); container.row();
        }
    }

    /** 添加单个 Mod 卡片 — 固定高度 */
    private static void addModCard(Table container, LoadedMod mod) {
        container.button(b -> {
            b.left();
            b.margin(4f);

            if (mod.iconTexture != null) {
                b.image(new TextureRegion(mod.iconTexture)).size(44f).padRight(8f).scaling(Scaling.fit).padLeft(4f);
            } else {
                b.image(Icon.fileText).size(44f).padRight(8f).color(Pal.accent).padLeft(4f);
            }

            b.table(info -> {
                info.top().left();
                info.add(mod.meta.displayName != null ? mod.meta.displayName : mod.name).color(Pal.accent).left().growX().wrap();
                info.row();
                String sub = "";
                if (mod.meta.author != null) sub += mod.meta.author;
                if (mod.meta.version != null) sub += (sub.isEmpty() ? "" : "  ") + "v" + mod.meta.version;
                if (!sub.isEmpty()) info.add(sub).color(Color.lightGray).left().fontScale(0.8f);
                if (mod.meta.hidden) {
                    info.add("  [#88ff88]" + Core.bundle.get("mod.multiplayer.compatible") + "[]").color(Color.lightGray).left().fontScale(0.8f);
                }
            }).growX().left();

            b.add().growX();

            if (mod.enabled()) {
                b.button(Icon.leftOpen, Styles.emptyi, () -> {
                    mods.setEnabled(mod, false);
                    refreshModLists();
                }).size(36f).padRight(4f).tooltip("[#888888]点击禁用此模组[]");
            } else {
                b.button(Icon.rightOpen, Styles.emptyi, () -> {
                    mods.setEnabled(mod, true);
                    refreshModLists();
                }).size(36f).padRight(4f).tooltip("[#888888]点击启用此模组[]");
            }
        }, () -> showModInfo(mod)).growX().fillX().height(56f);
    }

    // ============================================================
    // Mod 详情弹窗
    // ============================================================
    private static void showModInfo(LoadedMod mod) {
        BaseDialog dialog = new BaseDialog(mod.meta.displayName != null ? Strings.stripColors(mod.meta.displayName) : mod.name);

        dialog.cont.pane(left -> {
            left.defaults().pad(6f).left();

            if (mod.iconTexture != null) {
                left.image(new TextureRegion(mod.iconTexture)).size(80f).scaling(Scaling.fit).padBottom(8f);
                left.row();
            }
            left.add("[accent]" + (mod.meta.displayName != null ? mod.meta.displayName : mod.name) + "[]").growX().wrap();
            left.row();
            if (mod.meta.author != null) {
                left.add(Core.bundle.format("bm.bettermodmanager.author", mod.meta.author)).color(Color.lightGray);
                left.row();
            }
            if (mod.meta.version != null) {
                left.add(Core.bundle.format("bm.bettermodmanager.version", mod.meta.version)).color(Color.lightGray);
                left.row();
            }
            String type = mod.isJava() ? "Java" : (mod.file.extension().equalsIgnoreCase("js") ? "JavaScript" : "JS");
            left.add(Core.bundle.format("bm.bettermodmanager.type", type)).color(Color.lightGray);
            left.row();
            left.add(Core.bundle.format("bm.bettermodmanager.internal", mod.name)).color(Color.gray);
            left.row();
            if (mod.meta.hidden) {
                left.add("[#88ff88]✓ " + Core.bundle.get("mod.multiplayer.compatible") + "[]").color(Color.lightGray);
                left.row();
            }
            left.image(Tex.whiteui, Pal.gray).height(1f).growX().pad(4f);
            left.row();

            if (mod.meta.description != null && !mod.meta.description.isEmpty()) {
                String rawDesc = loadRawDescription(mod);
                left.add(rawDesc != null && rawDesc.contains("[") ? rawDesc : mod.meta.description).growX().wrap().width(380f);
                left.row();
            }

            left.image(Tex.whiteui, Pal.gray).height(1f).growX().pad(4f);
            left.row();
            left.add(Core.bundle.format("bm.bettermodmanager.status",
                mod.enabled() ? Core.bundle.get("bm.bettermodmanager.enabled") : Core.bundle.get("bm.bettermodmanager.disabled")))
                .color(mod.enabled() ? Pal.accent : Color.lightGray);
            left.row();

            String state = getStateText(mod);
            if (state != null) { left.add(state).color(Color.scarlet).growX().wrap(); left.row(); }
        }).width(mobile ? Math.min(Core.graphics.getWidth() / Scl.scl(1f) - 40f, 420f) : 420f).growY();

        boolean isPortrait = mobile && Core.graphics.isPortrait();
        // 收集底部按钮，竖屏时以 2xn 网格排列
        Seq<Runnable> buttonTasks = new Seq<>();

        if (mod.getRepo() != null) {
            String repo = mod.getRepo();
            if (repo.startsWith("https://github.com/")) repo = repo.substring("https://github.com/".length());
            else if (repo.startsWith("http://github.com/")) repo = repo.substring("http://github.com/".length());
            else if (repo.startsWith("github.com/")) repo = repo.substring("github.com/".length());
            final String cleanRepo = repo;
            addGridButton(dialog.buttons, "@mods.github.open", Icon.link, () -> Core.app.openURI("https://github.com/" + cleanRepo), isPortrait, 0);
            addGridButton(dialog.buttons, "@mods.browser.reinstall", Icon.download, () -> githubImportMod(cleanRepo, mod.isJava(), null, false), isPortrait, 1);
        } else {
            addGridButton(dialog.buttons, "[lightgray]从本地重新安装[]", Icon.download, () -> {
                FileChooser.open("zip", "jar").submitMulti(files -> {
                    for (var file : files) {
                        try { mods.importMod(file); } catch (Exception ex) {
                            ui.showException(ex.getMessage() != null && ex.getMessage().toLowerCase(java.util.Locale.ROOT).contains("writable dex") ? "@error.moddex" : "", ex);
                        }
                    }
                });
            }, isPortrait, 0);
        }

        // 打开文件夹
        if (!mobile && !mod.meta.hidden) {
            addGridButton(dialog.buttons, "@mods.openfolder", Icon.link, () -> Core.app.openFolder(Vars.modDirectory.absolutePath()), isPortrait, isPortrait ? 0 : 99);
        }

        // 关联内容
        Seq<UnlockableContent> all = collectModContent(mod);
        if (all.any()) {
            addGridButton(dialog.buttons, "@mods.viewcontent", Icon.book, () -> {
                BaseDialog d = new BaseDialog(mod.meta.displayName);
                d.cont.pane(cs -> {
                    int i = 0;
                    for (UnlockableContent c : all) {
                        cs.button(new TextureRegionDrawable(c.uiIcon), Styles.flati, iconMed, () -> ui.content.show(c))
                            .size(50f).tooltip(c.localizedName);
                        if (++i % (int) Math.min(Core.graphics.getWidth() / Scl.scl(110), 14) == 0) cs.row();
                    }
                }).grow();
                d.addCloseButton();
                d.show();
            }, isPortrait, isPortrait ? 1 : 99);
        }

        dialog.addCloseButton();
        dialog.show();
    }

    /** 辅助：竖屏时按 2 列添加按钮，横屏时水平添加 */
    private static void addGridButton(Table buttons, String text, Drawable icon, Runnable task, boolean isPortrait, int column) {
        if (isPortrait) {
            buttons.defaults().size(140f, 50f).pad(4f);
            buttons.button(text, icon, task);
            if (column % 2 == 1) buttons.row();
        } else {
            buttons.defaults().size(200f, 50f).pad(4f);
            buttons.button(text, icon, task);
        }
    }

    private static Seq<UnlockableContent> collectModContent(LoadedMod mod) {
        return Seq.with(content.getContentMap()).<Content>flatten()
            .select(c -> c.minfo.mod == mod && c instanceof UnlockableContent u && !u.isHidden()).as();
    }

    private static String loadRawDescription(LoadedMod mod) {
        try {
            Fi jsonFile = mod.root.child("mod.json");
            if (!jsonFile.exists()) jsonFile = mod.root.child("mod.hjson");
            if (!jsonFile.exists()) jsonFile = mod.root.child("plugin.json");
            if (!jsonFile.exists()) return null;
            String raw = jsonFile.readString();
            int idx = raw.indexOf("\"description\"");
            if (idx < 0) idx = raw.indexOf("description:");
            if (idx < 0) return null;
            int valStart = raw.indexOf('"', idx + 12);
            if (valStart < 0) return null;
            valStart++;
            StringBuilder sb = new StringBuilder();
            for (int i = valStart; i < raw.length(); i++) {
                char c = raw.charAt(i);
                if (c == '\\') { if (i + 1 < raw.length()) sb.append(raw.charAt(++i)); }
                else if (c == '"') break;
                else sb.append(c);
            }
            if (sb.length() > 0) return sb.toString().replace("\\n", "\n");
        } catch (Exception e) { Log.err("[BM] Failed to read raw description", e); }
        return null;
    }

    private static String getStateText(LoadedMod mod) {
        if (mod.isOutdated()) return "@mod.incompatiblemod";
        if (mod.isBlacklisted()) return "@mod.blacklisted";
        if (!mod.isSupported()) return Core.bundle.format("mod.requiresversion.details", mod.meta.minGameVersion);
        if (mod.state == ModState.circularDependencies) return "@mod.circulardependencies";
        if (mod.state == ModState.incompleteDependencies) return Core.bundle.format("mod.incompletedependencies.details", mod.missingDependencies.toString(", "));
        if (mod.hasUnmetDependencies()) return Core.bundle.format("mod.missingdependencies.details", mod.missingDependencies.toString(", "));
        if (mod.hasContentErrors()) return "@mod.erroredcontent";
        return null;
    }

    private static void githubImportMod(String repo, boolean javaMod, @Nullable String oldRepo, boolean forceEnable) {
        try {
            var method = ModsDialog.class.getDeclaredMethod("githubImportMod", String.class, boolean.class, String.class, boolean.class);
            method.setAccessible(true);
            method.invoke(Vars.ui.mods, repo, javaMod, oldRepo, forceEnable);
        } catch (Exception e) {
            Log.err("[BM] GitHub import failed", e);
            Vars.ui.mods.show();
        }
    }
}
