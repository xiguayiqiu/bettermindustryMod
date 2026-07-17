package yiqiu.block;

import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.content.Liquids;
import mindustry.content.StatusEffects;
import mindustry.entities.Units;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.consumers.ConsumePower;
import mindustry.world.meta.BlockGroup;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;

import static mindustry.Vars.*;

/**
 * 眩晕者 —— 在范围内使敌方兵种眩晕（无法移动和攻击）。
 *
 * 属性：
 *   造价：100 钛 + 20 硅 + 40 石墨
 *   血量：100
 *   大小：2x2
 *   电力消耗：90
 *   电力存储：180
 *   熔渣存储：320
 *   影响范围：6格
 *   熔渣强化范围：8.5格
 *   脉冲间隔：3秒
 *   眩晕时间：2.5秒
 *   熔渣强化眩晕：3.5秒
 *   熔渣消耗：2/秒
 */
public class VertigoBlock extends Block {

    public float range = 6f * tilesize;
    public float slagRangeBoost = 2.5f * tilesize;
    public float reload = 3f * 60f;
    public float stunDuration = 2.5f * 60f;
    public float slagStunDuration = 3.5f * 60f;
    public Color baseColor = Color.valueOf("87ceeb"); // 淡蓝色
    public Color phaseColor = Color.valueOf("b0e0e6");

    public VertigoBlock(String name) {
        super(name);

        requirements(Category.turret, ItemStack.with(
            mindustry.content.Items.titanium, 100,
            mindustry.content.Items.silicon, 20,
            mindustry.content.Items.graphite, 40
        ));
        researchCost = ItemStack.with(
            mindustry.content.Items.titanium, 1000,
            mindustry.content.Items.silicon, 2000,
            mindustry.content.Items.graphite, 4000
        );
        health = 100;
        size = 2;

        configurable = false;
        hasPower = true;
        hasItems = false;
        hasLiquids = true;
        update = true;
        solid = true;
        group = BlockGroup.projectors;
        emitLight = true;
        lightRadius = 60f;
        drawCached = true;

        consumePower(1.5f); // 90/s（每tick消耗）
        // 同时设置电力存储容量显示
        consPower.capacity = 180f;
        consumeLiquid(Liquids.slag, 2f).boost();
        liquidCapacity = 360f;

        buildType = () -> new VertigoBuild();
    }

    @Override
    public void load() {
        super.load();
    }

    @Override
    public void setStats() {
        stats.timePeriod = reload / 60f;
        super.setStats();

        stats.add(Stat.range, range / tilesize, StatUnit.blocks);
        stats.add(Stat.reload, reload / 60f, StatUnit.seconds);
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid) {
        super.drawPlace(x, y, rotation, valid);
        Drawf.dashCircle(x * tilesize + offset, y * tilesize + offset, range, baseColor);
    }

    /** 眩晕者方块的 Building 实现 */
    public static class VertigoBuild extends Building {
        public float heat;
        public float charge;
        public float smoothEfficiency;
        public boolean slagBoosted;

        @Override
        public void updateTile() {
            smoothEfficiency = Mathf.lerpDelta(smoothEfficiency, efficiency, 0.08f);
            heat = Mathf.lerpDelta(heat, efficiency > 0 ? 1f : 0f, 0.08f);

            slagBoosted = optionalEfficiency > 0;

            charge += heat * Time.delta;

            VertigoBlock vBlock = (VertigoBlock) block;
            if (charge >= vBlock.reload) {
                charge = 0f;

                float realRange = vBlock.range + (slagBoosted ? vBlock.slagRangeBoost : 0f);
                float realStun = slagBoosted ? vBlock.slagStunDuration : vBlock.stunDuration;

                Units.nearbyEnemies(team, x, y, realRange, unit -> {
                    if (unit.isValid() && unit.within(x, y, realRange + unit.hitSize / 2f)) {
                        unit.apply(StatusEffects.unmoving, realStun);
                        unit.apply(StatusEffects.disarmed, realStun);
                    }
                });
            }
        }

        @Override
        public void drawLight() {
            VertigoBlock vBlock = (VertigoBlock) block;
            Drawf.light(x, y, vBlock.lightRadius * smoothEfficiency, vBlock.baseColor, 0.7f * smoothEfficiency);
        }

        @Override
        public void drawSelect() {
            VertigoBlock vBlock = (VertigoBlock) block;
            float realRange = vBlock.range + (slagBoosted ? vBlock.slagRangeBoost : 0f);
            Drawf.dashCircle(x, y, realRange, vBlock.baseColor);
        }

        @Override
        public void drawCached() {
            super.draw();
        }

        @Override
        public void draw() {
            VertigoBlock vBlock = (VertigoBlock) block;

            // 绘制方块本体
            Draw.rect(vBlock.region, x, y);

            // 脉冲光圈特效（类似修复投影）
            float f = 1f - (Time.time / 100f) % 1f;

            Draw.color(vBlock.baseColor, vBlock.phaseColor, slagBoosted ? 1f : 0f);
            Draw.alpha(heat * Mathf.absin(Time.time, 50f / Mathf.PI2, 1f) * 0.5f);
            Lines.stroke((2f * f + 0.2f) * heat);
            Lines.square(x, y, Math.min(1f + (1f - f) * vBlock.size * tilesize / 2f, vBlock.size * tilesize / 2f));
            Draw.reset();
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.f(heat);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            heat = read.f();
        }
    }
}
