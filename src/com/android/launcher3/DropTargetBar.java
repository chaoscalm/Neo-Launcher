/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3;

import static com.android.launcher3.ButtonDropTarget.TOOLTIP_DEFAULT;
import static com.android.launcher3.anim.AlphaUpdateListener.updateVisibility;

import android.animation.TimeInterpolator;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewPropertyAnimator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.dragndrop.DragController.DragListener;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.testing.shared.TestProtocol;

/*
 * The top bar containing various drop targets: Delete/App Info/Uninstall.
 */
public class DropTargetBar extends FrameLayout
        implements DragListener, Insettable {

    protected static final int DEFAULT_DRAG_FADE_DURATION = 175;
    protected static final TimeInterpolator DEFAULT_INTERPOLATOR = Interpolators.ACCEL;

    private final Runnable mFadeAnimationEndRunnable =
            () -> updateVisibility(DropTargetBar.this);

    private final Launcher mLauncher;

    @ViewDebug.ExportedProperty(category = "launcher")
    protected boolean mDeferOnDragEnd;

    @ViewDebug.ExportedProperty(category = "launcher")
    protected boolean mVisible = false;

    private ButtonDropTarget[] mDropTargets;
    private ButtonDropTarget[] mTempTargets;
    private ViewPropertyAnimator mCurrentAnimation;

    private boolean mIsVertical = true;

    public DropTargetBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        mLauncher = Launcher.getLauncher(context);
    }

    public DropTargetBar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mLauncher = Launcher.getLauncher(context);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mDropTargets = new ButtonDropTarget[getChildCount()];
        for (int i = 0; i < mDropTargets.length; i++) {
            mDropTargets[i] = (ButtonDropTarget) getChildAt(i);
            mDropTargets[i].setDropTargetBar(this);
        }
        mTempTargets = new ButtonDropTarget[getChildCount()];
    }

    @Override
    public void setInsets(Rect insets) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        DeviceProfile grid = mLauncher.getDeviceProfile();
        mIsVertical = grid.isVerticalBarLayout();

        lp.leftMargin = insets.left;
        lp.topMargin = insets.top;
        lp.bottomMargin = insets.bottom;
        lp.rightMargin = insets.right;
        int tooltipLocation = TOOLTIP_DEFAULT;

        int horizontalMargin;
        if (grid.isTablet) {
            // XXX: If the icon size changes across orientations, we will have to take
            //      that into account here too.
            horizontalMargin = ((grid.widthPx - 2 * grid.edgeMarginPx
                    - (grid.inv.numColumns * grid.cellWidthPx))
                    / (2 * (grid.inv.numColumns + 1)))
                    + grid.edgeMarginPx;
        } else {
            horizontalMargin = getContext().getResources()
                    .getDimensionPixelSize(R.dimen.drop_target_bar_margin_horizontal);
        }
        lp.topMargin += grid.dropTargetBarTopMarginPx;
        lp.bottomMargin += grid.dropTargetBarBottomMarginPx;
        lp.width = grid.availableWidthPx - 2 * horizontalMargin;
        if (mIsVertical) {
            lp.leftMargin = (grid.widthPx - lp.width) / 2;
            lp.rightMargin = (grid.widthPx - lp.width) / 2;
        }
        lp.height = grid.dropTargetBarSizePx;
        lp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;

        DeviceProfile dp = mLauncher.getDeviceProfile();
        int horizontalPadding = dp.dropTargetHorizontalPaddingPx;
        int verticalPadding = dp.dropTargetVerticalPaddingPx;
        setLayoutParams(lp);
        for (ButtonDropTarget button : mDropTargets) {
            button.setTextSize(TypedValue.COMPLEX_UNIT_PX, grid.dropTargetTextSizePx);
            button.setToolTipLocation(tooltipLocation);
            button.setPadding(horizontalPadding, verticalPadding, horizontalPadding,
                    verticalPadding);
        }
    }

    public void setup(DragController dragController) {
        dragController.addDragListener(this);
        for (int i = 0; i < mDropTargets.length; i++) {
            dragController.addDragListener(mDropTargets[i]);
            dragController.addDropTarget(mDropTargets[i]);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        int heightSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);

        int visibleCount = getVisibleButtons(mTempTargets);
        if (visibleCount == 1) {
            int widthSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST);

            ButtonDropTarget firstButton = mTempTargets[0];
            firstButton.setTextVisible(true);
            firstButton.setIconVisible(true);
            firstButton.measure(widthSpec, heightSpec);
        } else if (visibleCount == 2) {
            DeviceProfile dp = mLauncher.getDeviceProfile();
            int verticalPadding = dp.dropTargetVerticalPaddingPx;
            int horizontalPadding = dp.dropTargetHorizontalPaddingPx;

            ButtonDropTarget firstButton = mTempTargets[0];
            firstButton.setTextVisible(true);
            firstButton.setIconVisible(true);
            firstButton.setTextMultiLine(false);
            // Reset second button padding in case it was previously changed to multi-line text.
            firstButton.setPadding(horizontalPadding, verticalPadding, horizontalPadding,
                    verticalPadding);

            ButtonDropTarget secondButton = mTempTargets[1];
            secondButton.setTextVisible(true);
            secondButton.setIconVisible(true);
            secondButton.setTextMultiLine(false);
            // Reset second button padding in case it was previously changed to multi-line text.
            secondButton.setPadding(horizontalPadding, verticalPadding, horizontalPadding,
                    verticalPadding);

            float scale = dp.getWorkspaceSpringLoadScale();
            int scaledPanelWidth = (int) (dp.getCellLayoutWidth() * scale);

            int availableWidth;
            if (dp.isTwoPanels) {
                // Both buttons for two panel fit to the width of one Cell Layout (less
                // half of the center gap between the buttons).
                int halfButtonGap = dp.dropTargetGapPx / 2;
                availableWidth = scaledPanelWidth - halfButtonGap / 2;
            } else {
                // Both buttons plus the button gap do not display past the edge of the scaled
                // workspace, less a pre-defined gap from the edge of the workspace.
                availableWidth = scaledPanelWidth - dp.dropTargetGapPx
                        - 2 * dp.dropTargetButtonWorkspaceEdgeGapPx;
            }

            int widthSpec = MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.AT_MOST);
            firstButton.measure(widthSpec, heightSpec);
            if (!mIsVertical) {
                // Remove icons and put the button's text on two lines if text is truncated.
                if (firstButton.isTextTruncated(availableWidth)) {
                    firstButton.setIconVisible(false);
                    firstButton.setTextMultiLine(true);
                    firstButton.setPadding(horizontalPadding, verticalPadding / 2,
                            horizontalPadding, verticalPadding / 2);
                }
            }

            if (!dp.isTwoPanels) {
                availableWidth -= firstButton.getMeasuredWidth() + dp.dropTargetGapPx;
                widthSpec = MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.AT_MOST);
            }
            secondButton.measure(widthSpec, heightSpec);
            if (!mIsVertical) {
                if (secondButton.isTextTruncated(availableWidth)) {
                    secondButton.setIconVisible(false);
                    secondButton.setTextMultiLine(true);
                    secondButton.setPadding(horizontalPadding, verticalPadding / 2,
                            horizontalPadding, verticalPadding / 2);
                }
            }
        }
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int visibleCount = getVisibleButtons(mTempTargets);
        if (visibleCount == 0) {
            return;
        }

        DeviceProfile dp = mLauncher.getDeviceProfile();
        // Center vertical bar over scaled workspace, accounting for hotseat offset.
        float scale = dp.getWorkspaceSpringLoadScale();
        Workspace<?> ws = mLauncher.getWorkspace();
        int barCenter;
        if (dp.isTwoPanels) {
            barCenter = (right - left) / 2;
        } else {
            int workspaceCenter = (ws.getLeft() + ws.getRight()) / 2;
            int cellLayoutCenter = ((dp.getInsets().left + dp.workspacePadding.left) + (dp.widthPx
                    - dp.getInsets().right - dp.workspacePadding.right)) / 2;
            int cellLayoutCenterOffset = (int) ((cellLayoutCenter - workspaceCenter) * scale);
            barCenter = workspaceCenter + cellLayoutCenterOffset - left;
        }

        if (visibleCount == 1) {
            ButtonDropTarget button = mTempTargets[0];
            button.layout(barCenter - (button.getMeasuredWidth() / 2), 0,
                    barCenter + (button.getMeasuredWidth() / 2), button.getMeasuredHeight());
        } else if (visibleCount == 2) {
            int buttonGap = dp.dropTargetGapPx;

            ButtonDropTarget leftButton = mTempTargets[0];
            ButtonDropTarget rightButton = mTempTargets[1];
            if (dp.isTwoPanels) {
                leftButton.layout(barCenter - leftButton.getMeasuredWidth() - (buttonGap / 2), 0,
                        barCenter - (buttonGap / 2), leftButton.getMeasuredHeight());
                rightButton.layout(barCenter + (buttonGap / 2), 0,
                        barCenter + (buttonGap / 2) + rightButton.getMeasuredWidth(),
                        rightButton.getMeasuredHeight());
            } else {
                int scaledPanelWidth = (int) (dp.getCellLayoutWidth() * scale);

                int leftButtonWidth = leftButton.getMeasuredWidth();
                int rightButtonWidth = rightButton.getMeasuredWidth();
                int extraSpace = scaledPanelWidth - leftButtonWidth - rightButtonWidth - buttonGap;

                int leftButtonStart = barCenter - (scaledPanelWidth / 2) + extraSpace / 2;
                int leftButtonEnd = leftButtonStart + leftButtonWidth;
                int rightButtonStart = leftButtonEnd + buttonGap;
                int rightButtonEnd = rightButtonStart + rightButtonWidth;

                leftButton.layout(leftButtonStart, 0, leftButtonEnd,
                        leftButton.getMeasuredHeight());
                rightButton.layout(rightButtonStart, 0, rightButtonEnd,
                        rightButton.getMeasuredHeight());
            }
        }
    }

    private int getVisibleButtons(ButtonDropTarget[] outVisibleButtons) {
        int visibleCount = 0;
        for (ButtonDropTarget button : mDropTargets) {
            if (button.getVisibility() != GONE) {
                outVisibleButtons[visibleCount] = button;
                visibleCount++;
            }
        }
        return visibleCount;
    }

    public void animateToVisibility(boolean isVisible) {
        if (TestProtocol.sDebugTracing) {
            Log.d(TestProtocol.NO_DROP_TARGET, "8");
        }
        if (mVisible != isVisible) {
            mVisible = isVisible;

            // Cancel any existing animation
            if (mCurrentAnimation != null) {
                mCurrentAnimation.cancel();
                mCurrentAnimation = null;
            }

            float finalAlpha = mVisible ? 1 : 0;
            if (Float.compare(getAlpha(), finalAlpha) != 0) {
                setVisibility(View.VISIBLE);
                mCurrentAnimation = animate().alpha(finalAlpha)
                        .setInterpolator(DEFAULT_INTERPOLATOR)
                        .setDuration(DEFAULT_DRAG_FADE_DURATION)
                        .withEndAction(mFadeAnimationEndRunnable);
            }

        }
    }

    /*
     * DragController.DragListener implementation
     */
    @Override
    public void onDragStart(DropTarget.DragObject dragObject, DragOptions options) {
        if (TestProtocol.sDebugTracing) {
            Log.d(TestProtocol.NO_DROP_TARGET, "7");
        }
        animateToVisibility(true);
    }

    /**
     * This is called to defer hiding the delete drop target until the drop animation has completed,
     * instead of hiding immediately when the drag has ended.
     */
    protected void deferOnDragEnd() {
        mDeferOnDragEnd = true;
    }

    @Override
    public void onDragEnd() {
        if (!mDeferOnDragEnd) {
            animateToVisibility(false);
        } else {
            mDeferOnDragEnd = false;
        }
    }

    public ButtonDropTarget[] getDropTargets() {
        return getVisibility() == View.VISIBLE ? mDropTargets : new ButtonDropTarget[0];
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (TestProtocol.sDebugTracing) {
            if (visibility == VISIBLE) {
                Log.d(TestProtocol.NO_DROP_TARGET, "9");
            } else {
                Log.d(TestProtocol.NO_DROP_TARGET, "Hiding drop target", new Exception());
            }
        }
    }
}
