# JAVA Server Side SDK

## Introduction

This is the Java Server Side SDK for the feature management platform [featureflag.co](https://featureflag.co/). It is
intended for use in a multi-user Java server applications.

This SDK has two main purposes:

- Store the available feature flags and evaluate the feature flag variation for a given user
- Send feature flag usage and custom events for the insights and A/B/n testing.

## Data synchonization

We use websocket to make the local data synchronized with the server, and then store them in memory by default.Whenever there is any change to a feature flag or its related data, this change will be pushed to the SDK, the average synchronization time is less than **100** ms. Be aware the websocket connection may be interrupted due to internet outage, but it will be resumed automatically once the problem is gone.

## Offline mode support

In the offline mode, SDK DOES not exchange any data with [featureflag.co](https://featureflag.co/)

In the following situation, the SDK would work when there is no internet connection: it has been initialized in using `co.featureflags.server.exterior.FFCClient#initializeFromExternalJson(json)`

To open the offline mode:

```java
FFCConfig config = new FFCConfig.Builder()
               .offline(false)
               .build()
FFCClient client = new FFCClientImp(envSecret, config);
```

## Evaluation of a feature flag

SDK will initialize all the related data(feature flags, segments etc.) in the bootstrapping and receive the data updates
in real time, as mentioned in the above.

After initialization, the SDK has all the feature flags in the memory and all evaluation is done locally and
synchronously, the average evaluation time is < **10** ms.

## Installation

install the sdk in using maven
```xml
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
        <version>1.1.1</version>
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
streaming from [featureflag.co](https://featureflag.co/).

The constructor will return when it successfully connects, or when the timeout set
by `FFCConfig.Builder#startWaitTime(Duration)`
(default: 15 seconds) expires, whichever comes first. If it has not succeeded in connecting when the timeout elapses,
you will receive the client in an uninitialized state where feature flags will return default values; it will still
continue trying to connect in the background unless there has been an `java.net.ProtocolException` or you close the
client(using `close()`). You can detect whether initialization has succeeded by calling `isInitialized()`.

```java
FFCClient client = new FFCClientImp(sdkKey, config);
if(client.isInitialized()){
// do whatever is appropriate
}
```

If you prefer to have the constructor return immediately, and then wait for initialization to finish at some other
point, you can use `getDataUpdateStatusProvider()`, which provides an asynchronous way, as follows:

```java
FFCConfig config = new FFCConfig.Builder()
               .startWait(Duration.ZERO)
               .build();
FFCClient client = new FFCClientImp(sdkKey, config);
    
// later, when you want to wait for initialization to finish:
boolean inited = client.getDataUpdateStatusProvider().waitForOKState(Duration.ofSeconds(15))
if (inited) {
    // do whatever is appropriate
}
```

Note that the _**sdkKey(envSecret)**_ is mandatory.

### FFCConfig and Components

`FFCConfig` exposes advanced configuration options for the `FFCClient`.

`startWaitTime`: how long the constructor will block awaiting a successful data sync. Setting this to a zero or negative
duration will not block and cause the constructor to return immediately.

`offline`: Set whether SDK is offline. when set to true no connection to feature-flag.co anymore

We strongly recommend to use the default configuration or just set `startWaitTime` or `offline` if necessary.



```java
// default configuration
FFCConfig config = FFCConfig.DEFAULT

// set startWaitTime and offline
FFCConfig config = new FFCConfig.Builder()
               .startWaitTime(Duration.ZERO)
               .offline(false)
               .build()
FFCClient client = new FFCClientImp(sdkKey, config);

// default configuration
FFCClient client = new FFCClientImp(sdkKey);
```

`FFCConfig` provides advanced configuration options for setting the SDK component or you want to customize the behavior
of build-in components.

`HttpConfigFactory`: Interface for a factory that creates an `HttpConfig`. SDK sets the SDK's networking configuration,
using a factory object. This object by defaut is a configuration builder obtained from `Factory#httpConfigFactory()`.
With `HttpConfig`, Sets connection/read/write timeout, proxy or insecure/secure socket.

```java
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

```java
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

### Evaluation

SDK calculates the value of a feature flag for a given user, and returns a flag vlaue/an object that describes the way 
that the value was determined.

`FFUser`: A collection of attributes that can affect flag evaluation, usually corresponding to a user of your application.
This object contains built-in properties(`key`, `userName`, `email` and `country`). The `key` and `userName` are required.
The `key` must uniquely identify each user; this could be a username or email address for authenticated users, or a ID for anonymous users.
The `userName` is used to search your user quickly. All other built-in properties are optional, you may also define custom properties with arbitrary names and values.

```java
FFCClient client = new FFCClientImp(envSecret);

// FFUser creation
FFCUser user = new FFCUser.Builder("key")
    .userName("name")
    .country("country")
    .email("email@xxx.com")
    .custom("property", "value")
    .build()

// be sure that SDK is initialized
// this is not required
if(client.isInitialized()){
    // Evaluation details
    FlagState<String> res = client.variationDetail("flag key", user, "Not Found");
    // Flag value
    String res = client.variation("flag key", user, "Not Found");
    // get all variations for a given user
    AllFlagStates<String> res = client.getAllLatestFlagsVariations(user);
}
```

If evaluation called before Java SDK client initialized or you set the wrong flag key or user for the evaluation, SDK will return 
the default value you set. The `FlagState` and `AllFlagStates` will all details of later evaluation including the error reason.

SDK supports String, Boolean, and Number as the return type of flag values, see JavaDocs for more details.

### Experiments (A/B/n Testing)
We support automatic experiments for pageviews and clicks, you just need to set your experiment on our SaaS platform, then you should be able to see the result in near real time after the experiment is started.

In case you need more control over the experiment data sent to our server, we offer a method to send custom event.
```java
client.trackMetric(user, eventName, numericValue);
```
**numericValue** is not mandatory, the default value is **1**.

Make sure `trackMetric` is called after the related feature flag is called by simply calling `variation` or `variationDetail`
otherwise, the custom event won't be included into the experiment result.


