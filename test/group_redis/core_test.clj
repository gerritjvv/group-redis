(ns group-redis.core-test
  (:require [group-redis.core :refer :all])
  (:use midje.sweet))

(facts "Test Redis group management"
       
       (fact "Test create join close connector"
             
             (let [c (create-group-connector "localhost")]
             
               (join c "abc")
               (join c "123")
               
               (Thread/sleep 5000)
               (let [members (map :path (get-members c))]
                 (sort members) => ["/default-group/members/123" "/default-group/members/abc"])
               (close c)
               ))
       
       
       (fact "Test lock"
             (let [c (create-group-connector "localhost")]
               
               (lock c "lock1") => true
               (lock c "lock1") => false
               
               (release c "another-member" "lock1") => false
               (release c "lock1") => true
               (release c "lock1") => false)))