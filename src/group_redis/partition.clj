(ns group-redis.partition)

;Env n members and a list of paths/ids, each member should get a near to equal share of ids,
;but no member can have an id that another member already has.


(defn upper-divisor [t n]
  (if (= n 0)
    0
   (Math/ceil (/ t n))))

(defn distribute-ids [members ids]
  (if (not (or (empty? members) (empty? ids)))
    (let [d (upper-divisor (count ids) (count members))]
     (apply hash-map (interleave  (set members) (partition-all d (set ids)))))))

