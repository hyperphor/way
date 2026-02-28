(ns hyperphor.way.transit
  (:require [cognitect.transit :as transit]
            [hyperphor.multitool.core :as u])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]))

(defn to-str
  [thing]
  (let [out (ByteArrayOutputStream. 4096)
        writer (transit/writer out :json)] ;:msgpack is shorter but a bit opaque
    (transit/write writer thing)
    (.toString out)))

(defn from-str
  [transit]
  (let [input-stream (ByteArrayInputStream. (.getBytes transit))
        reader (transit/reader input-stream :json)]
    (transit/read reader)))


