package com.eastwood.tools.plugins.mis.extension

class MavenRepository {

    String url
    Object authentication

    void url(String url) {
        this.url = url
    }

    void authentication(Object values) {
        this.authentication = values
    }

}