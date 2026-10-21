package local.driveboard;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputFilter;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Locale;

public final class MainActivity extends Activity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private BoardView board;
    private Button access;
    private TextView status;
    private final Runnable tick = new Runnable() {
        @Override public void run() { board.refresh(); updateAccess(); handler.postDelayed(this,1000); }
    };
    private int dp(int n) { return Math.round(n*getResources().getDisplayMetrics().density); }
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(Color.rgb(9,15,25));
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(20),dp(20),dp(20),dp(28));
        scroll.addView(content);
        content.setOnApplyWindowInsetsListener((v,insets)->{
            android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars() | android.view.WindowInsets.Type.displayCutout());
            v.setPadding(dp(20)+bars.left,dp(20)+bars.top,dp(20)+bars.right,dp(28)+bars.bottom); return insets;
        });
        TextView title = text("DriveBoard",28); title.setTextColor(0xFF88D8CF); content.addView(title);
        content.addView(text("Your car screen, arranged on your phone",15));
        content.addView(text("Drag cards to move · drag the mint corner to resize\n12 columns × 6 rows · overlaps allowed",14));
        board = new BoardView(this); board.setContentDescription("DriveBoard layout preview. Drag a card to move it or its bottom right corner to resize.");
        board.setEditing(true,()->status.setText("Layout saved · updates live in the car"));
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(180));
        previewParams.topMargin=dp(16); previewParams.bottomMargin=dp(8); content.addView(board,previewParams);
        board.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->updateAspect());
        status=text("Preview uses the last car surface ratio (default 1920 × 720)",12); content.addView(status);
        Button reset = new Button(this); reset.setText("Reset layout"); reset.setOnClickListener(v->Store.prefs(this).edit().putString("layout",BoardLayout.defaults().toJson()).apply()); content.addView(reset);
        content.addView(text("Malaysia JAKIM zone",18));
        EditText zone=new EditText(this); zone.setSingleLine(true); zone.setHint("WLY01"); zone.setText(WidgetData.zone(this));
        zone.setFilters(new InputFilter[]{new InputFilter.AllCaps(),new InputFilter.LengthFilter(5)});
        zone.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS); content.addView(zone);
        Button save=new Button(this); save.setText("Save zone"); save.setOnClickListener(v->{
            String z=zone.getText().toString().trim().toUpperCase(Locale.ROOT);
            if(!z.matches("[A-Z]{3}[0-9]{2}")) { zone.setError("Enter a JAKIM zone, e.g. WLY01"); return; }
            if (!z.equals(WidgetData.zone(this))) Store.prefs(this).edit().putString("zone",z).putLong("prayer_refresh_"+z,System.currentTimeMillis()).apply();
            board.refresh(); status.setText("Zone saved: "+z+" · today's prayer times");
            ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(zone.getWindowToken(),0);
        }); content.addView(save);
        access=new Button(this); access.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))); content.addView(access);
        content.addView(text("Enable Unknown sources in Android Auto developer settings to see DriveBoard in the car.",14));
        content.addView(text("Prayer times use Malaysia time. Media shows the active playing session. Car display support must be tested in a real Android Auto host.",12));
        setContentView(scroll);
    }
    private TextView text(String s,int size) {
        TextView t=new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(0xFFD7E2EE); t.setPadding(0,dp(8),0,dp(8)); return t;
    }
    private void updateAccess() {
        boolean granted=WidgetData.hasMediaAccess(this);
        access.setText(granted?"Notification access granted":"Grant notification access"); access.setVisibility(granted?View.GONE:View.VISIBLE);
    }
    private void updateAspect() {
        if(board.getWidth()==0) return;
        SharedPreferences p=Store.prefs(this);
        int width=Math.max(1,p.getInt("visible_width",p.getInt("surface_width",1920))),height=Math.max(1,p.getInt("visible_height",p.getInt("surface_height",720)));
        int desired=Math.max(1,Math.round(board.getWidth()*(height/(float)width)));
        if(board.getLayoutParams().height!=desired) { board.getLayoutParams().height=desired; board.requestLayout(); }
    }
    @Override protected void onStart() { super.onStart(); Store.prefs(this).registerOnSharedPreferenceChangeListener(this); handler.post(tick); board.post(this::updateAspect); }
    @Override protected void onStop() { handler.removeCallbacks(tick); Store.prefs(this).unregisterOnSharedPreferenceChangeListener(this); super.onStop(); }
    @Override public void onSharedPreferenceChanged(SharedPreferences prefs,String key) {
        handler.post(()->{ board.refresh(); if("surface_width".equals(key)||"surface_height".equals(key)||"visible_width".equals(key)||"visible_height".equals(key)) updateAspect(); });
    }
}
