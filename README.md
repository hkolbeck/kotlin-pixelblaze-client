Kotlin Pixelblaze Client
========================

Status: Alpha
----------------------

The Pixelblaze LED controller exposes a semi-public websocket API, this library exposes it as a set of outbound and
inbound messages, with various methods of dispatching outbound and receiving inbound, filtered by message type. The
acts of sending outbound and receiving inbound are largely disconnected. Request/response methods can be employed
using issueOutboundAndWait(), but they are largely discouraged as the API does not make them a priority, and they
suffer from significant race conditions.

Information about the Pixelblaze can be found at https://electromage.com. Needless to say I think they're pretty neat.


Obtaining a Client
------------------

A great deal of client behavior is specifiable, but default clients can also be created.

```kotlin
/**
 * Create a pure default client, including the default IP of 192.168.4.1
 */
val pixelblaze = Pixelblaze.default()

/**
 * Create a default client, specifying only the address of the Pixelblaze
 */
val pixelblaze = Pixelblaze.default("10.0.0.68")

/**
 * Get a builder with required fields set
 *
 * Note that when specifying anything except the address, it's necessary to use the implementation class's functions
 */
val pixelblaze = WebsocketPixelblaze.defaultBuilder()
    .setConfig(PixelblazeConfig(requestQueueDepth = 100u))
    .build()

/**
 * Get a builder with nothing set. Have fun don't die. The example here specifies all required fields, but does
 * not explicitly set up any parsers, meaning that all inbound traffic will be discarded until some are added.
 */
val pixelblaze = WebsocketPixelblaze.bareBuilder()
    .setPixelblazeIp("10.0.0.68")
    .setPort(81)
    .setConfig(PixelblazeConfig(
        requestQueueDepth = 50u // Config objects have their own defaults specified, with no way to avoid them 
    ))
    .setHttpClient(HttpClient {
        install(WebSockets) // Must install WebSockets, no other HttpClient needs
    })
    .setIoLoopDispatcher(Dispatchers.IO)
    .setRepeatedOutboundDispatcher(Dispatchers.Default)
    .setSaveAfterDispatcher(Dispatchers.Default)
    .build()
```

Sending Outbound Messages
-------------------------

Requests can be sent asynchronously, synchronously, or on a schedule. Support is also offered for accepting a stream of
updates for some value, issuing them in a temporary manner, and saving on a specified interval even if no new values
have arisen

The simplest way to send messages is with `issueOutbound()`. Note that this example is purely fire-and-forget.
It takes a message and places it in the outbound queue, returning only whether that enqueue operation was successful.

```kotlin
pixelblaze.issueOutbound(Ping)
```

You can also issue a request with a synchronous response, but it's fragile and discouraged. Please read the
`issueOutboundAndWait()` method docs for more information.

To send repeated messages on a cadence, use `repeatOutbound()`. It generates and sends messages at a specified interval

```kotlin
pixelblaze.repeatOutbound(
    msgGenerator = { GetSystemState },
    interval = 10.seconds
)
```

Many operations require frequent updates to some value, but in order to spare the Pixelblaze's flash memory you want
to only save the value occasionally. `saveAfter()` provides a way to send writes, but only save on a cadence if the
most recent update has not yet been saved.

```kotlin
val sendChannel = pixelblaze.saveAfter(3.seconds) { brightness: Float, save -> SetBrightness(brightness, save) }
sendChannel.trySend(0.5f) // Or send() is also possible from a coroutine context or using runBlocking {}
```

Receiving Inbound Messages
--------------------------

Messages are received by watcher functions, which can be added and removed at build or runtime. A watcher is registered
to handle a message type with function to be called when a message of that type comes in. No watchers are added
by default unless you use the `PixelblazeStateCache`, in which case it registers its own watchers. If multiple
watchers are specified for a given type, they will all be called.

```kotlin
pixelblaze.addWatcher(InboundAck) { _ -> println("Got an ack!") }
```

Detecting Connection Issues
---------------------------

All actual communication with the Pixelblaze occurs in a coroutine (by default using the `Dispatchers.IO` dispatcher).
To detect and handle connection issues, a function can be provided in the builder:

```kotlin
fun handleConnectionIssues(event: ConnectionEvent, message: String?, thrown: Throwable?) {
    // Do what is necessary
}

/* ... */

val pixelblaze = WebsocketPixelblaze.defaultBuilder()
    .setConnectionWatcher(::handleConnectionIssues)
    .build()

```

Local Pixelblaze Discovery
--------------------------

Pixelblaze offers a utility to discover controllers on your local network if that network is connected to the internet.
To do so, use the `Discovery` class. If you call `getDiscovery()` it will use the same HTTP client as the Pixelblaze
instance. If you need an instance with its own client or without creating a Pixelblaze instance, its constructor is
also exposed

```kotlin
val pixelblaze = Pixelblaze.default()
val discovery = pixelblaze.getDiscovery()
val discovered: List<Discovered> = discovery.discoverLocalPixelblazes()
```


Pixelblaze State Caching
------------------------

If you need to maintain a picture of the current state of the connected Pixelblaze, you can use a 
`PixelblazeStateCache`. It takes a running client and adds a number of scheduled requests and watchers, then exposes
the most recent objects received or extracts fields from those objects. Note that initialization is asynchronous, and
values may not be available immediately, though they are requested immediately.

```kotlin
val pixelblaze = Pixelblaze.default()
val stateCache = pixelblaze.getStateCache()
stateCache.awaitFill(3.seconds) || throw RuntimeException("Cache never populated!")

val currentPlaylistIdx = stateCache.currentPlaylistIndex()!!
```


Tuning
------

I've had to fine-tune a lot of clients, so I'm happy to provide defaults for as many things as possible while still
allowing you to tweak them. Check out `PixelblazeConfig` for various buffer sizes and sleep durations if you want to
tune the client. Almost every complex class comes with the ability to tweak internal configs, with sensible defaults
specified if no override is provided. Check constructors and method definitions being used for more info.


Examples
-------

A few simple usage examples are offered in the 
[`examples` directory](/src/main/kotlin/industries/hannah/pixelblaze/examples)


Advanced Usage
--------------

See [this example](/src/main/kotlin/industries/hannah/pixelblaze/examples/CustomMessages.kt) for how to go about
implementing your own types.


Contributing
------------

Contributions, especially of new inbound and outbound message definitions, is very welcome. The project is governed by
the [Contributor Covenant](ContributorCovenant.md).