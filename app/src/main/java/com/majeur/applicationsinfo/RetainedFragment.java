package com.majeur.applicationsinfo;

import android.app.Fragment;
import android.os.Bundle;

import java.util.List;

public class RetainedFragment extends Fragment {

    static final String FRAGMENT_TAG = "fragment_retained";

    private List<MainListFragment.Item> mList;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    public void setList(List<MainListFragment.Item> list) {
        mList = list;
    }

    public List<MainListFragment.Item> getList() {
        return mList;
    }
}
