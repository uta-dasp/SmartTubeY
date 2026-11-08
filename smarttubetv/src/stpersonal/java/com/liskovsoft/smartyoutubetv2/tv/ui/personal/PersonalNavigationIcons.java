package com.liskovsoft.smartyoutubetv2.tv.ui.personal;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import com.liskovsoft.smartyoutubetv2.tv.R;

/** Original code-native symbols shared by personal navigation surfaces. */
public final class PersonalNavigationIcons {
    private PersonalNavigationIcons() { }

    /** Unknown resources retain their original drawable, including custom/pinned section icons. */
    public static Drawable create(Context context, int resource) {
        String symbol = null;
        if (resource == R.drawable.action_search) symbol = "search";
        else if (resource == R.drawable.icon_home) symbol = "home";
        else if (resource == R.drawable.icon_shorts) symbol = "shorts";
        else if (resource == R.drawable.icon_subscriptions) symbol = "subscriptions";
        else if (resource == R.drawable.icon_playlist) symbol = "library";
        else if (resource == R.drawable.icon_music) symbol = "music";
        else if (resource == R.drawable.icon_live) symbol = "live";
        else if (resource == R.drawable.icon_gaming) symbol = "gaming";
        else if (resource == R.drawable.icon_sports) symbol = "sports";
        else if (resource == R.drawable.icon_settings) symbol = "settings";
        return create(context, symbol);
    }

    /** The viewport is 24 units, with a roughly 16-unit visible glyph centered inside it. */
    public static Drawable create(Context context, String symbol) {
        if (!isSupported(symbol)) return null;
        return new NavigationDrawable(symbol, Math.round(24 * context.getResources().getDisplayMetrics().density));
    }

    private static boolean isSupported(String symbol) {
        return "search".equals(symbol) || "home".equals(symbol) || "shorts".equals(symbol)
                || "subscriptions".equals(symbol) || "library".equals(symbol) || "music".equals(symbol)
                || "live".equals(symbol) || "gaming".equals(symbol) || "sports".equals(symbol)
                || "settings".equals(symbol);
    }

    /** DrawableCompat wrappers supply pre-21 tint filters and forward the parent state. */
    private static final class NavigationDrawable extends Drawable {
        private final String symbol;
        private final int intrinsicSize;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF rect = new RectF();
        private ColorStateList tint;
        private PorterDuff.Mode tintMode = PorterDuff.Mode.SRC_IN;
        private ColorFilter colorFilter;
        private ColorFilter tintFilter;
        private int tintColor = Color.WHITE;
        private int alpha = 255;
        private boolean active;

        NavigationDrawable(String symbol, int intrinsicSize) {
            this.symbol = symbol;
            this.intrinsicSize = intrinsicSize;
            paint.setStrokeWidth(1.5f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            float size = Math.min(bounds.width(), bounds.height());
            if (size <= 0) return;
            int save = canvas.save();
            canvas.translate(bounds.left + (bounds.width() - size) / 2f, bounds.top + (bounds.height() - size) / 2f);
            canvas.scale(size / 24f, size / 24f);
            paint.setColor(Color.WHITE);
            paint.setAlpha(alpha);
            paint.setColorFilter(colorFilter != null ? colorFilter : tintFilter);
            paint.setStyle(Paint.Style.STROKE);
            path.reset();
            path.setFillType(Path.FillType.WINDING);
            switch (symbol) {
                case "home": drawHome(canvas); break;
                case "subscriptions": drawSubscriptions(canvas); break;
                case "shorts": drawShorts(canvas); break;
                case "library":
                    rect.set(7, 5, 19.25f, 17.25f); canvas.drawRoundRect(rect, .5f, .5f, paint);
                    path.moveTo(4.75f, 8); path.lineTo(4.75f, 19.25f); path.lineTo(16, 19.25f);
                    canvas.drawPath(path, paint); triangle(canvas, 11, 8, 15.5f, 11.25f, 11, 14.5f); break;
                case "music":
                    canvas.drawCircle(12, 12, 7.25f, paint); canvas.drawCircle(12, 12, 4.75f, paint);
                    triangle(canvas, 10.75f, 9.5f, 14.5f, 12, 10.75f, 14.5f); break;
                case "live":
                    rect.set(4.75f, 4.75f, 19.25f, 19.25f); canvas.drawArc(rect, 140, 80, false, paint); canvas.drawArc(rect, -40, 80, false, paint);
                    rect.set(8, 8, 16, 16); canvas.drawArc(rect, 135, 90, false, paint); canvas.drawArc(rect, -45, 90, false, paint);
                    paint.setStyle(Paint.Style.FILL); canvas.drawCircle(12, 12, 1.5f, paint); break;
                case "gaming": drawGaming(canvas); break;
                case "sports": drawSports(canvas); break;
                case "settings": drawSettings(canvas); break;
                default:
                    canvas.drawCircle(10.75f, 10.75f, 5.75f, paint); canvas.drawLine(15, 15, 19.25f, 19.25f, paint); break;
            }
            canvas.restoreToCount(save);
        }

        private void drawHome(Canvas canvas) {
            float edge = active ? 4 : 4.75f;
            float bottom = active ? 20 : 19.25f;
            path.moveTo(edge, 10.5f); path.lineTo(12, edge); path.lineTo(24 - edge, 10.5f);
            path.lineTo(18, 10.5f); path.lineTo(18, bottom); path.lineTo(13.75f, bottom);
            path.lineTo(13.75f, 13.5f); path.lineTo(10.25f, 13.5f); path.lineTo(10.25f, bottom);
            path.lineTo(6, bottom); path.lineTo(6, 10.5f); path.close();
            if (active) paint.setStyle(Paint.Style.FILL);
            canvas.drawPath(path, paint);
        }

        private void drawSubscriptions(Canvas canvas) {
            canvas.drawLine(7, 5, 17, 5, paint);
            if (active) rect.set(4, 7.25f, 20, 20);
            else rect.set(4.75f, 8, 19.25f, 19.25f);
            if (active) {
                paint.setStyle(Paint.Style.FILL);
                path.setFillType(Path.FillType.EVEN_ODD);
                path.addRoundRect(rect, .75f, .75f, Path.Direction.CW);
                path.moveTo(10, 10.5f); path.lineTo(15, 13.5f); path.lineTo(10, 16.5f); path.close();
                canvas.drawPath(path, paint);
            } else {
                canvas.drawRoundRect(rect, .75f, .75f, paint);
                triangle(canvas, 10, 10.5f, 15, 13.5f, 10, 16.5f);
            }
        }

        private void drawShorts(Canvas canvas) {
            path.moveTo(14.5f, 4.9f); path.cubicTo(16, 4.1f, 18, 5.2f, 18, 7);
            path.cubicTo(18, 8, 17.5f, 8.7f, 16.6f, 9.2f); path.lineTo(14.8f, 10.25f); path.lineTo(16.6f, 11.3f);
            path.cubicTo(18.9f, 12.6f, 18.9f, 15.3f, 16.6f, 16.6f); path.lineTo(9.5f, 19.1f);
            path.cubicTo(8, 19.9f, 6, 18.8f, 6, 17); path.cubicTo(6, 16, 6.5f, 15.3f, 7.4f, 14.8f);
            path.lineTo(9.2f, 13.75f); path.lineTo(7.4f, 12.7f);
            path.cubicTo(5.1f, 11.4f, 5.1f, 8.7f, 7.4f, 7.4f); path.close();
            canvas.drawPath(path, paint); triangle(canvas, 10.5f, 9.5f, 15, 12, 10.5f, 14.5f);
        }

        private void drawGaming(Canvas canvas) {
            // A compact outlined controller, with a cross and two action buttons.
            path.moveTo(8.5f, 6.75f); path.lineTo(12, 8.25f); path.lineTo(15.5f, 6.75f);
            path.lineTo(19.25f, 9); path.lineTo(19.25f, 15); path.lineTo(12, 19.25f);
            path.lineTo(4.75f, 15); path.lineTo(4.75f, 9); path.close(); canvas.drawPath(path, paint);
            canvas.drawLine(8.25f, 10.5f, 8.25f, 14, paint); canvas.drawLine(6.5f, 12.25f, 10, 12.25f, paint);
            canvas.drawPoint(14.5f, 10.75f, paint); canvas.drawPoint(17, 13.25f, paint);
        }

        private void drawSports(Canvas canvas) {
            path.moveTo(8, 4.75f); path.lineTo(16, 4.75f); path.lineTo(16, 11.5f);
            path.cubicTo(16, 17, 8, 17, 8, 11.5f); path.close(); canvas.drawPath(path, paint);
            path.reset(); path.moveTo(8, 7); path.lineTo(4.75f, 7); path.lineTo(4.75f, 10);
            path.cubicTo(4.75f, 12.5f, 6.25f, 13.25f, 8.5f, 13.25f);
            path.moveTo(16, 7); path.lineTo(19.25f, 7); path.lineTo(19.25f, 10);
            path.cubicTo(19.25f, 12.5f, 17.75f, 13.25f, 15.5f, 13.25f);
            path.moveTo(12, 15.5f); path.lineTo(12, 17); path.lineTo(9.5f, 19.25f);
            path.lineTo(14.5f, 19.25f); path.lineTo(12, 17); canvas.drawPath(path, paint);
        }

        private void drawSettings(Canvas canvas) {
            // Eight short teeth remain an outline in both selected and focused states.
            for (int i = 0; i < 32; i++) {
                double angle = -Math.PI / 2 + i * Math.PI / 16;
                float radius = i % 4 == 1 || i % 4 == 2 ? 7.25f : 5.75f;
                float x = 12 + (float) Math.cos(angle) * radius;
                float y = 12 + (float) Math.sin(angle) * radius;
                if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
            path.close(); canvas.drawPath(path, paint); canvas.drawCircle(12, 12, 2.5f, paint);
        }

        private void triangle(Canvas canvas, float x1, float y1, float x2, float y2, float x3, float y3) {
            path.reset(); path.moveTo(x1, y1); path.lineTo(x2, y2); path.lineTo(x3, y3); path.close();
            paint.setStyle(Paint.Style.FILL); canvas.drawPath(path, paint); paint.setStyle(Paint.Style.STROKE);
        }

        @Override protected boolean onStateChange(int[] state) {
            boolean nextActive = false;
            for (int value : state) if (value == android.R.attr.state_focused || value == android.R.attr.state_selected) nextActive = true;
            int nextColor = tint == null ? Color.WHITE : tint.getColorForState(state, tint.getDefaultColor());
            boolean changed = nextActive != active || nextColor != tintColor;
            active = nextActive; tintColor = nextColor;
            if (changed) { updateTintFilter(); invalidateSelf(); }
            return changed;
        }

        @Override public boolean isStateful() { return true; }
        @Override public int getIntrinsicWidth() { return intrinsicSize; }
        @Override public int getIntrinsicHeight() { return intrinsicSize; }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        @Override public void setAlpha(int alpha) { if (this.alpha != alpha) { this.alpha = alpha; invalidateSelf(); } }
        @Override public void setColorFilter(ColorFilter filter) { colorFilter = filter; invalidateSelf(); }
        @Override public void setTint(int color) { setTintList(ColorStateList.valueOf(color)); }
        @Override public void setTintList(ColorStateList tint) {
            this.tint = tint; onStateChange(getState()); updateTintFilter(); invalidateSelf();
        }
        @Override public void setTintMode(PorterDuff.Mode mode) {
            // Navigation uses SRC_IN, but honor callers using DrawableCompat on older Android too.
            tintMode = mode;
            updateTintFilter(); invalidateSelf();
        }
        private void updateTintFilter() { tintFilter = tint == null || tintMode == null ? null : new PorterDuffColorFilter(tintColor, tintMode); }
    }
}
