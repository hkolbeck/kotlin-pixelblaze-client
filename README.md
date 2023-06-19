Kotlin Pixelblaze Client
========================

Status: Not even alpha
----------------------

The Pixelblaze LED controller exposes a semi-public websocket API, this library exposes it as a set of outbound and 
inbound messages, with various methods of dispatching outbound and receiving inbound, filtered by message type. The
acts of sending outbound and receiving inbound are largely disconnected. Request/response methods can be employed
using issueOutboundAndWait(), but they are largely discouraged as the API does not make them a priority.

Information about the Pixelblaze can be found at https://electromage.com. Needless to say I think they're pretty neat.

Sending Requests
----------------

Requests can be sent asynchronously, synchronously, or on a schedule. Support is also offered for accepting a stream of
updates for some value, issuing them in a temporary manner, and saving on a specified interval even if no new values have arisen 


Tuning
------

I've had to fine-tune a lot of clients, so I'm happy to provide defaults for as many things as possible while still 
allowing you to tweak them.

Example
-------

Advanced Usage
--------------