/* Native adaptation of BlueOutside / Dressroom Advanced. See LICENSE.upstream. */
package dev.denver.dressroomadvanced;
public final class NativeArtworkFrameLayout extends android.widget.FrameLayout {
    public NativeArtworkFrameLayout(android.content.Context c){super(c);}
    public NativeArtworkFrameLayout(android.content.Context c,android.util.AttributeSet a){super(c,a);}
    public NativeArtworkFrameLayout(android.content.Context c,android.util.AttributeSet a,int s){super(c,a,s);}
    @Override public boolean dispatchTouchEvent(android.view.MotionEvent event){int result=MediaInteraction.artwork(this,event);return result<0?super.dispatchTouchEvent(event):result!=0;}
}
