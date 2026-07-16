package yiqiu.block;

import mindustry.content.Items;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.blocks.defense.Wall;

/**
 * 强化铜墙。
 *
 * 属性：
 *   血量：400
 *   大小：1x1
 *   造价：6 铜 + 3 铅
 */
public class StrengthenCopperWall extends Wall {

    public StrengthenCopperWall(String name) {
        super(name);

        requirements(Category.defense, ItemStack.with(Items.copper, 6, Items.lead, 3));
        health = 400;
        researchCost = ItemStack.with(Items.copper, 20, Items.lead, 20);
    }
}
