/* Native integration of BlueOutside / Dressroom Advanced. See LICENSE.upstream. */
package dev.denver.dressroomadvanced;
final class NativeCredits {
    static void bind(android.widget.TextView text) {
        text.setText("\"Dressroom-Advanced\" by BlueOutside");text.setTextSize(12);
        text.setOnClickListener(view->{try {view.getContext().startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse("https://github.com/BlueOutside/Dressroom-Advanced")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));}catch(android.content.ActivityNotFoundException ignored){}});
    }
}
