(ns group-redis.partition)

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



