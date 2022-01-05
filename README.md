# ffc-java-sdk

## 安装

使用maven安装指令
  ```
  <dependency>
    <groupId>co.featureflags</groupId>
    <artifactId>ffc-java-sdk</artifactId>
    <version>0.1</version>
  </dependency>  
  ```

## 集成SDK到自己的项目中

```java
   // 初始化sdk，传入环境Secret Key
   FFCClient client = new FFCClientImp("your-env-secret");
```
如果需要修改基本配置可以使用`FFCConfig`
```java
   FFCConfig config = new FFCConfig.Builder()
        .baseUrl("your-ff-url") // 敏捷开关服务器地址 （只有使用本地安装的服务器才需要设置此参数）
        .appType("your-app-type") // 应用类型， 默认为 'Java'
        .timeoutInSeconds(300)// 网络操作（连接，读写）等待的最大时间默认为300秒
   FFCClient client = new FFCClientImp("your-env-secret"， config);
   // 如果有多个env,可以随时切换
   client.initialize("your-another-env-secret", config);
   
```

### 从敏捷开关服务器获取分配给用户的变量值，并根据业务逻辑执行不同的功能模块
```java
  FFCUser user = new FFCUser.Builder('your-user-key')
        .userName('your-user-name')
        .email('your-email')
        .country('your-country')
        .build();
  String result = client.variation('your-feature-flag-key', user, "your-default-value");
```



