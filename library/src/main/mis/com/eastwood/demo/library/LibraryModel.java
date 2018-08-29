package com.eastwood.demo.library;

import com.google.gson.Gson;

/**
 * @author eastwood
 * createDate: 2018-06-14
 */
public class LibraryModel {

    private String name;

    public String getName() {
        Gson gson;
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
