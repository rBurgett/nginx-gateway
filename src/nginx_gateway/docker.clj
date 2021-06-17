(ns nginx-gateway.docker
  (:require [clojure.data.json :as json]
            [clojure.java.shell :as shell]
            [clojure.string :as str]))

(defn str->vec
  [str]
  (str/split (str/trim str) #"\s+"))

(defn make-docker-call
  ([command] (make-docker-call command ""))
  ([command params]
  (apply shell/sh (concat ["docker" command] (str->vec params)))))

(defn version
  []
  (let [{output :out} (make-docker-call "--version")]
    (re-find (re-matcher #"\d+\.\d+\.\d+" output))))

(defn start
  [params]
  (make-docker-call "start" params))

(defn run
  [params]
  (make-docker-call "run" params))

(defn exec
  [params]
  (make-docker-call "exec" params))

(defn inspect
  [params]
  (make-docker-call "inspect" params))

(defn container-is-running?
  [container-name]
  (let [{output :out} (inspect container-name)
        [instance-data] (json/read-str output)]
    (if instance-data
      (get-in instance-data ["State" "Running"])
      false)))

(defn container-exists?
  [container-name]
  (let [{output :out} (inspect container-name)
        [instance-data] (json/read-str output)]
    (if instance-data true false)))
