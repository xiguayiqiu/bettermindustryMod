package yiqiu.block;

import mindustry.content.*;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.blocks.production.GenericCrafter;
import mindustry.world.draw.*;

/**
 * 三重合金压缩机 —— 将硅、石墨与爆炸混合物压缩为三重合金。
 *
 * 配方：500 硅 + 500 石墨 + 500 爆炸混合物 → 1 三重合金
 * 电力：40/s
 * 容量：4500
 * 血量：600
 * 大小：3x3
 */
public class TripleAlloyCompressor extends GenericCrafter {

    public TripleAlloyCompressor(String name) {
        super(name);

        requirements(Category.crafting, ItemStack.with(Items.silicon, 900, Items.graphite, 900, Items.titanium, 1000));
        size = 3;
        health = 600;
        itemCapacity = 4500;
        craftTime = 600f; // 10 秒 = 600 ticks

        // 爆炸特性：核弹级（配合内部爆炸混合物产生巨大爆炸）
        baseExplosiveness = 50f;
        baseShake = 20f;

        // 产出
        outputItem = new ItemStack(yiqiu.bettermindustryMod.tripleAlloy, 1);

        // 电力消耗（40/s = 0.667/tick）
        consumePower(0.667f);

        // 配方输入
        consumeItems(ItemStack.with(Items.silicon, 500, Items.graphite, 500, Items.blastCompound, 500));

        // 液体加成：水 1.5x，冷却液 2x
        consumeLiquid(Liquids.water, 0.1f).boost();
        consumeLiquid(Liquids.cryofluid, 0.1f).boost();

        // 绘制器：主体 + 旋转叶片 + 顶盖
        drawer = new DrawMulti(
            new DrawDefault(),
            new DrawRegion("-rotator", 2f, true),
            new DrawRegion("-top")
        );

        // 研究花费
        researchCost = ItemStack.with(Items.silicon, 9000, Items.graphite, 9000, Items.blastCompound, 9000, Items.titanium, 9000);
    }
}
