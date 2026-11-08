package com.liskovsoft.smartyoutubetv2.tv.ui.personal;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.liskovsoft.smartyoutubetv2.tv.R;

/** Original icon artwork with accessible native text, rather than a rasterized header label. */
public final class PersonalBrandHeader {
    private PersonalBrandHeader() { }

    public static LinearLayout create(Context context, float textSize) {
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(context);
        icon.setImageResource(R.mipmap.app_logo_v2);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(context, 36), dp(context, 28));
        iconParams.rightMargin = dp(context, 8);
        header.addView(icon, iconParams);
        TextView title = new TextView(context);
        title.setText(R.string.personal_brand);
        title.setTextSize(textSize);
        title.setTextColor(0xFFF1F1F1);
        title.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setSingleLine(true);
        header.addView(title, new LinearLayout.LayoutParams(-2, -1));
        return header;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
