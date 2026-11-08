package com.liskovsoft.smartyoutubetv2.tv.ui.personal.search;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Small code-native TV symbols drawn in a consistent 24-unit coordinate space. */
final class PersonalSearchIcon extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String symbol;
    private int color = 0xFFAAAAAA;

    PersonalSearchIcon(Context context, String symbol) {
        super(context);
        this.symbol = symbol;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setColor(int color) { this.color = color; invalidate(); }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        float size = Math.min(getWidth(), getHeight());
        canvas.translate((getWidth() - size) / 2, (getHeight() - size) / 2);
        canvas.scale(size / 24f, size / 24f);
        paint.setColor(color);
        paint.setStrokeWidth(1.9f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStyle(Paint.Style.STROKE);
        Path path = new Path();
        switch (symbol) {
            case "mic":
                canvas.drawRoundRect(new RectF(9, 2, 15, 15), 3, 3, paint);
                canvas.drawArc(new RectF(6, 5, 18, 19), 0, 180, false, paint);
                canvas.drawLine(12, 19, 12, 23, paint); break;
            case "home":
                path.moveTo(2, 11); path.lineTo(12, 3); path.lineTo(22, 11);
                path.moveTo(5, 9); path.lineTo(5, 22); path.lineTo(10, 22);
                path.lineTo(10, 15); path.lineTo(14, 15); path.lineTo(14, 22);
                path.lineTo(19, 22); path.lineTo(19, 9); canvas.drawPath(path, paint); break;
            case "history":
                canvas.drawArc(new RectF(3, 3, 22, 22), -130, 310, false, paint);
                canvas.drawLine(3, 2, 3, 8, paint); canvas.drawLine(3, 8, 9, 8, paint);
                canvas.drawLine(12, 7, 12, 13, paint); canvas.drawLine(12, 13, 17, 15, paint); break;
            case "delete":
                path.moveTo(8, 4); path.lineTo(22, 4); path.lineTo(22, 20); path.lineTo(8, 20);
                path.lineTo(1, 12); path.close(); canvas.drawPath(path, paint);
                canvas.drawLine(11, 8, 17, 16, paint); canvas.drawLine(17, 8, 11, 16, paint); break;
            case "language":
                canvas.drawCircle(12, 12, 10, paint); canvas.drawOval(new RectF(7, 2, 17, 22), paint);
                canvas.drawLine(2, 12, 22, 12, paint); break;
            case "settings":
                canvas.drawCircle(12, 12, 4, paint);
                for (int i = 0; i < 8; i++) {
                    canvas.save(); canvas.rotate(i * 45, 12, 12);
                    canvas.drawLine(12, 1, 12, 5, paint); canvas.restore();
                }
                canvas.drawCircle(12, 12, 8, paint); break;
            case "music":
                canvas.drawCircle(12, 12, 10, paint); canvas.drawCircle(12, 12, 6, paint);
                triangle(canvas, 10, 8, 16, 12, 10, 16); break;
            case "live":
                canvas.drawArc(new RectF(1, 2, 23, 22), 140, 80, false, paint);
                canvas.drawArc(new RectF(1, 2, 23, 22), -40, 80, false, paint);
                canvas.drawArc(new RectF(6, 6, 18, 18), 130, 100, false, paint);
                canvas.drawArc(new RectF(6, 6, 18, 18), -50, 100, false, paint);
                paint.setStyle(Paint.Style.FILL); canvas.drawCircle(12, 12, 2, paint); break;
            case "shorts":
                path.moveTo(8, 3); path.cubicTo(18,-3,24,7,15,12);
                path.cubicTo(25,17,17,28,8,21); path.cubicTo(-2,26,-2,16,8,12);
                path.cubicTo(-2,7,0,3,8,3); canvas.drawPath(path,paint);
                triangle(canvas,10,8,16,12,10,16); break;
            case "library":
                canvas.drawRect(6, 3, 22, 20, paint);
                path.moveTo(2, 7); path.lineTo(2, 23); path.lineTo(18, 23); canvas.drawPath(path, paint);
                triangle(canvas,11,7,17,11,11,15); break;
            case "subscriptions":
            case "movies":
                canvas.drawRoundRect(new RectF(2, 6, 22, 22), 1, 1, paint);
                canvas.drawLine(5, 2, 19, 2, paint); triangle(canvas,10,10,16,14,10,18); break;
            case "gaming":
                canvas.drawRoundRect(new RectF(2, 6, 22, 20), 5, 5, paint);
                canvas.drawLine(7,10,7,16,paint); canvas.drawLine(4,13,10,13,paint);
                canvas.drawPoint(16,11,paint); canvas.drawPoint(19,14,paint); break;
            default:
                canvas.drawCircle(10, 10, 8, paint); canvas.drawLine(16,16,22,22,paint); break;
        }
        canvas.restore();
    }

    private void triangle(Canvas canvas, float x1, float y1, float x2, float y2, float x3, float y3) {
        Path path = new Path(); path.moveTo(x1,y1); path.lineTo(x2,y2); path.lineTo(x3,y3); path.close();
        Paint.Style previous = paint.getStyle(); paint.setStyle(Paint.Style.FILL); canvas.drawPath(path,paint); paint.setStyle(previous);
    }
}
