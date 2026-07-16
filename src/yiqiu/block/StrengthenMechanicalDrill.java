package yiqiu.block;

import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.blocks.production.Drill;
import mindustry.world.meta.*;

/**
 * 强化机械钻机 —— 速度介于机械钻机与气动钻头之间。
 *
 * 属性：
 *   挖掘等级：2（与机械钻机相同）
 *   挖掘时间：500（机械钻机600，气动钻头400）
 *   血量：160
 *   大小：2x2
 *   造价：12 铜（与机械钻机相同）
 *   研究：10 铜 + 10 铅
 */
public class StrengthenMechanicalDrill extends Drill {

    public StrengthenMechanicalDrill(String name) {
        super(name);

        requirements(Category.production, ItemStack.with(Items.copper, 12, Items.lead, 2));
        tier = 2;
        drillTime = 500;
        size = 2;
        health = 160;
        // 与机械钻机一样不能在太空工作
        envEnabled ^= Env.space;
        researchCost = ItemStack.with(Items.copper, 10, Items.lead, 10);

        consumeLiquid(Liquids.water, 0.05f).boost();
    }
}
