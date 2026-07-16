package yiqiu.pinyin.ui;

import arc.*;
import arc.scene.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import yiqiu.pinyin.match.*;

public final class ScopeTree{
    static final int MODE_LIST = 0;
    static final int MODE_GRID = 1;
    static final int MODE_SECTIONED = 2;

    private final TextField field;
    private final Context context;
    private final Seq<SubScope> scopes;

    private ScopeTree(TextField field, Context context, Seq<SubScope> scopes){
        this.field = field; this.context = context; this.scopes = scopes;
    }

    public boolean isValid(){
        if(context == null || !context.isActive(field) || scopes == null || scopes.isEmpty()) return false;
        for(int i = 0; i < scopes.size; i++){ if(!scopes.get(i).isValid()) return false; }
        return true;
    }

    public static ScopeTree locate(TextField field){ return locate(field, capture(field)); }

    static ScopeTree locate(TextField field, Context context){
        if(context == null || !context.isActive(field)) return null;
        Seq<ScrollPane> allPanes = new Seq<>();
        collectScrollPanes(context.root, context.root, allPanes);

        Candidate best = null;
        boolean ambiguous = false;
        for(int i = 0; i < allPanes.size; i++){
            Candidate candidate = candidate(field, context, allPanes.get(i));
            if(candidate == null) continue;
            if(best == null || candidate.score.isBetterThan(best.score)){ best = candidate; ambiguous = false; }
            else if(!best.score.isBetterThan(candidate.score)){ ambiguous = true; }
        }
        if(best == null || ambiguous) return null;

        Seq<SubScope> scopes = new Seq<>();
        scopes.add(new SubScope(field, context, best.pane, best.table, detectMode(best.table)));
        return new ScopeTree(field, context, scopes);
    }

    public void postFilter(String query, MatchEngine.MatchOptions opts){
        if(query == null || query.isEmpty()) return;
        SearchTextExtractor.invalidate();
        for(int i = 0; i < scopes.size; i++) scopes.get(i).filter(query, opts);
    }

    public ScrollPane primaryPane(){ return scopes == null || scopes.isEmpty() ? null : scopes.first().pane; }
    public Table primaryTable(){ return scopes == null || scopes.isEmpty() ? null : scopes.first().table; }

    static Context capture(TextField field){
        if(field == null || field.getScene() == null || field.parent == null) return null;
        Scene scene = field.getScene();
        Group originalParent = field.parent;
        Group boundary = globalBoundaryOf(field);
        Group cursor = originalParent;

        for(int depth = 0; depth < 32 && cursor != null; depth++){
            if(cursor instanceof Dialog) return new Context(scene, cursor, (Dialog)cursor, originalParent, boundary);
            if(cursor == scene.root || cursor == boundary) break;
            cursor = cursor.parent;
        }
        cursor = originalParent;
        for(int depth = 0; depth < 32 && cursor != null && cursor != scene.root && cursor != boundary; depth++){
            Context ctx = new Context(scene, cursor, null, originalParent, boundary);
            if(hasQualifyingScrollPane(field, ctx)) return ctx;
            cursor = cursor.parent;
        }
        return null;
    }

    private static boolean hasQualifyingScrollPane(TextField field, Context context){
        Seq<ScrollPane> panes = new Seq<>();
        collectScrollPanes(context.root, context.root, panes);
        for(int i = 0; i < panes.size; i++){ if(candidate(field, context, panes.get(i)) != null) return true; }
        return false;
    }

    private static void collectScrollPanes(Element root, Element current, Seq<ScrollPane> out){
        if(current != root){
            if(current instanceof Dialog) return;
            if(current instanceof Group && isGlobalUiGroup((Group)current)) return;
        }
        if(current instanceof ScrollPane) out.add((ScrollPane)current);
        if(current instanceof Group){
            Seq<Element> ch = ((Group)current).getChildren();
            for(int i = 0; i < ch.size; i++) collectScrollPanes(root, ch.get(i), out);
        }
    }

    private static Candidate candidate(TextField field, Context context, ScrollPane pane){
        if(field == null || context == null || pane == null || pane.getScene() != context.scene) return null;
        if(!context.owns(field) || !context.owns(pane)) return null;
        Element widget = pane.getWidget();
        if(!(widget instanceof Table)) return null;
        Table table = (Table)widget;
        if(table.getScene() != context.scene || table.getCells().isEmpty()) return null;
        if(pane.getHeight() > 0f && pane.getHeight() < 80f) return null;
        if(field.isDescendantOf(pane) || field.isDescendantOf(table)) return null;
        if(!context.owns(table)) return null;
        if(!hasActor(table)) return null;
        StructuralScore score = StructuralScore.between(field, pane, context.root);
        return score == null ? null : new Candidate(pane, table, score);
    }

    private static boolean hasActor(Table table){
        Seq<Cell> cells = table.getCells();
        for(int i = 0; i < cells.size; i++){ if(cells.get(i).get() != null) return true; }
        return false;
    }

    private static boolean isGlobalUiGroup(Group group){
        return Vars.ui != null && (group == Vars.ui.menuGroup || group == Vars.ui.hudGroup);
    }

    private static Group globalBoundaryOf(Element element){
        Element cursor = element;
        while(cursor != null){
            if(cursor instanceof Group && isGlobalUiGroup((Group)cursor)) return (Group)cursor;
            cursor = cursor.parent;
        }
        return null;
    }

    private static boolean visibleThrough(Element element, Element root){
        Element cursor = element;
        while(cursor != null){
            if(!cursor.visible) return false;
            if(cursor == root) return true;
            cursor = cursor.parent;
        }
        return false;
    }

    private static int detectMode(Table t){
        Seq<Cell> cells = t.getCells();
        if(cells.isEmpty()) return MODE_LIST;
        int actorCount = 0, buttonCount = 0, tableCount = 0;
        boolean hasMultiCellRow = false;
        for(int i = 0; i < cells.size; i++){
            Element actor = cells.get(i).get();
            if(actor != null){
                actorCount++;
                if(actor instanceof Button) buttonCount++;
                if(actor instanceof Table) tableCount++;
            }
            if(i < cells.size - 1 && !cells.get(i).isEndRow()) hasMultiCellRow = true;
        }
        if(buttonCount > 0 && buttonCount * 2 >= Math.max(1, actorCount)) return MODE_GRID;
        if(hasMultiCellRow) return MODE_GRID;
        if(tableCount > 0 && !hasMultiCellRow) return MODE_SECTIONED;
        if(tableCount > actorCount / 2) return MODE_SECTIONED;
        return MODE_LIST;
    }

    static final class Context{
        final Scene scene; final Group root; final Dialog dialog; final Group originalParent; final Group boundary;
        Context(Scene scene, Group root, Dialog dialog, Group originalParent, Group boundary){
            this.scene = scene; this.root = root; this.dialog = dialog;
            this.originalParent = originalParent; this.boundary = boundary;
        }
        boolean owns(Element element){
            if(element == null || scene == null || root == null) return false;
            if(element.getScene() != scene || root.getScene() != scene) return false;
            if(root == scene.root || isGlobalUiGroup(root) || !element.isDescendantOf(root)) return false;
            if(boundary != null){
                if(!isGlobalUiGroup(boundary) || boundary.getScene() != scene) return false;
                return root != boundary && root.isDescendantOf(boundary) && element.isDescendantOf(boundary);
            }
            return globalBoundaryOf(root) == null && globalBoundaryOf(element) == null;
        }
        boolean isActive(TextField field){
            if(field == null || scene == null || root == null || originalParent == null) return false;
            if(Core.scene != scene || field.parent != originalParent || !owns(field)) return false;
            if(!visibleThrough(field, root)) return false;
            Element keyboard = scene.getKeyboardFocus();
            Element scroll = scene.getScrollFocus();
            if(dialog != null){
                if(!dialog.isShown()) return false;
                return (keyboard != null && keyboard.isDescendantOf(dialog))
                    || (scroll != null && scroll.isDescendantOf(dialog));
            }
            return keyboard != null && keyboard.isDescendantOf(root);
        }
    }

    private static final class Candidate{
        final ScrollPane pane; final Table table; final StructuralScore score;
        Candidate(ScrollPane pane, Table table, StructuralScore score){
            this.pane = pane; this.table = table; this.score = score;
        }
    }

    private static final class StructuralScore{
        final int fieldDepth, totalDepth, directionPenalty, siblingGap;
        StructuralScore(int fd, int td, int dp, int sg){
            fieldDepth = fd; totalDepth = td; directionPenalty = dp; siblingGap = sg;
        }

        boolean isBetterThan(StructuralScore o){
            if(o == null) return true;
            if(fieldDepth != o.fieldDepth) return fieldDepth < o.fieldDepth;
            if(totalDepth != o.totalDepth) return totalDepth < o.totalDepth;
            if(directionPenalty != o.directionPenalty) return directionPenalty < o.directionPenalty;
            return siblingGap < o.siblingGap;
        }

        static StructuralScore between(Element field, Element pane, Group root){
            Element common = field;
            int fieldDepth = 0;
            while(common != null && !pane.isDescendantOf(common)){ common = common.parent; fieldDepth++; }
            if(common == null || !common.isDescendantOf(root)) return null;
            int paneDepth = 0;
            Element cursor = pane;
            while(cursor != null && cursor != common){ cursor = cursor.parent; paneDepth++; }
            if(cursor != common) return null;
            int directionPenalty = 0, siblingGap = 0;
            if(common instanceof Group){
                Element fieldBranch = branchBelow(field, common);
                Element paneBranch = branchBelow(pane, common);
                if(fieldBranch != null && paneBranch != null && fieldBranch != paneBranch){
                    Seq<Element> children = ((Group)common).getChildren();
                    int fi = children.indexOf(fieldBranch, true);
                    int pi = children.indexOf(paneBranch, true);
                    if(fi >= 0 && pi >= 0){
                        directionPenalty = pi >= fi ? 0 : 1;
                        siblingGap = Math.abs(pi - fi);
                    }
                }
            }
            return new StructuralScore(fieldDepth, fieldDepth + paneDepth, directionPenalty, siblingGap);
        }

        private static Element branchBelow(Element element, Element ancestor){
            Element cursor = element, branch = element;
            while(cursor != null && cursor != ancestor){ branch = cursor; cursor = cursor.parent; }
            return cursor == ancestor ? branch : null;
        }
    }

    private static final class SubScope{
        final TextField field; final Context context; final ScrollPane pane; final Table table; final int mode;
        SubScope(TextField field, Context context, ScrollPane pane, Table table, int mode){
            this.field = field; this.context = context; this.pane = pane; this.table = table; this.mode = mode;
        }

        boolean isValid(){
            return context != null && context.isActive(field) && pane != null && context.owns(pane)
                && table != null && context.owns(table) && !field.isDescendantOf(table);
        }

        void filter(String query, MatchEngine.MatchOptions opts){
            if(!isValid()) return;
            float scrollY = pane.getScrollY();
            int matches = filterTable(table, mode, query, opts);
            if(matches == 0) table.add("@none.found").padLeft(54f).padTop(10f);
            table.invalidateHierarchy();
            try{ pane.layout(); }catch(Throwable t){ Log.warn("[BM-Pinyin] pane.layout() threw: @", t.getMessage()); }
            pane.setScrollYForce(Math.max(0f, Math.min(scrollY, pane.getMaxY())));
            pane.updateVisualScroll();
        }

        private int filterTable(Table target, int layout, String query, MatchEngine.MatchOptions opts){
            if(target == null || target == context.root || field.isDescendantOf(target)) return 0;
            if(target == table && !context.owns(target)) return 0;
            Seq<Cell> cells = target.getCells();
            if(cells.isEmpty()) return 0;
            int n = cells.size;
            Element[] actors = new Element[n];
            CellSnapshot[] snaps = new CellSnapshot[n];
            boolean[] endRows = new boolean[n];
            for(int i = 0; i < n; i++){
                Cell<?> c = cells.get(i);
                actors[i] = c.get(); snaps[i] = CellSnapshot.capture(c); endRows[i] = c.isEndRow();
            }
            target.clearChildren();
            if(layout == MODE_SECTIONED) return filterSectioned(target, actors, snaps, endRows, n, query, opts);
            else if(layout == MODE_GRID) return filterGrid(target, actors, snaps, endRows, n, query, opts);
            else return filterList(target, actors, snaps, endRows, n, query, opts);
        }

        private int filterList(Table target, Element[] actors, CellSnapshot[] snaps, boolean[] endRows, int n,
                               String query, MatchEngine.MatchOptions opts){
            int matches = 0;
            for(int i = 0; i < n; i++){
                if(actors[i] == null) continue;
                if(matchesActor(actors[i], query, opts)){ addOriginal(target, actors, snaps, endRows, i); matches++; }
            }
            return matches;
        }

        private int filterGrid(Table target, Element[] actors, CellSnapshot[] snaps, boolean[] endRows, int n,
                               String query, MatchEngine.MatchOptions opts){
            int cols = detectColumns(endRows, n);
            int matches = 0, col = 0;
            for(int i = 0; i < n; i++){
                if(actors[i] == null) continue;
                if(matchesActor(actors[i], query, opts)){
                    Cell<?> cell = target.add(actors[i]);
                    if(snaps[i] != null) snaps[i].applyTo(cell);
                    cell.colspan(1); col++;
                    if(col % cols == 0){ target.row(); col = 0; }
                    matches++;
                }
            }
            if(col > 0) target.row();
            return matches;
        }

        private int filterSectioned(Table target, Element[] actors, CellSnapshot[] snaps, boolean[] endRows, int n,
                                    String query, MatchEngine.MatchOptions opts){
            int matches = 0, pendingCount = 0;
            int[] pendingHeaders = new int[n];
            for(int i = 0; i < n; i++){
                Element actor = actors[i];
                if(actor == null) continue;
                if(actor instanceof Table && !(actor instanceof Button)){
                    Table section = (Table)actor;
                    if(isHeaderTable(section)){ pendingHeaders[pendingCount++] = i; continue; }
                    if(isControlTable(section) && pendingCount == 0 && matches == 0){ addOriginal(target, actors, snaps, endRows, i); continue; }
                    int childMatches = filterTable(section, detectMode(section), query, opts);
                    if(childMatches > 0){
                        for(int p = 0; p < pendingCount; p++) addOriginal(target, actors, snaps, endRows, pendingHeaders[p]);
                        pendingCount = 0;
                        addOriginal(target, actors, snaps, endRows, i);
                        matches += childMatches;
                    }else{ pendingCount = 0; }
                }else if(actor instanceof Button && matchesActor(actor, query, opts)){
                    for(int p = 0; p < pendingCount; p++) addOriginal(target, actors, snaps, endRows, pendingHeaders[p]);
                    pendingCount = 0;
                    addOriginal(target, actors, snaps, endRows, i);
                    matches++;
                }else{ pendingHeaders[pendingCount++] = i; }
            }
            return matches;
        }

        private Cell<?> addOriginal(Table target, Element[] actors, CellSnapshot[] snaps, boolean[] endRows, int idx){
            Cell<?> cell = target.add(actors[idx]);
            if(snaps[idx] != null) snaps[idx].applyTo(cell);
            if(endRows[idx]) target.row();
            return cell;
        }

        private boolean matchesActor(Element actor, String query, MatchEngine.MatchOptions opts){
            String text = SearchTextExtractor.extract(actor);
            return text != null && MatchEngine.accepts(text, query, opts);
        }

        private int detectColumns(boolean[] endRows, int n){
            int cols = 1, current = 0;
            for(int i = 0; i < n; i++){
                current++;
                if(endRows[i]){ if(current > cols) cols = current; current = 0; }
            }
            if(current > cols) cols = current;
            return Math.max(1, cols);
        }

        private boolean isHeaderTable(Table t){
            Seq<Cell> c = t.getCells();
            return c.size == 1 && c.first().get() instanceof Label && !(c.first().get() instanceof Button);
        }

        private boolean isControlTable(Table t){
            Seq<Cell> c = t.getCells();
            for(int i = 0; i < c.size; i++){ if(c.get(i).get() instanceof Button) return true; }
            return false;
        }
    }
}
