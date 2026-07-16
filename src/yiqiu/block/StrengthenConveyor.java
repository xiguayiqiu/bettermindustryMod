package yiqiu.block;

import arc.graphics.g2d.TextureRegion;
import mindustry.content.Items;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.blocks.distribution.Conveyor;
import mindustry.world.meta.*;

/**
 * 强化传送带 —— 比原版传送带更快、更耐用。
 *
 * 速度介于普通传送带(6.5/s)与钛传送带(10/s)之间。
 * 属性：
 *   物品传输速度：8 个/秒
 *   容量：3
 *   造价：5 铜
 */
public class StrengthenConveyor extends Conveyor {

    public StrengthenConveyor(String name) {
        super(name);

        // 基础属性 —— 速度介于普通传送带(0.046)与钛传送带(0.0801)之间
        speed = 0.06f;
        displayedSpeed = 8f;
        health = 80;
        itemCapacity = 3;

        // 花费 5 铜
        requirements(Category.distribution, ItemStack.with(Items.copper, 3));

        // 初始科技研究花费（与造价一致）
        researchCost = ItemStack.with(Items.copper, 3);

        // 在所有环境下可用
        envEnabled |= Env.any;
    }

    @Override
    public void load() {
        // super.load() 会调用 Block.load()，其中：
        //   1) region = Core.atlas.find(name) — name=bettermindustry-strengthen-conveyorbelt,
        //      没有对应独立贴图，返回默认错误纹理，忽略即可。
        //   2) ContentRegions.loadRegions(this) — @Load 注解生成的代码，
        //      根据 content.name + "-{blend}-{frame}" 查找贴图，
        //      现在文件名已全小写，与 content.name 大小写一致，可以正确匹配。
        super.load();

        // 用第一帧作为图标/回退纹理，避免显示 "No"
        region = regions[0][0];

        // 只提供了 blend 0-4 的贴图，blend 5/6 用 blend 4 的纹理
        for (int frame = 0; frame < 4; frame++) {
            regions[5][frame] = regions[4][frame];
            regions[6][frame] = regions[4][frame];
        }
    }

    @Override
    public TextureRegion[] icons() {
        return new TextureRegion[]{regions[0][0]};
    }

    @Override
    public boolean canReplace(Block other) {
        // 允许替换原版传送带，但不能替换强化传送带自身和堆叠传送带
        return (other instanceof Conveyor && !(other instanceof StrengthenConveyor))
            || super.canReplace(other);
    }
}
