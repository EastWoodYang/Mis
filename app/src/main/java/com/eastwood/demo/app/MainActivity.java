package com.eastwood.demo.app;

import android.app.Activity;
import android.os.Bundle;

import com.eastwood.common.mis.MisService;

/**
 * @author eastwood
 * createDate: 2018-06-08
 */
public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //
        MisService.register(ITestService.class, new TestService());
        MisService.register(ITestService.class, TestService.class);


    }
}
