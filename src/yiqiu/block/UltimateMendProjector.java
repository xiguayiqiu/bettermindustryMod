package yiqiu.block;

import arc.*;
import arc.graphics.Color;
import mindustry.content.Items;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.blocks.defense.MendProjector;

import static mindustry.Vars.tilesize;

/**
 * 终极修复投影 —— 超远程、高速修复建筑。
 */
public class UltimateMendProjector extends MendProjector {

    public UltimateMendProjector(String name) {
        super(name);

        requirements(Category.effect, ItemStack.with(
            Items.lead, 600,
            Items.titanium, 150,
            Items.silicon, 240,
            Items.copper, 300,
            yiqiu.bettermindustryMod.tripleAlloy, 100
        ));

        researchCost = ItemStack.with(
            Items.lead, 600,
            Items.titanium, 150,
            Items.silicon, 240,
            Items.copper, 300,
            yiqiu.bettermindustryMod.tripleAlloy, 10
        );

        size = 2;
        health = 1000;
        range = 25 * tilesize;
        reload = 250f;
        healPercent = 28f;
        scaledHealth = 80;

        baseColor = Color.valueOf("7f5af0");
        phaseColor = Color.valueOf("a78bfa");

        consumePower(2f);

        phaseBoost = 44.8f;
        phaseRangeBoost = 50f;
        consumeItem(Items.phaseFabric).boost();
    }

    @Override
    public void load() {
        super.load();
        if(!topRegion.found()){
            topRegion = Core.atlas.find("bettermindustry-" + name + "-top");
        }
    }
}
