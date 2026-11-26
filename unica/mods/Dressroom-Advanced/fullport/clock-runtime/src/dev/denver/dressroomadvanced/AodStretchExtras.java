package dev.denver.dressroomadvanced;

import android.view.View;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

final class AodStretchExtras {
    private static final java.util.Set<View> mediaReturns =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    private AodStretchExtras() {}

    static void mediaReturn(View clock, boolean active) {
        if (active) mediaReturns.add(clock);
        else mediaReturns.remove(clock);
    }

    static boolean preserveHeight(
            boolean enabled,
            boolean mediaExpanded,
            boolean dozing,
            float dozeAmount,
            boolean returning) {
        return enabled && !mediaExpanded && (dozing || dozeAmount > 0f || !returning);
    }


    static Object begin(View clock) {
        try {
            ClockTransitionBinding binding=ClockTransitionBinding.of(clock);
            Object controller=binding.controller.get(clock);
            if(!preserveHeight(RuntimeExtras.enabled(clock.getContext(),"keepStretch"),binding.expanded.getBoolean(controller),binding.dozing.getBoolean(controller),binding.amount.getFloat(clock),mediaReturns.contains(clock))) return null;
            Object original=binding.axes.get(controller);
            if(!(original instanceof List<?> list)||!list.contains("HEGT"))return null;
            ArrayList<Object> remaining=new ArrayList<>();for(Object value:list)if(!"HEGT".equals(value))remaining.add(value);
            binding.axes.set(controller,remaining);return original;
        }catch(Exception ignored){return null;}
    }
    static void end(View clock,Object token) {
        if(token==null)return;
        try {ClockTransitionBinding b=ClockTransitionBinding.of(clock);b.axes.set(b.controller.get(clock),token);}catch(Exception ignored){}
    }
}
