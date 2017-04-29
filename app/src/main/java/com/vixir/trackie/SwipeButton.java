package com.vixir.trackie;

import android.content.Context;
import android.support.v7.widget.AppCompatButton;
import android.util.AttributeSet;
import android.view.MotionEvent;

public class SwipeButton extends AppCompatButton {
    private float x1;
    //x coordinate of where user first touches the button
    private float y1;
    //y coordinate of where user first touches the button
    private String originalButtonText;
    //the text on the button
    private boolean confirmThresholdCrossed;
    //whether the threshold distance beyond which action is considered confirmed is crossed or not
    private boolean swipeTextShown;
    //whether the text currently on the button is the text shown while swiping or the original text

    public SwipeButton(Context context) {
        super(context);
    }

    public SwipeButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SwipeButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        switch (event.getAction()) {
            // when user first touches the screen we get x and y coordinate
            case MotionEvent.ACTION_DOWN: {
                //when user first touches the screen change the button text to desired value
                x1 = event.getX();
                y1 = event.getY();
                break;
            }
            case MotionEvent.ACTION_UP: {
                //when the user releases touch then revert back the text
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                //here we'll capture when the user swipes from left to right and write the logic to create the swiping effect
                break;
            }
        }
        return super.onTouchEvent(event);
    }

}