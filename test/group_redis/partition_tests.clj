(ns group-redis.partition-tests
  (:require [group-redis.partition :refer :all]
            [group-redis.core :refer :all]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.clojure-test :refer [defspec]]))

(defspec always-unique-partitions 
  1000
  (prop/for-all [members-n (gen/such-that #(not (zero? %)) gen/nat)
                 ids-n (gen/such-that #(not (zero? %)) gen/nat)
                 ids-vec (gen/such-that not-empty (gen/vector gen/nat))
                 members-vec (gen/such-that not-empty (gen/vector gen/nat))]
    (let [ids (take ids-n (set ids-vec))
          members (take members-n (set members-vec))
          dis (distribute-ids members ids)]
      (and 
        (not-empty dis)
        ;check that each item is unique i.e there are no overlaps
        (= (count (flatten (vals dis)))
           (count (into #{} (flatten (vals dis)))))))))

(defn do-assignment [connector topic ids member]
    (join connector member)
  (loop [v (controlled-assignments connector member topic ids) c 0]
    (if (< c 5)
      (do 
        (Thread/sleep 100)
        (recur (controlled-assignments connector member topic ids) (inc c)))
      v)))
    

(defspec controlled-assignments-always-return 
  100
  (prop/for-all [members-n (gen/such-that #(> % 2) gen/nat)
                 ids-n (gen/such-that #(> % 10) gen/nat)]
    (let [connector (create-group-connector "localhost")
          ids (range ids-n (* ids-n 2))
          members (range members-n (-> members-n (* 2) (- 1)))
          do-assignment-f (partial do-assignment connector "test" ids)
          ;for each member run in parallel the do-assignment-f function
          master-assignment (future (do-assignment-f host-name))
          assignments-vec (doall (map deref (map #(future (do-assignment-f %)) members)))]
      ;test that the total of all the assignments done is equal to ids
      ;test that each member got the same assignments map
      (let [a  (rand-nth assignments-vec) r-a (rand-nth assignments-vec)]
        (prn "first " (= a r-a)  " " (if (not (= a r-a)) (str "a " a " r-a " r-a))))
      (let [res 
      (and 
        (= (-> assignments-vec first vals flatten sort) (sort ids))
        (= (rand-nth assignments-vec) (rand-nth assignments-vec)))]
        (if (not res)
          (prn assignments-vec))
        res))))
      
      
               

    