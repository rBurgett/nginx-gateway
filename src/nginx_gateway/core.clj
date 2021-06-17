(ns nginx-gateway.core
  (:require [compojure.core :refer [defroutes GET POST]]
            [compojure.route :as route]
            [hiccup.page :refer [html5]]
            [nginx-gateway.constants :as constants]
            [nginx-gateway.docker :as docker]
            [nginx-gateway.nginx-routes :as nginx-routes]
            [nginx-gateway.util :as util]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.util.response :refer [redirect]]
            [nginx-gateway.nginx :as nginx])
  (:gen-class))

(defn flex-row
  [& children]
  [:div {:class "flex-row"} children])

(defn scaffold
  [title body]
  (html5
    [:head
     [:title title]
     [:link {:rel "stylesheet" :type "text/css" :href "/css/sakura-dark.css"}]
     [:link {:rel "stylesheet" :type "text/css" :href "/css/main.css"}]]
    [:body body]))

(defn entry-form
  []
  [:form {:method "POST" :action "/add-route"}
   [:h3 {:class "text-center"} "Add New Route"]
   [:p "Enter domain and IP pairs below. Wildcards are only acceptable in TCP/HTTP routes. Subdomains must be added separately from root domains. Ranges can be added to entry domains using square brackets e.g. sub[0-2].mydomain.com will generate individual routes for sub0.mydomain.com, sub1.mydomain.com, and sub2.mydomain.com."]
   (flex-row
     [:div {:class "flex-grow-1"}
      [:label {:for "entry-domain"} "Entry Domain"]
      [:input {:type "text" :name "entry-domain" :pattern "^[[]a-zA-Z0-9-.*]+\\.\\w+$" :required true :placeholder "e.g. mydomain.com"}]]
     [:div {:class "flex-grow-1" :style "margin-right: 8px;"}
      [:label {:for "entry-port"} "Entry Port"]
      [:input {:type "number" :name "entry-port" :required true :placeholder "e.g. 443"}]])
   (flex-row
     [:div {:class "flex-grow-1"}
      [:label {:for "destination-ip"} "Destination IP"]
      [:input {:type "text" :name "destination-ip" :pattern "^\\d+\\.\\d+\\.\\d+\\.\\d+$" :required true :placeholder "e.g. 192.168.1.100"}]]
     [:div {:class "flex-grow-1"}
      [:label {:for "destination-port"} "Destination Port"]
      [:input {:type "number" :name "destination-port" :required true :placeholder "e.g. 443"}]])
   (flex-row
     [:div {:class "flex-grow-1"}
      [:label {:for "protocol"} "Protocol"]
      [:select {:name "protocol"}
       [:option {:value constants/protocol-http :selected true} (util/get-protocol-name constants/protocol-http)]
       [:option {:value constants/protocol-https} (util/get-protocol-name constants/protocol-https)]
       [:option {:value constants/protocol-udp} (util/get-protocol-name constants/protocol-udp)]]]
     [:div {:class "flex-grow-1"}])
    [:button {:type "submit"} "Save and apply new route"]])

(defn routes-table-row
  [{id :id date :date entry-domain :entry-domain  entry-port :entry-port destination-ip :destination-ip destination-port :destination-port protocol :protocol}]
  [:tr
   [:td date]
   [:td (util/get-protocol-name protocol)]
   [:td entry-domain]
   [:td entry-port]
   [:td destination-ip]
   [:td destination-port]
   [:td {:class "text-center"}
    [:button {:form id} "X"]]])

(defn routes-table
  [routes]
  [:div
   [:h3 {:class "text-center"} "Active Routes"]
   [:table {:style "margin-top: 8px;"}
    [:thead
     [:tr
      [:th "Date Added"]
      [:th "Protocol"]
      [:th "Entry Domain"]
      [:th "Port"]
      [:th "Destination IP"]
      [:th "Port"]
      [:th {:class "text-center"} "Delete"]]]
    [:tbody (->> routes
                 (sort-by :date)
                 (reverse)
                 (map routes-table-row))]
    (map (fn [{id :id}] [:form {:id id :method "POST" :action (str "/delete-route/" id)}]) routes)]])

(defn home
  [routes]
  [:div
   [:h1 {:class "text-center"} constants/site-name]
   (entry-form)
   (routes-table routes)])

(defn on-add-route
  [request]
  (let [entry-domain (get-in request [:form-params "entry-domain"])
        entry-port (get-in request [:form-params "entry-port"])
        destination-ip (get-in request [:form-params "destination-ip"])
        destination-port (get-in request [:form-params "destination-port"])
        protocol (get-in request [:form-params "protocol"])]
    (nginx-routes/add-nginx-route! constants/data-file constants/sites-enabled-dir constants/streams-enabled-dir entry-domain entry-port destination-ip destination-port protocol)
    (redirect "/" 302)))

(defn on-delete-route
  [request]
  (nginx-routes/delete-nginx-route! constants/data-file constants/sites-enabled-dir constants/streams-enabled-dir (get-in request [:params :id]))
  (redirect "/" 302))

(defroutes app
           (GET "/" [] (scaffold constants/site-name (home (nginx-routes/get-nginx-routes))))
           (POST "/add-route" [] (wrap-params on-add-route))
           (POST "/delete-route/:id" [] (wrap-params on-delete-route))
           (route/resources "/")
           (route/not-found "Not found."))

(defn start-server
  [port]
  (println (str "Starting server at http://localhost:" port))
  (run-jetty app {:port port}))

(defn check-environmental-variables!
  [required-variables]
  (when (seq required-variables)
    (let [var (first required-variables)]
      (if-not (System/getenv var)
        (do (println (str "You must set a " var " environmental variable."))
            (System/exit 1))
        (check-environmental-variables! (rest required-variables))))))

(defn -main
  "Starts nginx-gateway"
  [& _]
  (util/ensure-dirs [constants/data-dir
                     constants/sites-enabled-dir
                     constants/streams-enabled-dir])
  (nginx-routes/load-routes constants/data-file)
  (let [[err success] (nginx/start constants/container-name constants/sites-enabled-dir constants/streams-enabled-dir)]
    (if success (start-server 4000)
                (do (println err)
                    (System/exit 1)))))
