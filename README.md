# JAQ Services

A library to bring Clojure to the Google Cloud Platform.

## Installation

Use in ```deps.edn``` -

```
{com.alpeware/jaq-services {:git/url "https://github.com/alpeware/jaq-services"
                            :sha "LATEST SHA"}}
```

## Status

Alpha quality with some API changes expected.

## Services

``` bash
src/
└── jaq
    ├── gae
    │   ├── datastore.clj
    │   ├── deferred.clj
    │   └── memcache.clj
    ├── gce
    │   └── metadata.clj
    ├── gcp
    │   ├── appengine_admin.clj
    │   ├── billing.clj
    │   ├── compute.clj
    │   ├── iam.clj
    │   ├── management.clj
    │   ├── pubsub.clj
    │   ├── resource.clj
    │   ├── script.clj
    │   ├── storage.clj
    │   └── tasks.clj
    └── services
        ├── auth.clj
        ├── deferred.clj
        ├── env.clj
        └── util.clj

```

## Runtime Dependencies


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
