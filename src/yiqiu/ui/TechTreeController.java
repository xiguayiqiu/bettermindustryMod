package yiqiu.ui;

import arc.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.content.TechTree;
import mindustry.content.TechTree.TechNode;

/**
 * 科技树未解锁显示控制器
 */
public class TechTreeController{

    private static boolean hooked;
    private static int tickCounter;
    private static boolean objectivesCleared;

    public static void hook(){
        if(hooked) return;
        hooked = true;

        Log.info("[BM] TechTreeController hooked");

        // 持续每帧刷新, 覆盖 checkNodes 触发的重置
        Core.app.post(new Runnable(){
            @Override
            public void run(){
                try{
                    tickCounter++;
                    if(Core.settings.getBool("bm-showlocked", false)){
                        forceShowLocked();

                        // 每 60 帧 (约 1 秒) 输出一次调试日志
                        if((tickCounter % 60) == 0){
                            Log.info("[BM] TechTree show-locked active, tick=" + tickCounter
                                + ", nodes cleared=" + objectivesCleared);
                        }
                    }else{
                        // 设置关闭时重置标记, 下次开启时重新清空
                        objectivesCleared = false;
                    }
                }catch(Throwable ignored){}
                Core.app.post(this);
            }
        });
    }

    private static void clearAllObjectives(){
        if(objectivesCleared) return;
        try{
            int count = 0;
            for(int i = 0; i < TechTree.all.size; i++){
                TechNode tn = TechTree.all.get(i);
                // 跳过 SectorPreset 类节点（关卡/星球），只清除方块/物品等可研究内容的 objectives
                if(tn.objectives != null && tn.objectives.size > 0
                    && !(tn.content instanceof mindustry.type.SectorPreset)){
                         tn.objectives.clear();
                         count++;
                }
            }
            objectivesCleared = true;
            Log.info("[BM] Cleared objectives on " + count + " / " + TechTree.all.size + " tech nodes");
        }catch(Throwable t){
            Log.err("[BM] Failed to clear TechNode objectives", t);
        }
    }

    @SuppressWarnings({"unchecked","rawtypes"})
    private static void forceShowLocked(){
        try{
            // 0. 一次性清空所有 TechNode 的 objectives
            //    让 selectable(node) method 永远返回 true
            //    → rebuild() 详情面板显示真实名称/requirements/description
            clearAllObjectives();

            // 1. 正确入口: Vars.ui.research (ResearchDialog)
            Object research = Vars.ui.research;
            if(research == null) return;

            // nodes 字段直接在 ResearchDialog 中, 类型是 public ObjectSet<TechTreeNode>
            var nf = research.getClass().getDeclaredField("nodes");
            nf.setAccessible(true);
            Object nodesObj = nf.get(research);
            if(nodesObj == null) return;

            // 2. 每帧覆盖 node.visible / node.selectable field
            //    让 button.update() 中 imageUp 读取时显示 content.uiIcon 而非 Icon.lock
            if(nodesObj instanceof ObjectSet){
                ObjectSet set = (ObjectSet) nodesObj;
                set.each(node -> {
                    try{
                        Class<?> nc = node.getClass();
                        var visField = nc.getDeclaredField("visible");
                        visField.setAccessible(true);
                        visField.setBoolean(node, true);
                        var selField = nc.getDeclaredField("selectable");
                        selField.setAccessible(true);
                        selField.setBoolean(node, true);
                    }catch(Throwable ignored2){}
                });
            }else if(nodesObj instanceof Seq){
                // 兜底: 某些版本可能用 Seq
                Seq seq = (Seq) nodesObj;
                seq.each(node -> {
                    try{
                        Class<?> nc = node.getClass();
                        var visField = nc.getDeclaredField("visible");
                        visField.setAccessible(true);
                        visField.setBoolean(node, true);
                        var selField = nc.getDeclaredField("selectable");
                        selField.setAccessible(true);
                        selField.setBoolean(node, true);
                    }catch(Throwable ignored2){}
                });
            }else{
                if((tickCounter % 60) == 0){
                    Log.info("[BM] nodes field is unexpected type: " + nodesObj.getClass().getName());
                }
            }

        }catch(Throwable t){
            if((tickCounter % 60) == 0){
                Log.err("[BM] forceShowLocked error", t);
            }
        }
    }
}
