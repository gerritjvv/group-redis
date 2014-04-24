(ns group-redis.partition
  (:require [group-redis.core :refer [host-name empheral-set empheral-get empheral-ls empheral-del join reentrant-lock release get-remote-members]]
            [clojure.tools.logging :refer [info]]))

;Env n members and a list of paths/ids, each member should get a near to equal share of ids,
;but no member can have an id that another member already has.


(defn upper-divisor [t n]
  (if (= n 0)
    0
   (Math/ceil (/ t n))))

(defn distribute-ids 
  "Members and ids should be both collections. 
   A near to equal distribution of ids for each member will be calculated using (ceil (/ (count members) (count n)))
   Returns a map where each key is a member and the value is a list of ids uniquely assigned for that member
   e.g (distribute-ids [\"a\" \"b\" \"c\"] [1 2 3 4 5 6 7 8 9 10]) will return {\"a\" (7 1 4 6), \"b\" (3 2 9 5), \"c\" (10 8)}"
  [members ids]
  (if (not (or (empty? members) (empty? ids)))
    (let [d (upper-divisor (count ids) (count members))]
     (apply hash-map (interleave  (set members) (partition-all d (set ids)))))))


(defn- is-master!? 
  "Get a lock on $topic/master if the lock is not attained it means this host is not the master"
  [connector host topic]
  (reentrant-lock connector host (str topic "/master")))

(defn partition-flag-path [topic]
  (str topic "/partition-flag"))

(defn- assignments-path [topic]
  (str topic "/assignments"))
   
(defn is-partition-flag? [connector topic]
  (= (empheral-get connector (partition-flag-path topic)) true))

(defn- sync-point-path [connector host topic]
  (str topic "/sync-point/" host))

(defn- sync-point-path-keys [connector topic]
  (str topic "/sync-point/*"))


(defn- get-member-keys [connector]
  (map #(-> (clojure.string/split % #"/") last) (map :path (get-remote-members connector))))

(defn- perform-assignments! 
  "Read the members, sets the partition flag to true, sets the assignemnts from distribute-ids and finally sets the partition flag to nil"
  [connector host topic ids]
  (join connector host)
  ;(prn "perform-assignments is-flag " (empheral-get connector (partition-flag-path topic)) " - d1 "   (empheral-get connector (assignments-path topic))
   ; " d2 " (distribute-ids (get-member-keys connector) ids)  " ids " ids " members " (get-member-keys connector))
  (if (and ;only perform assignments if the partion flat is false and the calculated assignment differs from the saved one
        (not (empheral-get connector (partition-flag-path topic)))
        (not= (empheral-get connector (assignments-path topic)) (distribute-ids (get-member-keys connector) ids)))
      (empheral-set connector (partition-flag-path topic) true)))

(defn- wait-on-partition-flag! 
  "The controlling is defined based on an assignent flag that only the current master can set and unset
   It acts as a sync barrier, that when set the master will wait for all members to enter this barrier, only once
   all members have entered this barrier will the master set the new assignments and unset the barrier, only then can
   the members escape the barrier and continue. The whole process is protected by a timeout of 120 seconds"
  [connector host topic ids]
  ;(prn host " is-partition-flag? " (is-partition-flag? connector topic) " topic " topic "is master " (is-master!? connector host topic))
  (if (is-partition-flag? connector topic)
    (do 
      ;notify syncpoint
      ;(prn ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> set syncpoint path " (sync-point-path connector host topic) )
      (empheral-set connector (sync-point-path connector host topic) nil)
      ;run join because the host must be a meber for the algorithm to work
      (join connector host)
      
      ;loop in partition flag, only the master can unset the partition flag      
		  (loop [c 0]
		      (if (> c 600)
		        (throw (RuntimeException. "Waited on partition flag == true for over 2 minutes something. Possible cause would be that not all members are working correctly"))
		        (do
               ;if master and all the nodes have written to the sync point, write the new assignments and set the partition flag to nil
               (if (is-master!? connector host topic)
                 (if (>= (count (empheral-ls connector (sync-point-path-keys connector topic))) (count (get-remote-members connector)))
                   (let [members (get-member-keys connector)]
                     ;delete all keys in sync path
                     (doseq [ks (empheral-ls connector (sync-point-path-keys connector topic))]
                       (empheral-del connector (:path ks)))
                     ;set the assignments and un set the partition flag
                     (empheral-set connector (assignments-path topic) (distribute-ids members ids))
                     (empheral-del connector (partition-flag-path topic)))
                   (info  "sync keys " (empheral-ls connector (sync-point-path-keys connector topic)) " members " (map :path (get-remote-members connector))))
                 )
               
               (if (is-partition-flag? connector topic)
		             (do (Thread/sleep 200) (recur (inc c))))))))))

(defn controlled-assignments 
  ([connector topic ids]
    (controlled-assignments connector host-name topic ids))
  ([connector host topic ids]
     ;identify master
    ;(prn "is-master!? " host  " " (is-master!? connector host topic))
    (if (is-master!? connector host topic) ;here the master sets the assignment flag (sync barrier) only if needed
      (perform-assignments! connector host-name topic ids))
    
    ;if the sync barrier is set this function will block till the assignment process has been completed
    (wait-on-partition-flag! connector host topic ids)
    
    (empheral-get connector (assignments-path topic))))
      
        