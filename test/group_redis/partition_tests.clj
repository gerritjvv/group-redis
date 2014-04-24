(ns group-redis.partition-tests
  (:require [group-redis.partition :refer :all]
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
    