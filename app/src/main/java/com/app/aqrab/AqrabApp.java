package com.app.aqrab;

import android.app.Application;
import android.content.Context;

public class AqrabApp extends Application {

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleHelper.onAttach(base));
    }
}
