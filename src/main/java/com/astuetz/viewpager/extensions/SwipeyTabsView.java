/*
 * Copyright (C) 2011 Andreas Stuetz <andreas.stuetz@gmail.com>
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

package com.astuetz.viewpager.extensions;

import java.util.ArrayList;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.RelativeLayout;

public class SwipeyTabsView extends RelativeLayout implements
        OnPageChangeListener, OnTouchListener {

    @SuppressWarnings("unused")
    private static final String TAG = "com.astuetz.viewpager.extensions";

    // Scrolling direction
    private enum Direction {
        Left, None, Right;

        public int getPrev(TabPosition position) {
            return this == Direction.Left ? position.leftPos : position.oldPos;
        }

        public int getNext(TabPosition position) {
            return this == Direction.Right ? position.leftPos : position.oldPos;
        }

        public float getX(float positionOffset) {
            return this == Direction.Left ? 1 - positionOffset : positionOffset;
        }

    }

    private int mPosition;

    // This ArrayList stores the positions for each tab.
    private ArrayList<TabPosition> mPositions = new ArrayList<TabPosition>();

    // Length of the horizontal fading edges
    private static final int SHADOW_WIDTH = 20;

    private ViewPager mPager;

    private TabsAdapter mAdapter;

    private int mTabsCount = 0;

    private int mWidth = 0;

    private int mCenter = 0;

    private int mHighlightOffset = 0;

    // store measure specs for manual re-measuring after
    // data has changed
    private int mWidthMeasureSpec = 0;
    private int mHeightMeasureSpec = 0;

    // The offset at which tabs are going to
    // be moved, if they are outside the screen
    private int mOutsideOffset = -1;

    private float mDragX = 0.0f;

    /** Last offset of the view pager. */
    private int mLastOffsetX;

    /** Original direction. */
    private Direction mOrigDirection;

    /** Selected index. */
    private int mSelectedIndex;

    public SwipeyTabsView(Context context) {
        this(context, null);
    }

    public SwipeyTabsView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SwipeyTabsView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ViewPagerExtensions, defStyle, 0);

        mOutsideOffset = (int) a.getDimension(R.styleable.ViewPagerExtensions_outsideOffset, -1);

        a.recycle();

        setHorizontalFadingEdgeEnabled(true);
        setFadingEdgeLength((int) (getResources().getDisplayMetrics().density * SHADOW_WIDTH));
        setWillNotDraw(false);

        setOnTouchListener(this);
    }

    @Override
    protected float getLeftFadingEdgeStrength() {
        return 1.0f;
    }

    @Override
    protected float getRightFadingEdgeStrength() {
        return 1.0f;
    }

    /**
     * Notify the view that new data is available.
     */
    public void notifyDatasetChanged() {
        if (mPager != null && mAdapter != null) {
            initTabs();
            measure(mWidthMeasureSpec, mHeightMeasureSpec);
            calculateNewPositions(true);
        }
    }

    public void setAdapter(TabsAdapter adapter) {
        this.mAdapter = adapter;

        if (mPager != null && mAdapter != null) initTabs();
    }

    /**
     * Binds the {@link ViewPager} to this instance
     * 
     * @param pager
     *            An instance of {@link ViewPager}
     */
    public void setViewPager(ViewPager pager) {
        this.mPager = pager;
        mPager.setOnPageChangeListener(this);

        mLastOffsetX = pager.getScrollX();

        if (mPager != null && mAdapter != null) initTabs();
    }

    /**
     * Initialize and add all tabs to the Layout
     */
    private void initTabs() {

        // Remove all old child views
        removeAllViews();

        mPositions.clear();

        if (mAdapter == null || mPager == null) return;

        for (int i = 0; i < mPager.getAdapter().getCount(); i++) {
            addTab(mAdapter.getView(i), i);
            mPositions.add(new TabPosition());
        }

        mTabsCount = getChildCount();

        mPosition = mPager.getCurrentItem();
        mSelectedIndex = mPosition;
        getChildAt(mSelectedIndex).setSelected(true);
    }

    /**
     * Adds a new {@link SwipeyTabButton} to the layout
     * 
     * @param index
     *            The index from the Pagers adapter
     * @param title
     *            The title which should be used
     */
    public void addTab(View tab, final int index) {
        if (tab == null) return;

        addView(tab);

        tab.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mPager.setCurrentItem(index);
            }
        });

        tab.setOnTouchListener(this);
    }

    /**
    *
    */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {

        // Set a default outsideOffset
        if (mOutsideOffset < 0) mOutsideOffset = w;

        mWidth = w;
        mCenter = w / 2;
        mHighlightOffset = w / 5;

        if (mPager != null) mPosition = mPager.getCurrentItem();
        calculateNewPositions(true);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

        int maxTabHeight = 0;

        mWidthMeasureSpec = widthMeasureSpec;
        mHeightMeasureSpec = heightMeasureSpec;

        final int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(
                MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.AT_MOST);
        final int childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(
                MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.AT_MOST);

        for (int i = 0; i < mTabsCount; i++) {
            View child = getChildAt(i);

            if (child.getVisibility() == GONE) continue;

            child.measure(childWidthMeasureSpec, childHeightMeasureSpec);

            mPositions.get(i).width = child.getMeasuredWidth();
            mPositions.get(i).height = child.getMeasuredHeight();

            maxTabHeight = Math.max(maxTabHeight, mPositions.get(i).height);
        }

        setMeasuredDimension(
                resolveSize(0, widthMeasureSpec),
                resolveSize(
                        maxTabHeight + getPaddingTop() + getPaddingBottom(),
                        heightMeasureSpec));

    }

    private void higlightTab(View tab, int position) {
        if (tab instanceof SwipeyTab) {

            int tabCenter = mPositions.get(position).currentPos
                    + tab.getWidth() / 2;
            int diff = Math.abs(mCenter - tabCenter);
            int p = 100 * diff / mHighlightOffset;

            ((SwipeyTab) tab)
                    .setHighlightPercentage(diff <= mHighlightOffset ? 100 - p
                            : 0);

        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {

        int paddingTop = getPaddingTop();

        for (int i = 0; i < mTabsCount; i++) {

            View tab = getChildAt(i);
            TabPosition pos = mPositions.get(i);

            tab.layout(pos.currentPos, paddingTop, pos.currentPos + pos.width,
                    paddingTop + pos.height);

            higlightTab(tab, i);

        }

    }

    /**
     * This method calculates the previous, current and next position for each
     * tab
     * 
     * -5 -4 -3 /-2 |-1 0 +1| +2\ +3 +4 +5
     * 
     * There are the following cases:
     * 
     * [1] -5 to -3 are outside the screen 
     * [2] -2 is outside the screen, may come into the screen when swiping right 
     * [3] -1 is inside the screen, aligned at the left 
     * [4] 0 is inside the screen, aligned at the center 
     * [5] +1 is inside the screen, aligned at the right 
     * [6] +2 is outside the screen, may come into the screen when swiping left 
     * [7] +3 to +5 are outside the screen
     * 
     * @param layout
     *            If true, all tabs will be aligned at their initial position
     */
    private void calculateNewPositions(boolean layout) {

        if (mTabsCount == 0) return;

        final int currentItem = mPosition;

        for (int i = 0; i < mTabsCount; i++) {

            mOrigDirection = Direction.None;

            if (i < currentItem - 2) alignLeftOutside(i, false);
            else if (i == currentItem - 2) alignLeftOutside(i, true);
            else if (i == currentItem - 1) alignLeft(i);
            else if (i == currentItem) alignCenter(i);
            else if (i == currentItem + 1) alignRight(i);
            else if (i == currentItem + 2) alignRightOutside(i, true);
            else if (i > currentItem + 2) alignRightOutside(i, false);

        }

        preventFromOverlapping();

        if (layout) {
            for (TabPosition p : mPositions) {
                p.currentPos = p.oldPos;
            }
            requestLayout();
        }

    }

    private int leftOutside(int position) {
        View tab = getChildAt(position);
        final int width = tab.getMeasuredWidth();
        return width * (-1) - mOutsideOffset;
    }

    private int left(int position) {
        View tab = getChildAt(position);
        return 0 - tab.getPaddingLeft();
    }

    private int center(int position) {
        View tab = getChildAt(position);
        final int width = tab.getMeasuredWidth();
        return mWidth / 2 - width / 2;
    }

    private int right(int position) {
        View tab = getChildAt(position);
        final int width = tab.getMeasuredWidth();
        return mWidth - width + tab.getPaddingRight();
    }

    private int rightOutside(int position) {
        return mWidth + mOutsideOffset;
    }

    private void alignLeftOutside(int position,
            boolean canComeToLeft) {
        TabPosition pos = mPositions.get(position);

        pos.oldPos = leftOutside(position);
        pos.leftPos = pos.oldPos;
        pos.rightPos = canComeToLeft ? left(position) : pos.oldPos;
    }

    private void alignLeft(int position) {
        TabPosition pos = mPositions.get(position);

        pos.leftPos = leftOutside(position);
        pos.oldPos = left(position);
        pos.rightPos = center(position);
    }

    private void alignCenter(int position) {
        TabPosition pos = mPositions.get(position);

        pos.leftPos = left(position);
        pos.oldPos = center(position);
        pos.rightPos = right(position);
    }

    private void alignRight(int position) {
        TabPosition pos = mPositions.get(position);

        pos.leftPos = center(position);
        pos.oldPos = right(position);
        pos.rightPos = rightOutside(position);
    }

    private void alignRightOutside(int position, boolean canComeToRight) {
        TabPosition pos = mPositions.get(position);

        pos.oldPos = rightOutside(position);
        pos.rightPos = pos.oldPos;
        pos.leftPos = canComeToRight ? right(position) : pos.oldPos;
    }

    /**
    *
    */
    private void preventFromOverlapping() {

        final int currentItem = mPosition;

        TabPosition leftOutside = currentItem > 1 ? mPositions
                .get(currentItem - 2) : null;
        TabPosition left = currentItem > 0 ? mPositions
                .get(currentItem - 1) : null;
        TabPosition center = mPositions.get(currentItem);
        TabPosition right = currentItem < mTabsCount - 1 ? mPositions
                .get(currentItem + 1) : null;
        TabPosition rightOutside = currentItem < mTabsCount - 2 ? mPositions
                .get(currentItem + 2) : null;

        if (leftOutside != null) {
            if (leftOutside.rightPos + leftOutside.width >= left.rightPos) {
                leftOutside.rightPos = left.rightPos - leftOutside.width;
            }
        }

        if (left != null) {
            if (left.oldPos + left.width >= center.oldPos) {
                left.oldPos = center.oldPos - left.width;
            }
            if (center.rightPos <= left.rightPos + left.width) {
                center.rightPos = left.rightPos + left.width;
            }
        }

        if (right != null) {
            if (right.oldPos <= center.oldPos + center.width) {
                right.oldPos = center.oldPos + center.width;
            }
            if (center.leftPos + center.width >= right.leftPos) {
                center.leftPos = right.leftPos - center.width;
            }
        }

        if (rightOutside != null) {
            if (rightOutside.leftPos <= right.leftPos + right.width) {
                rightOutside.leftPos = right.leftPos + right.width;
            }
        }
    }

    private void updateDir(Direction dir) {
        if (mOrigDirection == Direction.None) {
            mOrigDirection = dir;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onPageScrollStateChanged(int state) {
        // nothing
    }

    /**
     * At this point the scrolling direction is determined and every child is
     * interpolated to its previous or next position
     * 
     * {@inheritDoc}
     */
    @Override
    public void onPageScrolled(int position, float positionOffset,
            int positionOffsetPixels) {

        if (position != mPosition) {
            mPosition = position;
            calculateNewPositions(false);
        }

        // Check if the user is swiping to the left or to the right
        Direction dir;
        int pageScroll = mPager.getScrollX();
        if (pageScroll < mLastOffsetX) {
            dir = Direction.Left;
        } else if (pageScroll > mLastOffsetX) {
            dir = Direction.Right;
        } else {
            dir = Direction.None;
        }
        mLastOffsetX = pageScroll;

        updateDir(dir);
        float x = mOrigDirection.getX(positionOffset);

        // Iterate over all tabs and set their current positions
        for (int i = 0; i < mTabsCount; i++) {
            TabPosition pos = mPositions.get(i);

            float y0 = mOrigDirection.getPrev(pos);
            float y1 = mOrigDirection.getNext(pos);

            float dest = y0 + (y1 - y0) * x;
            pos.currentPos = (int) (dest);
        }

        offsetChildren();
    }

    private void offsetChildren() {
        int count = mTabsCount;
        ArrayList<TabPosition> positions = mPositions;
        for (int i = 0; i < count; i++) {
            View tab = getChildAt(i);
            TabPosition pos = positions.get(i);

            tab.offsetLeftAndRight(pos.currentPos - tab.getLeft());

            higlightTab(tab, i);
        }
        invalidate();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onPageSelected(int position) {
        View selected = getChildAt(position);
        View unselected = getChildAt(mSelectedIndex);
        mSelectedIndex = position;
        selected.setSelected(true);
        unselected.setSelected(false);
    }

    /**
     * Helper class which holds different positions (and the width) for a tab
     * 
     */
    private static class TabPosition {

        public int oldPos;

        public int leftPos;
        public int rightPos;

        public int currentPos;

        public int width;
        public int height;

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();

            sb.append("oldPos: ").append(oldPos).append(", ");
            sb.append("leftPos: ").append(leftPos).append(", ");
            sb.append("rightPos: ").append(rightPos).append(", ");
            sb.append("currentPos: ").append(currentPos);

            return sb.toString();
        }

    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        float x = event.getRawX();

        switch (event.getAction()) {
        case MotionEvent.ACTION_DOWN:
            mDragX = x;
            mPager.beginFakeDrag();
            break;
        case MotionEvent.ACTION_MOVE:
            if (!mPager.isFakeDragging())
                break;
            mPager.fakeDragBy((mDragX - x) * (-1));
            mDragX = x;
            break;
        case MotionEvent.ACTION_UP:
            if (!mPager.isFakeDragging())
                break;
            mPager.endFakeDrag();
            break;
        }

        return v.equals(this) ? true : super.onTouchEvent(event);
    }

}
