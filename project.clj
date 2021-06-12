(defproject nginx-gateway "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[compojure "1.6.2"]
                 [hiccup "1.0.5"]
                 [http-kit "2.5.3"]
                 [org.clojure/clojure "1.10.0"]
                 [org.clojure/data.json "2.3.1"]
                 [ring "1.9.3"]]
  :main ^:skip-aot nginx-gateway.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
