package yiqiu.pinyin.match;

import java.util.Locale;

public final class MatchEngine{
    private MatchEngine(){}

    public static boolean accepts(String rawText, String query, MatchOptions opts){
        if(query == null || query.isEmpty()) return true;
        if(rawText == null || rawText.isEmpty()) return false;

        String q = query.toLowerCase(Locale.ROOT);
        StringBuilder qb = new StringBuilder(q.length());
        for(int i = 0; i < q.length(); i++){
            char c = q.charAt(i);
            if(Character.isLetterOrDigit(c) || PinyinIndex.isCjk(c)) qb.append(c);
        }
        String qn = qb.toString();
        if(qn.isEmpty()) return true;

        PinyinIndex.Profile p = PinyinIndex.of(rawText);

        if(p.lower.contains(qn)) return true;
        if(hasCjk(qn)) return false;
        if(p.pinyinFull.contains(qn)) return true;
        if(opts.initials && qn.length() >= 2 && qn.length() <= 8 && p.initials.contains(qn)) return true;
        if((opts.heteronym || opts.fuzzy) && walkMatch(p.tokens, qn, opts.fuzzy)) return true;

        return false;
    }

    private static boolean hasCjk(String s){
        for(int i = 0; i < s.length(); i++){
            if(PinyinIndex.isCjk(s.charAt(i))) return true;
        }
        return false;
    }

    private static boolean walkMatch(String[][] tokens, String query, boolean fuzzy){
        for(int start = 0; start < tokens.length; start++){
            if(tryFrom(tokens, start, query, 0, fuzzy)) return true;
        }
        return false;
    }

    private static boolean tryFrom(String[][] tokens, int ti, String q, int qi, boolean fuzzy){
        if(qi >= q.length()) return true;
        if(ti >= tokens.length) return false;
        String[] cands = tokens[ti];
        if(cands == null || cands.length == 0) return tryFrom(tokens, ti + 1, q, qi, fuzzy);
        for(String reading : cands){
            if(reading == null || reading.isEmpty()) continue;
            int eaten = consumeExpanded(q, qi, reading, fuzzy);
            if(eaten > 0){
                if(qi + eaten == q.length()) return true;
                if(eaten == reading.length() && tryFrom(tokens, ti + 1, q, qi + eaten, fuzzy)) return true;
            }
            char init = reading.charAt(0);
            if(q.charAt(qi) == init){
                if(qi + 1 == q.length()) return true;
                if(tryFrom(tokens, ti + 1, q, qi + 1, fuzzy)) return true;
            }
        }
        return false;
    }

    private static int consumeExpanded(String q, int qi, String reading, boolean fuzzy){
        int qp = qi, rp = 0;
        while(rp < reading.length() && qp < q.length()){
            char rc = reading.charAt(rp);
            char qc = q.charAt(qp);
            if(rc == qc){ rp++; qp++; continue; }
            if(fuzzy && rp + 1 < reading.length() && reading.charAt(rp + 1) == 'h'
                && (rc == 'z' || rc == 'c' || rc == 's') && qc == rc){
                rp += 2; qp += 1; continue;
            }
            return 0;
        }
        if(fuzzy && rp < reading.length() && qp == q.length()
            && reading.charAt(rp) == 'g' && rp > 0 && reading.charAt(rp - 1) == 'n') return rp;
        return rp;
    }

    public static final class MatchOptions{
        public final boolean fuzzy;
        public final boolean initials;
        public final boolean heteronym;
        public MatchOptions(boolean fuzzy, boolean initials, boolean heteronym){
            this.fuzzy = fuzzy; this.initials = initials; this.heteronym = heteronym;
        }
    }
}
