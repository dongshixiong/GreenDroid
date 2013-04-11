
package greendroid.widget;


import com.cyrilmottier.android.greendroid.R;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;

public class TextActionBarItem extends ActionBarItem {

    protected View createItemView() {
        return LayoutInflater.from(mContext).inflate(
                R.layout.gd_action_bar_item_text, mActionBar, false);
    }

    protected void prepareItemView() {
        super.prepareItemView();
        final Button button = (Button) mItemView
                .findViewById(R.id.gd_action_bar_item);
        button.setContentDescription(mContentDescription);
        button.setText(mContentDescription);
    }

    protected void onContentDescriptionChanged() {
        super.onContentDescriptionChanged();
        final Button button = (Button) mItemView
                .findViewById(R.id.gd_action_bar_item);
        button.setContentDescription(mContentDescription);
        button.setText(mContentDescription);
    }

    protected void onDrawableChanged() {
        super.onDrawableChanged();
        Button imageButton = (Button) mItemView
                .findViewById(R.id.gd_action_bar_item);
        imageButton.setBackgroundDrawable(mDrawable);
    }

}
