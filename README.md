# group-redis


Group management api that supports locks, empheral data and membership join and leave notification

## Usage

[![Clojars Project](http://clojars.org/group-redis/latest-version.svg)](http://clojars.org/group-redis)

For java see the section on java below.

### Joining a group

```clojure
(use 'group-redis.core)

;join the default-group
(def c (create-group-connector "localhost"))

(join c "abc")
(join c "123")

(get-members c)
;;({:path "/default-group/members/123", :val {:ts 1389465833687 :sub-groups ["default"]} {:path "/default-group/members/abc", :val {:ts 1389465833688 :sub-groups ["default"]})
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

### Members and sub groups

Sometimes members need to be divided into sub groups or given attributes that divide them up logically.

When creating a connection a series of attributes using the key :sub-groups can be defined for a connector e.g
```clojure
(def c (create-group-connector "localhost" {:group-name "mygroup" :heart-beat-freq 10 :sub-gruops ["abc" "123"]}))
(join c)
```

Here the connection will belong to the sub groups "abc" and "123"
To get the sub groups call:

```clojure
(map :sub-groups (get-members c))
```

Sub groups can be added and removed to an active connection.

```clojure
(add-sub-group c "mynewsubgroup")
(join c) ; we mus rejoin for the member data to reflect this

(remove-sub-group c "mynewsubgroup")
(leave c)
(join c)


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

(use 'group-redis.core)
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

### Empheral set/get

These sets expire when the connection is closed or the jvm crashes, or freezes.

```clojure
(use 'group-redis.core)
(def c (create-group-connector "localhost"))

(empheral-set c "mykey1" 1)
;; ["OK" 1]

(empheral-get c "mykey1")
;; 1

```

### Persistent set/get

Set data with a path prefix of ```/[group-name]/persistent/[path..]```

```clojure
(use 'group-redis.core)
(def c (create-group-connector "localhost"))

(persistent-set c "1/2" {:a "hi"})
;; "OK"

(persistent-get c "1/2")
;; {:a "hi"}

(persistent-set* c [["t/1" 1] ["t/2" 2] ["t/3" 3]])
;; ["OK" "OK" "OK"]
;; data is: keys /default-group/persistent/*
;;  "/default-group/persistent/t/3"
;;  "/default-group/persistent/t/2"
;;  "/default-group/persistent/t/1"

```

## Java

Redis group provides a java class for easy integration with java projects.

### Joining a group

```java

Object connector = RedisConn.create_group_connector("localhost");
		
RedisConn.join(connector);
RedisConn.join(connector, "anotherhost");
		
Collection<Map<?,?>> members = RedisConn.getMembers(connector);
System.out.println(Arrays.toString(members.toArray()));
		
assertEquals(members.size(), 2);
		
RedisConn.close(connector);
```

### Locks

```java

Object connector = RedisConn.create_group_connector("localhost");
		
assertTrue(RedisConn.lock(connector, "lock1"));
assertFalse(RedisConn.lock(connector, "lock1"));
		
assertFalse(RedisConn.release(connector, "another-member", "lock1"));
		

assertTrue(RedisConn.release(connector, "lock1"));
assertFalse(RedisConn.release(connector, "lock1"));
	
assertTrue(RedisConn.reentrant_lock(connector, "lock2"));
assertTrue(RedisConn.reentrant_lock(connector, "lock2"));
		
assertFalse(RedisConn.reentrant_lock(connector, "another-member", "lock2"));
		
assertFalse(RedisConn.release(connector, "another-member", "lock2"));
		
assertTrue(RedisConn.release(connector, "lock2"));
	
RedisConn.close(connector);
```

## Empheral Get/Set

```java

Object connector = RedisConn.create_group_connector("localhost");
		
RedisConn.empheral_set(connector, "mykey1", 1);
assertEquals(RedisConn.empheral_get(connector, "mykey1"), 1);
		
RedisConn.close(connector);
```

## Persistent Get/Set

```java

Object connector = RedisConn.create_group_connector("localhost");
		
RedisConn.persistent_set(connector, "mykey2", 1);
assertEquals(RedisConn.persistent_get(connector, "mykey2"), 1);
RedisConn.close(connector);
		
```

## Member group management and task assignment (resource scheduling)

Some basic functions are provided to help with assigning work (here called ids) exclusively between nodes of the same group.

A simple master assignment scheme is used by using an exclusive lock that decides which node is a master, any node at any time can be the master
but only one node at any time can be master.

The function to use is: controlled-assignments

TODO: Provide example
```clojure

```

## Support

Please contact me on gerritjvv@gmail.com 

Twitter: gerritjvv

or raise an [issue | https://github.com/gerritjvv/group-redis/issues]

## License

Distributed under the Eclipse Public License either version 1.0

