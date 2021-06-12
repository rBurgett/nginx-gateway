(ns nginx-gateway.nginx
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [nginx-gateway.constants :as constants]))

(defn ssl-preread-name-block
  [items]
  (concat ["map $ssl_preread_server_name $name {"]
          (map (fn [{ domain :entry-domain } i] (str "    " domain " backend" i ";")) items (range))
          ["}"]))

(defn upstream-block
  [{ip :destination-ip port :destination-port} i]
  [(str "upstream backend" i " {")
   (str "   server " ip ":" port ";")
   "}"]);

(defn upstream-blocks
  [items]
  (flatten (map (fn [data i] (upstream-block data i)) items (range))))

(defn https-server-block
  [port]
  ["server {"
   (str "    listen " port ";")
   "    proxy_pass $name;"
   "    ssl_preread on;"
   "}"])

(defn http-server-block
  [entry-port destination-ip destination-port items]
  ["server {"
   (str "    listen " entry-port ";")
   (str "    server_name " (str/join " " (map :entry-domain items)) ";")
   "    location / {"
   "        proxy_set_header X-Forwarded-For $remote_addr;"
   "        proxy_http_version 1.1;"
   "        proxy_set_header Upgrade $http_upgrade;"
   "        proxy_set_header Connection 'upgrade';"
   "        proxy_set_header Host $host;"
   "        proxy_cache_bypass $http_upgrade;"
   (str "        proxy_pass http://" destination-ip (if (= 80 destination-port) "" (str ":" destination-port)) ";")
   "    }"
   "}"])

(defn make-config
  [items]
  (->> items
       (flatten)
       (str/join "\n")))

(defn write-configs
  [dir configs]
  (doseq [i (take (count configs) (range))]
    (spit (str dir "/config" i ".nginx") (nth configs i))))

(defn clear-config-folder
  [dir]
  (doseq [f (seq (.listFiles (io/file dir)))] (.delete f)))

(defn generate-https-configs
  [routes]
  (let [https-routes (filter (fn [{ p :protocol }] (= p constants/protocol-https)) routes)
        by-port (group-by :entry-port https-routes)]
    (map (fn [[port rr]] (make-config [(ssl-preread-name-block rr)
                                       (upstream-blocks rr)
                                       (https-server-block port)])) (seq by-port))))

(defn generate-http-configs
  [routes]
  (let [http-routes (filter (fn [{ p :protocol }] (= p constants/protocol-http)) routes)
        by-port-ip-port (group-by #(str (:entry-port %) "-" (:destination-ip %) "-" (:destination-port %)) http-routes)]
    ;(println (map (fn [[_ rr]] (first rr)) (seq by-port-ip-port)))))
    (map (fn [[_ rr]] (let [{entry-port :entry-port
                                 destination-ip :destination-ip
                                 destination-port :destination-port} (first rr)]
                            ;(println entry-port destination-ip destination-port))) by-port-ip-port)))
                            (make-config (http-server-block entry-port destination-ip destination-port rr)))) (seq by-port-ip-port))))

(defn generate-all-configs
  [routes]
  (concat (generate-https-configs routes) (generate-http-configs routes)))

(defn write-all-configs
  [sites-enabled-dir streams-enabled-dir routes]
  (write-configs sites-enabled-dir (generate-http-configs routes))
  (write-configs streams-enabled-dir (generate-https-configs routes)))
