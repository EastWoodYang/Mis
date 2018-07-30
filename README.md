# MIS
MIS - 模块接口服务（Module Interface Service）

模块A对外暴露SDK（接口+数据Model），在运行时，通过接口将对应的接口服务注册到服务容器中。

模块B引用模块A对外暴露的SDK，通过SDK中的接口在服务容器中查找对应的接口服务并调用。

基于上述，MIS需要解决的问题：

* 模块如何对外暴露SDK
* 如何通过接口查找对应的接口服务

**模块如何对外暴露SDK**

这里所述的SDK对应的就是一个jar包，其实就是Android module中，打一个包含接口和数据model的jar包。

**如何通过接口查找对应的服务**

维护一个Map，接口为key, 对应的接口服务为value，是不是就可以了？


## Usage

#### 引用 mis 插件

在根项目的build.gradle中添加 **mis 插件**的引用：
```
buildscript {
    dependencies {
        classpath 'com.eastwood.tools.plugins:mis:1.0.5'
    }
}
```

在模块的build.gradle中添加**mis 插件**：
```
apply plugin: 'mis' 
```

#### 创建 mis 目录

在**src/main/java** 同级目录，创建**mis**文件夹

![](https://user-gold-cdn.xitu.io/2018/7/30/164eab1718831a1e?w=270&h=321&f=png&s=6663)

**定义接口和数据Model，实现对应接口服务**

直接在**mis**文件夹下，创建对应的包名、接口类和数据Model（即对外暴露SDK）。并在**java**文件夹下，实现对应的接口服务。

![接口+数据Model](https://user-gold-cdn.xitu.io/2018/7/30/164eab5630a021d4?w=308&h=421&f=png&s=10071)

#### 声明当前模块的sdk

在模块 build.gradle 中dependencies中,通过 `misSource` 声明，例如：

```
dependencies {
    ...
    implementation misSource(
            group: 'com.eastwood.demo',
            name: 'library-sdk'
            // version: 1.0.0 // 上传maven时指定版本号
    )
}
```

#### 注册服务

在模块build.gradle中添加mis服务容器库引用：

```
dependencies {
    implementation 'com.eastwood.common:mis:1.0.0'
}
```


然后，在**MisService**（服务容器）注册服务，可以使用 服务接口 + 服务接口的实现对象 **或** 服务接口的实现类 进行注册，例如：

```
// 服务接口 + 服务接口的实现对象
MisService.register(ILibraryService.class, new LibraryService());
 
// 服务接口 + 服务接口的实现类
MisService.register(ILibraryService.class, LibraryService.class);
```

#### 获取服务

在其他模块build.gradle中添加mis库，以及通过 `misProvider` 引用sdk：

```
dependencies {
    implementation 'com.eastwood.common:mis:1.0.0'
    implementation misProvider('com.eastwood.demo:library-sdk')
}
```


Sync后，就可以通过接口在**MisService**服务容器中查找对应的接口服务并调用，例如：

```
ILibraryService libraryService = MisService.getService(ILibraryService.class);
libraryService.getLibraryInfo()
```

## 上传Maven
接口调试结束后，需将`mis`文件夹打包上传至Maven。

#### 配置 Maven
在根项目的 build.gradle 或 模块 build.gradle 中添加配置：

```
apply plugin: 'mis-maven'
 
misMaven {
    url = 'maven地址'
    repository = 'maven上对应的repository'
    username = '用户名'
    password = '密码'
}
```

#### 配置 GAV

```
dependencies {
    ...
    implementation misSource(
        group: 'com.eastwood.demo',
        name: 'library-sdk'
        version: 1.0.0 // 上传maven时必须指定版本号，目前不不支持SNAPSHOT
    )
}
```

除了[GAV](https://maven.apache.org/guides/mini/guide-naming-conventions.html)等必配项，还有以下配置：
* **dependencies** String[] 类型
  
    若上传的sdk引用其他类库，需配置对应的GAV，例如:

    ```
    dependencies {
        ...
        implementation misSource(
            group: 'com.eastwood.demo',
            name: 'library-sdk',
            version: '1.0.0',
            dependencies = ['com.google.code.gson:gson:2.8.1']
        )
    }
    ```

另外，在[**MicroModule**](https://github.com/EastWoodYang/MicroModule)目录结构下的配置
```
dependencies {
    ...
    implementation misSource(
        group: 'com.eastwood.demo',
        name: 'library-sdk',
        version: '1.0.0',
        microModuleName: '**microModule name**',
        dependencies = ['com.google.code.gson:gson:2.8.1']
    )
}
```

#### 执行上传Task
打开Gradle Tasks View，在对应项目中，双击 Tasks/upload/uploadMis，将执行上传任务。

![上传SDK](https://user-gold-cdn.xitu.io/2018/7/30/164eacb1e751a292?w=236&h=265&f=png&s=5912)

上传成功之后，需指定或更新 `misProvider` 中的version。
```
dependencies {
    implementation 'com.eastwood.common:mis:1.0.0'
    implementation misProvider('com.eastwood.demo:library-sdk:1.0.0')
}
```
    
## QA
#### 1. 没有Maven私服，怎么办？

    不指定misSource 和 misProvider 中的version。
    
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
