# ffc-java-sdk

## 安装

使用maven安装指令
  ```
<repositories>
        <repository>
            <id>github-ffc-java-sdk-repo</id>
            <name>The Maven Repository on Github</name>
            <url>https://feature-flags-co.github.io/ffc-java-sdk/maven-repo</url>
        </repository>
    </repositories>

    <dependencies>
        <dependency>
            <groupId>co.featureflags</groupId>
            <artifactId>ffc-java-sdk</artifactId>
            <version>0.1</version>
        </dependency>
    </dependencies> 
  ```

## 集成SDK到自己的项目中

```
   // 初始化sdk，传入环境Secret Key
   FFCClient client = new FFCClientImp("your-env-secret");
```

如果需要修改基本配置或是设置默认的user：

```
   // 设置FFCConfig
   FFCConfig config = new FFCConfig.Builder()
        .baseUrl("your-ff-url") // 敏捷开关服务器地址 （只有使用本地安装的服务器才需要设置此参数）
        .appType("your-app-type") // 应用类型， 默认为 'Java'
        .timeoutInSeconds(300)// 网络操作（连接，读写）等待的最大时间默认为300秒
   
   // 设置默认的user     
   FFCUser user = new FFCUser.Builder('your-user-key') // user key必须设置
        .userName('your-user-name') // user name最好设置，方便查询
        .email('your-email') // 非必须设置
        .country('your-country') //非必须设置
        .build();     
   FFCClient client = new FFCClientImp("your-env-secret", user, config);
   
   // 如果有多个env,可以随时切换(env secret, user, config不为空时, 才会修改通过构造函数设置的默认值)
   client.reinitialize("your-another-env-secret", user, config);
   
```

### 从敏捷开关服务器获取分配给用户的变量值，并根据业务逻辑执行不同的功能模块
```
  String result = client.variation('your-feature-flag-key', user, "your-default-value");
  // 如果想使用默认的user值（如果没有设置默认user，会抛出null pointer exception）
  String result = client.variation('your-feature-flag-key', "your-default-value");
```



