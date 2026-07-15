// 更好的像素工厂 - 内置楷体字体注入脚本
// 参考 kai-font JS 字体包做法

const FreeTypeFontGenerator = Packages.arc.freetype.FreeTypeFontGenerator
const AssetManager = Packages.arc.assets.AssetManager

// 获取 mod 根目录
const ROOT = Vars.mods.locateMod(modName).root;

// 读取 Java 端注册的设置(bm-kai-font-enabled)
let enabled = false;
try {
    // arc.Settings 没有 get(String) 方法,需要用 getBool/getString
    // getBool(String, boolean) 接受 key 和默认值
    enabled = Core.settings.getBool("bm-kai-font-enabled", false);
} catch (e) {
    print("[BM-JS] 读取设置失败: " + e);
}

print("[BM-JS] 启动, kai 字体开关=" + enabled);

if (enabled) {
    try {
        // 注入 fonts/font.woff.gen(主字体)
        // ROOT 是 ZipFi(jar 根),ZipFi.child() 只支持单级查找(name().equals)
        // 不能用 ROOT.child("Fonts/kai.woff"),必须两级
        const fontsDir = ROOT.child("Fonts");
        const kaiWoff = fontsDir.child("kai.woff");
        const kaiTech = fontsDir.child("kaiTech.ttf");

        print("[BM-JS] kai.woff path=" + kaiWoff.path() + " exists=" + kaiWoff.exists());
        print("[BM-JS] kaiTech.ttf path=" + kaiTech.path() + " exists=" + kaiTech.exists());

        // 用 ZipFi 直接创建 FreeTypeFontGenerator(同 kai-font 字体包做法)
        // 不需要 extract 到本地
        Reflect.invoke(
            AssetManager, Core.assets, "addAsset",
            ["fonts/font.woff.gen", FreeTypeFontGenerator, new FreeTypeFontGenerator(kaiWoff)],
            Packages.java.lang.String, Packages.java.lang.Class, Packages.java.lang.Object
        );

        if (kaiTech.exists()) {
            Reflect.invoke(
                AssetManager, Core.assets, "addAsset",
                ["fonts/tech.ttf.gen", FreeTypeFontGenerator, new FreeTypeFontGenerator(kaiTech)],
                Packages.java.lang.String, Packages.java.lang.Class, Packages.java.lang.Object
            );
            print("[BM-JS] 楷体字体注入完成(包含 kai.woff 和 kaiTech.ttf)");
        } else {
            print("[BM-JS] 楷体字体注入完成(仅 kai.woff,tech 字体缺失)");
        }
    } catch (e) {
        print("[BM-JS] 楷体字体注入失败: " + e);
    }
}

// =====================================================================
// 时间流速 UI(可选)
// =====================================================================
// 在 ClientLoadEvent 中注入 HUD,默认 1x(0),最大 64x(6),仅加速。
// 通过 Time.setDeltaProvider 调整游戏 deltaTime,不影响游戏规则。

try {
    let tfEnabled = false;
    try {
        tfEnabled = Core.settings.getBool("bm-timeflow-enabled", false);
    } catch (e) {
        print("[BM-JS] 读取时间流速设置失败: " + e);
    }
    print("[BM-JS] 时间流速开关=" + tfEnabled);

    if (tfEnabled) {
        Events.on(ClientLoadEvent, e => {
            try {
                setupTimeFlowHud();
                print("[BM-JS] 时间流速 HUD 已注入");
            } catch (ex) {
                print("[BM-JS] 时间流速 HUD 注入失败: " + ex);
            }
        });

        // 离开地图(回到菜单)时,恢复 deltaProvider 为默认
        // 注意:Time.setDeltaProvider(null) 会让下一帧 Time.updateGlobal 抛 NPE,
        // 所以必须传回一个等同默认值的 provider:
        //   Time.deltaTime 默认实现就是 Core.graphics.getDeltaTime() * 60
        // (Rhino 无法直接访问 Java 静态字段 Time.deltaTime,所以用 Core 间接访问)
        Events.on(Packages.mindustry.game.EventType.StateChangeEvent, ev => {
            try {
                if (ev.to.toString() === "menu") {
                    Time.setDeltaProvider(function() { return Core.graphics.getDeltaTime() * 60; });
                    print("[BM-JS] 离开地图,已重置 deltaProvider");
                }
            } catch (err) {
                print("[BM-JS] 监听 StateChangeEvent 失败: " + err);
            }
        });
    }
} catch (e) {
    print("[BM-JS] 时间流速初始化失败: " + e);
}

function setupTimeFlowHud() {
    // 参考 /home/yiqiu/projects/Mindustry-mod/时间控制[手机版] (1) 的 UI 定位模式:
    //   1. 用 hudGroup.fill 占满整个屏幕
    //   2. fill 提供的 table 设为 bottom().left(),update 回调里动态计算
    //      uiGroup 中所有可见子元素的高度,作为 y 偏移
    //   3. 在 table 内嵌套子 table 作为 UI 容器
    Vars.ui.hudGroup.fill(null, table => {
        // 外层 table:对齐到屏幕左下 + 动态 y 偏移(浮在 uiGroup 之上)
        table.bottom().left();
        table.update(() => {
            let h = 0;
            try {
                Vars.control.input.uiGroup.getChildren().each(e => {
                    h += (e.visible ? e.getPrefHeight() : 0);
                });
            } catch (e) {
                // ignore
            }
            table.translation.set(0, h);
        });

        // 内部面板:4 个速度按钮
        const win = new Table();
        win.background(Styles.black3);
        win.touchable = Touchable.enabled;
        win.margin(6);

        const speeds = [
            { name: "1x",  speed: 1  },
            { name: "16x", speed: 16 },
            { name: "32x", speed: 32 },
            { name: "64x", speed: 64 }
        ];

        const colorNormal  = Color.white;
        const colorActive  = Color.valueOf("88ff88"); // 当前速度按钮:亮浅绿(比 #7fff7f 更显眼)
        const labels = [];

        // 应用速度并更新按钮高亮
        const applySpeed = speed => {
            if (speed <= 1.0001) {
                Time.setDeltaProvider(() => Math.min(Core.graphics.getDeltaTime() * 60, 3));
            } else {
                Time.setDeltaProvider(() => Math.min(Core.graphics.getDeltaTime() * 60 * speed, 3 * speed));
            }
            for (let i = 0; i < labels.length; i++) {
                labels[i].setColor(speeds[i].speed === speed ? colorActive : colorNormal);
            }
            let log2 = 0;
            if (speed === 16) log2 = 4;
            else if (speed === 32) log2 = 5;
            else if (speed === 64) log2 = 6;
            try {
                // Core.settings 是 ScriptableObject 包装,只暴露 put(Object),不接受 Double
                // 用 java.lang.Integer.valueOf 转 int 装箱
                Core.settings.put("bm-timeflow-value", Packages.java.lang.Integer.valueOf(log2));
            } catch (e) {
                print("[BM-JS] 持久化时间流速失败: " + e);
            }
        };

        // 一行放 4 个速度按钮
        // 注意:Button.setColor() 会把整个按钮(含背景)染绿,文字颜色反而看不清。
        // 所以这里对按钮内部的 Label 单独 setColor,文字亮绿,背景保持 grayt。
        const row = new Table();
        for (let i = 0; i < speeds.length; i++) {
            const s = speeds[i];
            const btn = new Button(Styles.grayt);
            btn.margin(8);
            const lbl = new Label(s.name);
            lbl.setColor(colorNormal);
            btn.add(lbl).center();
            btn.clicked(() => applySpeed(s.speed));
            labels.push(lbl);
            row.add(btn).size(60, 40).padRight(2);
        }
        win.add(row).row();

        // 拖动监听(命中 win 自身空白区才启动拖动,命中子元素不拦截)
        const dragListener = extend(Packages.arc.scene.event.InputListener, {
            touchDown: function(event, x, y, pointer, button) {
                if (event.targetActor !== win) {
                    return false;
                }
                this._drag = true;
                this._offX = x;
                this._offY = y;
                this._ox = win.x;
                this._oy = win.y;
                return true;
            },
            touchDragged: function(event, x, y, pointer) {
                if (this._drag) {
                    win.setPosition(this._ox + (x - this._offX), this._oy + (y - this._offY));
                }
            },
            touchUp: function(event, x, y, pointer, button) {
                this._drag = false;
            }
        });
        win.addListener(dragListener);

        // 把 win 嵌套到外层 table(底左对齐,留 20px 边距)
        table.table(null, t => {
            t.add(win);
        }).bottom().left().padLeft(20).padBottom(20);

        // 还原上次保存的档位
        let savedLog2 = 0;
        try {
            const v = Core.settings.get("bm-timeflow-value", null);
            if (v != null) {
                savedLog2 = parseInt(v + "");
            }
        } catch (e) {
            savedLog2 = 0;
        }
        const savedSpeed = Math.pow(2, savedLog2);
        const validSpeeds = [1, 16, 32, 64];
        const finalSpeed = validSpeeds.indexOf(savedSpeed) >= 0 ? savedSpeed : 1;
        applySpeed(finalSpeed);
    });
}

// =====================================================================
// 核心资源显示(可选)
// =====================================================================

var coreItemsTable = new Table();
var usedItems = new ObjectSet();
var coreItemsScale = 0.85;

function buildCoreItems(){
	coreItemsTable.clear();
	coreItemsTable.background(Styles.black3);
	
	var items = coreItemsTable.table().get();
	var rebuild = run(() => {
		items.clear();
		let i = 0;
		usedItems.each((item) => {
			items.image(item.uiIcon).size(Vars.iconSmall * coreItemsScale);
			items.label(() => "" + UI.formatAmount(Vars.player.core() == null ? 0 : Vars.player.core().items.get(item))).padRight(5).minWidth(Vars.iconSmall * coreItemsScale + 5).get().setFontScale(coreItemsScale);
			if(++i % 4 == 0) items.row();
		});
	});

	items.update(() => {
		Vars.content.items().each(item => {
			if(Vars.player.core() != null && Vars.player.core().items.get(item) > 0 ) usedItems.add(item);
		});
		rebuild.run();
	});
	
	coreItemsTable.row();
	var info = coreItemsTable.table().growX().get();
	info.image(Blocks.coreNucleus.uiIcon).size(Vars.iconSmall * coreItemsScale).growX();
	info.label(() => Vars.player.team().cores().size + "").padRight(5).get().setFontScale(coreItemsScale);
	info.image(UnitTypes.mono.uiIcon).size(Vars.iconSmall * coreItemsScale).growX();
  	info.label(() => countMiner(Vars.player.team()) + "").padRight(5).get().setFontScale(coreItemsScale);
  	info.image(UnitTypes.gamma.uiIcon).size(Vars.iconSmall * coreItemsScale).growX();
	info.label(() => "[#" + Vars.player.team().color + "]" + countPlayer(Vars.player.team()) + "[]/[accent]" + Groups.player.size()).padRight(5).get().setFontScale(coreItemsScale);
}

Events.on(ResetEvent, e => {
	usedItems.clear();
});

try {
    let ciEnabled = false;
    try {
        ciEnabled = Core.settings.getBool("bm-coreitems-enabled", false);
    } catch (e) {
        print("[BM-JS] 读取核心资源显示设置失败: " + e);
    }
    print("[BM-JS] 核心资源显示开关=" + ciEnabled);

    if (ciEnabled) {
        Events.on(EventType.ClientLoadEvent, e => {
            try {
                buildCoreItems();
                Vars.ui.hudGroup.fill(cons(t => {
                    t.left().name = "coreItems/info";
                    t.add(coreItemsTable);
                    
                    t.addListener(extend(InputListener, {
                        lastx: 0,
                        lasty: 0,
                
                        touchDown(event, x, y, pointer, button){
                            var v = t.localToParentCoordinates(Tmp.v1.set(x, y));
                            this.lastx = v.x;
                            this.lasty = v.y;
                            return true;
                        },
                
                        touchDragged(event, x, y, pointer){
                            var v = t.localToParentCoordinates(Tmp.v1.set(x, y));
                            t.translation.add(v.x - this.lastx, v.y - this.lasty);
                            this.lastx = v.x;
                            this.lasty = v.y;
                        },
                    }));
                    
                }));
                print("[BM-JS] 核心资源显示 HUD 已注入");
            } catch (ex) {
                print("[BM-JS] 核心资源显示注入失败: " + ex);
            }
        });
    }
} catch (e) {
    print("[BM-JS] 核心资源显示初始化失败: " + e);
}

function countPlayer(team){
	return Groups.player.count(player => player.team() == team);
}

function countMiner(team){
	return team.data().units.count(u => u.controller instanceof MinerAI);
}
