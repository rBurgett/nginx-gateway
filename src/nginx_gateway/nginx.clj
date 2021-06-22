(ns nginx-gateway.nginx
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [nginx-gateway.docker :as docker]
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

(def range-patt #"\[(\d+)-(\d+)\s*,?\s*(\d*)\]")

(defn parse-route
  [route]
  (let [entry-domain (:entry-domain route)
        matches (re-find (re-matcher range-patt entry-domain))
        [_ num-str-1 num-str-2 num-len-str] (if matches matches [])
        num1 (if num-str-1 (Integer/parseInt num-str-1))
        num2 (if num-str-2 (Integer/parseInt num-str-2))
        num-len (if (> (count num-len-str) 0) num-len-str "1")]
    (if (and matches (< num1 num2))
      (->> (range num1 (inc num2))
           (map (fn [n] (str/replace entry-domain range-patt (format (str "%0" num-len "d") n))))
           (map (fn [new-entry-domain] (assoc route :entry-domain new-entry-domain))))
      route)))

(defn parse-routes
  [routes]
  (->> routes
       (map #(parse-route %))
       (flatten)))

(defn write-all-configs
  [sites-enabled-dir streams-enabled-dir routes]
  (let [expanded-routes (parse-routes routes)]
    (write-configs sites-enabled-dir (generate-http-configs expanded-routes))
    (write-configs streams-enabled-dir (generate-https-configs expanded-routes))))

(defn start
  [container-name sites-enabled-dir streams-enabled-dir]
  (let [container-exists (docker/container-exists? container-name)
        container-is-running (docker/container-is-running? container-name)]
    (if container-exists
      (if container-is-running
        [nil true]
        (let [{exit-code :exit err :err} (docker/start container-name)]
          (if (= exit-code 0) [nil true] [err false])))
      (let [{exit-code :exit err :err} (docker/run (format "-d --restart always -p 80:80 -p 443:443 -p 26656:26656/udp -v %s:/etc/nginx/sites-enabled -v %s:/etc/nginx/streams-enabled --name %s rburgett/docker-nginx-tcp-proxy:1.18.0.0" sites-enabled-dir streams-enabled-dir container-name))]
        (if (= exit-code 0) [nil true] [err false])))))

(defn test-config
  [container-name]
  (let [{exit-code :exit err :err} (docker/exec (format "%s nginx -t" container-name))]
    (if (= exit-code 0) [nil true] [err false])))

(defn reload-config
  [container-name]
  (let [{exit-code :exit err :err} (docker/exec (format "%s nginx -s reload" container-name))]
    (if (= exit-code 0) [nil true] [err false])))

(defn test-reload-config
  [container-name]
  (let [[err success] (test-config container-name)
        [err1 success1] (if success (reload-config container-name) [])]
    (cond success1 [nil true]
          err1 [err1 false]
          err [err false]
          :else [nil false])))
