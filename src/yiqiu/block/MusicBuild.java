package yiqiu.block;

import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import mindustry.gen.Building;

/**
 * 音乐方块的 Building 实现。
 * 拆分为顶层类以避免内部类在 Android R8 dexing 时的字节码兼容性问题。
 */
public class MusicBuild extends Building {

    private TextButton musicBtn;

    private void onMusicClick() {
        MusicBlock.toggleMusic();
        if (musicBtn != null) {
            musicBtn.setText((MusicBlock.customMusic != null && MusicBlock.customMusic.isPlaying())
                ? "[scarlet]暂停音乐[]" : "[#7fff7f]播放音乐[]");
        }
    }

    @Override
    public void buildConfiguration(Table table) {
        musicBtn = new TextButton((MusicBlock.customMusic != null && MusicBlock.customMusic.isPlaying())
            ? "[scarlet]暂停音乐[]" : "[#7fff7f]播放音乐[]");
        musicBtn.clicked(this::onMusicClick);
        table.add(musicBtn).size(180f, 55f).pad(4f);
    }
}
