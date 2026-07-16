package yiqiu.block;

import mindustry.content.Items;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.blocks.storage.StorageBlock;

/**
 * 一般容器 —— 大容量存储容器。
 *
 * 属性：
 *   容量：500
 *   血量：440
 *   大小：2x2
 *   造价：100 钛 + 100 硅
 */
public class GeneralContainer extends StorageBlock {

    public GeneralContainer(String name) {
        super(name);

        requirements(Category.effect, ItemStack.with(Items.titanium, 100, Items.silicon, 100));
        size = 2;
        itemCapacity = 500;
        health = 440;
        researchCost = ItemStack.with(Items.titanium, 4000, Items.silicon, 4000);
    }
}
