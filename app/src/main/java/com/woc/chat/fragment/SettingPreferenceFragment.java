package com.woc.chat.fragment;

import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.support.v7.internal.widget.ThemeUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import com.woc.chat.R;

/**
 * Created by Administrator on 2017/2/3.
 */
public class SettingPreferenceFragment extends PreferenceFragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        ListView lv = (ListView)view.findViewById(android.R.id.list);
        lv.setBackgroundColor(Color.BLACK);
        lv.setDivider(getResources().getDrawable(R.color.horizontal_vertical));
        addPreferencesFromResource(R.xml.setting);
        return view;
    }
}
