package yiqiu.pinyin;

import arc.*;
import arc.util.*;
import mindustry.game.EventType.*;
import yiqiu.pinyin.ui.*;

public class PinyinSearchMod{
    public static final String keyEnabled   = "bm-pinyin-enabled";
    public static final String keyFuzzy     = "bm-pinyin-fuzzy";
    public static final String keyInitials  = "bm-pinyin-initials";
    public static final String keyHeteronym = "bm-pinyin-heteronym";
    public static final String keyDelayMs   = "bm-pinyin-delay-ms";
    public static final int defaultDelayMs = 120;

    private static final FieldDispatcher dispatcher = new FieldDispatcher();
    private static boolean built;

    public static void build(){
        if(built) return;
        built = true;

        Core.settings.defaults(keyEnabled, true);
        Core.settings.defaults(keyFuzzy, true);
        Core.settings.defaults(keyInitials, true);
        Core.settings.defaults(keyHeteronym, true);
        Core.settings.defaults(keyDelayMs, defaultDelayMs);

        Events.run(Trigger.update, () -> {
            if(!Core.settings.getBool(keyEnabled, true)) return;
            dispatcher.scan();
        });

        Log.info("[BM-Pinyin] 拼音搜索模块已安装");
    }
}
