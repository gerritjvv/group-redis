(ns group-redis.core
  (:require [fun-utils.core :refer [fixdelay]]
            [clojure.core.async :refer [chan >! <! >!! go mult tap untap sliding-buffer close!]]
            [taoensso.carmine :as car :refer [wcar]])
  (:import [java.net InetAddress]))

;(get-conf2 :etl-redis-port 6379

;Paths
; /[group-name]/members/[node-name]
; /[group-name]/empherals/[path..]/...
; /[group-name]/locks/[path..]/...

(defonce host-name (-> (InetAddress/getLocalHost) (.getHostName)))

(defn members-path 
  ([connector member]
    (clojure.string/join "/" [(members-path connector) member]))
  ([{:keys [group-name]}]
  (clojure.string/join ["/" (clojure.string/join "/" [group-name "members"])])))

(defn empherals-path [{:keys [group-name]} & path]
  (clojure.string/join ["/" (clojure.string/join "/" (flatten [group-name "empherals" path]))]))

(defn lock-path [{:keys [group-name]} & path]
  (clojure.string/join ["/" (clojure.string/join "/" (flatten [group-name "locks" path]))]))


(defn close [{:keys [state-ref member-event-ch heart-beat-ch]}]
  ;delete connection's state
  (close! heart-beat-ch)
  (close! member-event-ch)
  
  (dosync (alter state-ref (fn [state] {}))))
                        
(defn create-connection 
  "Creates a redis connection the default port used is 6379"
  ([host]
    (create-connection host {}))
  ([host {:keys [port max-active password timeout] :or {port 6379 max-active 20 timeout 4000}}]
                         {:pool {:max-active max-active}
                          :spec {:host  host
                               :port    port
                               :password password
                               :timeout  timeout}}))


(defn calc-ttl [heart-beat-freq]
  "Takes the heart-beat-freq and calculates the time to live relative to the heart beat"
      (long (+ heart-beat-freq (/ heart-beat-freq 2))))


(defn empheral-set [{:keys [conn state-ref] :as connector} path val]
  (let [final-path (empherals-path connector path)]
    (dosync (alter state-ref (fn [state] (assoc state :empherals (into #{} (conj (:empherals state) {:path final-path :val val}))))))
    (car/wcar conn (car/set final-path val))))

(defn empheral-get [connector path ]
  (->> connector :state-ref deref :empherals (filter #(= (:path %) (empherals-path connector path) )) first :val))

(defn release 
  "Releases the lock, return true if the lock was released, false otherwise"
  ([connecor path]
    (release connecor host-name path))
  ([{:keys [conn conf state-ref] :as connector} member path]
  (let [lock-path (lock-path connector path)
        lock-val (car/wcar conn (car/get lock-path))]
    
    (if (= (:member lock-val) member)
      (do 
        ;remove from state
        (try (dosync (alter state-ref (fn [state]
                                   (assoc state :locks (drop-while #(= (:path %) lock-path) (:locks state))))))
          (finally ;delete from redis
                 (car/wcar conn (car/del lock-path))))
        true)
      false))))
      
  
(defn lock 
  "Returns true if the lock was obtained"
  ([connector path]
    (lock connector host-name path))
  ([{:keys [conn conf state-ref] :as connector} member path]
  (let [lock-path (lock-path connector path)
        {:keys [heart-beat-freq]} conf
        expire (calc-ttl heart-beat-freq)
        val {:member member :ts (System/currentTimeMillis)}
		    [has-lock? _] (car/wcar conn
                              (car/setnx lock-path val)
		                                   (car/expire lock-path expire))]
    
        (if (= has-lock? 1)
          (dosync (alter state-ref 
            (fn [state]
               (assoc state :locks (conj (:locks state) {:path lock-path :val val}))))))
        
        (= has-lock? 1))))
    


(defn send-updates [{:keys [conn conf] :as connector} records]
  "Iterates trough all records calling set using the keys [path val] in each record"
  (let [{:keys [heart-beat-freq]} conf
        expire (calc-ttl heart-beat-freq) ]
	  (car/wcar conn 
	           (doseq [{:keys [path val]} records]
	             (car/set path val)
               (car/expire path expire)
              ))))


(defn get-members [{:keys [conn] :as connector}]
  (into #{} (map (fn [path]
									 {:path path :val (System/currentTimeMillis)})
                 (car/wcar conn
									            (car/keys (clojure.string/join [(members-path connector) "/*"]))))))

(defn join 
  ([connector]
    (join connector host-name))
  ([{:keys [conn conf member-event-ch state-ref] :as connector} node-name]
    (let [{:keys [heart-beat-freq]} conf
          path (members-path connector node-name)
          val (System/currentTimeMillis)]
	    (car/wcar conn 
	              (car/set path val)
                (car/expire path (calc-ttl heart-beat-freq))
                )
     (dosync (alter state-ref 
            (fn [state]
               (assoc state :members (into #{} (conj (:members state) {:path path :val val})))
              )))
     (>!! member-event-ch {:left-members #{} :joined-members #{node-name}})
     )))
     
       
	    
(defn- members-left [members u-members]
  (clojure.set/difference (into #{} (map :path members)) (into #{} (map :path u-members))))

(defn- members-joined [members u-members]
  (clojure.set/difference (into #{} (map :path u-members)) (into #{} (map :path members))))

(defn heart-beat [connector {:keys [members locks empherals] :as state}]
  "Sends out the heart beat and updates all TTL nodes,
   The members are queried and if any difference a fresh list created for the state
   which is returned"
  (send-updates connector (concat members locks empherals))
    ;(prn "calling lef-members " members " " u-members)
    (let [u-members (get-members connector)
          left-members (if (> (count members) 0) (members-left members u-members) #{})
          joined-members (members-joined members u-members)]
        
         ;publish event
        (if (or (> (count left-members) 0) (> (count joined-members) 0))
          (>!! (:member-event-ch connector) {:left-members left-members :joined-members joined-members}))

        (if (not (= members u-members))
		      (merge state {:members u-members})
		           state)))

(defn unregister-event-ch [{:keys [member-event-mult]} ch]
  (untap member-event-mult ch))

(defn register-member-event-ch [{:keys [member-event-mult]}]
  "Returns a channel from which member register events can be read"
  (let [ch (chan (sliding-buffer 10))]
    (tap member-event-mult ch)
    ch))

(defn create-group-connector 
  ([host]
   (create-group-connector host {}))
  ([host {:keys [group-name heart-beat-freq] :or {group-name "default-group" heart-beat-freq 5} :as conf}]
  "Starts a connecion to redis, and a hearbeat in the background, and returns 
   a map with keys conn state-ref conf group-name and host"
  (let [state-ref (ref {:members #{} :locks #{} :empherals #{}})
        conf2 (merge conf {:group-name group-name :heart-beat-freq heart-beat-freq})
        c (create-connection host conf)
        member-event-ch (chan (sliding-buffer 10))
        member-event-mult (mult member-event-ch)
        connector {:conn c :state-ref state-ref :conf conf2 :host host :group-name group-name
                   :member-event-ch member-event-ch :member-event-mult member-event-mult}
    
		    heart-beat-ch (fixdelay (* heart-beat-freq 1000) 
								              (dosync 
								                (alter state-ref (partial heart-beat connector))))]
      (assoc connector :heart-beat-ch heart-beat-ch))))
        
        