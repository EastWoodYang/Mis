package com.eastwood.demo.library;

import com.google.gson.annotations.SerializedName;

/**
 * @author eastwood
 * createDate: 2018-06-14
 */
public class LibraryInfo {

    @SerializedName("name")
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
