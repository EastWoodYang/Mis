# MIS
Module Interface Service.

## 配置

#### 添加 mis 插件

在根项目的build.gradle中添加 **mis 插件**的引用：
```
buildscript {
    dependencies {
        classpath 'com.eastwood.tools.plugins:mis:1.0.3'
    }
}
```

在模块的build.gradle中添加**mis 插件**：
```
apply plugin: 'mis' 
```

#### 创建 mis 目录
在**src/main/java** 同级目录，创建**mis**文件夹
 
<img src='https://github.com/EastWoodYang/Mis/blob/master/picture/1.png'/>
 

## 定义接口和Model，以及实现和注册
直接在**mis**文件夹下，创建对应的包名、接口类和Model。并在**java**文件夹下，实现对应的接口类。
 
<img src='https://github.com/EastWoodYang/Mis/blob/master/picture/2.png'/>
 
#### 注册服务

在模块build.gradle中添加mis库引用，例如：

    dependencies {
        implementation 'com.eastwood.common:mis:1.0.0'
    }


然后，通过**MisService**注册服务，使用 服务接口 + 服务接口的实现对象 **或** 服务接口的实现类 进行注册，例如：

    // 服务接口 + 服务接口的实现对象
    MisService.register(ILibraryService.class, new LibraryService());
     
    // 服务接口 + 服务接口的实现类
    MisService.register(ILibraryService.class, LibraryService.class);


## 上传Maven

#### 配置 Maven
在根项目的 build.gradle 或 模块 build.gradle 中添加配置：

    apply plugin: 'mis-maven'
     
    misMaven {
        url = ...          // maven地址
        repository = ...   // maven上对应的repository
        username = ...     // 用户名
        password = ...     // 密码
    }

#### 配置 GAV
在模块 build.gradle 中添加上传GAV配置，例如：

    dependencies {
            implementation 'com.eastwood.common:mis:1.0.0'
            
            implementation misSource(
                        group: 'com.eastwood.demo',
                        name: 'library-sdk',
                        version: '1.0.0'
            )
    }
 
除了[GAV](https://maven.apache.org/guides/mini/guide-naming-conventions.html)等必配项，还有以下配置：
* **dependencies** String[] 类型
  
  若上传的sdk引用其他类库，需配置对应的GAV，例如:
  
            dependencies {
                    implementation 'com.eastwood.common:mis:1.0.0'
                    
                    implementation misSource(
                                group: 'com.eastwood.demo',
                                name: 'library-sdk',
                                version: '1.0.0',
                                dependencies = ['com.google.code.gson:gson:2.8.1']
                    )
            }

在[**MicroModule**](https://github.com/EastWoodYang/MicroModule)中的配置

    dependencies {
            implementation 'com.eastwood.common:mis:1.0.0'
            
            implementation misSource(
                        group: 'com.eastwood.demo',
                        name: 'library-sdk',
                        version: '1.0.0',
                        microModuleName: '**microModule name**',
                        dependencies = ['com.google.code.gson:gson:2.8.1']
            )
    }

#### 执行上传
打开Gradle Tasks View，在对应项目中，双击 Tasks/upload/uploadMis(_项目名称)，将执行上传任务。
 
<img src='https://github.com/EastWoodYang/Mis/blob/master/picture/3.png'/>


## 其他模块获取服务

在其他模块build.gradle中添加mis库以及之前上传的aar引用，例如：

    dependencies {
        implementation 'com.eastwood.common:mis:1.0.0'
        implementation misProvider('com.eastwood.demo:library-sdk:1.0.0')
    }


通过接口在**MisService**中获取服务，例如：

    ILibraryService libraryService = MisService.getService(ILibraryService.class);
    libraryService.getServiceName()
    
    
    
## QA
#### 1. 没有Maven私服，怎么办？

    不指定misSource 和 misProvider 中的version。