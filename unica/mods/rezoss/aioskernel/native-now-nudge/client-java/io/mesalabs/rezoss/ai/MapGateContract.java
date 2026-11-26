package io.mesalabs.rezoss.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Eligibility only: the existing SSCO model still determines intent and parameters. */
public final class MapGateContract {
    public static final String MARKER = "rezoss_map_fallback";
    private MapGateContract() {}
    /** Null retains Samsung's original filter. This seam receives raw enum labels. */
    public static List<String> beforeFilter(List<?> labels, boolean enabled, boolean worker) {
        if (!enabled || !worker || labels == null || labels.size() != 1) return null;
        Object label = labels.get(0);
        if (!(label instanceof Enum<?>) || !"OTHER".equals(((Enum<?>) label).name())
                || !"other".equals(label.toString())) return null;
        List<String> retained = new ArrayList<>();
        retained.add("other");
        return retained;
    }
    public static List<String> extend(String input, List<String> labels, boolean enabled, boolean worker) {
        if (!enabled || !worker || input == null || input.trim().isEmpty() || input.length() > 12000
                || labels == null || labels.size() != 1 || !"other".equals(labels.get(0))) return labels;
        List<String> result = new ArrayList<>(labels);
        result.add("has_intention_action");
        result.add(MARKER);
        return result;
    }
    public static boolean marked(List<String> labels) { return labels != null && labels.contains(MARKER); }
    public static boolean fallback(List<String> labels) {
        return labels != null && labels.size() == 3 && labels.contains("other")
                && labels.contains("has_intention_action") && labels.contains(MARKER);
    }
    public static boolean mapAction(String action, Map<?, ?> parameters) {
        if (!"Browse Location".equals(action) || parameters == null || parameters.size() != 1) return false;
        Object location = parameters.get("location");
        if (!(location instanceof String)) return false;
        String value = (String) location;
        if (value.trim().isEmpty() || value.length() > 256) return false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isISOControl(ch) || Character.getType(ch) == Character.FORMAT) return false;
        }
        return true;
    }
}
