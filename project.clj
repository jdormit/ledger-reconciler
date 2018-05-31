(defproject ledger-reconciler "0.1.0-SNAPSHOT"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/data.csv "0.1.4"]
                 [org.clojure/tools.cli "0.3.7"]]
  :main ^:skip-aot ledger-reconciler.core
  :uberjar-name "ledger-reconciler.jar"
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
