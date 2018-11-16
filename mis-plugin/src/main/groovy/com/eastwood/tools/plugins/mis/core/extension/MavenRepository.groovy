package com.eastwood.tools.plugins.mis.core.extension

class MavenRepository {

    String url
    Closure credentials

    void url(String url) {
        this.url = url
    }

    void credentials(Closure credentials) {
        this.credentials = credentials
    }

}