/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.tgui.R;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;

import java.util.Collections;
import java.util.Locale;

/**
 * The local location page of ChatAttachAlertLocationLayout. Map/venue services,
 * GPS permissions, live-location and Telegram requests are replaced by a host
 * View or a host snapshot. The selected-location row is copied from the ordinary
 * SendLocationCell branch (release-11.4.2-5469:63-83,87-112,134,152-154).
 */
public class ChatAttachAlertLocationLayout extends FrameLayout {
    private final Theme.ResourcesProvider colors;
    private final Attachment.SelectionListener listener;
    private final FrameLayout mapContainer;
    private final ImageView mapImage;
    private final TextView emptyView;
    private final SimpleTextView titleTextView, accurateTextView;
    private View mapView;
    private Attachment.Location location;
    private boolean destroyed;

    public ChatAttachAlertLocationLayout(Context context, Theme.ResourcesProvider resourcesProvider,
                                         Attachment.SelectionListener selectionListener) {
        super(context);
        colors = resourcesProvider; listener = selectionListener;
        setBackgroundColor(color(Theme.key_windowBackgroundWhite));
        mapContainer = new FrameLayout(context);
        addView(mapContainer, LayoutHelper.createFrame(-1, -1, Gravity.TOP, 0, 0, 0, 60));
        mapImage = new ImageView(context);
        mapImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        mapContainer.addView(mapImage, LayoutHelper.createFrame(-1, -1));
        emptyView = new TextView(context);
        emptyView.setText(R.string.tgui_location_choose);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setTextColor(color(Theme.key_windowBackgroundWhiteGrayText3));
        emptyView.setTextSize(16);
        emptyView.setPadding(AndroidUtilities.dp(20), 0, AndroidUtilities.dp(20), 0);
        mapContainer.addView(emptyView, LayoutHelper.createFrame(-1, -1));

        FrameLayout row = new FrameLayout(context);
        row.setBackground(Theme.createSelectorWithBackgroundDrawable(color(Theme.key_windowBackgroundWhite), color(Theme.key_listSelector)));
        addView(row, LayoutHelper.createFrame(-1, 60, Gravity.BOTTOM));
        ImageView imageView = new ImageView(context);
        row.addView(imageView, LayoutHelper.createFrame(46, 46, Gravity.CENTER_VERTICAL | Gravity.LEFT, 13, 0, 0, 0));
        titleTextView = new SimpleTextView(context);
        titleTextView.setTextSize(16);
        titleTextView.setGravity(Gravity.LEFT);
        titleTextView.setTypeface(AndroidUtilities.bold());
        titleTextView.setTextColor(color(Theme.key_location_sendLocationText));
        titleTextView.setText(context.getString(R.string.SendSelectedLocation));
        row.addView(titleTextView, LayoutHelper.createFrame(-1, 20, Gravity.TOP | Gravity.LEFT, 73, 9.33f, 16, 0));
        accurateTextView = new SimpleTextView(context);
        accurateTextView.setTextSize(14);
        accurateTextView.setTextColor(color(Theme.key_windowBackgroundWhiteGrayText3));
        accurateTextView.setGravity(Gravity.LEFT);
        accurateTextView.setText(context.getString(R.string.tgui_location_choose));
        row.addView(accurateTextView, LayoutHelper.createFrame(-1, 20, Gravity.TOP | Gravity.LEFT, 73, 33, 16, 0));
        Drawable circle = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(46),
                color(Theme.key_location_sendLocationBackground), color(Theme.key_location_sendLocationBackground));
        Drawable pin = context.getResources().getDrawable(R.drawable.pin).mutate();
        pin.setColorFilter(new PorterDuffColorFilter(color(Theme.key_location_sendLocationIcon), PorterDuff.Mode.MULTIPLY));
        CombinedDrawable combined = new CombinedDrawable(circle, pin);
        combined.setCustomSize(AndroidUtilities.dp(46), AndroidUtilities.dp(46));
        combined.setIconSize(AndroidUtilities.dp(24), AndroidUtilities.dp(24));
        imageView.setBackgroundDrawable(combined);
        row.setOnClickListener(v -> publish());
    }

    /** Host owns the map implementation and lifecycle; it is never inflated by the library. */
    public void setMapView(View view) {
        if (destroyed || mapView == view) return;
        if (view == this || view == mapContainer) throw new IllegalArgumentException("map view cannot contain its page");
        if (mapView != null) mapContainer.removeView(mapView);
        mapView = view;
        if (view != null) {
            if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
            mapContainer.addView(view, LayoutHelper.createFrame(-1, -1));
        }
        updateMapVisibility();
    }
    public void setHostLocationView(View view) { setMapView(view); }

    /** Main-thread callback target passed to the host's map picker. Null clears selection. */
    public void setLocation(Attachment.Location value) {
        if (destroyed) return;
        location = value;
        mapImage.setImageDrawable(value == null ? null : value.mapImage);
        titleTextView.setText(getContext().getString(R.string.SendSelectedLocation));
        accurateTextView.setText(value == null ? getContext().getString(R.string.tgui_location_choose)
                : !TextUtils.isEmpty(value.title) ? value.title
                : String.format(Locale.US, "%.5f, %.5f", value.latitude, value.longitude));
        updateMapVisibility();
        publish();
    }
    public Attachment.Location getLocation() { return location; }
    private void updateMapVisibility() {
        mapImage.setVisibility(mapView == null && location != null && location.mapImage != null ? VISIBLE : GONE);
        emptyView.setVisibility(mapView == null && (location == null || location.mapImage == null) ? VISIBLE : GONE);
    }
    private void publish() {
        if (!destroyed && listener != null)
            listener.onSelectionChanged(location == null ? Collections.emptyList() : Collections.singletonList(Attachment.location(location)));
    }
    public void onShow() { if (!destroyed) publish(); }
    public void onHide() { }
    public void onDestroy() {
        destroyed = true;
        if (mapView != null) mapContainer.removeView(mapView);
        mapView = null;
        mapImage.setImageDrawable(null);
        location = null;
    }
    private int color(int key) { return Theme.getColor(key, colors); }
}
