(ns group-redis.partition
  (:require [group-redis.core :refer [expire-set host-name empheral-set empheral-get empheral-ls empheral-del join reentrant-lock release get-remote-members]]
            [clojure.tools.logging :refer [info warn]]))

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

(defn get-partition-members [connector topic]
  (doseq [member (empheral-ls connector (str topic "/partition-members/*"))]
    (empheral-get connector member))
  (empheral-ls connector (str topic "/partition-members/*")))
    

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


(defn- get-member-keys [connector topic]
  (map #(-> (clojure.string/split % #"/") last)  (get-partition-members connector topic)))

(defn- perform-assignments! 
  "Read the members, sets the partition flag to true, sets the assignemnts from distribute-ids and finally sets the partition flag to nil"
  [connector host topic ids]
  ;(prn "perform-assignments is-flag " (empheral-get connector (partition-flag-path topic)) " - d1 "   (empheral-get connector (assignments-path topic))
   ; " d2 " (distribute-ids (get-member-keys connector topic) ids)  " ids " ids " members " (get-member-keys connector topic))
  
  (if (and ;only perform assignments if the partion flag is false and the calculated assignment differs from the saved one
        (not (empheral-get connector (partition-flag-path topic)))
        (not= (empheral-get connector (assignments-path topic)) (distribute-ids (get-member-keys connector topic) ids)))
      (do 
        (expire-set connector 120 (partition-flag-path topic) true))
      
      ))

(defn- wait-on-partition-flag! 
  "The controlling is defined based on an assignent flag that only the current master can set and unset
   It acts as a sync barrier, that when set the master will wait for all members to enter this barrier, only once
   all members have entered this barrier will the master set the new assignments and unset the barrier, only then can
   the members escape the barrier and continue. The whole process is protected by a timeout of 120 seconds

   Returns true if any waiting was actually done (i.e if the sync barrier was entered or not)"
  [connector host topic ids]
  (if (is-partition-flag? connector topic)
    (let [start-time (System/currentTimeMillis)] 
      ;notify syncpoint
      ;(prn ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> set syncpoint path " (sync-point-path connector host topic) )
      (empheral-set connector (sync-point-path connector host topic) nil)
      ;run join because the host must be a meber for the algorithm to work
      
      ;loop in partition flag, only the master can unset the partition flag      
		  (loop [c 0]
		      (if (> c 600)
		        (if (is-master!? connector host topic)
              (do
                (info "Waited on partition flag for over 2 minutes. Possible cause could be that not all members are working correctly. Resetting partition flags")
                 ;it took too long for all members to enterh the partition-flag, we can only delete everything here and exit
                 ;delete all keys in sync path
                (empheral-del connector (partition-flag-path topic))
                (doseq [ks (empheral-ls connector (sync-point-path-keys connector topic))]
                   (empheral-del connector (:path ks)))
                 ))
		        (do
               ;if master and all the nodes have written to the sync point, write the new assignments and set the partition flag to nil
               (if (is-master!? connector host topic)
                 (if (>= (count (empheral-ls connector (sync-point-path-keys connector topic))) (count (get-partition-members connector topic)))
                   (let [members (get-member-keys connector topic)]
                     ;delete all keys in sync path
                     (doseq [ks (empheral-ls connector (sync-point-path-keys connector topic))]
                       (empheral-del connector (:path ks)))
                     ;set the assignments and un set the partition flag
                     (empheral-set connector (assignments-path topic) (distribute-ids members ids))
                     (empheral-del connector (partition-flag-path topic)))))
               
               (if (is-partition-flag? connector topic)
		             (do (Thread/sleep 100) (recur (inc c)))))))
      
      ;print out if this took longer than a second
      (if (> (- (System/currentTimeMillis) start-time) 1000)
        (warn "waiting on partition flag took " (- (System/currentTimeMillis) start-time) "ms")) 
      
      true)
    false))

(defn controlled-assignments 
  "Public function to call when several members are participating in a group that should consume the ids, one member will be automatically selected as a member (by way of an exclusive reentrant lock).
   This function contains a waiting function that acts as a lock barrier where each member must wait for all other memebers to enter the barrier to receive assignments. The barrier is only entered
   when the master is calculating new assignments, this means if no member leaves or enters the barrier will only be entered once on startup and then only again if any member leaves or enters, causing
   very little impact on performance.  
   As a performance improvemnt the curr-assignments acts as a cache so that if no new assignment was made the curr-assignments is returned, otherwise the new assignments are returned, its the responsibility of
   the calling function to ensure that the curr-assignments is actually the previously received assignments, doing this allows us to have caching without actually saving global state."
  ([connector topic ids curr-assignments]
    (controlled-assignments connector host-name topic ids curr-assignments))
  ([connector host topic ids curr-assignments]
     ;identify master
    ;(prn "is-master!? " host  " " (is-master!? connector host topic))
    (empheral-set connector (str topic "/partition-members/" host) (System/currentTimeMillis))
     
    (if (is-master!? connector host topic) ;here the master sets the assignment flag (sync barrier) only if needed
      (perform-assignments! connector host-name topic ids))
    
    ;if the sync barrier is set this function will block till the assignment process has been completed
    (if (wait-on-partition-flag! connector host topic ids)
      (empheral-get connector (assignments-path topic))
      (if (not curr-assignments)
        (empheral-get connector (assignments-path topic))
        curr-assignments))))
        