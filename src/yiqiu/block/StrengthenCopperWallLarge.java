package yiqiu.block;

import mindustry.content.Items;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.blocks.defense.Wall;

/**
 * 大型强化铜墙。
 *
 * 属性：
 *   血量：1500
 *   大小：2x2
 *   造价：24 铜 + 12 铅
 */
public class StrengthenCopperWallLarge extends Wall {

    public StrengthenCopperWallLarge(String name) {
        super(name);

        requirements(Category.defense, ItemStack.with(Items.copper, 24, Items.lead, 12));
        size = 2;
        health = 1500;
        researchCost = ItemStack.with(Items.copper, 700, Items.lead, 700);
    }
}
