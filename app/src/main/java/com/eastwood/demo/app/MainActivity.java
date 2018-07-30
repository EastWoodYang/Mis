package com.eastwood.demo.app;

import android.app.Activity;
import android.os.Bundle;

import com.eastwood.common.mis.MisService;
import com.eastwood.demo.library.ILibraryService;

/**
 * @author eastwood
 * createDate: 2018-06-08
 */
public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ILibraryService libraryService = MisService.getService(ILibraryService.class);
        libraryService.getLibraryInfo();
    }
}
