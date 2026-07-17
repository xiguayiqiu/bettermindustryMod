package yiqiu.block;

import arc.*;
import arc.audio.Music;
import arc.files.Fi;
import arc.util.Log;
import mindustry.content.Items;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.meta.BlockGroup;

import static mindustry.Vars.*;

/**
 * 音乐方块 —— 点击后播放/暂停自定义音乐。
 *
 * 属性：
 *   造价：1 铜
 *   血量：100
 *   大小：1x1
 */
public class MusicBlock extends Block {

    static Music customMusic;
    private static boolean musicLoaded;

    public MusicBlock(String name) {
        super(name);

        requirements(Category.effect, ItemStack.with(Items.copper, 1));
        health = 100;
        size = 1;
        researchCost = ItemStack.with(Items.copper, 1);

        configurable = true;
        hasItems = false;
        hasPower = false;
        update = false;
        solid = true;
        destructible = true;
        group = BlockGroup.transportation;

        // 显式设置 building 类型，避免反射查找内部类
        buildType = () -> new MusicBuild();
    }

    @Override
    public void load() {
        super.load();
    }

    /** 从 mod jar 内提取 ogg 文件到数据目录 */
    private static Fi extractMusicFile() {
        Fi dest = Core.settings.getDataDirectory().child("BM").child("temp_music.ogg");
        if (dest.exists()) return dest;

        try {
            // 确保 BM 目录存在
            dest.parent().mkdirs();

            // 从 jar 内的类路径读取资源（mod jar 通过自定义类加载器加载）
            var in = MusicBlock.class.getResourceAsStream("/muisc/kiss-me-more.ogg");
            if (in == null) {
                Log.err("[BM] 音乐方块: 类路径中找不到 /muisc/kiss-me-more.ogg");
                return null;
            }

            // 写入到数据目录
            dest.write(in, false);
            in.close();
            Log.info("[BM] 音乐方块: 已提取 ogg 到 @", dest.path());
            return dest;
        } catch (Exception e) {
            Log.err("[BM] 音乐方块: 提取 ogg 失败", e);
            return null;
        }
    }

    /** 加载音乐文件 */
    public static boolean loadMusic() {
        if (customMusic != null) return true;
        try {
            Fi file = extractMusicFile();
            if (file == null || !file.exists()) {
                Log.err("[BM] 音乐方块: ogg 文件不可用");
                return false;
            }
            customMusic = Core.audio.newMusic(file);
            if (customMusic == null) {
                Log.err("[BM] 音乐方块: Core.audio.newMusic 返回 null");
                return false;
            }
            customMusic.setLooping(true);
            customMusic.setVolume(1f);
            musicLoaded = true;
            Log.info("[BM] 音乐方块: 音乐加载成功");
            return true;
        } catch (Exception e) {
            Log.err("[BM] 音乐方块: 加载失败", e);
            return false;
        }
    }

    /** 切换音乐播放/暂停状态 */
    public static boolean toggleMusic() {
        try {
            if (!musicLoaded && !loadMusic()) {
                if (ui != null) {
                    ui.showInfo("[scarlet]音乐加载失败，请检查 muisc/kiss-me-more.ogg 文件[]");
                }
                return false;
            }

            if (customMusic == null) return false;

            if (customMusic.isPlaying()) {
                customMusic.stop();
                Core.settings.put("musicvol", 100);
                Log.info("[BM] 音乐方块: 音乐已停止");
            } else {
                Core.settings.put("musicvol", 0);
                customMusic.play();
                Log.info("[BM] 音乐方块: 开始播放自定义音乐");
            }
            return true;
        } catch (Exception e) {
            Log.err("[BM] 音乐方块: 播放失败", e);
            if (ui != null) {
                ui.showException("音乐播放失败: " + e.getMessage(), e);
            }
            return false;
        }
    }
}
