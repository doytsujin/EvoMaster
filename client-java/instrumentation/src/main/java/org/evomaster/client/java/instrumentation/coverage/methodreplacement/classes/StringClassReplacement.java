package org.evomaster.client.java.instrumentation.coverage.methodreplacement.classes;


import org.evomaster.client.java.instrumentation.coverage.methodreplacement.DistanceHelper;
import org.evomaster.client.java.instrumentation.coverage.methodreplacement.MethodReplacementClass;
import org.evomaster.client.java.instrumentation.coverage.methodreplacement.Replacement;
import org.evomaster.client.java.instrumentation.heuristic.Truthness;
import org.evomaster.client.java.instrumentation.shared.ReplacementType;
import org.evomaster.client.java.instrumentation.shared.StringSpecialization;
import org.evomaster.client.java.instrumentation.shared.StringSpecializationInfo;
import org.evomaster.client.java.instrumentation.shared.TaintInputName;
import org.evomaster.client.java.instrumentation.staticstate.ExecutionTracer;


public class StringClassReplacement implements MethodReplacementClass {

    @Override
    public Class<?> getTargetClass() {
        return String.class;
    }

    @Replacement(type = ReplacementType.BOOLEAN)
    public static boolean equals(String caller, Object anObject, String idTemplate) {

        boolean taintedCaller = TaintInputName.isTaintInput(caller);
        boolean taintedOther = anObject != null && TaintInputName.isTaintInput(anObject.toString());

        if ( taintedCaller || taintedOther) {
            if(taintedCaller) {
                ExecutionTracer.addStringSpecialization(caller,
                        new StringSpecializationInfo(StringSpecialization.CONSTANT, anObject.toString()));
            } else {
                ExecutionTracer.addStringSpecialization(anObject.toString(),
                        new StringSpecializationInfo(StringSpecialization.CONSTANT, caller));
            }
        }


        //not important if NPE
        boolean result = caller.equals(anObject);

        Truthness t;

        if (result) {
            t = new Truthness(1d, 0d);
        } else {
            if (!(anObject instanceof String)) {
                t = new Truthness(0d, 1d);
            } else {
                long distance = DistanceHelper.getLeftAlignmentDistance(caller, anObject.toString());
                t = new Truthness(1d / (1d + distance), 1d);
            }
        }

        ExecutionTracer.executedReplacedMethod(idTemplate, ReplacementType.BOOLEAN, t);

        return result;
    }

    @Replacement(type = ReplacementType.BOOLEAN)
    public static boolean equalsIgnoreCase(String caller, String anotherString, String idTemplate) {

        if (anotherString == null) {
            ExecutionTracer.executedReplacedMethod(idTemplate, ReplacementType.BOOLEAN, new Truthness(0, 1));
            return false;
        }

        return equals(caller.toLowerCase(), anotherString.toLowerCase(), idTemplate);
    }


    @Replacement(type = ReplacementType.BOOLEAN)
    public static boolean startsWith(String caller, String prefix, int toffset, String idTemplate) {

        boolean result = caller.startsWith(prefix, toffset);

        int pl = prefix.length();

        /*
            The penalty when there is a mismatch of lengths/offset
            should be at least pl, as should be always worse than
            when doing "equals" comparisons.
            Furthermore, need to add extra penalty in case string is
            shorter than prefix
         */
        int penalty = pl;
        if (caller.length() < pl) {
            penalty += (pl - caller.length());
        }

        Truthness t;

        if (toffset < 0) {
            long dist = (-toffset + penalty) * Character.MAX_VALUE;
            t = new Truthness(1d / (1d + dist), 1d);
        } else if (toffset > caller.length() - pl) {
            long dist = (toffset + penalty) * Character.MAX_VALUE;
            t = new Truthness(1d / (1d + dist), 1d);
        } else {
            int len = Math.min(prefix.length(), caller.length());
            String sub = caller.substring(toffset, Math.min(toffset + len, caller.length()));
            return equals(sub, prefix, idTemplate);
        }

        ExecutionTracer.executedReplacedMethod(idTemplate, ReplacementType.BOOLEAN, t);
        return result;
    }

    @Replacement(type = ReplacementType.BOOLEAN)
    public static boolean startsWith(String caller, String prefix, String idTemplate) {
        return startsWith(caller, prefix, 0, idTemplate);
    }

    @Replacement(type = ReplacementType.BOOLEAN)
    public static boolean endsWith(String caller, String suffix, String idTemplate) {
        return startsWith(caller, suffix, caller.length() - suffix.length(), idTemplate);
    }


    @Replacement(type = ReplacementType.BOOLEAN)
    public static boolean isEmpty(String caller, String idTemplate) {

        int len = caller.length();
        Truthness t;
        if (len == 0) {
            t = new Truthness(1, 0);
        } else {
            t = new Truthness(1d / (1d + len), 1);
        }

        ExecutionTracer.executedReplacedMethod(idTemplate, ReplacementType.BOOLEAN, t);
        return caller.isEmpty();
    }

    @Replacement(type = ReplacementType.BOOLEAN)
    public static boolean contentEquals(String caller, CharSequence cs, String idTemplate) {
        return equals(caller, cs.toString(), idTemplate);
    }


    @Replacement(type = ReplacementType.BOOLEAN)
    public static boolean contentEquals(String caller, StringBuffer sb, String idTemplate) {
        return equals(caller, sb.toString(), idTemplate);
    }


    @Replacement(type = ReplacementType.BOOLEAN)
    public static boolean contains(String caller, CharSequence s, String idTemplate) {

        boolean result = caller.contains(s);

        String k = s.toString();
        if (caller.length() <= k.length()) {
            return equals(caller, k, idTemplate);
        }

        Truthness t;

        if (result) {
            t = new Truthness(1, 0);
        } else {
            assert caller.length() > k.length();
            long best = Long.MAX_VALUE;

            for (int i = 0; i < (caller.length() - k.length()) + 1; i++) {
                String sub = caller.substring(i, i + k.length());
                long h = DistanceHelper.getLeftAlignmentDistance(sub, k);
                if (h < best) {
                    best = h;
                }
            }
            t = new Truthness(1d / (1d + best), 1);
        }

        ExecutionTracer.executedReplacedMethod(idTemplate, ReplacementType.BOOLEAN, t);
        return result;
    }

    /*
        TODO:
        public boolean regionMatches(int toffset, String other, int ooffset, int len)
        public boolean regionMatches(boolean ignoreCase, int toffset, String other, int ooffset, int len)
        public boolean matches(String regex)
     */


}