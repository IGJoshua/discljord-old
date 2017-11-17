(defproject discljord "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-RC1"]
                 [org.clojure/data.json "0.2.6"]
                 [http-kit "2.2.0"]
                 [http-kit.fake "0.2.1"]
                 [org.clojure/core.async "0.3.442"]
                 [stylefruits/gniazdo "1.0.1"]]
  :main ^:skip-aot discljord.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
