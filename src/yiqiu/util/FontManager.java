package yiqiu.util;

import arc.*;
import arc.files.*;
import arc.util.*;

/**
 * 字体管理器
 */
public class FontManager{

    public static final String FONT_DIR = "BM/Fonts";

    /** 启用内置楷体字体的开关(由 BM 设置控制,JS 端读取) */
    public static final String KAI_FONT_ENABLED_KEY = "bm-kai-font-enabled";

    /** 启动时确保目录存在 */
    public static void ensureDirectories(){
        try{
            Fi fontsDir = Core.settings.getDataDirectory().child(FONT_DIR);
            if(!fontsDir.exists()){
                fontsDir.mkdirs();
                Log.info("[BM] 已创建目录: @", fontsDir.absolutePath());
            }
        }catch(Throwable t){
            Log.err("[BM] 创建目录失败", t);
        }
    }

    public static String getCurrentFontDisplayName(){
        if(Core.settings.getBool(KAI_FONT_ENABLED_KEY, false)){
            return "[#7fff7f]内置楷体(已启用)[]";
        }
        return "默认字体";
    }

    public static boolean isKaiFontEnabled(){
        return Core.settings.getBool(KAI_FONT_ENABLED_KEY, false);
    }

    public static void setKaiFontEnabled(boolean enabled){
        Core.settings.put(KAI_FONT_ENABLED_KEY, enabled);
        // 手动保存(autosave 不会被自动调)
        try{
            Core.settings.manualSave();
        }catch(Throwable t){
            Log.err("[BM] manualSave 失败", t);
        }
        Log.info("[BM] 内置楷体字体: @", enabled ? "已启用" : "已关闭");
    }

    /** 获取 BM 数据目录(Core.settings.getDataDirectory().child("BM")) */
    public static Fi getBMDir(){
        return Core.settings.getDataDirectory().child("BM");
    }
}
