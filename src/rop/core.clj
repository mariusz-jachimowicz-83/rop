(ns rop.core
  "Implementation of Railway Oriented Programming based on
   https://gist.github.com/ah45/7518292c620679c460557a7038751d6d"
  (:require
    [cats.builtin]
    [cats.core :as m]
    [cats.monad.either :as meither]))


(def succeed
  "Convert a value into a two-track (success) result"
  meither/right)


(def fail
  "Convert a value into a two-track (failure) result"
  meither/left)


(def success?
  "Returns true if the given two-track value is a success"
  meither/right?)


(def failure?
  "Returns true if the given two-track value is a failure"
  meither/left?)


(defn switch
  "Converts a normal fn into a switch (one-track input, two-track output)"
  [f]
  (comp succeed f))


(defn tee
  "Returns a fn that calls f on its argument and returns its argument.
   Converts otherwise 'dead-end' fns into one-track fns."
  [f]
  (fn [v]
    (f v)
    v))


(def dead
  "A shortcut for calling (rop/switch (rop/tee send-email!)"
  (comp switch tee))


(defn >>=
  "An infix version of bind for piping two-track values into switch fns. Can be used to pipe two-track values
   through a series of switch fns. First is an input hash-map it will be passed throgh switch fns.
   Rest parameters as switch fns."
  [input & fns]
  (-> m/>>=
      (apply (into [(succeed input)] fns))
      m/extract))


(defn- extract-output-keys
  [result output-keys]
  (if (map? result)
    (select-keys result output-keys)
    (map #(select-keys % output-keys) result)))


(defn >>=*
  "An infix version of bind for piping two-track values into switch fns. Can be used to pipe two-track values
   through a series of switch fns. A result of this function is Ring's response.
   First parameter is a success key (it will be used as :body in result hash-map) or a tuple with success-key and
   output-keys (at the end `select-keys` will be applied on a success result with these `output-keys`).
   Second is an input hash-map it will be passed throgh switch fns. Rest parameters as switch fns."
  [success-key-or-tuple input & fns]
  (let [[success-key output-keys] (if (vector? success-key-or-tuple) success-key-or-tuple [success-key-or-tuple nil])
        result (apply m/>>= (into [(succeed input)] fns))
        extracted-result (m/extract result)
        format-output (fn [success-result]
                        (if (coll? output-keys)
                          (extract-output-keys success-result output-keys)
                          success-result))]
    (if (success? result)
      {:body (format-output (get extracted-result success-key))
       :status (get-in extracted-result [:response :status] 200)
       :headers (get-in extracted-result [:response :headers] {})}
      extracted-result)))
