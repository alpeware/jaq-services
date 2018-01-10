# JAQ Services

A Clojure library designed to make using Google App Engine and Google Cloud
Platform usage idiomatic.

## Installation

Available on Clojars:

```
[com.alpeware/jaq-services "0.1.0-SNAPSHOT"]
```

## Status

Alpha quality with some API changes expected.

## Services

### App Engine
- Datastore
- Cloud Storage
- Memcache
- Task Queues

### Cloud APIs
- App Engine Admin
- OAuth
- Resource Manager
- Service Management

## Runtime Dependencies

See profile ```dev``` in ```project.clj``` -

``` clojure
  :profiles {:dev {:dependencies [[com.google.appengine/appengine-java-sdk ~sdk-version :extension "zip"]
                                  [com.google.appengine/appengine-api-1.0-sdk ~sdk-version]
                                  [com.google.appengine/appengine-api-labs ~sdk-version]
                                  [com.google.appengine/appengine-remote-api ~sdk-version]
                                  [com.google.appengine/appengine-tools-sdk ~sdk-version]]}}

```

## License

Copyright © 2017 Alpeware, LLC.

Distributed under the Eclipse Public License, the same as Clojure.
