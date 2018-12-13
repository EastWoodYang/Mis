package com.eastwood.tools.plugins.mis.core.extension

class Dependencies {

    List<Object> implementation
    List<Object> compileOnly

    void implementation(Object value) {
        if (implementation == null) {
            implementation = new ArrayList<>()
        }
        implementation.add(value)
    }

    void compileOnly(Object value) {
        if (compileOnly == null) {
            compileOnly = new ArrayList<>()
        }
        compileOnly.add(value)
    }

}