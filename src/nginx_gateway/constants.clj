(ns nginx-gateway.constants)

; UI Strings
(def site-name "NGINX Gateway")

(def container-name "nginx-gateway")

; File paths
(def data-dir (str (System/getProperty "user.home") "/.nginx-gateway"))
(def sites-enabled-dir (str data-dir "/sites-enabled"))
(def streams-enabled-dir (str data-dir "/streams-enabled"))
(def data-file (str data-dir "/routes.json"))

; Protocols
(def protocol-http "http")
(def protocol-https "https")
(def protocol-udp "udp")
