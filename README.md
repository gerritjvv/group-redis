# group-redis

Underconstruction !!!

Group management api that supports locks, empheral data and membership join and leave notification

## Usage

```[group-redis "0.1.2-SNAPSHOT"]```

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
(use 'group-redis.core)

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

### Locks 

Locks will be released automatically when the client connections closes, crashes or stalls.
Note that GC collections might be a problem here if pauses are longer than the heart beat.
Make sure that the heart beat is longer than the expected GC pause.

```clojure
(use 'group-redis.core)

(lock c "lock1")
;; true

(lock c "lock1")
;; false
               
(release c "another-member" "lock1")
;; false

(release c "lock1")
;; true
(release c "lock1")
;; false

(reentrant-lock c "lock2")
;; true
(reentrant-lock c "lock2")
;; true

(reentrant-lock c "another-member" "lock2")
;; false
(lock c "another-member" "lock2")
;; false

(release c "lock2")
;; true

 ```
### Event listeners

The https://github.com/clojure/core.async project is used to provide async channels, from which events can be read.
i.e. rather than a callback function you get a channel from which you can read member leave and join events.

The event data structure is: ```clojure {:left-members #{}, :joined-members #{"abc"}}```


```clojure

(use 'group-redis.core :reload)
(require '[clojure.core.async :refer [go <! >!]])
(use 'clojure.tools.logging)

(def c (create-group-connector "localhost"))
(def c1 (register-member-event-ch c)) ;this registers an event and returns a channel

;we use a go loop to print out the events, note do not use while true, because
;if the connector is closed this channel will return nil always
(go (loop [] (if-let [c (<! c1)]
               (do (info c) (recur)))))

;join the current host as a member
(join c)

;;INFO: {:left-members #{}, :joined-members #{myhost-local}}

```

## License

Distributed under the Eclipse Public License either version 1.0

