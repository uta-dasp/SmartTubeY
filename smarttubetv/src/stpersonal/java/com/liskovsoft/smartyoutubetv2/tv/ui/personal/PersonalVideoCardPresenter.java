package com.liskovsoft.smartyoutubetv2.tv.ui.personal;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.util.Pair;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.BaseCardView;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.presenter.VideoCardPresenter;
import com.liskovsoft.smartyoutubetv2.tv.ui.widgets.complexcardview.ComplexImageCardView;

/** Reference presentation around the existing thumbnail, preview and watched-state bindings. */
public class PersonalVideoCardPresenter extends VideoCardPresenter {
    @Override
    protected Pair<Integer, Integer> getCardDimensPx(Context context) {
        return new Pair<>(dp(context, 360), dp(context, 202.5f));
    }

    @Override
    protected boolean isCardMultilineTitleEnabled(Context context) { return true; }

    @Override
    protected boolean isCardMultilineSubtitleEnabled(Context context) { return true; }

    @Override
    public Presenter.ViewHolder onCreateViewHolder(ViewGroup parent) {
        Presenter.ViewHolder holder = super.onCreateViewHolder(parent);
        ComplexImageCardView card = (ComplexImageCardView) holder.view;
        Context context = parent.getContext();
        // Bare recycled grids do not receive Leanback RowPresenter activation.
        // Titles and metadata belong below every thumbnail, including unfocused cards.
        card.setInfoVisibility(BaseCardView.CARD_REGION_VISIBLE_ALWAYS);
        card.setBackgroundColor(Color.TRANSPARENT);
        card.setClipChildren(false);
        card.setClipToPadding(false);
        card.setPadding(0, 0, 0, 0);
        View image = card.findViewById(R.id.main_image_wrapper);
        if (image != null) {
            GradientDrawable rounded = shape(context, Color.TRANSPARENT, false);
            image.setBackground(rounded);
            if (android.os.Build.VERSION.SDK_INT >= 21) image.setClipToOutline(true);
            // The reference ring surrounds the thumbnail without covering its pixels.
            // Keep the image's rounded clipping separate from the card foreground.
            if (android.os.Build.VERSION.SDK_INT >= 23) card.setForeground(new ThumbnailRing(context, card, image));
        }
        TextView title = card.findViewById(R.id.title_text);
        if (title != null) {
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            title.setIncludeFontPadding(false);
            title.setMinLines(1);
            title.setMaxLines(2);
            title.setLineSpacing(dp(context, 2.5f), 1);
            title.setPadding(0, dp(context, 13.5f), 0, 0);
        }
        TextView subtitle = card.findViewById(R.id.content_text);
        if (subtitle != null) {
            subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            subtitle.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            subtitle.setIncludeFontPadding(false);
            subtitle.setMinLines(1);
            subtitle.setMaxLines(2);
            subtitle.setLineSpacing(dp(context, 4), 1);
            subtitle.setPadding(0, dp(context, 4), 0, 0);
        }
        return holder;
    }

    @Override
    public void onViewAttachedToWindow(Presenter.ViewHolder holder) {
        super.onViewAttachedToWindow(holder);
        // Leanback's item and row containers otherwise cut off the outset focus ring.
        android.view.ViewParent parent = holder.view.getParent();
        while (parent instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) parent;
            group.setClipChildren(false);
            group.setClipToPadding(false);
            if (group instanceof androidx.leanback.widget.VerticalGridView) break;
            parent = group.getParent();
        }
    }

    private static final class ThumbnailRing extends Drawable {
        private final View card;
        private final View image;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rectangle = new RectF();
        private final int inset;
        private final int radius;

        ThumbnailRing(Context context, View card, View image) {
            this.card = card;
            this.image = image;
            inset = dp(context, 2);
            radius = dp(context, 11);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(context, 4));
            paint.setColor(0xFFF1F1F1);
        }

        @Override public void draw(Canvas canvas) {
            // BaseCardView deliberately removes focused/selected from drawableState.
            // Read the actual card state rather than relying on a StateListDrawable.
            if (!card.hasFocus() && !card.isSelected()) return;
            rectangle.set(image.getLeft() - inset, image.getTop() - inset,
                    image.getRight() + inset, image.getBottom() + inset);
            canvas.drawRoundRect(rectangle, radius, radius, paint);
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    @Override
    public void onBindViewHolder(Presenter.ViewHolder holder, Object item) {
        super.onBindViewHolder(holder, item);
        ComplexImageCardView card = (ComplexImageCardView) holder.view;
        Video video = (Video) item;
        TextView subtitle = card.findViewById(R.id.content_text);
        if (subtitle != null) subtitle.setText(formatMetadata(video));
        boolean large = isLargeCard(video);
        card.setMainImageDimensions(dp(card.getContext(), large ? 360 : 264),
                dp(card.getContext(), large ? 202.5f : 148.5f));
        View info = card.findViewById(R.id.info_field);
        if (info != null) {
            info.setBackgroundColor(0xFF0F0F0F);
            info.setPadding(0, 0, 0, 0);
        }
    }

    protected boolean isLargeCard(Video video) {
        return video.getGroup() == null || video.getGroup().getPosition() <= 0;
    }

    @Override
    protected int getContentTextColor(Context context, boolean selected, int defaultColor) {
        return 0xFFAAAAAA;
    }

    private static CharSequence formatMetadata(Video video) {
        CharSequence metadata = video.getSecondTitle();
        if (metadata == null) return null;
        String author = video.getAuthor();
        String text = metadata.toString();
        int separator = text.indexOf('\u2022');
        if (author == null || author.isEmpty() || !text.startsWith(author) || separator <= 0) return metadata;
        int authorEnd = separator;
        while (authorEnd > 0 && Character.isWhitespace(text.charAt(authorEnd - 1))) authorEnd--;
        int detailsStart = separator + 1;
        while (detailsStart < text.length() && Character.isWhitespace(text.charAt(detailsStart))) detailsStart++;
        return new SpannableStringBuilder().append(metadata, 0, authorEnd).append('\n')
                .append(metadata, detailsStart, metadata.length());
    }

    private static GradientDrawable shape(Context context, int color, boolean focused) {
        GradientDrawable result = new GradientDrawable();
        result.setColor(color);
        result.setCornerRadius(dp(context, 9));
        if (focused) result.setStroke(dp(context, 4), 0xFFF1F1F1);
        return result;
    }

    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
