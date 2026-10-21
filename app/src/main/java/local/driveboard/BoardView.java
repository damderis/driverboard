package local.driveboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import java.text.DateFormat;
import java.util.Date;

/** One renderer for the phone preview and the car's offscreen surface. */
public final class BoardView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private BoardLayout layout = BoardLayout.defaults();
    private String[] prayer = {"—","",""}, media = {"Nothing playing",""};
    private String time = "", date = "";
    private boolean editing;
    private BoardLayout.Card dragging;
    private float downX, downY;
    private int startCol, startRow, startW, startH;
    private boolean resizing;
    private Runnable onEdit;
    public BoardView(Context c) { super(c); refresh(); }
    public void setEditing(boolean editing, Runnable onEdit) { this.editing = editing; this.onEdit = onEdit; invalidate(); }
    public void refresh() {
        if (dragging == null) layout = BoardLayout.fromJson(Store.prefs(getContext()).getString("layout", ""));
        Date now = new Date();
        time = android.text.format.DateFormat.getTimeFormat(getContext()).format(now);
        date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(now);
        prayer = WidgetData.prayer(getContext()); media = WidgetData.media(getContext()); invalidate();
    }
    private RectF bounds(BoardLayout.Card c) {
        float cellW=getWidth()/12f, cellH=getHeight()/6f, gap=Math.max(2,getWidth()*.006f);
        return new RectF(c.col*cellW+gap,c.row*cellH+gap,(c.col+c.w)*cellW-gap,(c.row+c.h)*cellH-gap);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); canvas.drawColor(Color.rgb(9,15,25));
        if (editing) {
            paint.setColor(0xFF233044); paint.setStrokeWidth(1);
            for(int i=1;i<12;i++) canvas.drawLine(i*getWidth()/12f,0,i*getWidth()/12f,getHeight(),paint);
            for(int i=1;i<6;i++) canvas.drawLine(0,i*getHeight()/6f,getWidth(),i*getHeight()/6f,paint);
        }
        for(BoardLayout.Card c:layout.cards) {
            RectF r=bounds(c); paint.setColor(0xFF172438); canvas.drawRoundRect(r, Math.min(20,getWidth()*.012f),Math.min(20,getWidth()*.012f),paint);
            canvas.save(); canvas.clipRect(r);
            float pad=Math.max(4,Math.min(r.width(),r.height())*.10f);
            float base=Math.max(7,Math.min(getWidth()*.022f,Math.min(r.width()/9f,r.height()/5.5f)));
            float x=r.left+pad, y=r.top+pad+base;
            label(canvas,c.type.equals("media")?"NOW PLAYING":c.type.equals("prayer")?"NEXT PRAYER":"CLOCK",x,y,base*.7f,0xFF88D8CF,r.width()-2*pad);
            String title=c.type.equals("clock")?time:c.type.equals("prayer")?prayer[0]:media[0];
            String sub=c.type.equals("clock")?date:c.type.equals("prayer")?prayer[1]:media[1];
            label(canvas,title,x,y+base*1.8f,c.type.equals("clock")?base*1.65f:base,Color.WHITE,r.width()-2*pad);
            label(canvas,sub,x,y+base*3.1f,base*.72f,0xFFB8C7D9,r.width()-2*pad);
            if(c.type.equals("prayer")) label(canvas,prayer[2],x,y+base*4.3f,base*.55f,0xFFB8C7D9,r.width()-2*pad);
            if(editing) { paint.setColor(0xFF88D8CF); float handle=handleSize(); canvas.drawRect(r.right-handle,r.bottom-handle,r.right,r.bottom,paint); }
            canvas.restore();
        }
        // ponytail: view.draw() won't render WebView/video — VirtualDisplay+Presentation needed later for browser/YouTube.
    }
    private void label(Canvas canvas,String text,float x,float y,float size,int color,float width) {
        paint.setTextSize(size); paint.setColor(color); paint.setTypeface(android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.NORMAL));
        String s=text==null?"—":text;
        if(paint.measureText(s)>width) {
            int count=paint.breakText(s,true,Math.max(0,width-paint.measureText("…")),null);
            s=s.substring(0,count)+"…";
        }
        canvas.drawText(s,x,y,paint);
    }
    private float handleSize() { return Math.max(12, Math.min(getWidth(),getHeight())*.07f); }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if(!editing || getWidth()==0 || getHeight()==0) return false;
        switch(event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                for(int i=layout.cards.size()-1;i>=0;i--) {
                    BoardLayout.Card c=layout.cards.get(i); RectF r=bounds(c);
                    if(r.contains(event.getX(),event.getY())) {
                        dragging=c; downX=event.getX(); downY=event.getY(); startCol=c.col; startRow=c.row; startW=c.w; startH=c.h;
                        resizing=event.getX()>=r.right-handleSize()*1.6f && event.getY()>=r.bottom-handleSize()*1.6f;
                        getParent().requestDisallowInterceptTouchEvent(true); return true;
                    }
                }
                return false;
            case MotionEvent.ACTION_MOVE:
                if(dragging==null) return false;
                int dx=BoardLayout.snap(event.getX()-downX,getWidth()/12f), dy=BoardLayout.snap(event.getY()-downY,getHeight()/6f);
                if(resizing) layout.resize(dragging,startW+dx,startH+dy); else layout.move(dragging,startCol+dx,startRow+dy);
                Store.prefs(getContext()).edit().putString("layout",layout.toJson()).apply();
                invalidate(); return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if(dragging==null) return false;
                dragging=null; getParent().requestDisallowInterceptTouchEvent(false);
                if(onEdit!=null) onEdit.run(); performClick(); return true;
            default: return dragging!=null;
        }
    }
    @Override public boolean performClick() { super.performClick(); return true; }
}
