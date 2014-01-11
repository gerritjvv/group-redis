# group-redis

Underconstruction !!!

Group management api that supports locks, empheral data and membership join and leave notification

## Usage

### Joining a group

```clojure
(use 'group-redis.core :reload)

;join the default-group
(def c (create-group-connector "localhost"))

(join c "abc")
(join c "123")

(get-members c)
;;({:path "/default-group/members/123", :val 1389465833687} {:path "/default-group/members/abc", :val 1389465833688})
```

Join a specific group

```clojure
(use 'group-redis.core :reload)

;join the default-group
(def c (create-group-connector "localhost" {:group-name "mygroup" :heart-beat-freq 10}))
;;we set heart-beat-freq to 10 seconds

(join c "abc")
(join c "123")

(get-members c)
;;({:path "/mygroup/members/123", :val 1389465833687} {:path "/mygroup/members/abc", :val 1389465833688})
```

### Leaving a group

All member entries are set with ```EXPIRE``` ```(/ heart-beat-freq 2)```, this means that 
if the member's jvm crashes, freezes or ends, its entry will automatically expire in n seconds,
and other members on querying get-members will see the member has leaved. 


## License

Copyright © 2014 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
