package com.eastwood.tools.plugins.mis.core.extension

import org.gradle.api.Project

interface OnMisExtensionListener {

    void onPublicationAdded(Project childProject, Publication publication)

}
