(ns group-redis.core
  (:require [fun-utils.core :refer [fixdelay]]
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


(defn close [connector])
                        
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
      (long (+ heart-beat-freq (/ heart-beat-freq 2))))

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
  (map (fn [path]
         {:path path :val (System/currentTimeMillis)})
       (car/wcar conn
            (car/keys (clojure.string/join [(members-path connector) "/*"])))))


(defn join 
  ([connector]
    (join connector host-name))
  ([{:keys [conn conf state-ref] :as connector} node-name]
    (let [{:keys [heart-beat-freq]} conf
          path (members-path connector node-name)
          val (System/currentTimeMillis)]
	    (car/wcar conn 
	              (car/set path val)
                (car/expire path (calc-ttl heart-beat-freq))
                )
     (dosync (alter state-ref 
            (fn [state]
               (assoc state :members (conj (:members state) {:path path :val val}))
              ))))))
     
	              
	    
(defn heart-beat [connector {:keys [members locks empherals] :as state}]
  "Sends out the heart beat and updates all TTL nodes,
   The members are queried and if any difference a fresh list created for the state
   which is returned"
  (send-updates connector (concat members locks empherals))
  (let [u-members (get-members connector)]
   
    (if (not (= members u-members))
     (merge state {:members u-members})
      state)))


(defn create-group-connector 
  ([host]
   (create-group-connector host {}))
  ([host {:keys [group-name heart-beat-freq] :or {group-name "default-group" heart-beat-freq 5} :as conf}]
  "Starts a connecion to redis, and a hearbeat in the background, and returns 
   a map with keys conn state-ref conf group-name and host"
  (let [state-ref (ref {:members #{} :locks #{} :empherals #{}})
        conf2 (merge conf {:group-name group-name :heart-beat-freq heart-beat-freq})
        c (create-connection host conf)
        connector {:conn c :state-ref state-ref :conf conf2 :host host :group-name group-name}]
    
    (fixdelay (* heart-beat-freq 1000) 
              (dosync 
                (alter state-ref (partial heart-beat connector))))
     connector)))
        
        