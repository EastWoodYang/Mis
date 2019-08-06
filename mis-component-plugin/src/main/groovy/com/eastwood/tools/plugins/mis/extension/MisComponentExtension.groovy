package com.eastwood.tools.plugins.mis.extension

import org.gradle.util.ConfigureUtil

class MisComponentExtension {

    private Map<String, MisComponent> misComponents = new HashMap<>()

    Map<String, MisComponent> getMisComponents() {
        return misComponents
    }

    def methodMissing(String name, def args) {
        if (args[0] instanceof Closure) {
            MisComponent misComponent = new MisComponent()
            ConfigureUtil.configure(args[0], misComponent)
            misComponents.put(name, misComponent)
        }
    }

    def propertyMissing(String name, def arg) {

    }

}