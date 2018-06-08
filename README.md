# Mis - Module interface service.
模块接口服务



## 配置

#### 添加 mis 插件

在根项目的build.gradle中添加 **mis 插件**的引用：
```
buildscript {
    dependencies {
        classpath 'com.eastwood.tools.plugins:mis:1.0.0'
    }
}
```

在模块的build.gradle中添加**mis 插件**：
```
apply plugin: 'com.android.application'

// under plugin 'com.android.application'
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
        compile 'com.eastwood.common:mis:1.0.0'
    }


通过**MisService**注册服务，使用 服务接口 + 服务接口的实现对象 **或** 服务接口的实现类 进行注册，例如：

    // 服务接口 + 服务接口的实现对象
    MisService.register(ITestService.class, new TestService());
     
    // 服务接口 + 服务接口的实现类
    MisService.register(ITestService.class, TestService.class);


## 打包Mis并上传

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

    uploadMis {
     
        main {
            groupId = 'com.eastwood.demo'
            artifactId = 'app-sdk'
            version = '1.0.0'
        }
     
    }
 
除了[GAV](https://maven.apache.org/guides/mini/guide-naming-conventions.html)等必配项，还有以下配置：
* **dependencies** String[] 类型
  
  若上传的sdk引用其他类库，需配置对应的GAV，例如:
  
            uploadMis {
             
                main {
                    groupId = 'com.eastwood.demo'
                    artifactId = 'app-sdk'
                    version = '1.0.0'
             
                    dependencies = ['com.google.code.gson:gson:2.8.1']
                }
             
            }

在[**MicroModule**](https://github.com/EastWoodYang/MicroModule)中的配置

    uploadMis {
     
        main {
            groupId = 'com.eastwood.demo'
            artifactId = 'micromodule-main-sdk'
            version = '1.0.0'
        }
     
        p_common {
            groupId = 'com.eastwood.demo'
            artifactId = 'micromodule-main-sdk'
            version = '1.0.0'
        }
    
    }

#### 执行上传
打开Gradle Tasks View，在对应项目中，双击 Tasks/upload/uploadMis(_项目名称)，将执行上传mis-sdk任务。
 
<img src='https://github.com/EastWoodYang/Mis/blob/master/picture/3.png'/>
 

上传成功成功后，在模块build.gradle中添加aar引用，例如：
         
     dependencies {
         compile 'com.eastwood.common:mis:1.0.0'
         compile 'com.eastwood.demo:app-sdk:1.0.0'
     }

## 其他模块获取服务

在其他模块build.gradle中添加mis库以及之前上传的aar引用，例如：

    dependencies {
        compile 'com.eastwood.common:mis:1.0.0'
        compile 'com.eastwood.demo:app-sdk:1.0.0'
    }


通过接口在**MisService**中获取服务，例如：

    ITestService testService = MisService.getService(ITestService.class);
    testService.get()
