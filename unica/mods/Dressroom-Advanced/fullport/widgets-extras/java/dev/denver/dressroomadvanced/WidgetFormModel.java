// Native adaptation of BlueOutside / Dressroom-Advanced.
// SPDX-License-Identifier: LicenseRef-Dressroom-Advanced-Source-Available-1.0
package dev.denver.dressroomadvanced;

/** Exact Samsung semWidgetSize values from aod.yo5.K0; these select real provider forms. */
final class WidgetFormModel {
    static final int[][] FORMS={{1,1,1},{2,1,2},{2,2,8},{4,2,16}};
    static boolean tallPicker(boolean stock,boolean lockscreen,boolean custom,int rows){return stock || (lockscreen&&custom&&rows>=2);}
    static int code(int width,int height){for(int[] f:FORMS)if(f[0]==width&&f[1]==height)return f[2];return 0;}
    static boolean supported(int mask,int width,int height){int code=code(width,height);return code!=0&&(mask&code)!=0;}
    static int[] nearest(int mask,int width,int height,int maxWidth,int maxHeight){
        int[] result=null;double distance=Double.MAX_VALUE;
        for(int[] f:FORMS)if((mask&f[2])!=0&&f[0]<=maxWidth&&f[1]<=maxHeight){
            double d=(f[0]-width)*(f[0]-width)+(f[1]-height)*(f[1]-height);
            if(d<distance){distance=d;result=new int[]{f[0],f[1]};}
        }
        return result;
    }
    static Object copy(Object current,int[] next,boolean vertical)throws ReflectiveOperationException{
        return Reflect.call(current,"copy",Reflect.call(current,"getGridId"),next[2],next[3],
                vertical?Reflect.call(current,"getX"):next[0],vertical?Reflect.call(current,"getY"):next[1],
                vertical?next[0]:Reflect.call(current,"getSecondaryX"),vertical?next[1]:Reflect.call(current,"getSecondaryY"),
                Reflect.call(current,"isDragAllowed"),Reflect.call(current,"getXPositionUnit"),Reflect.call(current,"getYPositionUnit"),Reflect.call(current,"getAvailable"));
    }
}
