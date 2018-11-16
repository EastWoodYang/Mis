package com.eastwood.tools.plugins.mis.core.extension

class Dependencies {

    List<String> implementation
    List<String> compileOnly

    void implementation(String value) {
        if (implementation == null) {
            implementation = new ArrayList<>()
        }
        implementation.add(value)
    }

    void compileOnly(String value) {
        if (compileOnly == null) {
            compileOnly = new ArrayList<>()
        }
        compileOnly.add(value)
    }

}