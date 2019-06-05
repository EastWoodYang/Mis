# MIS
模块接口服务（Module Interface Service）

MIS主要解决的问题是如何在一个模块内维护其对外暴露的接口（包括打包发布），而不是把接口和接口实现分离到两个不同的模块。

<img src='https://github.com/EastWoodYang/Mis/blob/master/picture/1.png'/>

## Usage

#### 引用 mis 插件

在根项目的build.gradle中添加mis插件的**classpath**：
```
buildscript {
    dependencies {
        ...
        classpath 'com.eastwood.tools.plugins:mis:2.0.1'
    }
}
```

在根项目的build.gradle中添加mis插件的**相关配置**：
```
...

apply plugin: 'mis'

mis {

    compileSdkVersion 27

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }

}
```
* compileSdkVersion 同 android { compileSdkVersion ... }
* compileOptions 同 android { compileOptions { ... } }

#### 创建 mis 目录

**Gradle Sync**后，在**java**同级目录创建**mis**文件夹

<img src='https://github.com/EastWoodYang/Mis/blob/master/picture/2.png'/>

#### 定义接口，并实现接口服务

直接在**mis**文件夹下，创建对应的包名、接口类和数据Model。并在**java**文件夹下实现接口服务。

<img src='https://github.com/EastWoodYang/Mis/blob/master/picture/1.png'/>

#### 在模块目录内，创建单独的mis.gradle文件, 并在内声明mis对应的publication

mis.gradle:
```
mis {
    publications {
        main {
            groupId 'com.eastwood.demo'
            artifactId 'library-sdk'
            // version '1.0.0-SNAPSHOT'

            dependencies {
                compileOnly 'com.google.code.gson:gson:2.8.1'
            }
        }
    }
    ...
}
```

* `main`指的是`src/main/java`中的`main`，除了`main`之外，其值还可以为 build types和product flavors对应的值，即对应目录下的mis。比如与`src/debug/java`对应的`src/debug/mis`。

* `groupId`、`artifactId`、`version`对应的是Maven的[GAV](https://maven.apache.org/guides/mini/guide-naming-conventions.html)。**初次配置时不设置`version`，发布至maven时设置`version`。**

* 在`dependencies`中可声明该mis Publication编译和运行时需用到的第三方库，仅支持`compileOnly`和`implementation`。如果mis文件夹下的类使用了kotlin语法，需要添加kotlin相关的依赖，比如'org.jetbrains.kotlin:kotlin-stdlib-jdk7:$kotlin_version'。

####  发布mis publication 至 Maven
在根项目的build.gradle中添加mis插件的**repositories配置**：
```
mis {

    compileSdkVersion 27
    ...

    repositories {
        maven {
            url "http://***"
            credentials {
                username '***'
                password '***'
            }
        }
    }
    ...
}
```

* 发布用到的插件是`maven-publish`，其中`repositories`相关设置请查阅[# Maven Publish Plugin](https://docs.gradle.org/current/userguide/publishing_maven.html#publishing_maven:repositories)

**Gradle Sync**后，打开Gradle Tasks View，选择**publishMis[...]PublicationToMavenRepository**执行发布任务。

<img src='https://github.com/EastWoodYang/Mis/blob/master/picture/3.png'/>

其中publishMis[...]PublicationToMavenLocal 是发布至本地maven。如果使用本地maven，请将`mavenLocal()`添加至根项目的build.gradle中，比如：
```
allprojects {
    repositories {
        google()
        jcenter()
        mavenLocal()
    }
}
```

***
上文介绍了如何通过mis插件创建接口并发布到maven，接下来介绍接口的注册和调用。

####  注册接口服务
注册接口需要有个服务容器来管理接口和接口的实现对象。mis提供了一个简单的**MisService**服务容器，可根据自己项目实际情况自行选用。

在模块build.gradle中添加**MisService**服务容器库的引用：
```
dependencies {
    implementation 'com.eastwood.common:mis:1.0.0'
}
```

在**MisService**服务容器中注册服务，可以使用 服务接口 + 服务接口的实现对象 **或** 服务接口的实现类 进行注册，例如：
```
// 服务接口 + 服务接口的实现对象
MisService.register(ILibraryService.class, new LibraryService());

// 服务接口 + 服务接口的实现类
MisService.register(ILibraryService.class, LibraryService.class);
```

在Demo样例中，接口所在的模块通过AutoInject在编译期主动注册接口。（这推荐下# **[AutoInject](https://github.com/EastWoodYang/AutoInject)**）

#### 获取接口服务

在其他模块build.gradle中添加mis库，以及发布至maven的接口库：

```
dependencies {
    implementation 'com.eastwood.common:mis:1.0.0'
    implementation 'com.eastwood.demo:library-sdk:1.0.0-SNAPSHOT'
}
```

Gradle Sync后，就可以通过接口在**MisService**服务容器中查找对应的接口服务并调用，比如：
```
ILibraryService libraryService = MisService.getService(ILibraryService.class);
libraryService.getLibraryInfo()
```

## Q&A
#### 1.mis目录下的类会参与编译吗？
不会。虽然`mis`目录下的类能被`java`目录下的类直接引用，但不会参与编译，真正参与编译的是该`mis`目录生成的jar包，其位于当前工程`.gradle/mis`下。在当前工程Sync&Build的时候，mis插件会对这些配置了publication的`mis`目录进行编译打包生成jar包，并且依赖该jar包。

`mis`目录下的类之所以能被`java`目录下的类直接引用，是因为`mis`目录被设置为sourceSets aidl的src目录，而Android Studio对sourceSets aidl的src目录有特别支持。

#### 2.没有Maven私服，所有模块都在一个工程下，其他模块怎么引用接口？
不设置`publication`的`version`。通过`misPublication`声明依赖，比如：
```
dependencies {
    ...
    implementation misPublication('com.eastwood.demo:library-sdk')
}
```
`misPublication`运行机理是会自动在当前工程`.gradle/mis`下查找是否有对应的mis提供的jar包。如果有，就使用对应的mis提供的jar包；如果没有且指定了`version`，就使用maven上的jar包。

#### 3.将接口发布到maven后，其他模块通过misPublication声明依赖，那jar包用的是`.gradle/mis`下的还是maven上的？
接口被发布到maven后，其`.gradle/mis`下的jar包会被删除，接口所在的模块根据`publication`中设置的GAV使用maven上的jar包。如果其他模块通过misPublication声明对其依赖，比如：
```
dependencies {
    ...
    implementation misPublication('com.eastwood.demo:library-sdk')
    // 或 implementation misPublication('com.eastwood.demo:library-sdk:1.0.0-SNAPSHOT')
}
```
不管`misPublication`中是否设置了的`version`，都会使用maven上的jar包，其版本同接口所在的模块`publication`中的GAV。

当`mis`目录下类发生实质性的修改后（生成不同的jar包），在当前工程Sync&Build的时，会在`.gradle/mis`下的重新生成jar包，接口所在的模块不管`publication`中是否设置`version`，都使用`.gradle/mis`下的jar包。如果其他模块通过misPublication声明对其依赖，不管`misPublication`中是否设置的`version`，都会使用`.gradle/mis`下的jar包。

#### 4.为什么在Gradle Tasks View中找不到`publishing`相关发布Task？
初次发布时，请检查对应的`publication`是否已经设置的`version`，以及是否添加相关`repositories`。

#### 5.在mis publication `dependencies {}`中声明的依赖会作用于mis publication所在的模块吗？
mis publication `dependencies {}`是用于声明mis publication编译和运行时需用到的第三方库，但不会作用于mis publication所在的模块。
在开发的时候，`src/java`下的类能引用到mis依赖的第三方库类，但编译时会报错，提示找不到对应的类。
产生这种情况的原因是mis插件在Gradle sync时，将声明的依赖传递给mis publication所在的模块。但clean或build时不传递。

#### 6.如果mis publication 依赖于其他mis publication怎么处理？
建议使用`misPublication`包裹GAV，比如：
```
mis {
    publications {
        main {
            groupId 'com.eastwood.demo'
            artifactId 'library-sdk'

            dependencies {
                implementation misPublication('com.eastwood.demo:module-main-sdk:1.0.0')
            }
        }
    }

    repositories {
        ...
    }

}
```
这里`misPublication`的作用是，如果所依赖的mis publication也在当前项目，那么与其所提供jar包的方式保持一致。


## License

```
   Copyright 2018 EastWood Yang

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
```
