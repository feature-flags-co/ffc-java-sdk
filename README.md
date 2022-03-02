# JAVA Server Side SDK

## Introduction

This is the Java Server Side SDK for the feature management platform [feature-flags.co](feature-flags.co). It is
intended for use in a multiple-users Java server applications.

This SDK has two main purposes:

- Store the available feature flags and evaluate the feature flags by given user in the server side SDK
- Sends feature flags usage, and custom events for the insights and A/B/n testing.

## Data synchonization

We use websocket to make the local data synchronized with the server, and then store them in the memory by default.
Whenever there is any changes to a feature flag or his related data, the changes would be pushed to the SDK, the average
synchronization time is less than **100** ms. Be aware the websocket connection can be interrupted by any error or
internet interruption, but it would be restored automatically right after the problem is gone.

## Offline mode support

In the offline mode, SDK DOES not exchange any data with [feature-flags.co](feature-flags.co)

In the following situation, the SDK would work when there is no internet connection: it has been initialized in
using `co.featureflags.server.exterior.FFCClient#initializeFromExternalJson(json)`

To open the offline mode:

```
 FFCConfig config = new FFCConfig.Builder()
                    .offline(false)
                    .build()
 FFCClient client = new FFCClientImp(envSecret, config);
```

## Evaluation of a feature flag

SDK will initialize all the related data(feature flags, segments etc.) in the bootstrapping and receive the data updates
in real time, as mentioned in the above

After initialization, the SDK has all the feature flags in the memory and all evaluation is done locally and
synchronously, the average evaluation time is < **10** ms.

## Installation

install the sdk in using maven

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
            <artifactId>ffc-java-server-sdk</artifactId>
            <version>1.0</version>
        </dependency>
    </dependencies> 
  ```

## SDK

### FFCClient

Applications SHOULD instantiate a single instance for the lifetime of the application. In the case where an application
needs to evaluate feature flags from different environments, you may create multiple clients, but they should still be
retained for the lifetime of the application rather than created per request or per thread.

### Bootstrapping

The bootstrapping is in fact the call of constructor of `FFCClientImp`, in which the SDK will be initialized, using
streaming from [feature-flags.co](feature-flags.co).

The constructor will return when it successfully connects, or when the timeout set
by `FFCConfig.Builder#startWaitTime(Duration)`
(default: 15 seconds) expires, whichever comes first. If it has not succeeded in connecting when the timeout elapses,
you will receive the client in an uninitialized state where feature flags will return default values; it will still
continue trying to connect in the background unless there has been an `java.net.ProtocolException` or you close the
client(using `close()`). You can detect whether initialization has succeeded by calling `isInitialized()`.

```
FFCClient client = new FFCClient(sdkKey, config);
if(client.isInitialized()){
// do whatever is appropriate
}
```

If you prefer to have the constructor return immediately, and then wait for initialization to finish at some other
point, you can use `getDataUpdateStatusProvider()`, which provides an asynchronous way, as follows:

```
FFCConfig config = new FFCConfig.Builder()
             .startWait(Duration.ZERO)
             .build();
FFCClient client = new FFCClient(sdkKey, config);
    
// later, when you want to wait for initialization to finish:
boolean inited = client.getDataUpdateStatusProvider().waitForOKState(Duration.ofSeconds(15))
if (!inited) {
    // do whatever is appropriate if initialization has timed out
}
```

Note that the _**sdkKey(envSecret)**_ is mandatory.

### FFCConfig and Components

`FFCConfig` exposes advanced configuration options for the `FFCClient`.

`startWaitTime`: how long the constructor will block awaiting a successful data sync. Setting this to a zero or negative
duration will not block and cause the constructor to return immediately.

`offline`: Set whether SDK is offline. when set to true no connection to feature-flag.co anymore

We strongly recommend to use the default configuration or just set `startWaitTime` or `offline` if necessary.



```
// default configuration
FFCConfig config = FFCConfig.DEFAULT

// set startWaitTime and offline
FFCConfig config = new FFCConfig.Builder()
            .startWaitTime(Duration.ZERO)
            .offline(false)
            .build()
FFCClient client = new FFCClient(sdkKey, config);

// default configuration
FFCClient client = new FFCClient(sdkKey);
```

`FFCConfig` provides advanced configuration options for setting the SDK component or you want to customize the behavior
of build-in components.

`HttpConfigFactory`: Interface for a factory that creates an `HttpConfig`. SDK sets the SDK's networking configuration,
using a factory object. This object by defaut is a configuration builder obtained from `Factory#httpConfigFactory()`.
With `HttpConfig`, Sets connection/read/write timeout, proxy or insecure/secure socket.

```

HttpConfigFactory factory = Factory.httpConfigFactory()
                                   .connectTime(Duration.ofMillis(3000))
                                   .httpProxy("my-proxy", 9000)

FFCConfig config = new FFCConfig.Builder()
                      .httpConfigFactory(factory)
                      .build();
```


`DataStorageFactory` Interface for a factory that creates some implementation of `DataStorage`, that holds feature flags, 
user segments or any other related data received by the SDK. SDK sets the implementation of the data storage, using `Factory#inMemoryDataStorageFactory()`
to instantiate a memory data storage. Developers can customize the data storage to persist received data in redis, mongodb, etc.

```
FFCConfig config = new FFCConfig.Builder()
                      .dataStorageFactory(factory)
                      .build();

```

`UpdateProcessorFactory` SDK sets the implementation of the `UpdateProcessor` that receives feature flag data from feature-flag.co, 
using a factory object. The default is `Factory#streamingBuilder()`, which will create a streaming, using websocket.
If Developers would like to know what the implementation is, they can read the javadoc and source code.

`InsightProcessorFactory` SDK sets the implementation of `InsightProcessor` to be used for processing analytics events, 
using a factory object. The default is `Factory#insightProcessorFactory()`. If Developers would like to know what the implementation is, 
they can read the javadoc and source code.

###Evaluation

SDK calculates the value of a feature flag for a given user, and returns a flag vlaue/an object that describes the way 
that the value was determined.

`FFUser`: A collection of attributes that can affect flag evaluation, usually corresponding to a user of your application.
This object contains built-in properties(`key`, `userName`, `email` and `country`). The only mandatory property is the key,
which must uniquely identify each user; this could be a username or email address for authenticated users, or a ID for anonymous users.
All other built-in properties are optional, it's strongly recommended to set userName in order to search your user quickly
You may also define custom properties with arbitrary names and values.

```
    FFCClient client = new FFCClientImp(envSecret);
    
    // FFUser creation
    FFCClient user = new FFCClient.Builder("key")
        .userName("name")
        .country("country")
        .email("email@xxx.com")
        .custom("property", "value")
        .build()
    
    // Evaluation details
    FlagState<String> res = client.variationDetail("flag key", user, "Not Found");
    // Flag value
    String res = client.variation("flag key", user, "Not Found");
    
```

If evaluation called before Java SDK client initialized or you set the wrong flag key or user for the evaluation, SDK will return 
the default value you set. The `FlagState` will explain the reason of the last evaluation error.

SDK support the String, Boolean, and Number as the return type of flag values, see JavaDocs for more details.

### Experiments (A/B/n Testing)
We support automatic experiments for pageviews and clicks, you just need to set your experiment on our SaaS platform, then you should be able to see the result in near real time after the experiment is started.

In case you need more control over the experiment data sent to our server, we offer a method to send custom event.
```
client.trackMetric(user, eventName, numericValue);
```
**numericValue** is not mandatory, the default value is **1**.

Make sure `trackMetric` is called after the related feature flag is called by simply calling `variation` or `variationDetail`
otherwise, the custom event won't be included into the experiment result.


