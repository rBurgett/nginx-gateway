(ns nginx-gateway.util
  (:require [clojure.java.io :as io]
            [nginx-gateway.constants :as constants])
  (:import (java.util Date UUID)))

(defn generate-uuid
  []
  (.toString (UUID/randomUUID)))

(defn now
  []
  (.getTime (Date.)))

(defn get-protocol-name
  [protocol]
  (cond
    (= protocol constants/protocol-http) "TCP/HTTP"
    (= protocol constants/protocol-https) "TCP/HTTPS"
    (= protocol constants/protocol-udp) "UDP"
    :else protocol))

(defn ensure-dirs
  [paths]
  (when (seq paths)
    (let [d (io/as-file (first paths))]
      (when (not (.exists d))
        (.mkdir d))
      (recur (rest paths)))))
