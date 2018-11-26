package com.eastwood.tools.plugins.mis.core

import com.android.build.gradle.AppExtension
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryExtension
import com.eastwood.tools.plugins.mis.core.extension.Publication
import org.gradle.api.Project

class MisUtil {

    static addMisSourceSets(Project project) {
        BaseExtension baseExtension = project.extensions.getByName('android')
        addMisSourceSets(baseExtension, 'main')
        if(baseExtension instanceof AppExtension) {
            AppExtension appExtension = (AppExtension) baseExtension
            appExtension.getApplicationVariants().each {
                addMisSourceSets(baseExtension, it.buildType.name)
                it.productFlavors.each {
                    addMisSourceSets(baseExtension, it.name)
                }
                if(it.productFlavors.size() >= 1) {
                    if(it.productFlavors.size() > 1) {
                        addMisSourceSets(baseExtension, it.flavorName)
                    }
                    addMisSourceSets(baseExtension, it.name)
                }
            }
        } else if(baseExtension instanceof LibraryExtension) {
            LibraryExtension libraryExtension = (LibraryExtension) baseExtension
            libraryExtension.getLibraryVariants().each {
                addMisSourceSets(baseExtension, it.buildType.name)
                it.productFlavors.each {
                    addMisSourceSets(baseExtension, it.name)
                }
                if(it.productFlavors.size() >= 1) {
                    if(it.productFlavors.size() > 1) {
                        addMisSourceSets(baseExtension, it.flavorName)
                    }
                    addMisSourceSets(baseExtension, it.name)
                }
            }
        }

    }

    static addMisSourceSets(BaseExtension baseExtension, String name) {
        def obj = baseExtension.sourceSets.getByName(name)
        obj.java.srcDirs.each {
            obj.aidl.srcDirs(it.absolutePath.replace('java', 'mis'))
        }
    }

    static boolean isMicroModule(Project project) {
        return project.plugins.findPlugin("micro-module")
    }

    static boolean hasAndroidPlugin(Project project) {
        if (project.plugins.findPlugin("com.android.application") || project.plugins.findPlugin("android") ||
                project.plugins.findPlugin("com.android.test")) {
            return true
        } else if (project.plugins.findPlugin("com.android.library") || project.plugins.findPlugin("android-library")) {
            return true
        } else {
            return false
        }
    }

    static Map<String, ?> optionsFilter(Publication publication) {
        Map<String, ?> opts = new HashMap<>()
        opts.put("group", publication.groupId)
        opts.put("name", publication.artifactId)
        opts.put("version", publication.version)
        return opts
    }

    static void copyFile(File source, File target) {
        try {
            InputStream input = new FileInputStream(source)
            OutputStream output = new FileOutputStream(target)
            byte[] buf = new byte[1024]
            int bytesRead
            while ((bytesRead = input.read(buf)) > 0) {
                output.write(buf, 0, bytesRead)
            }
            input.close()
            output.close()
        } catch (IOException e) {
            e.printStackTrace()
        }
    }

}