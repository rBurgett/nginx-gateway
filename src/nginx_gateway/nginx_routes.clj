(ns nginx-gateway.nginx-routes
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [nginx-gateway.nginx :as nginx]
            [nginx-gateway.util :as util]))

(defn generate-nginx-route
  ([entry-domain entry-port destination-ip destination-port protocol]
   (generate-nginx-route entry-domain entry-port destination-ip destination-port protocol (util/now) (util/generate-uuid)))
  ([entry-domain entry-port destination-ip destination-port protocol date id]
   {:id id
    :entry-domain entry-domain
    :entry-port entry-port
    :destination-ip destination-ip
    :destination-port destination-port
    :protocol protocol
    :date date}))

(def nginx-routes (atom []))

(defn load-routes
  [data-file]
  (let [f (io/as-file data-file)
        data-file-exists (.exists f)]
    (when (not data-file-exists) (spit data-file (json/write-str [])))
    (reset! nginx-routes (->> (json/read-str (slurp data-file))
                              (map (fn [route] (generate-nginx-route
                                                 (get route "entry-domain")
                                                 (get route "entry-port")
                                                 (get route "destination-ip")
                                                 (get route "destination-port")
                                                 (get route "protocol")
                                                 (get route "date")
                                                 (get route "id"))))))))

(defn get-nginx-routes
  []
  @nginx-routes);

(defn add-nginx-route!
  [data-file sites-enabled-dir streams-enabled-dir entry-domain entry-port destination-ip destination-port protocol]
  (let [new-routes (swap! nginx-routes #(conj % (generate-nginx-route entry-domain entry-port destination-ip destination-port protocol)))]
    (spit data-file (with-out-str (json/pprint new-routes)))
    (nginx/clear-config-folder sites-enabled-dir)
    (nginx/write-all-configs sites-enabled-dir streams-enabled-dir new-routes)))

(defn delete-nginx-route!
  [data-file sites-enabled-dir streams-enabled-dir id]
  (let [new-routes (swap! nginx-routes (fn [routes] (filter #(not= (:id %) id) routes)))]
    (spit data-file (json/write-str new-routes))
    (nginx/clear-config-folder sites-enabled-dir)
    (nginx/write-all-configs sites-enabled-dir streams-enabled-dir new-routes)
    new-routes))
