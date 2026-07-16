package yiqiu.util;

import arc.*;
import arc.files.*;
import arc.util.*;
import arc.util.Log.*;

import java.io.*;

/**
 * bettermindustry 启动日志记录器
 *
 * 在 Mod 构造函数中调用 {@link #start()} 即可拦截所有游戏日志
 * 并实时写入文件。每次启动会覆盖上一次的日志文件。
 */
public class loader_log{

    private static boolean started;
    private static Writer writer;
    private static LogHandler previousLogger;

    private static final String LOG_FILE = "startup.log";

    public static void start(){
        if(started) return;
        started = true;

        try{
            Fi logFile = Core.settings.getDataDirectory().child("BM").child(LOG_FILE);
            if(!logFile.parent().exists()){
                logFile.parent().mkdirs();
            }
            writer = logFile.writer(false);

            writer.write("=== bettermindustry 启动日志 ===\n");
            writer.write("时间: " + java.time.LocalDateTime.now() + "\n");
            writer.write("Mindustry 版本: " + mindustry.core.Version.build + "\n");
            writer.write("Java 版本: " + OS.javaVersion + "\n");
            writer.write("内存上限: " + Runtime.getRuntime().maxMemory() / 1024 / 1024 + " MB\n");
            writer.write("CPU 核心: " + OS.cores + "\n");
            writer.write("==============================\n\n");
            writer.flush();

            previousLogger = Log.logger;
            Log.logger = (level, text) -> {
                if(previousLogger != null){
                    previousLogger.log(level, text);
                }
                try{
                    writer.write("[" + level.name() + "] " + Log.removeColors(text) + "\n");
                    writer.flush();
                }catch(IOException ignored){}
            };

            Log.info("[BM] 日志记录已启动 -> " + logFile.absolutePath());

        }catch(IOException e){
            Log.err("[BM] 无法创建日志文件", e);
        }
    }

    public static void stop(){
        if(!started) return;
        started = false;

        try{
            if(writer != null){
                writer.write("\n=== 日志记录结束 ===\n");
                writer.flush();
                writer.close();
                writer = null;
            }
        }catch(IOException e){
            Log.err("[BM] 关闭日志文件失败", e);
        }

        if(previousLogger != null){
            Log.logger = previousLogger;
            previousLogger = null;
        }

        Log.info("[BM] 日志记录已停止");
    }
}
