package yiqiu.pinyin.ui;

import arc.*;
import arc.func.*;
import arc.scene.*;
import arc.scene.event.*;
import arc.scene.ui.*;
import arc.struct.*;
import arc.util.*;
import arc.util.pooling.*;
import mindustry.*;
import mindustry.gen.*;
import yiqiu.pinyin.*;
import yiqiu.pinyin.match.*;
import java.util.Locale;

public final class FieldDispatcher{
    private final ObjectSet<TextField> patched = new ObjectSet<>();
    private final ObjectMap<TextField, Seq<ChangeListener>> vanillas = new ObjectMap<>();
    private final ObjectMap<TextField, Timer.Task> debounces = new ObjectMap<>();
    private final ObjectMap<TextField, ChangeListener> proxies = new ObjectMap<>();
    private final ObjectSet<String> bundleSearchKeys = new ObjectSet<>();
    private boolean keysCollected;

    private static final class EventProv implements Prov<ChangeListener.ChangeEvent>{
        @Override public ChangeListener.ChangeEvent get(){ return new ChangeListener.ChangeEvent(); }
    }
    private static final EventProv eventProv = new EventProv();

    @SuppressWarnings("unchecked")
    private static <T> T obtainEvent(Class<T> type, Prov<T> prov){
        return Pools.obtain(type, prov);
    }

    public void scan(){
        if(Vars.headless || Core.scene == null || Core.scene.root == null) return;
        if(!keysCollected) collectBundleKeys();

        Seq<TextField> stale = new Seq<>();
        for(TextField f : patched){ if(f == null || f.getScene() == null) stale.add(f); }
        for(TextField f : stale){ cancelDebounce(f); proxies.remove(f); patched.remove(f); vanillas.remove(f); }

        Seq<TextField> all = new Seq<>();
        collect(Core.scene.root, all);
        for(TextField f : all){
            if(patched.contains(f)) continue;
            if(!isSearchField(f)) continue;
            attach(f);
            patched.add(f);
        }
    }

    private void collectBundleKeys(){
        keysCollected = true;
        if(Core.bundle == null) return;
        try{
            String[] hints = {"players.search", "editor.search", "save.search", "schematic.search", "search",
                "locales.searchname", "locales.searchvalue", "locales.searchlocale"};
            for(String k : hints){ String v = Core.bundle.get(k, ""); if(v != null && !v.isEmpty()) bundleSearchKeys.add(v); }
        }catch(Throwable ignored){}
    }

    private static void collect(Element root, Seq<TextField> out){
        if(root instanceof TextField) out.add((TextField)root);
        if(root instanceof Group){
            Seq<Element> ch = ((Group)root).getChildren();
            for(int i = 0; i < ch.size; i++) collect(ch.get(i), out);
        }
    }

    private boolean isSearchField(TextField f){
        if(f == null) return false;
        if(f.name != null && f.name.toLowerCase(Locale.ROOT).contains("search")) return true;
        String msg = f.getMessageText();
        if(msg != null && !msg.isEmpty() && bundleSearchKeys.contains(msg)) return true;
        return hasZoomSibling(f);
    }

    private static boolean hasZoomSibling(TextField f){
        if(f == null || f.parent == null) return false;
        Seq<Element> sib = f.parent.getChildren();
        for(int i = 0; i < sib.size; i++){
            Element e = sib.get(i);
            if(e instanceof Image && ((Image)e).getDrawable() == Icon.zoom) return true;
        }
        return false;
    }

    private void attach(TextField field){
        Seq<EventListener> listeners = field.getListeners();
        Seq<ChangeListener> existing = new Seq<>();
        for(int i = 0; i < listeners.size; i++){
            EventListener l = listeners.get(i);
            if(l == proxies.get(field)) continue;
            if(l instanceof ChangeListener) existing.add((ChangeListener)l);
        }
        if(existing.isEmpty()) return;
        for(ChangeListener cl : existing) field.removeListener(cl);
        vanillas.put(field, existing);

        proxies.put(field, new ProxyListener(this, field));
        field.addListener(proxies.get(field));
    }

    private static final class ProxyListener extends ChangeListener{
        private final FieldDispatcher dispatcher; private final TextField field;
        ProxyListener(FieldDispatcher d, TextField f){ dispatcher = d; field = f; }
        @Override public void changed(ChangeEvent event, Element actor){
            if(actor == field) dispatcher.onChange(field);
        }
    }

    private void onChange(TextField field){
        cancelDebounce(field);
        if(!Core.settings.getBool(PinyinSearchMod.keyEnabled, true)){ fireVanilla(field); return; }
        ScopeTree.Context context = ScopeTree.capture(field);
        int delay = Math.max(0, Core.settings.getInt(PinyinSearchMod.keyDelayMs, PinyinSearchMod.defaultDelayMs));
        if(delay <= 0){ runFilter(field, context); return; }

        String scheduledText = field.getText();
        DebounceTask task = new DebounceTask(this, field, context, scheduledText);
        debounces.put(field, Timer.schedule(task, delay / 1000f));
    }

    private static final class DebounceTask extends Timer.Task{
        private final FieldDispatcher dispatcher;
        private final TextField field;
        private final ScopeTree.Context context;
        private final String scheduledText;

        DebounceTask(FieldDispatcher d, TextField f, ScopeTree.Context c, String s){
            dispatcher = d; field = f; context = c; scheduledText = s;
        }

        @Override
        public void run(){
            Timer.Task taskRef = this;
            Core.app.post(() -> {
                if(dispatcher.debounces.get(field) != taskRef) return;
                dispatcher.debounces.remove(field);
                String currentText = field.getText();
                boolean sameText = scheduledText == null ? currentText == null : scheduledText.equals(currentText);
                if(!sameText || context == null || !context.isActive(field)){ dispatcher.fireVanilla(field); return; }
                dispatcher.runFilter(field, context);
            });
        }
    }

    private void runFilter(TextField field, ScopeTree.Context context){
        Seq<ChangeListener> list = vanillas.get(field);
        if(list == null || list.isEmpty()) return;

        String typed = field.getText();
        if(typed == null) typed = "";
        if(typed.isEmpty() || !shouldUsePinyinSearch(typed)){ fireListeners(field, list); return; }
        if(context == null || !context.isActive(field)){ fireListeners(field, list); return; }

        MatchEngine.MatchOptions opts = new MatchEngine.MatchOptions(
            Core.settings.getBool(PinyinSearchMod.keyFuzzy, true),
            Core.settings.getBool(PinyinSearchMod.keyInitials, true),
            Core.settings.getBool(PinyinSearchMod.keyHeteronym, true)
        );

        if(SectorListAdapter.isSectorSearch(field)){
            fireListeners(field, list);
            SectorListAdapter.filter(field, typed, opts);
            return;
        }

        if(SchematicsAdapter.isSchematicsSearch(field)){
            if(!SchematicsAdapter.filter(field, typed, opts, context)) fireListeners(field, list);
            return;
        }

        String prev = FieldTextProxy.swap(field, "");
        if(field.getText() != null && !field.getText().isEmpty()){ fireListeners(field, list); return; }
        try{ fireListeners(field, list);
        }finally{ FieldTextProxy.swap(field, prev != null ? prev : typed); }

        if(!context.isActive(field)){ fireListeners(field, list); return; }

        ScopeTree scope = ScopeTree.locate(field, context);
        if(scope == null || !scope.isValid()){ fireListeners(field, list); return; }

        try{ scope.postFilter(typed, opts);
        }catch(Throwable t){ Log.warn("[BM-Pinyin] post filter failed: @", t.getMessage()); fireListeners(field, list); }
    }

    private static boolean shouldUsePinyinSearch(String query){
        if(query == null || query.isEmpty()) return false;
        for(int i = 0; i < query.length(); i++){
            char c = query.charAt(i);
            if(Character.isLetter(c) || PinyinIndex.isCjk(c)) return true;
        }
        return false;
    }

    private void fireVanilla(TextField field){
        Seq<ChangeListener> list = vanillas.get(field);
        if(list != null) fireListeners(field, list);
    }

    private void fireListeners(TextField field, Seq<ChangeListener> list){
        for(int i = 0; i < list.size; i++){
            try{
                ChangeListener.ChangeEvent ev = obtainEvent(ChangeListener.ChangeEvent.class, eventProv);
                ev.targetActor = field;
                list.get(i).changed(ev, field);
                Pools.free(ev);
            }catch(Throwable t){ Log.warn("[BM-Pinyin] vanilla listener threw: @", t.getMessage()); }
        }
    }

    private void cancelDebounce(TextField field){
        Timer.Task t = debounces.remove(field);
        if(t != null) t.cancel();
    }
}
