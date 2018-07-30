package com.eastwood.demo.app;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Toast;

import com.eastwood.common.autoinject.AutoTarget;
import com.eastwood.common.mis.MisService;
import com.eastwood.demo.library.ILibraryService;
import com.eastwood.demo.library.LibraryModel;

/**
 * @author eastwood
 * createDate: 2018-06-08
 */
public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        register();

        ILibraryService libraryService = MisService.getService(ILibraryService.class);
        LibraryModel libraryModel = libraryService.getLibraryInfo();
        Toast.makeText(this, libraryModel.getName(), Toast.LENGTH_LONG).show();
    }

    @AutoTarget
    private void register() {

    }

}
