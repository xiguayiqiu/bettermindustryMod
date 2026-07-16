package yiqiu.pinyin.ui;

import arc.scene.Element;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Log;
import mindustry.Vars;
import mindustry.game.Schematic;
import yiqiu.pinyin.match.MatchEngine;
import java.lang.reflect.Field;

public final class SchematicsAdapter{
    private static volatile Field searchFieldField, searchTextField, rebuildPaneField, selectedTagsField, firstSchematicField;
    private static volatile boolean unavailable;

    private SchematicsAdapter(){}

    public static boolean isSchematicsSearch(TextField field){ return find(field) != null; }

    public static boolean filter(TextField field, String query, MatchEngine.MatchOptions opts){
        return filter(field, query, opts, ScopeTree.capture(field));
    }

    static boolean filter(TextField field, String query, MatchEngine.MatchOptions opts, ScopeTree.Context context){
        Target target = find(field);
        if(target == null || query == null || query.isEmpty() || context == null || !context.isActive(field)) return false;
        String prev = null;
        try{
            prev = (String)searchTextField.get(target.dialog);
            searchTextField.set(target.dialog, "");
            target.rebuild.run();
            Seq<Schematic> candidates = candidates(target);
            ScopeTree located = ScopeTree.locate(field, context);
            ResultScope scope = located == null ? null : new ResultScope(located);
            if(scope == null || !scope.isValid()){ Log.warn("[BM-Pinyin] schematics result table not found"); return false; }
            applyFilter(scope, candidates, query, opts, target);
        }catch(Throwable t){
            Log.warn("[BM-Pinyin] schematics rebuild adapter failed: @", t.getMessage());
            return false;
        }finally{
            try{ searchTextField.set(target.dialog, prev == null ? "" : prev); }catch(Throwable ignored){}
        }
        return true;
    }

    private static Target find(TextField field){
        if(field == null || field.getScene() == null || Vars.ui == null || Vars.ui.schematics == null || Vars.schematics == null) return null;
        if(!ensure()) return null;
        try{
            Object dialog = Vars.ui.schematics;
            if(searchFieldField.get(dialog) != field) return null;
            Object rebuild = rebuildPaneField.get(dialog);
            return rebuild instanceof Runnable ? new Target(dialog, (Runnable)rebuild) : null;
        }catch(Throwable t){ Log.warn("[BM-Pinyin] schematics adapter lookup failed: @", t.getMessage()); unavailable = true; return null; }
    }

    @SuppressWarnings("unchecked")
    private static Seq<Schematic> candidates(Target target) throws IllegalAccessException{
        Seq<Schematic> out = new Seq<>();
        Seq<String> selected = (Seq<String>)selectedTagsField.get(target.dialog);
        boolean hasTags = selected != null && selected.any();
        Seq<Schematic> all = Vars.schematics.all();
        for(int i = 0; i < all.size; i++){
            Schematic s = all.get(i);
            if(s == null) continue;
            if(hasTags && (s.labels == null || !s.labels.containsAll(selected))) continue;
            out.add(s);
        }
        return out;
    }

    private static void applyFilter(ResultScope scope, Seq<Schematic> candidates, String query, MatchEngine.MatchOptions opts, Target target) throws IllegalAccessException{
        Table table = scope.table;
        float scrollY = scope.pane.getScrollY();
        Seq<Cell> cells = table.getCells();
        int available = Math.min(candidates.size, cells.size);
        Element[] actors = new Element[available];
        CellSnapshot[] snapshots = new CellSnapshot[available];
        boolean[] endRows = new boolean[available];
        for(int i = 0; i < available; i++){
            Cell<?> c = cells.get(i);
            actors[i] = c.get(); snapshots[i] = CellSnapshot.capture(c); endRows[i] = c.isEndRow();
        }
        int columns = detectColumns(endRows, available);
        table.clearChildren();
        int matches = 0, displayed = 0, col = 0;
        Schematic first = null;
        for(int i = 0; i < candidates.size; i++){
            Schematic s = candidates.get(i);
            if(s == null || !MatchEngine.accepts(s.name(), query, opts)) continue;
            if(first == null) first = s;
            matches++;
            if(i >= available || actors[i] == null) continue;
            Cell<?> cell = table.add(actors[i]);
            if(snapshots[i] != null) snapshots[i].applyTo(cell);
            cell.colspan(1); col++; displayed++;
            if(col % columns == 0){ table.row(); col = 0; }
        }
        if(col > 0) table.row();
        if(matches == 0) table.add("@none.found").padLeft(54f).padTop(10f);
        firstSchematicField.set(target.dialog, first);
        table.invalidateHierarchy();
        try{ scope.pane.layout(); }catch(Throwable t){ Log.warn("[BM-Pinyin] schematics pane.layout() threw: @", t.getMessage()); }
        scope.pane.setScrollYForce(Math.max(0f, Math.min(scrollY, scope.pane.getMaxY())));
        scope.pane.updateVisualScroll();
    }

    private static int detectColumns(boolean[] endRows, int n){
        int cols = 1, current = 0;
        for(int i = 0; i < n; i++){
            current++;
            if(endRows[i]){ if(current > cols) cols = current; current = 0; }
        }
        if(current > cols) cols = current;
        return Math.max(1, cols);
    }

    private static boolean ensure(){
        if(unavailable) return false;
        if(searchFieldField != null && searchTextField != null && rebuildPaneField != null
            && selectedTagsField != null && firstSchematicField != null) return true;
        synchronized(SchematicsAdapter.class){
            if(searchFieldField != null && searchTextField != null && rebuildPaneField != null
                && selectedTagsField != null && firstSchematicField != null) return true;
            try{
                Class<?> type = Vars.ui.schematics.getClass();
                searchFieldField = field(type, "searchField");
                searchTextField = field(type, "search");
                rebuildPaneField = field(type, "rebuildPane");
                selectedTagsField = field(type, "selectedTags");
                firstSchematicField = field(type, "firstSchematic");
                return true;
            }catch(Throwable t){ Log.warn("[BM-Pinyin] cannot access SchematicsDialog: @", t.getMessage()); unavailable = true; return false; }
        }
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException{
        Field f = type.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private static final class Target{
        final Object dialog; final Runnable rebuild;
        Target(Object d, Runnable r){ dialog = d; rebuild = r; }
    }

    private static final class ResultScope{
        final ScopeTree owner; final ScrollPane pane; final Table table;
        ResultScope(ScopeTree owner){ this.owner = owner; pane = owner.primaryPane(); table = owner.primaryTable(); }
        boolean isValid(){ return owner != null && owner.isValid() && pane != null && pane.getScene() != null && table != null && table.getScene() != null; }
    }
}
