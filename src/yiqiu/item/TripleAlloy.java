package yiqiu.item;

import arc.graphics.Color;
import mindustry.type.Item;

/**
 * 三重合金 —— 由多种金属融合而成的坚固合金。
 */
public class TripleAlloy extends Item {

    public TripleAlloy() {
        super("triple-alloy", Color.valueOf("d4a373"));

        cost = 1.5f;
        radioactivity = 0.1f;
    }
}
